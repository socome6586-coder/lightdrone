package com.lightdrone.domain;

/**
 * 작성자(회원) 소유 개념이 있는 엔티티의 공통 인터페이스.
 * <p>
 * Order·Inquiry·Qna 처럼 "본인만 접근/수정 가능"한 자원에서
 * 소유권 검증을 한 곳({@link com.lightdrone.support.OwnershipUtils})으로
 * 모으기 위한 마커 겸 접근 인터페이스다. (IDOR 재발 방지)
 */
public interface Ownable {

    /** 소유 회원. 비회원 작성 등으로 null일 수 있다. */
    Member getMember();
}
