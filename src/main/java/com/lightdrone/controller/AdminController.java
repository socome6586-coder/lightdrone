package com.lightdrone.controller;

import com.lightdrone.domain.DroneService;
import com.lightdrone.domain.enums.BusinessGrade;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.dto.AdminMemberEditDto;
import com.lightdrone.dto.ManualDto;
import com.lightdrone.dto.NoticeDto;
import com.lightdrone.dto.SupportVideoDto;
import com.lightdrone.service.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final DateTimeFormatter ADMIN_ALERT_TIME_FMT = DateTimeFormatter.ofPattern("MM.dd HH:mm");
 
    private final MemberService memberService;
    private final NoticeService noticeService;
    private final ManualService manualService;
    private final SupportVideoService supportVideoService;
    private final DroneServiceService droneServiceService;
    private final InquiryService inquiryService;
    private final QnaService qnaService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final VisitorLogService visitorLogService;
    private final PricingService pricingService;
    private final StockAlertService stockAlertService;

    @GetMapping
    public String dashboard(Model model) {
        // ── 퀵스탯 ──
        model.addAttribute("memberCount",      memberService.count());
        model.addAttribute("unansweredCount",  inquiryService.countUnanswered());
        model.addAttribute("productCount",     productService.count());
        model.addAttribute("pendingOrderCount",orderService.countPendingPayment());

        // ── 통계 카드 ──
        model.addAttribute("todayMemberCount", memberService.countToday());
        model.addAttribute("visitorTotal",     visitorLogService.countTotal());
        model.addAttribute("visitorToday",     visitorLogService.countToday());
        model.addAttribute("orderTotal",       orderService.sumTotalPrice());
        model.addAttribute("orderToday",       orderService.sumTodayPrice());
        model.addAttribute("orderTodayCount",  orderService.countToday());
        model.addAttribute("recentOrderAlerts", orderService.getRecentAdminOrderAlerts().stream()
                .map(order -> new AdminOrderAlert(
                        order.getId(),
                        order.getPaymentMethod() == com.lightdrone.domain.enums.PaymentMethod.CARD ? "카드결제 완료" : "무통장 주문",
                        order.getOrderNumber(),
                        order.getBuyerName(),
                        order.getItems().isEmpty() ? "상품"
                                : order.getItems().get(0).getProductName()
                                + (order.getItems().size() > 1 ? " 외 " + (order.getItems().size() - 1) + "건" : ""),
                        order.getTotalPrice(),
                        (order.getPaymentMethod() == com.lightdrone.domain.enums.PaymentMethod.CARD
                                ? (order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt())
                                : order.getCreatedAt()).format(ADMIN_ALERT_TIME_FMT)
                ))
                .toList());

        // ── 7일 방문자 차트 ──
        Map<String, Long> dailyVisitors = visitorLogService.getDailyVisitors(8);
        model.addAttribute("chartLabels", dailyVisitors.keySet().stream()
                .map(d -> d.substring(5))           // "06-03" 형식으로 자름
                .collect(Collectors.joining(",")));
        model.addAttribute("chartData", dailyVisitors.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));

        // ── 재고 부족/품절 위젯 ──
        int lowStockThreshold = stockAlertService.getLowThreshold();
        var lowStockProducts = productService.getLowStockProducts(lowStockThreshold);
        model.addAttribute("lowStockThreshold", lowStockThreshold);
        model.addAttribute("lowStockCount", lowStockProducts.size());
        model.addAttribute("lowStockProducts", lowStockProducts.stream().limit(8).toList());

        // ── 최근 공지 ──
        model.addAttribute("recentNotices", noticeService.getRecentNotices());
        return "admin/dashboard";
    }

    private record AdminOrderAlert(
            Long id,
            String label,
            String orderNumber,
            String buyerName,
            String itemSummary,
            Long totalPrice,
            String createdAt
    ) {}

    // ─── 회원 관리 ───────────────────────────────────────────────
    @GetMapping("/members")
    public String members(Model model) {
        model.addAttribute("members", memberService.findAll());
        model.addAttribute("gradePolicy", pricingService.getPolicy());
        return "admin/members";
    }

    /** 등급별 상시 할인율 전역 설정 저장 */
    @PostMapping("/member-grades/policy")
    public String updateGradePolicy(@RequestParam int wholesalePercent,
                                    @RequestParam int superWholesalePercent,
                                    RedirectAttributes redirectAttributes) {
        pricingService.updatePolicy(wholesalePercent, superWholesalePercent);
        redirectAttributes.addFlashAttribute("successMsg", "등급별 할인율이 저장되었습니다.");
        return "redirect:/admin/members";
    }

    @GetMapping("/members/{id}/edit")
    public String editMemberForm(@PathVariable Long id, Model model) {
        var member = memberService.findById(id);
        var dto = new AdminMemberEditDto();
        dto.setName(member.getName());
        dto.setEmail(member.getEmail());
        dto.setPhone(member.getPhone());
        dto.setRole(member.getRole());
        dto.setBusinessGrade(member.getBusinessGrade());
        dto.setEnabled(member.isEnabled());
        model.addAttribute("member", member);
        model.addAttribute("memberDto", dto);
        model.addAttribute("roles", Role.values());
        model.addAttribute("grades", BusinessGrade.values());
        model.addAttribute("gradePolicy", pricingService.getPolicy());
        return "admin/member-edit";
    }

    @PostMapping("/members/{id}/edit")
    public String editMember(@PathVariable Long id,
                             @Valid @ModelAttribute AdminMemberEditDto memberDto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("member", memberService.findById(id));
            model.addAttribute("roles", Role.values());
            model.addAttribute("grades", BusinessGrade.values());
            model.addAttribute("gradePolicy", pricingService.getPolicy());
            return "admin/member-edit";
        }
        memberService.adminUpdateMember(id, memberDto);
        redirectAttributes.addFlashAttribute("successMsg", "회원 정보가 수정되었습니다.");
        return "redirect:/admin/members";
    }

    @PostMapping("/members/{id}/delete")
    public String deleteMember(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        var target = memberService.findById(id);
        if (target.getRole() == Role.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMsg", "관리자 계정은 삭제할 수 없습니다.");
            return "redirect:/admin/members";
        }
        memberService.deleteMember(id);
        redirectAttributes.addFlashAttribute("successMsg", "회원이 삭제되었습니다.");
        return "redirect:/admin/members";
    }

    // ─── 공지사항 관리 ────────────────────────────────────────────
    @GetMapping("/notices")
    public String notices(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("notices", noticeService.getNotices(PageRequest.of(page, 15)));
        return "admin/notices";
    }

    @GetMapping("/notices/write")
    public String writeNoticeForm(Model model) {
        model.addAttribute("noticeDto", new NoticeDto());
        return "admin/notice-write";
    }

    @PostMapping("/notices")
    public String createNotice(@Valid @ModelAttribute NoticeDto noticeDto,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("noticeDto", noticeDto);
            return "admin/notice-write";
        }
        noticeService.create(noticeDto, memberService.findByUsername(userDetails.getUsername()));
        redirectAttributes.addFlashAttribute("successMsg", "공지사항이 등록되었습니다.");
        return "redirect:/admin/notices";
    }

    @GetMapping("/notices/{id}/edit")
    public String editNoticeForm(@PathVariable Long id, Model model) {
        var notice = noticeService.getNoticeWithoutView(id);
        var dto = new NoticeDto();
        dto.setTitle(notice.getTitle());
        dto.setContent(notice.getContent());
        dto.setPinned(notice.isPinned());
        model.addAttribute("notice", notice);
        model.addAttribute("noticeDto", dto);
        return "admin/notice-edit";
    }

    @PostMapping("/notices/{id}/edit")
    public String editNotice(@PathVariable Long id,
                             @Valid @ModelAttribute NoticeDto noticeDto,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("notice", noticeService.getNoticeWithoutView(id));
            model.addAttribute("noticeDto", noticeDto);
            return "admin/notice-edit";
        }
        noticeService.update(id, noticeDto);
        redirectAttributes.addFlashAttribute("successMsg", "공지사항이 수정되었습니다.");
        return "redirect:/admin/notices";
    }

    @PostMapping("/notices/{id}/delete")
    public String deleteNotice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        noticeService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "공지사항이 삭제되었습니다.");
        return "redirect:/admin/notices";
    }

    // ─── 메뉴얼 관리 ─────────────────────────────────────────────
    @GetMapping("/manuals")
    public String manuals(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "0") int videoPage,
                          Model model) {
        model.addAttribute("manuals", manualService.getManuals("", "", PageRequest.of(page, 15)));
        model.addAttribute("videos", supportVideoService.getAdminVideos(PageRequest.of(videoPage, 10)));
        model.addAttribute("manualDto", new ManualDto());
        model.addAttribute("supportVideoDto", new SupportVideoDto());
        return "admin/manuals";
    }

    @PostMapping("/manual-videos")
    public String createSupportVideo(@Valid @ModelAttribute SupportVideoDto supportVideoDto,
                                     BindingResult bindingResult,
                                     RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMsg", "영상 입력값을 확인해주세요.");
            return "redirect:/admin/manuals";
        }
        try {
            supportVideoService.create(supportVideoDto);
            redirectAttributes.addFlashAttribute("successMsg", "영상 자료가 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/manuals";
    }

    @GetMapping("/manual-videos/{id}/edit")
    public String editSupportVideoForm(@PathVariable Long id, Model model) {
        var video = supportVideoService.getVideo(id);
        var dto = new SupportVideoDto();
        dto.setTitle(video.getTitle());
        dto.setYoutubeUrl(video.getYoutubeUrl());
        dto.setCategory(video.getCategory());
        dto.setVisible(video.isVisible());
        dto.setSortOrder(video.getSortOrder());
        model.addAttribute("video", video);
        model.addAttribute("supportVideoDto", dto);
        return "admin/manual-video-edit";
    }

    @PostMapping("/manual-videos/{id}/edit")
    public String editSupportVideo(@PathVariable Long id,
                                   @Valid @ModelAttribute SupportVideoDto supportVideoDto,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("video", supportVideoService.getVideo(id));
            model.addAttribute("supportVideoDto", supportVideoDto);
            return "admin/manual-video-edit";
        }
        try {
            supportVideoService.update(id, supportVideoDto);
            redirectAttributes.addFlashAttribute("successMsg", "영상 자료가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/manuals";
    }

    @PostMapping("/manual-videos/{id}/delete")
    public String deleteSupportVideo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        supportVideoService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "영상 자료가 삭제되었습니다.");
        return "redirect:/admin/manuals";
    }

    @PostMapping("/manuals")
    public String createManual(@Valid @ModelAttribute ManualDto manualDto,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMsg", "입력값을 확인해주세요.");
            return "redirect:/admin/manuals";
        }
        manualService.create(manualDto, memberService.findByUsername(userDetails.getUsername()));
        redirectAttributes.addFlashAttribute("successMsg", "메뉴얼이 등록되었습니다.");
        return "redirect:/admin/manuals";
    }

    @GetMapping("/manuals/{id}/edit")
    public String editManualForm(@PathVariable Long id, Model model) {
        var manual = manualService.getManual(id);
        var dto = new ManualDto();
        dto.setTitle(manual.getTitle());
        dto.setContent(manual.getContent());
        dto.setCategory(manual.getCategory());
        dto.setPinned(manual.isPinned());
        model.addAttribute("manual", manual);
        model.addAttribute("manualDto", dto);
        return "admin/manual-edit";
    }

    @PostMapping("/manuals/{id}/edit")
    public String editManual(@PathVariable Long id,
                             @Valid @ModelAttribute ManualDto manualDto,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("manual", manualService.getManual(id));
            model.addAttribute("manualDto", manualDto);
            return "admin/manual-edit";
        }
        manualService.update(id, manualDto);
        redirectAttributes.addFlashAttribute("successMsg", "메뉴얼이 수정되었습니다.");
        return "redirect:/admin/manuals";
    }

    @PostMapping("/manuals/{id}/delete")
    public String deleteManual(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        manualService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "메뉴얼이 삭제되었습니다.");
        return "redirect:/admin/manuals";
    }

    // ─── 서비스 관리 ─────────────────────────────────────────────
    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("services", droneServiceService.getAllServices());
        model.addAttribute("newService", new DroneService());
        return "admin/services";
    }

    @PostMapping("/services")
    public String createService(@ModelAttribute DroneService droneService,
                                RedirectAttributes redirectAttributes) {
        droneServiceService.save(droneService);
        redirectAttributes.addFlashAttribute("successMsg", "서비스가 등록되었습니다.");
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/delete")
    public String deleteService(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        droneServiceService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "서비스가 삭제되었습니다.");
        return "redirect:/admin/services";
    }

    // ─── 문의 관리 ───────────────────────────────────────────────
    @GetMapping("/inquiries")
    public String inquiries(@RequestParam(defaultValue = "0") int inquiryPage,
                            @RequestParam(defaultValue = "0") int qnaPage,
                            @RequestParam(defaultValue = "0") int reviewPage,
                            @RequestParam(defaultValue = "inquiry") String tab,
                            Model model) {
        model.addAttribute("inquiries", inquiryService.getAllInquiries(PageRequest.of(inquiryPage, 15)));
        model.addAttribute("qnaList", qnaService.getList(PageRequest.of(qnaPage, 15)));
        model.addAttribute("reviews", reviewService.getListForAdmin(PageRequest.of(reviewPage, 15)));
        model.addAttribute("tab", tab);
        return "admin/inquiries";
    }

    @GetMapping("/inquiries/{id}")
    public String inquiryDetail(@PathVariable Long id, Model model) {
        model.addAttribute("inquiry", inquiryService.getInquiry(id));
        model.addAttribute("inquirySmsPhone", inquiryService.resolveInquiryPhone(id));
        return "admin/inquiry-detail";
    }

    @PostMapping("/inquiries/{id}/answer")
    public String answerInquiry(@PathVariable Long id,
                                @RequestParam String answer,
                                @RequestParam(required = false) String smsPhone,
                                RedirectAttributes redirectAttributes) {
        inquiryService.answer(id, answer, smsPhone);
        redirectAttributes.addFlashAttribute("successMsg", "답변이 등록되었습니다.");
        return "redirect:/admin/inquiries/" + id;
    }

    @PostMapping("/inquiries/{id}/delete")
    public String deleteInquiry(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        inquiryService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "문의가 삭제되었습니다.");
        return "redirect:/admin/inquiries?tab=inquiry";
    }

    // ─── Q&A 관리 (관리자 전용) ──────────────────────────────────
    @GetMapping("/qna/{id}")
    public String adminQnaDetail(@PathVariable Long id, Model model) {
        model.addAttribute("qna", qnaService.getById(id));
        return "admin/qna-detail";
    }

    @PostMapping("/qna/{id}/answer")
    public String adminQnaAnswer(@PathVariable Long id,
                                 @RequestParam String answer,
                                 RedirectAttributes redirectAttributes) {
        qnaService.answer(id, answer);
        redirectAttributes.addFlashAttribute("successMsg", "답변이 등록되었습니다.");
        return "redirect:/admin/qna/" + id;
    }

    @PostMapping("/qna/{id}/delete")
    public String deleteQna(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        qnaService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "Q&A 게시글이 삭제되었습니다.");
        return "redirect:/admin/inquiries?tab=qna";
    }

    @PostMapping("/qna/bulk-delete")
    public String bulkDeleteQna(@RequestParam(value = "ids", required = false) List<Long> ids,
                                RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "삭제할 Q&A를 선택해 주세요.");
            return "redirect:/admin/inquiries?tab=qna";
        }
        int count = qnaService.bulkDelete(ids);
        ra.addFlashAttribute("successMsg", count + "건의 Q&A가 삭제되었습니다.");
        return "redirect:/admin/inquiries?tab=qna";
    }

    // ─── 구매 후기 관리 (inquiries 탭에서 통합 관리) ────────────────
    @PostMapping("/reviews/{id}/toggle")
    public String toggleReviewVisible(@PathVariable Long id, RedirectAttributes ra) {
        reviewService.toggleVisible(id);
        ra.addFlashAttribute("successMsg", "후기 공개/숨김 상태가 변경되었습니다.");
        return "redirect:/admin/inquiries?tab=review";
    }

    @PostMapping("/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id,
                               @org.springframework.web.bind.annotation.RequestParam(defaultValue = "list") String from,
                               RedirectAttributes ra) {
        reviewService.delete(id);
        ra.addFlashAttribute("successMsg", "후기가 삭제되었습니다.");
        return "list".equals(from) ? "redirect:/admin/reviews" : "redirect:/admin/inquiries?tab=review";
    }

    @PostMapping("/reviews/bulk-delete")
    public String bulkDeleteReviews(@RequestParam(value = "ids", required = false) List<Long> ids,
                                    RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "삭제할 후기를 선택해 주세요.");
            return "redirect:/admin/inquiries?tab=review";
        }
        int count = reviewService.bulkDelete(ids);
        ra.addFlashAttribute("successMsg", count + "건의 후기가 삭제되었습니다.");
        return "redirect:/admin/inquiries?tab=review";
    }
    @PostMapping("/inquiries/bulk-delete")
    public String bulkDeleteInquiries(@RequestParam(value = "ids", required = false) List<Long> ids,
                                      RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "삭제할 견적문의를 선택해 주세요.");
            return "redirect:/admin/inquiries?tab=inquiry";
        }
        int count = inquiryService.bulkDelete(ids);
        ra.addFlashAttribute("successMsg", count + "건의 견적문의가 삭제되었습니다.");
        return "redirect:/admin/inquiries?tab=inquiry";
    }
}
