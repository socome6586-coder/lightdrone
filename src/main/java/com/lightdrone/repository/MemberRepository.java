package com.lightdrone.repository;

import com.lightdrone.domain.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByCreatedAtDesc();

    Optional<Member> findByUsername(String username);
    Optional<Member> findByEmail(String email);
    Optional<Member> findByPhone(String phone);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /* 아이디 찾기 */
    Optional<Member> findByNameAndEmail(String name, String email);
    Optional<Member> findByNameAndPhone(String name, String phone);

    /* 비밀번호 찾기 - 본인 확인 */
    Optional<Member> findByUsernameAndNameAndEmail(String username, String name, String email);
    Optional<Member> findByUsernameAndNameAndPhone(String username, String name, String phone);

    /* 소셜 로그인 회원 조회 */
    Optional<Member> findByProviderAndProviderId(String provider, String providerId);

    /* 오늘 가입한 회원 수 */
    @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :start AND m.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /* 통합검색: 이름·아이디·이메일·연락처 부분일치 (최신 가입순) */
    @Query("SELECT m FROM Member m " +
           "WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(m.username) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR m.phone LIKE CONCAT('%', :kw, '%') " +
           "ORDER BY m.createdAt DESC")
    List<Member> searchForAdmin(@Param("kw") String kw, Pageable pageable);
}
