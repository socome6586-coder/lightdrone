package com.lightdrone.controller;

import com.lightdrone.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviewService.getListForAdmin(PageRequest.of(page, 20)));
        return "admin/reviews";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("review", reviewService.getById(id));
        return "admin/review-detail";
    }

    @PostMapping("/{id}/reply")
    public String saveReply(@PathVariable Long id,
                            @RequestParam String reply,
                            RedirectAttributes redirectAttributes) {
        reviewService.saveAdminReply(id, reply);
        redirectAttributes.addFlashAttribute("successMsg", "답글이 저장되었습니다.");
        return "redirect:/admin/reviews/" + id;
    }

    @PostMapping("/{id}/toggle-visible")
    public String toggleVisible(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        reviewService.toggleVisible(id);
        redirectAttributes.addFlashAttribute("successMsg", "공개/숨김 상태가 변경되었습니다.");
        return "redirect:/admin/reviews/" + id;
    }

}
