package com.lightdrone.controller;

import com.lightdrone.dto.CustomPaymentDto;
import com.lightdrone.service.CustomPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/custom-payments")
@RequiredArgsConstructor
public class AdminCustomPaymentController {

    private final CustomPaymentService customPaymentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("payments", customPaymentService.findAllForAdmin());
        return "admin/custom-payments";
    }

    @GetMapping("/new")
    public String writeForm(Model model) {
        model.addAttribute("customPaymentDto", new CustomPaymentDto());
        return "admin/custom-payment-write";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute CustomPaymentDto customPaymentDto,
                         BindingResult bindingResult,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "admin/custom-payment-write";
        }
        try {
            customPaymentService.create(customPaymentDto);
            ra.addFlashAttribute("successMsg", "맞춤결제가 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/custom-payments";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var cp = customPaymentService.findById(id);
        var dto = new CustomPaymentDto();
        dto.setName(cp.getName());
        dto.setCode(cp.getCode());
        dto.setPrice(cp.getPrice());
        dto.setDeliveryMethod(cp.getDeliveryMethod());
        dto.setExistingImageUrl(cp.getImageUrl());
        model.addAttribute("customPayment", cp);
        model.addAttribute("customPaymentDto", dto);
        return "admin/custom-payment-edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute CustomPaymentDto customPaymentDto,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("customPayment", customPaymentService.findById(id));
            return "admin/custom-payment-edit";
        }
        try {
            customPaymentService.update(id, customPaymentDto);
            ra.addFlashAttribute("successMsg", "맞춤결제가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/custom-payments";
    }

    @PostMapping("/{id}/mark-paid")
    public String markPaid(@PathVariable Long id, RedirectAttributes ra) {
        customPaymentService.adminSetStatus(id, com.lightdrone.domain.CustomPayment.Status.PAID);
        ra.addFlashAttribute("successMsg", "결제완료로 변경되었습니다.");
        return "redirect:/admin/custom-payments";
    }

    @PostMapping("/{id}/mark-pending")
    public String markPending(@PathVariable Long id, RedirectAttributes ra) {
        customPaymentService.adminSetStatus(id, com.lightdrone.domain.CustomPayment.Status.PENDING);
        ra.addFlashAttribute("successMsg", "미결제로 변경되었습니다.");
        return "redirect:/admin/custom-payments";
    }

    @PostMapping("/{id}/payment-cancel")
    public String cancelPayment(@PathVariable Long id,
                                @RequestParam(required = false) String cancelReason,
                                RedirectAttributes ra) {
        try {
            customPaymentService.cancelPaid(id, cancelReason);
            ra.addFlashAttribute("successMsg", "맞춤결제 전체취소가 완료되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/custom-payments";
    }

    @PostMapping("/{id}/payment-reconcile")
    public String reconcilePayment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var cp = customPaymentService.findById(id);
            if (cp.getOrderNumber() == null || cp.getOrderNumber().isBlank()) {
                throw new IllegalStateException("토스 주문번호가 없어 재조회할 수 없습니다.");
            }
            var outcome = customPaymentService.reconcileByOrderNumber(cp.getOrderNumber());
            String message = switch (outcome) {
                case CONFIRMED_SYNCED -> "토스 승인 내역을 결제완료로 동기화했습니다.";
                case CANCEL_SYNCED -> "토스 전체취소 내역을 동기화했습니다.";
                case PARTIAL_CANCEL_DETECTED -> "부분취소를 확인했습니다. 맞춤결제 상태는 자동 변경하지 않았습니다.";
                case AMOUNT_MISMATCH -> "토스 금액과 맞춤결제 금액이 달라 자동 동기화하지 않았습니다.";
                case PAYMENT_NOT_FOUND -> "맞춤결제 건을 찾을 수 없습니다.";
                case NO_CHANGE -> "토스 결제 내역과 사이트 상태가 이미 일치합니다.";
            };
            if (outcome == CustomPaymentService.ReconcileOutcome.AMOUNT_MISMATCH
                    || outcome == CustomPaymentService.ReconcileOutcome.PAYMENT_NOT_FOUND) {
                ra.addFlashAttribute("errorMsg", message);
            } else {
                ra.addFlashAttribute("successMsg", message);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/custom-payments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        customPaymentService.delete(id);
        ra.addFlashAttribute("successMsg", "맞춤결제가 삭제되었습니다.");
        return "redirect:/admin/custom-payments";
    }
}
