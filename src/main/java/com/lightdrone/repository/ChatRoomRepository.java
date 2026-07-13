package com.lightdrone.repository;

import com.lightdrone.domain.ChatRoom;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.ChatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 회원의 아직 종료되지 않은(WAITING/ACTIVE) 채팅방 1개 */
    Optional<ChatRoom> findFirstByMemberAndStatusNotOrderByIdDesc(Member member, ChatStatus status);

    /** 관리자 목록용: 종료되지 않은 채팅방 전체, 최근 메시지순 */
    List<ChatRoom> findByStatusNotOrderByLastMessageAtDescIdDesc(ChatStatus status);

    /**
     * 관리자 독점 잠금: admin이 아직 없을 때만 점유에 성공한다.
     * 동시에 두 관리자가 들어와도 단 한 명만 1행을 갱신할 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoom r set r.admin = :admin, r.status = com.lightdrone.domain.enums.ChatStatus.ACTIVE "
            + "where r.id = :id and r.admin is null")
    int claim(@Param("id") Long id, @Param("admin") Member admin);

    /** 상태별 채팅방 수 (관리자 알림 배지용) */
    long countByStatus(ChatStatus status);

    /** 회원 삭제 시: 해당 회원이 개설한 채팅방 목록 (메시지 선삭제용) */
    List<ChatRoom> findByMember(Member member);

    void deleteAllByMember(Member member);

    /** 회원 삭제 시: 이 회원이 담당 관리자로 배정된 방의 admin 참조만 해제 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoom r set r.admin = null where r.admin = :member")
    void detachAdmin(@Param("member") Member member);
}
