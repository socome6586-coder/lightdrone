package com.lightdrone.repository;

import com.lightdrone.domain.SupportVideo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportVideoRepository extends JpaRepository<SupportVideo, Long> {

    Page<SupportVideo> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable pageable);

    Page<SupportVideo> findByVisibleTrueOrderBySortOrderAscCreatedAtDesc(Pageable pageable);

    Page<SupportVideo> findByVisibleTrueAndCategoryOrderBySortOrderAscCreatedAtDesc(String category, Pageable pageable);

    Page<SupportVideo> findByVisibleTrueAndTitleContainingIgnoreCaseOrderBySortOrderAscCreatedAtDesc(String keyword, Pageable pageable);

    Page<SupportVideo> findByVisibleTrueAndCategoryAndTitleContainingIgnoreCaseOrderBySortOrderAscCreatedAtDesc(String category, String keyword, Pageable pageable);
}
