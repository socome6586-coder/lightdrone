package com.lightdrone.service;

import com.lightdrone.domain.Popup;
import com.lightdrone.repository.PopupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PopupService {

    private final PopupRepository popupRepository;
    private final FileStorageService fileStorageService;
    private final LinkUrlNormalizer linkUrlNormalizer;

    /** 관리자 목록 전체 */
    @Transactional(readOnly = true)
    public List<Popup> getAllForAdmin() {
        return popupRepository.findAllByOrderBySortOrderAscIdDesc();
    }

    @Transactional(readOnly = true)
    public Popup getOrNull(Long id) {
        return popupRepository.findById(id).orElse(null);
    }

    /** 현재 노출 가능한 팝업 (active=true, 기간 내) */
    @Transactional(readOnly = true)
    public List<Popup> getActivePopups() {
        LocalDateTime now = LocalDateTime.now();
        return popupRepository.findByActiveTrueOrderBySortOrderAscIdDesc().stream()
                .filter(p -> p.getStartAt() == null || !p.getStartAt().isAfter(now))
                .filter(p -> p.getEndAt() == null || !p.getEndAt().isBefore(now))
                .filter(p -> p.getImageUrl() != null && !p.getImageUrl().isBlank())
                .toList();
    }

    /** 신규 등록 */
    @Transactional
    public void create(String title, String imageUrl, String linkUrl, boolean newTab,
                       boolean active, LocalDateTime startAt, LocalDateTime endAt,
                       int width, int sortOrder) {
        validatePeriod(startAt, endAt);
        Popup popup = new Popup();
        popup.setTitle(blankToDefault(title, "팝업"));
        popup.setImageUrl(nullIfBlank(imageUrl));
        popup.setLinkUrl(linkUrlNormalizer.normalize(linkUrl));
        popup.setNewTab(newTab);
        popup.setActive(active);
        popup.setStartAt(startAt);
        popup.setEndAt(endAt);
        popup.setWidth(width > 0 ? width : 420);
        popup.setSortOrder(sortOrder);
        popupRepository.save(popup);
    }

    /** 수정 — imageUrl 이 null 이면 기존 이미지를 유지합니다. */
    @Transactional
    public void update(Long id, String title, String imageUrl, String linkUrl, boolean newTab,
                       boolean active, LocalDateTime startAt, LocalDateTime endAt,
                       int width, int sortOrder) {
        validatePeriod(startAt, endAt);
        Popup popup = popupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. id=" + id));
        popup.setTitle(blankToDefault(title, popup.getTitle()));
        if (imageUrl != null && !imageUrl.isBlank()) {
            popup.setImageUrl(imageUrl);
        }
        popup.setLinkUrl(linkUrlNormalizer.normalize(linkUrl));
        popup.setNewTab(newTab);
        popup.setActive(active);
        popup.setStartAt(startAt);
        popup.setEndAt(endAt);
        popup.setWidth(width > 0 ? width : 420);
        popup.setSortOrder(sortOrder);
        popupRepository.save(popup);
    }

    /** 노출 on/off 토글 */
    @Transactional
    public void toggleActive(Long id) {
        popupRepository.findById(id).ifPresent(p -> p.setActive(!p.isActive()));
    }

    /** 삭제 (이미지 파일도 함께 제거) */
    @Transactional
    public void delete(Long id) {
        popupRepository.findById(id).ifPresent(p -> {
            if (p.getImageUrl() != null && p.getImageUrl().startsWith("/uploads/")) {
                fileStorageService.delete(p.getImageUrl());
            }
            popupRepository.delete(p);
        });
    }

    /** 노출 기간 검증 — 시작일이 종료일보다 늦으면 안 됩니다. */
    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("노출 시작일이 종료일보다 늦을 수 없습니다.");
        }
    }

    private String nullIfBlank(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    private String blankToDefault(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s.trim() : fallback;
    }
}
