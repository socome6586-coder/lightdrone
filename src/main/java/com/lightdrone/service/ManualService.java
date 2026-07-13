package com.lightdrone.service;

import com.lightdrone.domain.Manual;
import com.lightdrone.domain.Member;
import com.lightdrone.dto.ManualDto;
import com.lightdrone.repository.ManualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualService {

    private final ManualRepository manualRepository;

    public Page<Manual> getManuals(String category, String keyword, Pageable pageable) {
        if (StringUtils.hasText(keyword)) {
            return manualRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
        }
        if (StringUtils.hasText(category)) {
            return manualRepository.findByCategoryOrderByPinnedDescCreatedAtDesc(category, pageable);
        }
        return manualRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    @Transactional
    public Manual getManual(Long id) {
        Manual manual = manualRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴얼을 찾을 수 없습니다."));
        manual.incrementViewCount();
        return manual;
    }

    public List<Manual> getRecentManuals() {
        return manualRepository.findTop5ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void create(ManualDto dto, Member author) {
        Manual manual = Manual.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .category(dto.getCategory())
                .pinned(dto.isPinned())
                .author(author)
                .build();
        manualRepository.save(manual);
    }

    @Transactional
    public void update(Long id, ManualDto dto) {
        Manual manual = manualRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴얼을 찾을 수 없습니다."));
        manual.setTitle(dto.getTitle());
        manual.setContent(dto.getContent());
        manual.setCategory(dto.getCategory());
        manual.setPinned(dto.isPinned());
    }

    @Transactional
    public void delete(Long id) {
        manualRepository.deleteById(id);
    }
}
