package com.lightdrone.service;

import com.lightdrone.domain.Inquiry;
import com.lightdrone.domain.Member;
import com.lightdrone.dto.InquiryDto;
import com.lightdrone.repository.InquiryRepository;
import com.lightdrone.support.OwnershipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AdminAlertService adminAlertService;

    @Transactional
    public Inquiry submit(InquiryDto dto, Member member) {
        Inquiry inquiry = Inquiry.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .member(member)
                .build();
        inquiryRepository.save(inquiry);
        CompletableFuture.runAsync(() -> sendNotificationEmail(inquiry));
        CompletableFuture.runAsync(() -> sendAdminNotificationSms(inquiry));
        return inquiry;
    }

    public Page<Inquiry> getAllInquiries(Pageable pageable) {
        return inquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** 통합검색용 — 이름·이메일·연락처·제목 부분일치 (최신순, limit 건) */
    public List<Inquiry> searchForAdmin(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return inquiryRepository.searchForAdmin(keyword.trim(),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public Page<Inquiry> getMyInquiries(Member member, Pageable pageable) {
        return inquiryRepository.findByMemberOrderByCreatedAtDesc(member, pageable);
    }

    public Inquiry getInquiry(Long id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다."));
    }

    /** 로그인 회원의 문의 열람 권한 (관리자이거나 본인 문의일 때만 허용) — IDOR 방지 */
    public boolean canView(Long id, Long memberId, boolean isAdmin) {
        return OwnershipUtils.canAccess(getInquiry(id), memberId, isAdmin);
    }

    /** 답변 SMS 발송에 사용할 번호를 트랜잭션 안에서 미리 결정 (Lazy 로딩 안전) */
    public String resolveInquiryPhone(Long id) {
        Inquiry inquiry = getInquiry(id);
        String phone = inquiry.getPhone();
        if ((phone == null || phone.isBlank()) && inquiry.getMember() != null) {
            phone = inquiry.getMember().getPhone();
        }
        return (phone != null) ? phone : "";
    }

    @Transactional
    public void answer(Long id, String answer, String smsPhone) {
        Inquiry inquiry = getInquiry(id);
        inquiry.setAnswer(answer);
        inquiry.setAnswered(true);

        CompletableFuture.runAsync(() -> sendAnswerEmail(inquiry));

        // SMS 번호 우선순위: 관리자 입력값 > inquiry.phone > member.phone
        String phone = (smsPhone != null && !smsPhone.isBlank()) ? smsPhone.trim() : null;
        if (phone == null) phone = inquiry.getPhone();
        if ((phone == null || phone.isBlank()) && inquiry.getMember() != null) {
            phone = inquiry.getMember().getPhone();
        }
        if (phone == null || phone.isBlank()) {
            log.warn("[SMS] 문의 ID={} 수신 번호 없음 — 답변 SMS를 건너뜁니다.", id);
        } else {
            log.info("[SMS] 답변 알림 발송 → {}", phone);
            final String finalPhone = phone;
            final String msg = "[라이트드론] '" + inquiry.getSubject() + "' 문의에 대한 답변이 등록되었습니다.\n"
                       + "홈페이지에서 확인하시거나 추가 문의 사항은 lightdrone.co.kr을 이용해 주세요.";
            CompletableFuture.runAsync(() -> smsService.send(finalPhone, msg));
        }
    }

    public List<Inquiry> getGuestInquiries(String name, String phone) {
        String normalized = phone.replace("-", "").replace(" ", "");
        return inquiryRepository.findByNameOrderByCreatedAtDesc(name).stream()
                .filter(i -> i.getPhone() != null &&
                             i.getPhone().replace("-", "").replace(" ", "").equals(normalized))
                .collect(java.util.stream.Collectors.toList());
    }

    public long countUnanswered() {
        return inquiryRepository.countByAnsweredFalse();
    }

    @Transactional
    public void delete(Long id) {
        inquiryRepository.deleteById(id);
    }

    @Transactional
    public int bulkDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (inquiryRepository.existsById(id)) {
                inquiryRepository.deleteById(id);
                count++;
            }
        }
        return count;
    }

    private void sendNotificationEmail(Inquiry inquiry) {
        try {
            String html = ("새로운 문의가 접수되었습니다.\n\n"
                    + "이름: " + inquiry.getName() + "\n"
                    + "이메일: " + inquiry.getEmail() + "\n"
                    + "내용: " + inquiry.getContent()).replace("\n", "<br>");
            emailService.sendHtml("admin@lightdrone.co.kr",
                    "[라이트드론] 새 문의: " + inquiry.getSubject(), html);
        } catch (Exception ignored) {
        }
    }

    private void sendAdminNotificationSms(Inquiry inquiry) {
        String msg = "[라이트드론] 새 견적문의가 접수되었습니다.\n"
                   + "문의자: " + inquiry.getName()
                   + (inquiry.getPhone() != null && !inquiry.getPhone().isBlank()
                       ? " / " + inquiry.getPhone() : "") + "\n"
                   + "제목: " + inquiry.getSubject() + "\n"
                   + "홈페이지 관리자 페이지에서 확인해 주세요.";
        adminAlertService.sendToAdmins(msg);
    }

    private void sendAnswerEmail(Inquiry inquiry) {
        try {
            String html = ("안녕하세요, 라이트드론입니다.\n\n"
                    + "문의하신 내용에 대한 답변입니다.\n\n"
                    + "답변: " + inquiry.getAnswer()).replace("\n", "<br>");
            emailService.sendHtml(inquiry.getEmail(),
                    "[라이트드론] 문의 답변: " + inquiry.getSubject(), html);
        } catch (Exception ignored) {
        }
    }
}
