package com.lightdrone.controller;

import com.lightdrone.domain.Order;
import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.RefundStatus;
import com.lightdrone.service.EmailService;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final EmailService emailService;
    private final SmsService smsService;

    // ─── 주문 관리 ───────────────────────────────────────────────
    @GetMapping("/orders")
    public String orders(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", orderService.findAll(PageRequest.of(page, 20)));
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.findByIdWithItems(id));
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/order-detail";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam OrderStatus status,
                                    @RequestParam(required = false) String adminMemo,
                                    @RequestParam(required = false) String courierCompany,
                                    @RequestParam(required = false) String trackingNumber,
                                    RedirectAttributes ra) {
        Order order = orderService.findById(id);
        OrderStatus prevStatus = order.getStatus();
        try {
            orderService.updateStatus(id, status, adminMemo, courierCompany, trackingNumber);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/admin/orders/" + id;
        }
        // 배송 시작·완료 시 고객 이메일 발송
        if (prevStatus != status &&
            (status == OrderStatus.SHIPPING || status == OrderStatus.DELIVERED)) {
            Order updated = orderService.findById(id);
            emailService.sendOrderStatusEmail(updated);
        }
        ra.addFlashAttribute("successMsg", "주문 상태가 변경되었습니다.");
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/refund-status")
    public String updateRefundStatus(@PathVariable Long id,
                                     @RequestParam RefundStatus refundStatus,
                                     @RequestParam(required = false) String refundNote,
                                     RedirectAttributes ra) {
        orderService.updateRefundStatus(id, refundStatus, refundNote);
        ra.addFlashAttribute("successMsg", "환불/반품 상태가 변경되었습니다.");
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable Long id, RedirectAttributes ra) {
        try {
            orderService.cancel(id);
            ra.addFlashAttribute("successMsg", "주문이 취소되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /** 토스로 승인된 카드 주문 전체취소. 토스 성공 확인 후에만 주문 취소·재고 복원이 반영된다. */
    @PostMapping("/orders/{id}/payment-cancel")
    public String cancelCardPayment(@PathVariable Long id,
                                    @RequestParam(required = false) String cancelReason,
                                    RedirectAttributes ra) {
        try {
            orderService.cancelPaidOrder(id, cancelReason);
            ra.addFlashAttribute("successMsg", "토스 결제 전체취소와 재고 복원이 완료되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /** 토스 결제 원장을 다시 조회해 사이트 주문 상태를 안전하게 동기화한다. */
    @PostMapping("/orders/{id}/payment-reconcile")
    public String reconcileCardPayment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            OrderService.ReconcileOutcome outcome = orderService.reconcileWithToss(id);
            String message = switch (outcome) {
                case CONFIRMED_SYNCED -> "토스 승인 내역을 확인해 결제완료로 동기화했습니다.";
                case CANCEL_SYNCED -> "토스 전체취소 내역을 확인해 주문 취소와 재고 복원을 완료했습니다.";
                case PARTIAL_CANCEL_DETECTED -> "부분취소를 확인했습니다. 주문 전체 상태와 재고는 변경하지 않았습니다.";
                case AMOUNT_MISMATCH -> "토스 금액과 주문 금액이 달라 자동 동기화하지 않았습니다.";
                case ORDER_NOT_FOUND -> "주문을 찾을 수 없습니다.";
                case NO_CHANGE -> "토스 결제 내역과 사이트 상태가 이미 일치합니다.";
            };
            if (outcome == OrderService.ReconcileOutcome.AMOUNT_MISMATCH
                    || outcome == OrderService.ReconcileOutcome.ORDER_NOT_FOUND) {
                ra.addFlashAttribute("errorMsg", message);
            } else {
                ra.addFlashAttribute("successMsg", message);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/reject")
    public String rejectOrder(@PathVariable Long id,
                              @RequestParam(required = false) String rejectReason,
                              RedirectAttributes ra) {
        try {
            Order order = orderService.findById(id);
            orderService.reject(id, rejectReason);
            // 거절 SMS 발송
            String phone = order.getBuyerPhone();
            if (phone != null && !phone.isBlank()) {
                String msg = "[라이트드론] 주문번호 " + order.getOrderNumber() + " 주문이 거절되었습니다.\n"
                           + "자세한 내용은 홈페이지(lightdrone.co.kr) 참조 또는 1:1 문의 부탁드립니다.";
                smsService.send(phone, msg);
            }
            ra.addFlashAttribute("successMsg", "주문이 거절되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    /** 주문 단건 삭제 (취소·거절만) */
    @PostMapping("/orders/{id}/delete")
    public String deleteOrder(@PathVariable Long id, RedirectAttributes ra) {
        try {
            orderService.delete(id);
            ra.addFlashAttribute("successMsg", "주문이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/orders";
    }

    /** 주문 일괄 삭제 (취소·거절만) */
    @PostMapping("/orders/delete-bulk")
    public String deleteOrdersBulk(@RequestParam(value = "ids", required = false) List<Long> ids,
                                   RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "삭제할 주문을 선택해 주세요.");
            return "redirect:/admin/orders";
        }
        int count = orderService.deleteAll(ids);
        ra.addFlashAttribute("successMsg", count + "건의 주문이 삭제되었습니다.");
        return "redirect:/admin/orders";
    }
}
