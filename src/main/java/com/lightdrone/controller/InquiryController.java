package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.dto.InquiryDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.InquiryService;
import com.lightdrone.service.MemberService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inquiry")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final MemberService memberService;
    private final CartService cartService;

    @GetMapping
    public String form(@AuthenticationPrincipal UserDetails userDetails,
                       @RequestParam(required = false) String subject,
                       Model model) {
        InquiryDto inquiryDto = new InquiryDto();
        if (userDetails != null) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            inquiryDto.setName(member.getName());
            inquiryDto.setEmail(member.getEmail());
            inquiryDto.setPhone(member.getPhone());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        }
        if (subject != null && !subject.isBlank()) {
            inquiryDto.setSubject(subject);
        }
        model.addAttribute("inquiryDto", inquiryDto);
        return "inquiry";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute InquiryDto inquiryDto,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetails userDetails,
                         HttpSession session,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        // 연락처 형식 검증 (값이 있을 때만)
        // 허용 형식: 0XX-XXXX-XXXX (모바일 010~019) 또는 02-XXXX-XXXX (서울) 또는 0XX-XXX-XXXX (지역번호)
        if (inquiryDto.getPhone() != null && !inquiryDto.getPhone().isBlank() &&
                !inquiryDto.getPhone().matches("^0\\d{1,2}-\\d{3,4}-\\d{4}$")) {
            bindingResult.rejectValue("phone", "pattern", "올바른 연락처 형식을 입력해주세요. (예: 010-1234-5678)");
        }
        if (bindingResult.hasErrors()) {
            if (userDetails != null) {
                Member member = memberService.findByUsername(userDetails.getUsername());
                model.addAttribute("member", member);
                model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            }
            return "inquiry";
        }

        Member member = (userDetails != null)
                ? memberService.findByUsername(userDetails.getUsername())
                : null;

        var saved = inquiryService.submit(inquiryDto, member);
        redirectAttributes.addFlashAttribute("successMsg", "문의가 접수되었습니다. 빠른 시일 내에 답변 드리겠습니다.");
        redirectAttributes.addFlashAttribute("submittedInquiryId", saved.getId());
        // 비로그인 사용자: 세션에 문의 ID 저장 → 본인 문의만 열람 가능
        if (member == null) {
            session.setAttribute("GUEST_INQUIRY_ID", saved.getId());
        }
        return "redirect:/inquiry";
    }

    @GetMapping("/my")
    public String myInquiries(@RequestParam(defaultValue = "0") int page,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        String username = userDetails.getUsername();
        Member member = memberService.findByUsername(username);
        var pageable = PageRequest.of(page, 10);
        model.addAttribute("inquiries", inquiryService.getMyInquiries(member, pageable));
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(username));
        return "inquiry-my";
    }

    @GetMapping("/lookup")
    public String lookupForm(Model model) {
        return "inquiry-lookup";
    }

    @PostMapping("/lookup")
    public String lookupSubmit(@RequestParam String name,
                               @RequestParam String phone,
                               HttpSession session,
                               Model model) {
        if (name.isBlank() || phone.isBlank()) {
            model.addAttribute("errorMsg", "이름과 연락처를 모두 입력해주세요.");
            return "inquiry-lookup";
        }
        var results = inquiryService.getGuestInquiries(name, phone);
        // 조회 인증된 문의 ID 목록을 세션에 저장 → 상세 열람 허용
        List<Long> ids = results.stream().map(i -> i.getId()).toList();
        session.setAttribute("GUEST_LOOKUP_IDS", ids);
        model.addAttribute("guestInquiries", results);
        model.addAttribute("searchName", name);
        return "inquiry-lookup";
    }

    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails userDetails,
                       HttpSession session,
                       Model model) {
        Member member = null;
        if (userDetails == null) {
            // 비로그인 사용자: 직접 접수했거나 이름+연락처 조회로 인증된 문의만 열람 허용
            Long submittedId = (Long) session.getAttribute("GUEST_INQUIRY_ID");
            @SuppressWarnings("unchecked")
            List<Long> lookupIds = (List<Long>) session.getAttribute("GUEST_LOOKUP_IDS");
            boolean allowed = (submittedId != null && submittedId.equals(id))
                           || (lookupIds != null && lookupIds.contains(id));
            if (!allowed) {
                return "redirect:/inquiry/lookup";
            }
        } else {
            // 로그인 사용자: 본인 문의 또는 관리자만 열람 가능 (IDOR 방지)
            member = memberService.findByUsername(userDetails.getUsername());
            boolean isAdmin = member.getRole().name().equals("ADMIN");
            if (!inquiryService.canView(id, member.getId(), isAdmin)) {
                return "redirect:/inquiry/my";
            }
        }
        var inquiry = inquiryService.getInquiry(id);
        model.addAttribute("inquiry", inquiry);
        if (member != null) {
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        }
        return "inquiry-detail";
    }
}
