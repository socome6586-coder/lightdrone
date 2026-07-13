package com.lightdrone.service;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.ProductOption;
import com.lightdrone.repository.CartItemRepository;
import com.lightdrone.repository.ProductOptionRepository;
import com.lightdrone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final MemberService memberService;
    private final PricingService pricingService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<CartItem> getCartItems(String username) {
        if (username == null) return Collections.emptyList();
        try {
            // Member 조회(findByUsername) 없이 username 컬럼으로 직접 조회
            // → UsernameNotFoundException 발생 불가, 트랜잭션 오염 없음
            return cartItemRepository.findByMemberUsernameOrderByCreatedAtDesc(username);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int getCartCount(String username) {
        if (username == null) return 0;
        try {
            // Member 조회(findByUsername) 없이 username 컬럼으로 직접 카운트
            // → UsernameNotFoundException 발생 불가, 트랜잭션 오염 없음
            return cartItemRepository.countByMemberUsername(username);
        } catch (Exception e) {
            return 0;
        }
    }

    @Transactional
    public void addToCart(String username, Long productId, int quantity) {
        addToCart(username, productId, quantity, (List<Long>) null);
    }

    @Transactional
    public void addToCart(String username, Long productId, int quantity, List<Long> optionIds) {
        Member member = memberService.findByUsername(username);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        PurchasePolicy.validate(product, quantity);

        // 선택 옵션 조합 (옵션 제목별 1개씩) 검증 + 스냅샷 생성
        OptionSelection sel = resolveOptions(product, optionIds);

        var existing = cartItemRepository.findByMemberAndProductAndOptionName(member, product, sel.combinedName);
        int currentQty = existing.map(CartItem::getQuantity).orElse(0);
        long newTotal = (long) currentQty + quantity;

        if (newTotal > product.getStock()) {
            if (currentQty > 0) {
                throw new IllegalArgumentException(
                    "재고가 부족합니다. (재고: " + product.getStock() + "개, 이미 담긴 수량: " + currentQty + "개)");
            } else {
                throw new IllegalArgumentException(
                    "재고가 부족합니다. (재고: " + product.getStock() + "개)");
            }
        }

        existing.ifPresentOrElse(
                item -> item.setQuantity((int) newTotal),
                () -> cartItemRepository.save(CartItem.builder()
                        .member(member)
                        .product(product)
                        .quantity(quantity)
                        .selectedOptionId(sel.firstOptionId)
                        .selectedOptionName(sel.combinedName)
                        .selectedOptionExtraPrice(sel.extraSum)
                        .build())
        );
    }

    /** 선택된 옵션 조합 결과 (조합명 + 추가금액 합계 + 대표 옵션 ID) */
    public static class OptionSelection {
        public final String combinedName;
        public final long extraSum;
        public final Long firstOptionId;
        OptionSelection(String combinedName, long extraSum, Long firstOptionId) {
            this.combinedName = combinedName;
            this.extraSum = extraSum;
            this.firstOptionId = firstOptionId;
        }
    }

    /**
     * 선택된 옵션들을 검증하고 "조합명 + 추가금액 합계"로 만든다.
     * 옵션은 자유 다중선택(체크박스)이며 0개 이상 선택 가능하다.
     * 예) A·B·C(각 5만원) 모두 선택 → 조합명 "A / B / C", 추가금액 15만원.
     */
    public OptionSelection resolveOptions(Product product, List<Long> optionIds) {
        List<ProductOption> selected = new java.util.ArrayList<>();
        if (optionIds != null) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (Long oid : optionIds) {
                if (oid == null || !seen.add(oid)) continue;   // null·중복 선택 무시
                ProductOption po = productOptionRepository.findById(oid)
                        .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다."));
                if (!po.getProduct().getId().equals(product.getId())) {
                    throw new IllegalArgumentException("잘못된 옵션입니다.");
                }
                selected.add(po);
            }
        }

        if (selected.isEmpty()) {
            return new OptionSelection(null, 0L, null);
        }

        selected.sort(java.util.Comparator.comparingInt(ProductOption::getSortOrder)
                .thenComparing(ProductOption::getId));
        StringBuilder sb = new StringBuilder();
        long extraSum = 0L;
        for (ProductOption po : selected) {
            if (sb.length() > 0) sb.append(" / ");
            if (po.getGroupName() != null && !po.getGroupName().isBlank()) {
                sb.append(po.getGroupName().trim()).append(": ");
            }
            sb.append(po.getName());
            extraSum += (po.getExtraPrice() != null ? po.getExtraPrice() : 0L);
        }
        return new OptionSelection(sb.toString(), extraSum, selected.get(0).getId());
    }

    @Transactional
    public void updateQuantity(String username, Long cartItemId, int quantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
        if (!item.getMember().getUsername().equals(username)) {
            throw new SecurityException("권한이 없습니다.");
        }
        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            PurchasePolicy.validate(item.getProduct(), quantity);
            item.setQuantity(quantity);
        }
    }

    @Transactional
    public void removeFromCart(String username, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
        if (!item.getMember().getUsername().equals(username)) {
            throw new SecurityException("권한이 없습니다.");
        }
        cartItemRepository.delete(item);
    }

    /** 등급 할인 미적용 합계 (비회원/하위호환) */
    public long getTotalPrice(List<CartItem> items) {
        return items.stream().mapToLong(CartItem::getTotalPrice).sum();
    }

    /**
     * 회원 등급 상시 할인을 적용한 장바구니 합계.
     * 주문 스냅샷(OrderService)과 동일하게 (상품 판매가+옵션 추가금) 단가에
     * 등급 할인을 적용한 뒤 수량을 곱한다.
     */
    public long getTotalPrice(List<CartItem> items, Member member) {
        return items.stream().mapToLong(i -> discountedLineTotal(i, member)).sum();
    }

    /** 회원 등급 할인을 적용한 단가(원) — (상품 판매가+옵션 추가금) 기준 */
    public long discountedUnitPrice(CartItem item, Member member) {
        long base = (item.getProduct() != null) ? item.getProduct().getEffectivePrice() : 0L;
        long extra = (item.getSelectedOptionExtraPrice() != null) ? item.getSelectedOptionExtraPrice() : 0L;
        return pricingService.applyTo(base + extra, member);
    }

    /** 회원 등급 할인을 적용한 항목 합계(원) — 단가 × 수량 */
    public long discountedLineTotal(CartItem item, Member member) {
        return discountedUnitPrice(item, member) * item.getQuantity();
    }
}
