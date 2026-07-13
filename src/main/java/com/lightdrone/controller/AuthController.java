package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.dto.SignupDto;
import com.lightdrone.service.EmailService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.SmsRateLimiter;
import com.lightdrone.service.SmsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final DateTimeFormatter SIGNUP_DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final String PASSWORD_RESET_SMS_TYPE = "PASSWORD_RESET";
    private static final int MAX_PASSWORD_RESET_OTP_ATTEMPTS = 5;

    private final MemberService memberService;
    private final SmsService smsService;
    private final SmsRateLimiter smsRateLimiter;
    private final EmailService emailService;

    /* ════════════════════════════════════════
       로그인
    ════════════════════════════════════════ */
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String blocked,
                            @RequestParam(required = false) String disabled,
                            @RequestParam(required = false) String redirect,
                            HttpSession session,
                            Model model) {
        if (disabled != null) {
            model.addAttribute("disabledMsg", "접근이 제한된 계정입니다, 자세한 사항은 문의해주시길 바랍니다.");
            model.addAttribute("disabledTel", "010-3565-9741");
        } else if (blocked != null) {
            long remaining = 0;
            try { remaining = Long.parseLong(blocked); } catch (NumberFormatException ignored) {}
            model.addAttribute("blockedMsg",
                    "로그인 시도가 5회 실패하여 " + (remaining > 0 ? remaining + "초" : "잠시") + " 동안 로그인이 제한됩니다.");
        } else if (error != null) {
            model.addAttribute("errorMsg", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (redirect != null && redirect.startsWith("/")) {
            model.addAttribute("redirectUrl", redirect);
            // 소셜 로그인 버튼 클릭 시에도 redirect 를 살릴 수 있도록 세션에도 보존
            session.setAttribute(com.lightdrone.config.OAuth2LoginSuccessHandler.REDIRECT_URI_SESSION_KEY, redirect);
        }
        return "auth/login";
    }

    /* ════════════════════════════════════════
       회원가입
    ════════════════════════════════════════ */
    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("signupDto", new SignupDto());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupDto signupDto,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {

        HttpSession session = request.getSession();
        boolean hasError = false;

        if (bindingResult.hasErrors()) hasError = true;

        if (!signupDto.isAgreeTerms()) {
            model.addAttribute("agreeTermsError", "이용약관에 동의하셔야 합니다.");
            hasError = true;
        }

        if (!signupDto.isAgreePrivacy()) {
            model.addAttribute("agreePrivacyError", "개인정보 수집 및 이용에 동의하셔야 합니다.");
            hasError = true;
        }

        if (signupDto.getPassword() != null && signupDto.getPasswordConfirm() != null
                && !signupDto.getPassword().equals(signupDto.getPasswordConfirm())) {
            model.addAttribute("pwMatchError", "비밀번호가 일치하지 않습니다.");
            hasError = true;
        }

        // 비밀번호 = 아이디 동일 금지
        if (StringUtils.hasText(signupDto.getPassword())
                && signupDto.getPassword().equals(signupDto.getUsername())) {
            model.addAttribute("pwSameAsIdError", "비밀번호는 아이디와 동일하게 설정할 수 없습니다.");
            hasError = true;
        }

        // 중복 전화번호 확인
        if (StringUtils.hasText(signupDto.getPhone())
                && !memberService.isPhoneAvailable(signupDto.getPhone())) {
            model.addAttribute("phoneDupError", "이미 등록된 휴대전화번호입니다.");
            hasError = true;
        }

        if (hasError) return "auth/signup";

        try {
            Member member = memberService.signup(signupDto);
            autoLogin(signupDto.getUsername(), session);
            if (signupDto.isAgreeOutsourcing()) {
                try { emailService.sendWelcomeEmail(member); } catch (Exception ignored) {}
            }
            redirectAttributes.addFlashAttribute("signupName", member.getName());
            redirectAttributes.addFlashAttribute("signupUsername", member.getUsername());
            redirectAttributes.addFlashAttribute("signupCompletedAt",
                    member.getCreatedAt() != null ? member.getCreatedAt().format(SIGNUP_DATE_FMT) : "-");
            return "redirect:/auth/signup-complete";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            return "auth/signup";
        }
    }

    @GetMapping("/signup-complete")
    public String signupCompletePage(@ModelAttribute("signupUsername") String signupUsername) {
        if (!StringUtils.hasText(signupUsername)) {
            return "redirect:/";
        }
        return "auth/signup-complete";
    }

    /* ════════════════════════════════════════
       소셜 로그인 추가정보 입력 (실제 이메일)
    ════════════════════════════════════════ */
    private static final String EMAIL_REGEX =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    @GetMapping("/complete-profile")
    public String completeProfilePage(java.security.Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/auth/login";
        }
        Member member = memberService.findByUsername(principal.getName());
        // 이미 실제 이메일을 보유한 회원은 접근 불필요
        if (!member.isEmailPlaceholder()) {
            return "redirect:/";
        }
        model.addAttribute("memberName", member.getName());
        return "auth/complete-profile";
    }

    @PostMapping("/complete-profile")
    public String completeProfile(@RequestParam(required = false) String email,
                                  java.security.Principal principal,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (principal == null) {
            return "redirect:/auth/login";
        }
        Member member = memberService.findByUsername(principal.getName());
        model.addAttribute("memberName", member.getName());

        String trimmed = email != null ? email.trim() : "";
        if (!StringUtils.hasText(trimmed) || !trimmed.matches(EMAIL_REGEX)) {
            model.addAttribute("errorMsg", "올바른 이메일 주소를 입력해주세요.");
            model.addAttribute("email", trimmed);
            return "auth/complete-profile";
        }

        try {
            memberService.updateEmail(principal.getName(), trimmed);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("email", trimmed);
            return "auth/complete-profile";
        }

        // 로그인 전 접근하려던 페이지가 있으면 그곳으로, 없으면 홈으로 복귀
        String redirectUri = (String) session.getAttribute(
                com.lightdrone.config.OAuth2LoginSuccessHandler.REDIRECT_URI_SESSION_KEY);
        session.removeAttribute(
                com.lightdrone.config.OAuth2LoginSuccessHandler.REDIRECT_URI_SESSION_KEY);

        redirectAttributes.addFlashAttribute("successMsg", "이메일이 등록되었습니다. 환영합니다!");
        if (redirectUri != null && redirectUri.startsWith("/")) {
            return "redirect:" + redirectUri;
        }
        return "redirect:/";
    }

    /* ════════════════════════════════════════
       아이디 찾기
    ════════════════════════════════════════ */
    @GetMapping("/find-id")
    public String findIdPage() {
        return "auth/find-id";
    }

    @PostMapping("/find-id")
    public String findId(@RequestParam String method,       // "email" | "phone"
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String phone,
                         Model model) {

        model.addAttribute("method", method);
        model.addAttribute("name",   name);
        model.addAttribute("email",  email);
        model.addAttribute("phone",  phone);

        if (!StringUtils.hasText(name)) {
            model.addAttribute("errorMsg", "이름을 입력해주세요.");
            return "auth/find-id";
        }

        Optional<String> found = "email".equals(method)
            ? memberService.findUsernameByNameAndEmail(name.trim(), email != null ? email.trim() : "")
            : memberService.findUsernameByNameAndPhone(name.trim(), phone != null ? phone.trim() : "");

        if (found.isPresent()) {
            model.addAttribute("maskedUsername", memberService.maskUsername(found.get()));
            model.addAttribute("found", true);
        } else {
            model.addAttribute("errorMsg", "일치하는 회원 정보를 찾을 수 없습니다.");
        }
        return "auth/find-id";
    }

    /* ════════════════════════════════════════
       비밀번호 찾기
    ════════════════════════════════════════ */
    @GetMapping("/find-password")
    public String findPasswordPage(HttpSession session) {
        session.removeAttribute("RESET_USERNAME");
        session.removeAttribute("RESET_VERIFY_USERNAME");
        session.removeAttribute("PHONE_OTP_RESET");
        return "auth/find-password";
    }

    /** Step 1: 본인 확인 */
    @PostMapping("/find-password/verify")
    public String verifyForPasswordReset(
            @RequestParam String method,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            HttpSession session,
            Model model) {

        model.addAttribute("method",   method);
        model.addAttribute("username", username);
        model.addAttribute("name",     name);
        model.addAttribute("email",    email);
        model.addAttribute("phone",    phone);

        if (!StringUtils.hasText(username) || !StringUtils.hasText(name)) {
            model.addAttribute("errorMsg", "아이디와 이름을 입력해주세요.");
            return "auth/find-password";
        }

        boolean verified = "email".equals(method)
            ? memberService.verifyForPasswordReset(username.trim(), name.trim(),
                                                    email != null ? email.trim() : "")
            : memberService.verifyForPasswordResetByPhone(username.trim(), name.trim(),
                                                          phone != null ? phone.trim() : "");

        if (verified) {
            // 본인 확인 성공 → 등록된 휴대폰으로 OTP 발송
            Member member = memberService.findByUsername(username.trim());
            String memberPhone = member.getPhone();

            if (!smsRateLimiter.tryConsume(memberPhone, PASSWORD_RESET_SMS_TYPE)) {
                model.addAttribute("errorMsg",
                        "오늘 인증번호 발송 횟수(" + SmsRateLimiter.DAILY_LIMIT + "회)를 초과했습니다. 내일 다시 시도해주세요.");
                return "auth/find-password";
            }

            String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
            Map<String, Object> otpData = new HashMap<>();
            otpData.put("phone",     memberPhone);
            otpData.put("otp",       otp);
            otpData.put("expiresAt", LocalDateTime.now().plusMinutes(3));
            otpData.put("verified",  false);
            otpData.put("attempts",  0);
            session.setAttribute("PHONE_OTP_RESET", otpData);
            session.setAttribute("RESET_VERIFY_USERNAME", username.trim());

            smsService.send(memberPhone,
                "[라이트드론] 비밀번호 재설정 인증번호: " + otp + " (3분 내 입력해주세요)");

            model.addAttribute("step", "phone_verify");
            model.addAttribute("maskedPhone", maskPhone(memberPhone));
        } else {
            model.addAttribute("errorMsg", "일치하는 회원 정보를 찾을 수 없습니다.");
        }
        return "auth/find-password";
    }

    /** Step 2: 휴대폰 OTP 확인 */
    @PostMapping("/find-password/verify-otp")
    public String verifyOtpForPasswordReset(@RequestParam String otp,
                                             HttpSession session,
                                             Model model) {
        String username = (String) session.getAttribute("RESET_VERIFY_USERNAME");
        if (!StringUtils.hasText(username)) {
            return "redirect:/auth/find-password";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> otpData =
            (Map<String, Object>) session.getAttribute("PHONE_OTP_RESET");

        if (otpData == null) {
            model.addAttribute("step",     "phone_verify");
            model.addAttribute("otpError", "인증번호가 만료되었습니다. 처음부터 다시 시도해주세요.");
            return "auth/find-password";
        }

        LocalDateTime expiresAt = (LocalDateTime) otpData.get("expiresAt");
        if (LocalDateTime.now().isAfter(expiresAt)) {
            session.removeAttribute("PHONE_OTP_RESET");
            model.addAttribute("step",     "phone_verify");
            model.addAttribute("otpError", "인증번호가 만료되었습니다. 처음부터 다시 시도해주세요.");
            return "auth/find-password";
        }

        if (!otp.trim().equals(otpData.get("otp"))) {
            int attempts = ((Number) otpData.getOrDefault("attempts", 0)).intValue() + 1;
            if (attempts >= MAX_PASSWORD_RESET_OTP_ATTEMPTS) {
                session.removeAttribute("PHONE_OTP_RESET");
                session.removeAttribute("RESET_VERIFY_USERNAME");
                model.addAttribute("otpError", "인증번호 입력 횟수를 초과했습니다. 처음부터 다시 시도해주세요.");
                return "auth/find-password";
            }
            otpData.put("attempts", attempts);
            session.setAttribute("PHONE_OTP_RESET", otpData);
            model.addAttribute("step",     "phone_verify");
            model.addAttribute("maskedPhone", maskPhone((String) otpData.get("phone")));
            model.addAttribute("otpError", "인증번호가 올바르지 않습니다. 다시 확인해주세요.");
            return "auth/find-password";
        }

        // OTP 인증 완료 → 비밀번호 재설정 단계로 진입
        session.removeAttribute("PHONE_OTP_RESET");
        session.removeAttribute("RESET_VERIFY_USERNAME");
        session.setAttribute("RESET_USERNAME", username);
        model.addAttribute("step", "reset");
        return "auth/find-password";
    }

    /** Step 3: 새 비밀번호 저장 */
    @PostMapping("/find-password/reset")
    public String resetPassword(@RequestParam String newPassword,
                                @RequestParam String newPasswordConfirm,
                                HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Model model) {

        String resetUsername = (String) session.getAttribute("RESET_USERNAME");
        if (!StringUtils.hasText(resetUsername)) {
            return "redirect:/auth/find-password";
        }

        if (!newPassword.equals(newPasswordConfirm)) {
            model.addAttribute("step",          "reset");
            model.addAttribute("pwMatchError",  "비밀번호가 일치하지 않습니다.");
            return "auth/find-password";
        }

        String pwRegex = "^(?:(?=.*[A-Z])(?=.*[a-z])|(?=.*[A-Z])(?=.*\\d)"
            + "|(?=.*[A-Z])(?=.*[^A-Za-z0-9])|(?=.*[a-z])(?=.*\\d)"
            + "|(?=.*[a-z])(?=.*[^A-Za-z0-9])|(?=.*\\d)(?=.*[^A-Za-z0-9])).{10,16}$";
        if (!newPassword.matches(pwRegex)) {
            model.addAttribute("step",       "reset");
            model.addAttribute("pwPolicyError",
                "비밀번호는 10~16자, 영문 대소문자/숫자/특수문자 중 2가지 이상 조합이어야 합니다.");
            return "auth/find-password";
        }

        memberService.resetPassword(resetUsername, newPassword);
        session.removeAttribute("RESET_USERNAME");

        redirectAttributes.addFlashAttribute("successMsg",
            "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");
        return "redirect:/auth/login";
    }

    /* ════════════════════════════════════════
       유틸
    ════════════════════════════════════════ */
    /** 휴대폰 번호 마스킹: 01012345678 → 010-****-5678 */
    private String maskPhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replace("-", "");
        if (digits.length() < 7) return phone;
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /* ════════════════════════════════════════
       자동 로그인 (회원가입 후)
    ════════════════════════════════════════ */
    private void autoLogin(String username, HttpSession session) {
        UserDetails userDetails = memberService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authToken =
            UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null,
                userDetails.getAuthorities()
            );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
