package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.dto.PasswordChangeDto;
import com.lightdrone.dto.PasswordVerifyDto;
import com.lightdrone.dto.ProfileUpdateDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.InquiryService;
import com.lightdrone.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private static final String PW_VERIFIED = "MYPAGE_PW_VERIFIED";

    private final MemberService memberService;
    private final InquiryService inquiryService;
    private final CartService cartService;

    /* ─────────────── 마이페이지 메인 ─────────────── */
    @GetMapping
    public String myPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("recentInquiries",
                inquiryService.getMyInquiries(member, PageRequest.of(0, 5)));
        model.addAttribute("cartCount",
                cartService.getCartCount(userDetails.getUsername()));
        return "mypage";
    }

    /* ─────────────── 비밀번호 확인 페이지 ─────────────── */
    @GetMapping("/verify")
    public String verifyPage(@AuthenticationPrincipal UserDetails userDetails,
                             HttpSession session,
                             Model model) {
        // 이미 인증된 경우 edit 페이지로 바로 이동
        if (Boolean.TRUE.equals(session.getAttribute(PW_VERIFIED))) {
            return "redirect:/mypage/edit";
        }
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        model.addAttribute("passwordVerifyDto", new PasswordVerifyDto());
        return "mypage-verify";
    }

    @PostMapping("/verify")
    public String verifyPassword(@Valid @ModelAttribute PasswordVerifyDto passwordVerifyDto,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpSession session,
                                 Model model) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));

        if (bindingResult.hasErrors()) {
            return "mypage-verify";
        }

        if (!memberService.verifyPassword(userDetails.getUsername(),
                                          passwordVerifyDto.getCurrentPassword())) {
            model.addAttribute("errorMsg", "비밀번호가 올바르지 않습니다. 다시 확인해주세요.");
            return "mypage-verify";
        }

        session.setAttribute(PW_VERIFIED, true);
        return "redirect:/mypage/edit";
    }

    /* ─────────────── 내 정보 수정 ─────────────── */
    @GetMapping("/edit")
    public String editPage(@AuthenticationPrincipal UserDetails userDetails,
                           HttpSession session,
                           Model model) {
        // 비밀번호 인증 안 됐으면 verify 페이지로
        if (!Boolean.TRUE.equals(session.getAttribute(PW_VERIFIED))) {
            return "redirect:/mypage/verify";
        }
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));

        // 기존 정보를 DTO에 pre-fill (저장 시 의도치 않게 지워지지 않도록)
        ProfileUpdateDto dto = new ProfileUpdateDto();
        dto.setPhone(member.getPhone());
        dto.setZipCode(member.getZipCode());
        dto.setAddress(member.getAddress());
        dto.setAddressDetail(member.getAddressDetail());
        model.addAttribute("profileUpdateDto", dto);
        return "mypage-edit";
    }

    /* ─────────────── 비밀번호 변경 ─────────────── */
    @GetMapping("/change-password")
    public String changePasswordPage(@AuthenticationPrincipal UserDetails userDetails,
                                     HttpSession session,
                                     Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute(PW_VERIFIED))) {
            return "redirect:/mypage/verify";
        }
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        model.addAttribute("passwordChangeDto", new PasswordChangeDto());
        return "mypage-change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute PasswordChangeDto passwordChangeDto,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute(PW_VERIFIED))) {
            return "redirect:/mypage/verify";
        }

        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));

        if (bindingResult.hasErrors()) {
            return "mypage-change-password";
        }

        if (!passwordChangeDto.getNewPassword().equals(passwordChangeDto.getNewPasswordConfirm())) {
            model.addAttribute("errorMsg", "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
            return "mypage-change-password";
        }

        memberService.resetPassword(userDetails.getUsername(), passwordChangeDto.getNewPassword());
        session.removeAttribute(PW_VERIFIED);
        redirectAttributes.addFlashAttribute("successMsg", "비밀번호가 성공적으로 변경되었습니다.");
        return "redirect:/mypage";
    }

    @PostMapping("/edit")
    public String updateProfile(@Valid @ModelAttribute ProfileUpdateDto profileUpdateDto,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        // 비밀번호 인증 안 됐으면 verify 페이지로
        if (!Boolean.TRUE.equals(session.getAttribute(PW_VERIFIED))) {
            return "redirect:/mypage/verify";
        }

        // 전화번호 변경 시 본인인증 확인
        if (StringUtils.hasText(profileUpdateDto.getPhone())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> phoneOtp =
                (Map<String, Object>) session.getAttribute("PHONE_OTP_PROFILE");

            boolean phoneVerified = phoneOtp != null
                && Boolean.TRUE.equals(phoneOtp.get("verified"))
                && profileUpdateDto.getPhone().equals(phoneOtp.get("phone"));

            if (!phoneVerified) {
                Member member = memberService.findByUsername(userDetails.getUsername());
                model.addAttribute("member", member);
                model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
                model.addAttribute("errorMsg", "휴대전화 본인인증을 완료해주세요.");
                return "mypage-edit";
            }
        }

        if (bindingResult.hasErrors()) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            return "mypage-edit";
        }

        try {
            memberService.updateProfile(userDetails.getUsername(), profileUpdateDto);
            session.removeAttribute(PW_VERIFIED);       // 수정 완료 후 비번 인증 초기화
            session.removeAttribute("PHONE_OTP_PROFILE"); // 전화 인증 초기화
            redirectAttributes.addFlashAttribute("successMsg", "정보가 성공적으로 수정되었습니다.");
            return "redirect:/mypage";
        } catch (IllegalArgumentException e) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("errorMsg", e.getMessage());
            return "mypage-edit";
        }
    }

    /* ─────────────── 회원 탈퇴 ─────────────── */
    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String withdrawPassword,
                           @AuthenticationPrincipal UserDetails userDetails,
                           HttpServletRequest request,
                           RedirectAttributes ra) {
        try {
            memberService.withdraw(userDetails.getUsername(), withdrawPassword);
            request.getSession().invalidate();
            ra.addFlashAttribute("successMsg", "회원 탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("withdrawErrorMsg", e.getMessage());
            return "redirect:/mypage";
        }
    }
}
