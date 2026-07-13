package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Qna;
import com.lightdrone.dto.QnaDto;
import com.lightdrone.dto.QnaUpdateDto;
import com.lightdrone.repository.QnaRepository;
import com.lightdrone.support.OwnershipUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnaService {

    private final QnaRepository qnaRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final SmsService smsService;
    private final HtmlSanitizer htmlSanitizer;

    @Transactional
    public Qna create(QnaDto dto, Member member) {
        List<String> imageUrls = storeImages(dto.getImageFiles());
        String category = (dto.getCategory() == null || dto.getCategory().isBlank()) ? "기타" : dto.getCategory();
        String encodedPassword = (dto.isSecret() && dto.getPassword() != null && !dto.getPassword().isBlank())
                ? passwordEncoder.encode(dto.getPassword()) : "PUBLIC";
        return qnaRepository.save(Qna.builder()
                .title(dto.getTitle())
                .content(htmlSanitizer.clean(dto.getContent()))
                .category(category)
                .secret(dto.isSecret())
                .password(encodedPassword)
                .imageUrls(imageUrls)
                .member(member)
                .build());
    }

    /** 업로드된 여러 이미지를 저장하고 URL 목록을 반환 */
    private List<String> storeImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null) return urls;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                urls.add(fileStorageService.store(file));
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
        return urls;
    }

    public Page<Qna> getList(Pageable pageable) {
        return qnaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<Qna> getListFiltered(String category, String keyword, Pageable pageable) {
        String cat = (category == null || category.isBlank()) ? null : category;
        String kw  = (keyword  == null || keyword.isBlank())  ? null : keyword.trim();
        if (cat != null && kw != null) {
            return qnaRepository.findByCategoryAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(cat, kw, pageable);
        }
        if (cat != null) {
            return qnaRepository.findByCategoryOrderByCreatedAtDesc(cat, pageable);
        }
        if (kw != null) {
            return qnaRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(kw, pageable);
        }
        return qnaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** 통합검색용 — 제목 부분일치 (최신순, limit 건) */
    public List<Qna> searchForAdmin(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return qnaRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                keyword.trim(), org.springframework.data.domain.PageRequest.of(0, limit)).getContent();
    }

    public Qna getById(Long id) {
        return qnaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
    }

    public boolean verifyPassword(Long id, String rawPassword) {
        return passwordEncoder.matches(rawPassword, getById(id).getPassword());
    }

    /** 로그인 회원이 게시글 작성자 본인인지 — 수정 권한 검증용 */
    public boolean isOwner(Long id, Long memberId) {
        return OwnershipUtils.isOwner(getById(id), memberId);
    }

    @Transactional
    public void incrementViewCount(Long id) {
        getById(id).incrementViewCount();
    }

    @Transactional
    public void answer(Long id, String answer) {
        Qna qna = getById(id);
        qna.setAnswer(htmlSanitizer.clean(answer));
        qna.setAnswered(true);
        sendAnswerSms(qna);
    }

    private void sendAnswerSms(Qna qna) {
        if (qna.getMember() == null) return;          // 비회원은 전화번호 없음
        String phone = qna.getMember().getPhone();
        if (phone == null || phone.isBlank()) return;
        String msg = "[라이트드론] Q&A '" + qna.getTitle() + "'에 대한 답변이 등록되었습니다.\n"
                   + "홈페이지에서 확인해 주세요. (문의: 010-3565-9741)";
        smsService.send(phone, msg);
    }

    @Transactional
    public void update(Long id, QnaUpdateDto dto) {
        Qna qna = getById(id);
        if (qna.isAnswered()) {
            throw new IllegalStateException("답변이 완료된 게시글은 수정할 수 없습니다.");
        }
        qna.setTitle(dto.getTitle());
        qna.setContent(htmlSanitizer.clean(dto.getContent()));

        // 새로 첨부한 이미지를 기존 목록에 추가
        List<String> newUrls = storeImages(dto.getImageFiles());
        if (!newUrls.isEmpty()) {
            qna.getImageUrls().addAll(newUrls);
        }
    }

    @Transactional
    public void delete(Long id) {
        Qna qna = getById(id);
        fileStorageService.delete(qna.getImageUrl());
        if (qna.getImageUrls() != null) {
            qna.getImageUrls().forEach(fileStorageService::delete);
        }
        qnaRepository.delete(qna);
    }

    /** 일괄 삭제 */
    @Transactional
    public int bulkDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try { delete(id); count++; } catch (Exception ignored) {}
        }
        return count;
    }
}
