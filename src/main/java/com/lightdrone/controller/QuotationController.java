package com.lightdrone.controller;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Product;
import com.lightdrone.dto.QuotationFormDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/quotation")
@RequiredArgsConstructor
public class QuotationController {

    private final CartService cartService;
    private final MemberService memberService;
    private final ProductService productService;

    /** /quotation 단독 접근 시 견적서 작성 폼으로 이동 (핸들러 없던 경로의 500 방지) */
    @GetMapping
    public String quotationRoot() {
        return "redirect:/quotation/form";
    }

    /** 견적서 작성 폼 */
    @GetMapping("/form")
    public String quotationForm(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestParam(required = false) Long productId,
                                @RequestParam(required = false, defaultValue = "1") int quantity,
                                Model model) {
        // productId 가 있으면 "해당 상품만" 견적 (장바구니 무시, 장바구니에 담지 않음)
        // productId 가 없으면 장바구니 전체 견적
        List<CartItem> cartItems;
        if (productId != null) {
            try {
                cartItems = List.of(buildSingleItem(productId, quantity));
            } catch (IllegalArgumentException e) {
                return "redirect:/cart";
            }
        } else {
            cartItems = cartService.getCartItems(userDetails.getUsername());
            if (cartItems.isEmpty()) return "redirect:/cart";
        }

        Member member = memberService.findByUsername(userDetails.getUsername());
        QuotationFormDto form = new QuotationFormDto();
        // 회원 정보 기본값 세팅
        form.setReceiverName(member.getName());
        form.setReceiverPhone(member.getPhone());
        // 소셜 로그인 시 자동 생성된 플레이스홀더 이메일은 노출하지 않음
        form.setReceiverEmail(member.isEmailPlaceholder() ? "" : member.getEmail());

        model.addAttribute("form", form);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalPrice", cartService.getTotalPrice(cartItems));
        // 단일 상품 견적이면 preview 단계에서도 같은 상품을 유지하도록 폼에 전달
        model.addAttribute("productId", productId);
        model.addAttribute("quantity", quantity);
        return "quotation/form";
    }

    /** 단일 상품 견적용 비영속 CartItem 생성 (장바구니에 저장하지 않음) */
    private CartItem buildSingleItem(Long productId, int quantity) {
        Product product = productService.findById(productId);
        return CartItem.builder()
                .product(product)
                .quantity(Math.max(1, quantity))
                .build();
    }

    /** 견적서 미리보기 (인쇄용) */
    @PostMapping("/preview")
    public String quotationPreview(@Valid @ModelAttribute("form") QuotationFormDto form,
                                   BindingResult bindingResult,
                                   @RequestParam(required = false) Long productId,
                                   @RequestParam(required = false, defaultValue = "1") int quantity,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   Model model) {
        // 단일 상품 견적이면 productId 기준, 아니면 장바구니 기준
        List<CartItem> cartItems;
        if (productId != null) {
            try {
                cartItems = List.of(buildSingleItem(productId, quantity));
            } catch (IllegalArgumentException e) {
                return "redirect:/cart";
            }
        } else {
            cartItems = cartService.getCartItems(userDetails.getUsername());
            if (cartItems.isEmpty()) return "redirect:/cart";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("totalPrice", cartService.getTotalPrice(cartItems));
            model.addAttribute("productId", productId);
            model.addAttribute("quantity", quantity);
            return "quotation/form";
        }

        long totalPrice = cartService.getTotalPrice(cartItems);
        long vat = (long) (totalPrice * 0.1);
        long grandTotal = totalPrice + vat;

        // 견적 번호: LD + 날짜 + 회원ID
        Member member = memberService.findByUsername(userDetails.getUsername());
        String quotationNo = "LD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", member.getId());

        model.addAttribute("form", form);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("vat", vat);
        model.addAttribute("grandTotal", grandTotal);
        model.addAttribute("quotationNo", quotationNo);
        model.addAttribute("issueDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")));
        model.addAttribute("validUntil",
                LocalDate.now().plusDays(form.getValidDays())
                        .format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")));

        return "quotation/preview";
    }
}
