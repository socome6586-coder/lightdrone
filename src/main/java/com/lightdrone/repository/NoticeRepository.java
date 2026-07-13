package com.lightdrone.repository;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);
    Page<Notice> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);
    List<Notice> findTop5ByOrderByCreatedAtDesc();
    List<Notice> findByPinnedTrueOrderByCreatedAtDesc();

    /** 회원 삭제 시: 작성자 참조만 해제 (공지 내용은 보존) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notice n set n.author = null where n.author = :member")
    void detachAuthor(@Param("member") Member member);
}
