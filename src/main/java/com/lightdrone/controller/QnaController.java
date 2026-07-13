package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Qna;
import com.lightdrone.dto.QnaDto;
import com.lightdrone.dto.QnaUpdateDto;
import com.lightdrone.service.CartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.QnaService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/qna")
@RequiredArgsConstructor
public class QnaController {

    private static final String SESSION_PREFIX = "QNA_UNLOCKED_";
    private static final int MAX_PW_ATTEMPTS = 5;
    private static final List<String> QNA_CATEGORIES =
            List.of("상품문의", "결제문의", "견적문의", "배송문의", "기타");

    private final QnaService qnaService;
    private final MemberService memberService;
    private final CartService cartService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String keyword,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {
        model.addAttribute("qnaList",
                qnaService.getListFiltered(category, keyword, PageRequest.of(page, 10)));
        model.addAttribute("qnaCategories", QNA_CATEGORIES);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("keyword", keyword);
        if (userDetails != null) {
            model.addAttribute("member", memberService.findByUsername(userDetails.getUsername()));
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        }
        return "qna/list";
    }

    @GetMapping("/write")
    public String writeForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member member = memberService.findByUsername(userDetails.getUsername());
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        model.addAttribute("qnaDto", new QnaDto());
        model.addAttribute("qnaCategories", QNA_CATEGORIES);
        return "qna/write";
    }

    @PostMapping("/write")
    public String write(@ModelAttribute QnaDto qnaDto,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal UserDetails userDetails,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        // 제목/내용 빈 값 검증
        if (qnaDto.getTitle() == null || qnaDto.getTitle().isBlank()) {
            bindingResult.rejectValue("title", "NotBlank", "제목을 입력해주세요.");
        }
        if (qnaDto.getContent() == null || qnaDto.getContent().isBlank()) {
            bindingResult.rejectValue("content", "NotBlank", "내용을 입력해주세요.");
        }
        // 비공개 글이면 비밀번호 검증
        if (qnaDto.isSecret()) {
            String pw = qnaDto.getPassword();
            if (pw == null || !pw.matches("^[0-9]{4}$")) {
                bindingResult.rejectValue("password", "Pattern", "비밀번호는 4자리 숫자여야 합니다.");
            }
        }
        if (bindingResult.hasErrors()) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("qnaCategories", QNA_CATEGORIES);
            return "qna/write";
        }
        qnaService.create(qnaDto, memberService.findByUsername(userDetails.getUsername()));
        redirectAttributes.addFlashAttribute("successMsg", "질문이 등록되었습니다.");
        return "redirect:/qna";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         HttpSession session,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {
        Qna qna = qnaService.getById(id);
        boolean isAdmin = false;
        boolean isOwner = false;
        if (userDetails != null) {
            Member member = memberService.findByUsername(userDetails.getUsername());
            isAdmin = member.getRole().name().equals("ADMIN");
            isOwner = qnaService.isOwner(id, member.getId());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            model.addAttribute("isAdmin", isAdmin);
        }
        // 공개 글이거나 관리자이거나 이미 비밀번호 인증된 경우 열람 가능
        boolean unlocked = isAdmin
                || !qna.isSecret()
                || Boolean.TRUE.equals(session.getAttribute(SESSION_PREFIX + id));
        if (unlocked) {
            qnaService.incrementViewCount(id);
        }
        model.addAttribute("qna", qna);
        model.addAttribute("unlocked", unlocked);
        // 수정 버튼은 작성자 본인 + 미답변 글에만 노출
        model.addAttribute("canEdit", isOwner && !qna.isAnswered());
        return "qna/detail";
    }

    @PostMapping("/{id}/verify")
    public String verify(@PathVariable Long id,
                         @RequestParam String password,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        // 4자리 비밀번호 무차별 대입 방어: 세션당 누적 실패 5회 초과 시 차단
        String failKey = "QNA_PW_FAIL_" + id;
        int fails = session.getAttribute(failKey) instanceof Integer f ? f : 0;
        if (fails >= MAX_PW_ATTEMPTS) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "비밀번호 입력 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/qna/" + id;
        }
        if (qnaService.verifyPassword(id, password)) {
            session.setAttribute(SESSION_PREFIX + id, true);
            session.removeAttribute(failKey);
        } else {
            session.setAttribute(failKey, fails + 1);
            redirectAttributes.addFlashAttribute("errorMsg",
                    "비밀번호가 올바르지 않습니다. (" + (fails + 1) + "/" + MAX_PW_ATTEMPTS + ")");
        }
        return "redirect:/qna/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id,
                         @RequestParam String answer,
                         RedirectAttributes redirectAttributes) {
        qnaService.answer(id, answer);
        redirectAttributes.addFlashAttribute("successMsg", "답변이 등록되었습니다.");
        return "redirect:/qna/" + id;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           HttpSession session,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        if (userDetails == null) return "redirect:/auth/login";
        Member member = memberService.findByUsername(userDetails.getUsername());
        Qna qna = qnaService.getById(id);
        // 작성자 본인 + 미답변 글만 수정 가능
        if (!qnaService.isOwner(id, member.getId()) || qna.isAnswered()) {
            return "redirect:/qna/" + id;
        }
        QnaUpdateDto dto = new QnaUpdateDto();
        dto.setTitle(qna.getTitle());
        dto.setContent(qna.getContent());
        model.addAttribute("qna", qna);
        model.addAttribute("qnaUpdateDto", dto);
        model.addAttribute("member", member);
        model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        return "qna/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute QnaUpdateDto qnaUpdateDto,
                       BindingResult bindingResult,
                       HttpSession session,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/auth/login";
        Member member = memberService.findByUsername(userDetails.getUsername());
        Qna qna = qnaService.getById(id);
        // 작성자 본인 + 미답변 글만 수정 가능
        if (!qnaService.isOwner(id, member.getId()) || qna.isAnswered()) {
            return "redirect:/qna/" + id;
        }
        if (qnaUpdateDto.getTitle() == null || qnaUpdateDto.getTitle().isBlank()) {
            bindingResult.rejectValue("title", "NotBlank", "제목을 입력해주세요.");
        }
        if (qnaUpdateDto.getContent() == null || qnaUpdateDto.getContent().isBlank()) {
            bindingResult.rejectValue("content", "NotBlank", "내용을 입력해주세요.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("qna", qna);
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            return "qna/edit";
        }
        qnaService.update(id, qnaUpdateDto);
        redirectAttributes.addFlashAttribute("successMsg", "게시글이 수정되었습니다.");
        return "redirect:/qna/" + id;
    }
}
