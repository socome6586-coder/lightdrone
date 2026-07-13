package com.lightdrone.service;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Product;
import com.lightdrone.dto.GuestCartItem;
import com.lightdrone.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 비회원(게스트) 장바구니 서비스.
 *
 * 회원 장바구니는 cart_items 테이블(CartService)을 사용하지만,
 * 비회원은 로그인 정보가 없으므로 장바구니를 HTTP 세션에 보관한다.
 * 옵션 검증/조합명 생성 로직은 CartService.resolveOptions()를 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class GuestCartService {

    public static final String SESSION_KEY = "GUEST_CART";

    private final ProductRepository productRepository;
    private final CartService cartService;

    @SuppressWarnings("unchecked")
    private List<GuestCartItem> store(HttpSession session) {
        Object o = session.getAttribute(SESSION_KEY);
        if (o instanceof List) {
            return (List<GuestCartItem>) o;
        }
        List<GuestCartItem> list = new ArrayList<>();
        session.setAttribute(SESSION_KEY, list);
        return list;
    }

    private long nextLineId(List<GuestCartItem> list) {
        long max = 0L;
        for (GuestCartItem gi : list) max = Math.max(max, gi.getLineId());
        return max + 1;
    }

    /** 게스트 장바구니에 담기 (회원 addToCart와 동일한 검증/병합 규칙) */
    @Transactional(readOnly = true)
    public void add(HttpSession session, Long productId, int quantity, List<Long> optionIds) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        PurchasePolicy.validate(product, quantity);

        CartService.OptionSelection sel = cartService.resolveOptions(product, optionIds);

        List<GuestCartItem> list = store(session);
        for (GuestCartItem gi : list) {
            if (gi.getProductId().equals(productId)
                    && Objects.equals(gi.getSelectedOptionName(), sel.combinedName)) {
                long newTotal = (long) gi.getQuantity() + quantity;
                if (newTotal > product.getStock()) {
                    throw new IllegalArgumentException(
                        "재고가 부족합니다. (재고: " + product.getStock() + "개, 이미 담긴 수량: " + gi.getQuantity() + "개)");
                }
                gi.setQuantity((int) newTotal);
                return;
            }
        }

        if (quantity > product.getStock()) {
            throw new IllegalArgumentException("재고가 부족합니다. (재고: " + product.getStock() + "개)");
        }

        GuestCartItem gi = new GuestCartItem();
        gi.setLineId(nextLineId(list));
        gi.setProductId(productId);
        gi.setQuantity(quantity);
        gi.setOptionIds(optionIds != null ? new ArrayList<>(optionIds) : null);
        gi.setSelectedOptionId(sel.firstOptionId);
        gi.setSelectedOptionName(sel.combinedName);
        gi.setSelectedOptionExtraPrice(sel.extraSum);
        list.add(gi);
    }

    /** 화면 렌더링용 — 세션 항목을 (비영속) CartItem 으로 변환. id 에는 lineId 를 넣는다. */
    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(HttpSession session) {
        List<CartItem> result = new ArrayList<>();
        for (GuestCartItem gi : store(session)) {
            Product p = productRepository.findById(gi.getProductId()).orElse(null);
            if (p == null) continue;
            CartItem ci = CartItem.builder()
                    .product(p)
                    .quantity(gi.getQuantity())
                    .selectedOptionId(gi.getSelectedOptionId())
                    .selectedOptionName(gi.getSelectedOptionName())
                    .selectedOptionExtraPrice(gi.getSelectedOptionExtraPrice() != null ? gi.getSelectedOptionExtraPrice() : 0L)
                    .build();
            ci.setId(gi.getLineId());
            result.add(ci);
        }
        return result;
    }

    public int getCount(HttpSession session) {
        return store(session).size();
    }

    @Transactional(readOnly = true)
    public void updateQuantity(HttpSession session, long lineId, int quantity) {
        List<GuestCartItem> list = store(session);
        list.removeIf(gi -> gi.getLineId() == lineId && quantity <= 0);
        for (GuestCartItem gi : list) {
            if (gi.getLineId() == lineId && quantity > 0) {
                Product product = productRepository.findById(gi.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
                PurchasePolicy.validate(product, quantity);
                gi.setQuantity(quantity);
                return;
            }
        }
    }

    public void remove(HttpSession session, long lineId) {
        store(session).removeIf(gi -> gi.getLineId() == lineId);
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    /**
     * 로그인 시 세션의 게스트 장바구니를 회원 장바구니로 병합한 뒤 세션에서 비운다.
     * 재고 초과 등으로 개별 항목 병합이 실패해도 로그인 흐름을 막지 않도록 항목별로 예외를 무시한다.
     */
    @Transactional
    public void mergeIntoMemberCart(HttpSession session, String username) {
        Object o = session.getAttribute(SESSION_KEY);
        if (!(o instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<GuestCartItem> list = (List<GuestCartItem>) o;
        if (list.isEmpty()) {
            session.removeAttribute(SESSION_KEY);
            return;
        }
        for (GuestCartItem gi : list) {
            try {
                cartService.addToCart(username, gi.getProductId(), gi.getQuantity(), gi.getOptionIds());
            } catch (Exception ignored) {
                // 개별 항목 병합 실패는 무시 (재고 초과 등)
            }
        }
        session.removeAttribute(SESSION_KEY);
    }
}
