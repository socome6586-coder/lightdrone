package com.lightdrone.service;

import com.lightdrone.domain.SupportVideo;
import com.lightdrone.dto.SupportVideoDto;
import com.lightdrone.repository.SupportVideoRepository;
import com.lightdrone.support.YoutubeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportVideoService {

    private final SupportVideoRepository supportVideoRepository;

    public Page<SupportVideo> getVisibleVideos(String category, String keyword, Pageable pageable) {
        if (StringUtils.hasText(category) && StringUtils.hasText(keyword)) {
            return supportVideoRepository.findByVisibleTrueAndCategoryAndTitleContainingIgnoreCaseOrderBySortOrderAscCreatedAtDesc(category, keyword, pageable);
        }
        if (StringUtils.hasText(keyword)) {
            return supportVideoRepository.findByVisibleTrueAndTitleContainingIgnoreCaseOrderBySortOrderAscCreatedAtDesc(keyword, pageable);
        }
        if (StringUtils.hasText(category)) {
            return supportVideoRepository.findByVisibleTrueAndCategoryOrderBySortOrderAscCreatedAtDesc(category, pageable);
        }
        return supportVideoRepository.findByVisibleTrueOrderBySortOrderAscCreatedAtDesc(pageable);
    }

    public Page<SupportVideo> getAdminVideos(Pageable pageable) {
        return supportVideoRepository.findAllByOrderBySortOrderAscCreatedAtDesc(pageable);
    }

    public SupportVideo getVideo(Long id) {
        return supportVideoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("영상 자료를 찾을 수 없습니다."));
    }

    @Transactional
    public void create(SupportVideoDto dto) {
        validateYoutubeUrl(dto.getYoutubeUrl());
        supportVideoRepository.save(SupportVideo.builder()
                .title(dto.getTitle())
                .youtubeUrl(dto.getYoutubeUrl())
                .category(dto.getCategory())
                .visible(dto.isVisible())
                .sortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder())
                .build());
    }

    @Transactional
    public void update(Long id, SupportVideoDto dto) {
        validateYoutubeUrl(dto.getYoutubeUrl());
        SupportVideo video = getVideo(id);
        video.setTitle(dto.getTitle());
        video.setYoutubeUrl(dto.getYoutubeUrl());
        video.setCategory(dto.getCategory());
        video.setVisible(dto.isVisible());
        video.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    @Transactional
    public void delete(Long id) {
        supportVideoRepository.deleteById(id);
    }

    private void validateYoutubeUrl(String youtubeUrl) {
        if (YoutubeUtils.extractVideoId(youtubeUrl) == null) {
            throw new IllegalArgumentException("유효한 유튜브 링크를 입력해주세요.");
        }
    }
}
