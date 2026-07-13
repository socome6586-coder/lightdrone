package com.lightdrone.controller;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.dto.OrderFormDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.CustomPaymentService;
import com.lightdrone.service.EmailService;
import com.lightdrone.service.GuestCartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.PricingService;
import com.lightdrone.service.ProductService;
import com.lightdrone.service.RequestRateLimiter;
import com.lightdrone.service.SmsService;
import com.lightdrone.service.AdminAlertService;
import com.lightdrone.support.OwnershipUtils;
import static com.lightdrone.service.OrderService.SHIPPING_FEE;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private static final int PUBLIC_LOOKUP_LIMIT_PER_MINUTE = 20;
    private static final Duration PUBLIC_LOOKUP_WINDOW = Duration.ofMinutes(1);

    private final OrderService orderService;
    private final MemberService memberService;
    private final ProductService productService;
    private final CartService cartService;
    private final GuestCartService guestCartService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final CustomPaymentService customPaymentService;
    private final AdminAlertService adminAlertService;
    private final PricingService pricingService;
    private final RequestRateLimiter requestRateLimiter;

    @Value("${toss.payments.client-key}")
    private String tossClientKey;

    /** 주문서 폼 — 바로구매: ?productId=X&quantity=Y  /  장바구니: ?fromCart=true */
    @GetMapping("/form")
    public String orderForm(@RequestParam(required = false) Long productId,
                            @RequestParam(defaultValue = "1") int quantity,
                            @RequestParam(defaultValue = "false") boolean fromCart,
                            @RequestParam(name = "optionId", required = false) List<Long> optionIds,
                            @AuthenticationPrincipal UserDetails userDetails,
                            HttpSession session,
                            Model model) {

        // 비로그인 허용 — member는 null일 수 있음
        // getUsername()이 null을 반환하는 엣지 케이스까지 방어
        String principalUsername = (userDetails != null) ? userDetails.getUsername() : null;
        Member member = (principalUsername != null)
                ? memberService.findByUsername(principalUsername) : null;
        OrderFormDto dto = new OrderFormDto();

        // 구매자 정보 자동 입력 (로그인 시에만)
        if (member != null) {
            dto.setBuyerName(nvl(member.getName()));
            dto.setBuyerPhone(nvl(member.getPhone()));
            dto.setBuyerEmail(nvl(member.getEmail()));
            dto.setReceiverName(nvl(member.getName()));
            dto.setReceiverPhone(nvl(member.getPhone()));
            dto.setZipCode(nvl(member.getZipCode()));
            dto.setAddress(nvl(member.getAddress()));
            dto.setAddressDetail(nvl(member.getAddressDetail()));
        }

        long shippingFee;
        if (fromCart) {
            // 회원은 DB 장바구니, 비회원은 세션 장바구니에서 주문 항목을 가져온다
            var cartItems = (principalUsername != null)
                    ? cartService.getCartItems(principalUsername)
                    : guestCartService.getCartItems(session);
            if (cartItems.isEmpty()) return "redirect:/cart";
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("totalPrice", cartService.getTotalPrice(cartItems, member));
            dto.setFromCart(true);
            shippingFee = shippingFeeForCart(cartItems);
        } else {
            if (productId == null) return "redirect:/products";
            var product = productService.findById(productId);
            if (!product.isAvailable()) return "redirect:/products";
            int qty = Math.max(1, Math.min(quantity, product.getStock()));
            // 옵션 조합이 유효하지 않으면 폼에 옵션을 싣지 않고 진행
            try {
                cartService.resolveOptions(product, optionIds);
            } catch (IllegalArgumentException ignored) {
                optionIds = null;
            }
            shippingFee = populateProductModel(model, product, qty, optionIds, member);
            dto.setProductId(productId);
            dto.setQuantity(qty);
            dto.setOptionIds(optionIds);
        }

        String username = principalUsername;  // 위에서 이미 계산된 값 재사용
        model.addAttribute("orderFormDto", dto);
        model.addAttribute("member", member);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("gradeDiscountPercent", pricingService.discountPercentFor(member));
        model.addAttribute("cartCount", (username != null)
                ? cartService.getCartCount(username)
                : guestCartService.getCount(session));
        model.addAttribute("tossClientKey", tossClientKey);
        return "order-form";
    }

    /** 카드결제 사전 주문 생성 (토스페이먼츠용 AJAX) */
    @PostMapping("/prepare")
    @ResponseBody
    public ResponseEntity<?> prepareOrder(@Valid @ModelAttribute OrderFormDto orderFormDto,
                                          BindingResult bindingResult,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          HttpSession session) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst().orElse("입력 정보를 확인해주세요.");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg));
        }
        try {
            Member member = (userDetails != null) ? memberService.findByUsername(userDetails.getUsername()) : null;
            var order = createOrderFromForm(orderFormDto, member, userDetails, session);

            String orderName = order.getItems().get(0).getProductName();
            if (order.getItems().size() > 1) {
                orderName += " 외 " + (order.getItems().size() - 1) + "건";
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderNumber", order.getOrderNumber(),
                    "amount", order.getTotalPrice(),
                    "buyerName", order.getBuyerName(),
                    "orderName", orderName
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** 주문 제출 */
    @PostMapping
    public String submitOrder(@Valid @ModelAttribute OrderFormDto orderFormDto,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpSession session,
                              RedirectAttributes ra,
                              Model model) {

        Member member = (userDetails != null) ? memberService.findByUsername(userDetails.getUsername()) : null;
        String username = (userDetails != null) ? userDetails.getUsername() : null;

        if (bindingResult.hasErrors()) {
            rebuildModel(orderFormDto, member, username, session, model);
            return "order-form";
        }

        try {
            var order = createOrderFromForm(orderFormDto, member, userDetails, session);

            // items가 LAZY이므로 findByOrderNumber로 재조회해야 LazyInitializationException 방지
            var orderWithItems = orderService.findByOrderNumber(order.getOrderNumber());
            emailService.sendOrderConfirmEmail(orderWithItems);
            try { sendOrderCompleteSms(orderWithItems); } catch (Exception ignored) {}
            try { sendAdminOrderAlert(orderWithItems, false); } catch (Exception ignored) {}
            return "redirect:/order/complete/" + order.getOrderNumber();
        } catch (IllegalArgumentException e) {
            rebuildModel(orderFormDto, member, username, session, model);
            model.addAttribute("errorMsg", e.getMessage());
            return "order-form";
        }
    }

    /**
     * 주문 폼/사전주문/제출에서 공통으로 사용하는 주문 생성 로직.
     * - 바로구매: 회원/비회원 모두 createDirect (member 가 null 일 수 있음)
     * - 장바구니: 회원은 DB 장바구니, 비회원은 세션 장바구니에서 주문 생성 후 세션 장바구니 비움
     */
    private com.lightdrone.domain.Order createOrderFromForm(OrderFormDto dto, Member member,
                                                            UserDetails userDetails, HttpSession session) {
        if (dto.isFromCart()) {
            if (userDetails != null) {
                return orderService.createFromCart(member, dto);
            }
            var guestItems = guestCartService.getCartItems(session);
            var order = orderService.createFromGuestCart(guestItems, dto);
            guestCartService.clear(session);
            return order;
        }
        return orderService.createDirect(member, dto.getProductId(), dto.getQuantity(), dto);
    }

    /** 주문 완료 */
    @GetMapping("/complete/{orderNumber}")
    public String orderComplete(@PathVariable String orderNumber,
                                @AuthenticationPrincipal UserDetails userDetails,
                                Model model) {
        var order = orderService.findByOrderNumber(orderNumber);

        if (userDetails != null) {
            // 로그인 사용자: 본인 주문만 열람 (비회원 주문 URL에 접근해도 차단하지 않음)
            Member currentMember = memberService.findByUsername(userDetails.getUsername());
            if (order.getMember() != null && !order.getMember().getId().equals(currentMember.getId())) {
                return "redirect:/";
            }
            model.addAttribute("member", currentMember);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        } else {
            // 비로그인: 비회원 주문만 열람 가능 (회원 주문이면 로그인 요구)
            if (order.getMember() != null) return "redirect:/auth/login";
            model.addAttribute("member", null);
            model.addAttribute("cartCount", 0);
        }

        model.addAttribute("order", order);
        return "order-complete";
    }

    /** 내 주문 목록 */
    @GetMapping("/my")
    public String myOrders(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("orders", orderService.findByMember(member));
        model.addAttribute("customPayments", customPaymentService.findByMemberId(member.getId()));
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        return "order-my";
    }

    /** 거절된 주문 삭제 (본인만) */
    @PostMapping("/{id}/delete")
    public String deleteRejected(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes ra) {
        var order = orderService.findById(id);
        Member currentMember = memberService.findByUsername(userDetails.getUsername());
        if (!OwnershipUtils.isOwner(order, currentMember.getId())) {
            return "redirect:/";
        }
        try {
            orderService.delete(id);
            ra.addFlashAttribute("successMsg", "주문 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/order/my";
    }

    /** 주문 취소 (본인만) */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails userDetails,
                         RedirectAttributes ra) {
        var order = orderService.findById(id);
        Member currentMember = memberService.findByUsername(userDetails.getUsername());
        // 프록시 ID 비교 — Lazy 로딩 없이 안전 (member null이면 false 반환)
        if (!OwnershipUtils.isOwner(order, currentMember.getId())) {
            return "redirect:/";
        }
        try {
            orderService.cancel(id);
            ra.addFlashAttribute("successMsg", "주문이 취소되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/order/my";
    }

    /** 환불/반품 신청 */
    @PostMapping("/{id}/refund")
    public String requestRefund(@PathVariable Long id,
                                @RequestParam(required = false) String refundReason,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes ra) {
        Member currentMember = memberService.findByUsername(userDetails.getUsername());
        try {
            orderService.requestRefund(id, currentMember.getId(), refundReason);
            ra.addFlashAttribute("successMsg", "환불/반품 신청이 접수되었습니다. 담당자가 확인 후 연락드립니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/order/my";
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    /** 장바구니 배송비: 모든 상품이 무료배송이면 0, 하나라도 유료면 SHIPPING_FEE */
    private long shippingFeeForCart(List<CartItem> cartItems) {
        boolean allFree = cartItems.stream()
                .allMatch(ci -> ci.getProduct() != null && ci.getProduct().isFreeShipping());
        return allFree ? 0L : SHIPPING_FEE;
    }

    /** 단일 상품 배송비 */
    private long shippingFeeForProduct(Product product) {
        return product.isFreeShipping() ? 0L : SHIPPING_FEE;
    }

    /** 단일 상품 주문서 모델(상품·수량·옵션명·옵션금액·총액) 세팅 후 배송비 반환 */
    private long populateProductModel(Model model, Product product, int qty, List<Long> optionIds, Member member) {
        long optionExtra = 0L;
        String optionName = null;
        try {
            CartService.OptionSelection sel = cartService.resolveOptions(product, optionIds);
            optionExtra = sel.extraSum;
            optionName = sel.combinedName;
        } catch (IllegalArgumentException ignored) {}
        // 회원 등급 상시 할인을 (상품 판매가+옵션 추가금) 단가에 적용 — 주문 스냅샷과 동일
        long unitPrice = pricingService.applyTo(product.getEffectivePrice() + optionExtra, member);
        model.addAttribute("product", product);
        model.addAttribute("quantity", qty);
        model.addAttribute("optionName", optionName);
        model.addAttribute("optionExtra", optionExtra);
        model.addAttribute("unitPrice", unitPrice);
        model.addAttribute("totalPrice", unitPrice * qty);
        return shippingFeeForProduct(product);
    }

    private void rebuildModel(OrderFormDto dto, Member member, String username, HttpSession session, Model model) {
        model.addAttribute("member", member);
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("tossClientKey", tossClientKey);
        model.addAttribute("cartCount", (username != null)
                ? cartService.getCartCount(username)
                : guestCartService.getCount(session));
        long shippingFee = SHIPPING_FEE;
        if (dto.isFromCart()) {
            var cartItems = (username != null)
                    ? cartService.getCartItems(username)
                    : guestCartService.getCartItems(session);
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("totalPrice", cartService.getTotalPrice(cartItems, member));
            shippingFee = shippingFeeForCart(cartItems);
        } else if (dto.getProductId() != null) {
            try {
                var product = productService.findById(dto.getProductId());
                int qty = dto.getQuantity() != null ? dto.getQuantity() : 1;
                shippingFee = populateProductModel(model, product, qty, dto.getOptionIds(), member);
            } catch (Exception ignored) {}
        }
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("gradeDiscountPercent", pricingService.discountPercentFor(member));
    }

    /** 비회원 주문 조회 폼 */
    @GetMapping("/lookup")
    public String lookupForm(Model model) {
        model.addAttribute("cartCount", 0);
        return "order-lookup";
    }

    /** 비회원 주문 조회 처리 — 주문번호 + 연락처로 본인 확인 */
    @PostMapping("/lookup")
    public String lookupResult(@RequestParam String orderNumber,
                               @RequestParam String buyerPhone,
                               HttpServletRequest request,
                               Model model) {
        model.addAttribute("cartCount", 0);
        model.addAttribute("searchOrderNumber", orderNumber);
        model.addAttribute("searchBuyerPhone", buyerPhone);
        if (publicLookupRateLimited("order-lookup", request)) {
            model.addAttribute("errorMsg", "조회 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return "order-lookup";
        }
        try {
            com.lightdrone.domain.Order order = orderService.findByOrderNumber(orderNumber.trim());
            // 연락처 일치 여부 확인 (하이픈 제거 후 비교)
            String inputPhone  = buyerPhone.replaceAll("[^0-9]", "");
            String storedPhone = order.getBuyerPhone() != null
                    ? order.getBuyerPhone().replaceAll("[^0-9]", "") : "";
            if (!inputPhone.equals(storedPhone)) {
                model.addAttribute("errorMsg", "주문번호 또는 연락처가 일치하지 않습니다.");
                return "order-lookup";
            }
            model.addAttribute("order", order);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", "주문번호 또는 연락처가 일치하지 않습니다.");
        }
        return "order-lookup";
    }

    /** 비회원 맞춤결제 조회 — 맞춤결제코드(전화 끝 4자리)로 조회 */
    @PostMapping("/lookup-custom")
    public String lookupCustom(@RequestParam String code,
                               HttpServletRequest request,
                               Model model) {
        model.addAttribute("cartCount", 0);
        model.addAttribute("searchedCode", code);
        if (publicLookupRateLimited("custom-order-lookup", request)) {
            model.addAttribute("errorMsg", "조회 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("customSearched", true);
            return "order-lookup";
        }
        model.addAttribute("customResults", customPaymentService.findByCode(code));
        model.addAttribute("customSearched", true);
        return "order-lookup";
    }

    private boolean publicLookupRateLimited(String scope, HttpServletRequest request) {
        return !requestRateLimiter.tryConsume(scope, request, PUBLIC_LOOKUP_LIMIT_PER_MINUTE, PUBLIC_LOOKUP_WINDOW);
    }

    private void sendOrderCompleteSms(com.lightdrone.domain.Order order) {
        String itemName = order.getItems().isEmpty() ? "상품" : order.getItems().get(0).getProductName();
        if (order.getItems().size() > 1) itemName += " 외 " + (order.getItems().size() - 1) + "건";
        String formattedPrice = String.format("%,d", order.getTotalPrice());

        StringBuilder msg = new StringBuilder();
        msg.append("[라이트드론] 주문이 접수되었습니다.\n")
           .append("주문번호: ").append(order.getOrderNumber()).append("\n")
           .append("상품: ").append(itemName).append("\n")
           .append("결제금액: ").append(formattedPrice).append("원\n");

        // 무통장입금인 경우 계좌 안내 추가
        if (order.getPaymentMethod() == com.lightdrone.domain.enums.PaymentMethod.BANK_TRANSFER) {
            msg.append("---\n")
               .append("[무통장입금 안내]\n")
               .append("은행: IBK기업은행\n")
               .append("계좌: 542-030989-01-018\n")
               .append("예금주: 이성희(라이트드론)\n")
               .append("입금자명: ").append(order.getBuyerName()).append("\n");
        }

        msg.append("비회원조회: 로그인화면 하단\n")
           .append("문의: 010-3565-9741");

        smsService.send(order.getBuyerPhone(), msg.toString());
    }

    private void sendAdminOrderAlert(com.lightdrone.domain.Order order, boolean paidCardOrder) {
        String statusLabel = paidCardOrder ? "카드결제 완료 주문" : "신규 주문";
        // 원가/할인 계산에는 product 가 필요하므로 fetch-join 으로 다시 로드 (OSIV off)
        com.lightdrone.domain.Order full = orderService.findByIdWithItems(order.getId());
        adminAlertService.sendOrderAlert(full, statusLabel);
    }
}
