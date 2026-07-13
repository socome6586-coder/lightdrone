package com.lightdrone.controller;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Notice;
import com.lightdrone.service.CartService;
import com.lightdrone.service.GuestCartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.NoticeService;
import com.lightdrone.service.ProductCategoryService;
import com.lightdrone.service.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final int DUPLICATE_CHECK_LIMIT_PER_MINUTE = 60;
    private static final Duration DUPLICATE_CHECK_WINDOW = Duration.ofMinutes(1);

    private final MemberService memberService;
    private final CartService cartService;
    private final GuestCartService guestCartService;
    private final ProductCategoryService productCategoryService;
    private final NoticeService noticeService;
    private final RequestRateLimiter requestRateLimiter;
 
    /** 카테고리별 세부 카테고리 목록 (AJAX — 제품 등록/수정 폼용) */
    @GetMapping("/categories/subcategories")
    public ResponseEntity<List<String>> getSubcategories(@RequestParam String category) {
        return ResponseEntity.ok(productCategoryService.getSubcategoryNames(category));
    }

    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username,
                                                              HttpServletRequest request) {
        if (duplicateCheckRateLimited(request)) {
            return duplicateCheckTooManyRequests();
        }
        return ResponseEntity.ok(Map.of("available", memberService.isUsernameAvailable(username)));
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email,
                                                           HttpServletRequest request) {
        if (duplicateCheckRateLimited(request)) {
            return duplicateCheckTooManyRequests();
        }
        return ResponseEntity.ok(Map.of("available", memberService.isEmailAvailable(email)));
    }

    @GetMapping("/check-phone")
    public ResponseEntity<Map<String, Boolean>> checkPhone(@RequestParam String phone,
                                                           HttpServletRequest request) {
        if (duplicateCheckRateLimited(request)) {
            return duplicateCheckTooManyRequests();
        }
        return ResponseEntity.ok(Map.of("available", memberService.isPhoneAvailable(phone)));
    }

    private boolean duplicateCheckRateLimited(HttpServletRequest request) {
        return !requestRateLimiter.tryConsume(
                "duplicate-check",
                request,
                DUPLICATE_CHECK_LIMIT_PER_MINUTE,
                DUPLICATE_CHECK_WINDOW
        );
    }

    private ResponseEntity<Map<String, Boolean>> duplicateCheckTooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("available", false));
    }

    /** 장바구니 수량 (비회원은 세션 장바구니 기준) */
    @GetMapping("/cart/count")
    public ResponseEntity<Map<String, Integer>> cartCount(
            @AuthenticationPrincipal UserDetails userDetails, HttpSession session) {
        int count = (userDetails != null)
                ? cartService.getCartCount(userDetails.getUsername())
                : guestCartService.getCount(session);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** 최근 공지사항 5건 (플로팅 사이드바용) */
    @GetMapping("/notices/recent")
    public ResponseEntity<List<Map<String, Object>>> recentNotices() {
        List<Notice> notices = noticeService.getRecentNotices();
        List<Map<String, Object>> result = notices.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /** 장바구니 항목 목록 (플로팅 사이드바용) */
    @GetMapping("/cart/items")
    public ResponseEntity<List<Map<String, Object>>> cartItems(
            @AuthenticationPrincipal UserDetails userDetails, HttpSession session) {
        List<CartItem> items = (userDetails != null)
                ? cartService.getCartItems(userDetails.getUsername())
                : guestCartService.getCartItems(session);
        List<Map<String, Object>> result = items.stream().map(item -> {
            long extra = (item.getSelectedOptionExtraPrice() != null) ? item.getSelectedOptionExtraPrice() : 0L;
            long basePrice = item.getProduct().getEffectivePrice();
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.getId());
            m.put("productId", item.getProduct().getId());
            m.put("name", item.getProduct().getName());
            // 옵션 추가금액 포함 단가 (사이드바에 옵션 포함 금액 표시)
            m.put("price", basePrice + extra);
            m.put("optionName", item.getSelectedOptionName());
            m.put("priceOnRequest", item.getProduct().isPriceOnRequest());
            m.put("quantity", item.getQuantity());
            m.put("imageUrl", item.getProduct().getImageUrl());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /** 장바구니 항목 삭제 (사이드바 AJAX용) */
    @PostMapping("/cart/remove")
    public ResponseEntity<Map<String, Object>> removeCartItem(
            @RequestParam Long cartItemId,
            @AuthenticationPrincipal UserDetails userDetails, HttpSession session) {
        try {
            int newCount;
            if (userDetails != null) {
                cartService.removeFromCart(userDetails.getUsername(), cartItemId);
                newCount = cartService.getCartCount(userDetails.getUsername());
            } else {
                guestCartService.remove(session, cartItemId);
                newCount = guestCartService.getCount(session);
            }
            return ResponseEntity.ok(Map.of("success", true, "cartCount", newCount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** 장바구니 담기 (AJAX — JSON 응답, 회원/비회원 모두 가능) */
    @PostMapping("/cart/add")
    public ResponseEntity<Map<String, Object>> addToCartAjax(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            @AuthenticationPrincipal UserDetails userDetails, HttpSession session) {
        try {
            int count;
            if (userDetails != null) {
                cartService.addToCart(userDetails.getUsername(), productId, quantity);
                count = cartService.getCartCount(userDetails.getUsername());
            } else {
                guestCartService.add(session, productId, quantity, null);
                count = guestCartService.getCount(session);
            }
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("count", count);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.ok(body);
        }
    }
}
