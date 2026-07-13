package com.lightdrone.repository;

import com.lightdrone.domain.Inquiry;
import com.lightdrone.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    Page<Inquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Inquiry> findByMemberOrderByCreatedAtDesc(Member member, Pageable pageable);
    long countByAnsweredFalse();
    List<Inquiry> findByNameOrderByCreatedAtDesc(String name);

    /** 알림용: 특정 회원의 answered=true 문의, 최근 30일 이내 */
    List<Inquiry> findByMemberAndAnsweredTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            Member member, LocalDateTime after);

    /** 회원 삭제 전 처리 */
    void deleteAllByMember(Member member);

    /** 통합검색: 이름·이메일·연락처·제목 부분일치 (최신순) */
    @Query("SELECT i FROM Inquiry i WHERE " +
           "LOWER(i.name) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(i.email) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR i.phone LIKE CONCAT('%', :kw, '%') " +
           "OR LOWER(i.subject) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "ORDER BY i.createdAt DESC")
    List<Inquiry> searchForAdmin(@Param("kw") String kw, Pageable pageable);
}
