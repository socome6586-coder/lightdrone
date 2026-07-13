package com.lightdrone.repository;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Qna;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QnaRepository extends JpaRepository<Qna, Long> {

    @EntityGraph(attributePaths = {"member"})
    Page<Qna> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"member", "imageUrls"})
    Optional<Qna> findById(Long id);

    /* 카테고리·검색어 복합 필터 (content는 CLOB이므로 title만 검색)
     * PostgreSQL은 null 바인딩 파라미터의 IS NULL 비교에서 타입을 추론하지 못해
     * "could not determine data type of parameter" 오류가 발생하므로,
     * 조건 조합별로 파생 쿼리 메서드를 분리한다. */

    @EntityGraph(attributePaths = {"member"})
    Page<Qna> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    @EntityGraph(attributePaths = {"member"})
    Page<Qna> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"member"})
    Page<Qna> findByCategoryAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            String category, String keyword, Pageable pageable);

    /** 알림용: 특정 회원의 answered=true Q&A, 최근 30일 이내 */
    List<Qna> findByMemberAndAnsweredTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            Member member, LocalDateTime after);

    /** 회원 삭제 전 처리 */
    void deleteAllByMember(Member member);
}
