package com.lightdrone.domain.enums;

public enum ChatStatus {
    /** 회원이 상담을 시작했지만 아직 관리자가 들어오지 않은 상태 */
    WAITING,
    /** 한 관리자가 점유(상담 중)한 상태 — 다른 관리자는 참여 불가 */
    ACTIVE,
    /** 상담 종료 */
    CLOSED
}
