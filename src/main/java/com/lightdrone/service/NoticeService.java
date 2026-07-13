package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Notice;
import com.lightdrone.dto.NoticeDto;
import com.lightdrone.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final FileStorageService fileStorageService;

    public Page<Notice> getNotices(Pageable pageable) {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    public Page<Notice> searchNotices(String keyword, Pageable pageable) {
        return noticeRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
    }

    @Transactional
    public Notice getNotice(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        notice.incrementViewCount();
        return notice;
    }

    /** 관리자 수정 폼 조회용 — viewCount 증가 없이 조회만 */
    public Notice getNoticeWithoutView(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
    }

    public List<Notice> getRecentNotices() {
        return noticeRepository.findTop5ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void create(NoticeDto dto, Member author) {
        String imageUrl = null;
        MultipartFile file = dto.getImageFile();
        if (file != null && !file.isEmpty()) {
            try {
                imageUrl = fileStorageService.store(file);
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
        Notice notice = Notice.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .pinned(dto.isPinned())
                .imageUrl(imageUrl)
                .author(author)
                .build();
        noticeRepository.save(notice);
    }

    @Transactional
    public void update(Long id, NoticeDto dto) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setPinned(dto.isPinned());

        MultipartFile file = dto.getImageFile();
        if (file != null && !file.isEmpty()) {
            // 기존 이미지 삭제 후 새 이미지 저장
            fileStorageService.delete(notice.getImageUrl());
            try {
                notice.setImageUrl(fileStorageService.store(file));
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다."));
        fileStorageService.delete(notice.getImageUrl());
        noticeRepository.delete(notice);
    }
}
