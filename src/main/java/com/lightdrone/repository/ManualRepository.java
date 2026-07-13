package com.lightdrone.repository;

import com.lightdrone.domain.Manual;
import com.lightdrone.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ManualRepository extends JpaRepository<Manual, Long> {
    Page<Manual> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
    Page<Manual> findByCategoryOrderByPinnedDescCreatedAtDesc(String category, Pageable pageable);
    Page<Manual> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);
    List<Manual> findTop5ByOrderByCreatedAtDesc();

    /** 회원 삭제 시: 작성자 참조만 해제 (자료 내용은 보존) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Manual m set m.author = null where m.author = :member")
    void detachAuthor(@Param("member") Member member);
}
