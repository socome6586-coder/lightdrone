package com.lightdrone.support;

import com.lightdrone.domain.Ownable;

/**
 * 자원 소유권 검증 공통 유틸.
 * <p>
 * 컨트롤러/서비스 곳곳에 흩어져 있던
 * {@code !entity.getMember().getId().equals(memberId)} 식의 IDOR 방어 로직을
 * 한 곳으로 모아 일관성과 재사용성을 확보한다.
 * <p>
 * <b>주의:</b> {@code member}가 LAZY 연관(Inquiry·Qna 등)인 경우,
 * 영속성 컨텍스트가 살아있는 {@code @Transactional} 서비스 메서드 안에서 호출해야
 * 안전하다. (OSIV off 환경)
 */
public final class OwnershipUtils {

    private OwnershipUtils() {
    }

    /** entity의 소유자가 memberId 회원인지 여부. (null 이면 항상 false) */
    public static boolean isOwner(Ownable entity, Long memberId) {
        return entity != null
                && memberId != null
                && entity.getMember() != null
                && memberId.equals(entity.getMember().getId());
    }

    /** 관리자이거나 소유자이면 접근 허용. (열람 권한 판단용) */
    public static boolean canAccess(Ownable entity, Long memberId, boolean isAdmin) {
        return isAdmin || isOwner(entity, memberId);
    }
}
