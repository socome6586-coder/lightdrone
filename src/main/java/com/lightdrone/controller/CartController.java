package com.lightdrone.controller;

import com.lightdrone.domain.CartItem;
import com.lightdrone.service.CartService;
import com.lightdrone.service.GuestCartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.PricingService;
import static com.lightdrone.service.OrderService.SHIPPING_FEE;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final GuestCartService guestCartService;
    private final MemberService memberService;
    private final PricingService pricingService;

    @GetMapping
    public String cart(@AuthenticationPrincipal UserDetails userDetails, HttpSession session, Model model) {
        boolean loggedIn = userDetails != null;
        String username = loggedIn ? userDetails.getUsername() : null;
        // 비회원으로 담아둔 항목이 있으면 로그인 시 회원 장바구니로 병합
        if (loggedIn) {
            guestCartService.mergeIntoMemberCart(session, username);
        }
        List<CartItem> items = loggedIn
                ? cartService.getCartItems(username)
                : guestCartService.getCartItems(session);
        // 유료배송 상품 분류
        java.util.List<String> paidShippingNames = items.stream()
                .filter(ci -> ci.getProduct() != null && !ci.getProduct().isFreeShipping())
                .map(ci -> ci.getProduct().getName())
                .collect(java.util.stream.Collectors.toList());
        long shippingFee = (items.isEmpty() || paidShippingNames.isEmpty()) ? 0L : SHIPPING_FEE;
        var member = loggedIn ? memberService.findByUsername(username) : null;
        model.addAttribute("cartItems", items);
        model.addAttribute("totalPrice", cartService.getTotalPrice(items, member));
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("paidShippingNames", paidShippingNames);
        model.addAttribute("cartCount", items.size());
        model.addAttribute("member", member);
        model.addAttribute("gradeDiscountPercent", pricingService.discountPercentFor(member));
        return "cart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") int quantity,
                            @AuthenticationPrincipal UserDetails userDetails,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (userDetails != null) {
            cartService.addToCart(userDetails.getUsername(), productId, quantity);
        } else {
            guestCartService.add(session, productId, quantity, null);
        }
        ra.addFlashAttribute("successMsg", "장바구니에 담았습니다.");
        return "redirect:/cart";
    }

    /** AJAX 전용 — 페이지 이동 없이 JSON 응답 (회원/비회원 모두 가능) */
    @PostMapping("/add/ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCartAjax(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(name = "optionId", required = false) List<Long> optionIds,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpSession session) {
        try {
            int count;
            if (userDetails != null) {
                cartService.addToCart(userDetails.getUsername(), productId, quantity, optionIds);
                count = cartService.getCartCount(userDetails.getUsername());
            } else {
                guestCartService.add(session, productId, quantity, optionIds);
                count = guestCartService.getCount(session);
            }
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("success", true);
            body.put("count", count);
            body.put("message", "장바구니에 담았습니다.");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @PostMapping("/update")
    public String updateQuantity(@RequestParam Long cartItemId,
                                 @RequestParam int quantity,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpSession session) {
        if (userDetails != null) {
            cartService.updateQuantity(userDetails.getUsername(), cartItemId, quantity);
        } else {
            guestCartService.updateQuantity(session, cartItemId, quantity);
        }
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long cartItemId,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpSession session) {
        if (userDetails != null) {
            cartService.removeFromCart(userDetails.getUsername(), cartItemId);
        } else {
            guestCartService.remove(session, cartItemId);
        }
        return "redirect:/cart";
    }
}
