package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Review;
import com.lightdrone.dto.ReviewDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private static final List<String> CATEGORIES = List.of("드론 구매후기", "A/S 후기", "교육 후기", "기타");

    private final ReviewService reviewService;
    private final MemberService memberService;
    private final CartService cartService;

    /** 후기 목록 */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {
        model.addAttribute("reviews", reviewService.getList(PageRequest.of(page, 10)));
        model.addAttribute("categories", CATEGORIES);
        if (userDetails != null) {
            model.addAttribute("member", memberService.findByUsername(userDetails.getUsername()));
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        }
        return "review/list";
    }

    /** 후기 상세 (비회원도 열람 가능) */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {
        Review review = reviewService.getById(id);
        if (!review.isVisible()) return "redirect:/review";
        reviewService.incrementViewCount(id);
        model.addAttribute("review", review);
        if (userDetails != null) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            boolean isOwner = review.getMember() != null &&
                              review.getMember().getId().equals(member.getId());
            model.addAttribute("isOwner", isOwner);
        }
        return "review/detail";
    }

    /** 작성 폼 */
    @GetMapping("/write")
    public String writeForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        model.addAttribute("reviewDto", new ReviewDto());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("purchasedProducts", reviewService.getPurchasedProducts(member));
        return "review/write";
    }

    /** 작성 제출 */
    @PostMapping("/write")
    public String write(@Valid @ModelAttribute ReviewDto reviewDto,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal UserDetails userDetails,
                        Model model,
                        RedirectAttributes ra) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        if (bindingResult.hasErrors()) {
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("purchasedProducts", reviewService.getPurchasedProducts(member));
            return "review/write";
        }
        try {
            reviewService.create(reviewDto, member);
        } catch (IllegalArgumentException e) {
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("categories", CATEGORIES);
            model.addAttribute("purchasedProducts", reviewService.getPurchasedProducts(member));
            model.addAttribute("errorMsg", e.getMessage());
            return "review/write";
        }
        ra.addFlashAttribute("successMsg", "후기가 등록되었습니다.");
        return "redirect:/review";
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        Review review = reviewService.getById(id);
        Member member = memberService.findByUsername(userDetails.getUsername());
        if (review.getMember() == null || !review.getMember().getId().equals(member.getId())) {
            return "redirect:/review/" + id;
        }
        ReviewDto dto = new ReviewDto();
        dto.setTitle(review.getTitle());
        dto.setContent(review.getContent());
        dto.setCategory(review.getCategory());
        dto.setRating(review.getRating() != null ? review.getRating() : 5);
        model.addAttribute("review", review);
        model.addAttribute("reviewDto", dto);
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        model.addAttribute("categories", CATEGORIES);
        return "review/edit";
    }

    /** 수정 제출 */
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute ReviewDto reviewDto,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model,
                       RedirectAttributes ra) {
        Review review = reviewService.getById(id);
        Member member = memberService.findByUsername(userDetails.getUsername());
        if (review.getMember() == null || !review.getMember().getId().equals(member.getId())) {
            return "redirect:/review/" + id;
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("review", review);
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("categories", CATEGORIES);
            return "review/edit";
        }
        reviewService.update(id, reviewDto);
        ra.addFlashAttribute("successMsg", "후기가 수정되었습니다.");
        return "redirect:/review/" + id;
    }

    /** 삭제 (작성자 본인만) */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails userDetails,
                         RedirectAttributes ra) {
        Review review = reviewService.getById(id);
        Member member = memberService.findByUsername(userDetails.getUsername());
        if (review.getMember() == null || !review.getMember().getId().equals(member.getId())) {
            ra.addFlashAttribute("errorMsg", "삭제 권한이 없습니다.");
            return "redirect:/review/" + id;
        }
        reviewService.delete(id);
        ra.addFlashAttribute("successMsg", "후기가 삭제되었습니다.");
        return "redirect:/review";
    }
}
