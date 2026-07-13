package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 메인 홈페이지에 노출되는 팝업(공지·이벤트 배너)을 저장합니다.
 *
 * 관리자 콘솔에서 이미지·링크·노출 기간을 자유롭게 등록/수정/삭제할 수 있으며,
 * 코드 수정 없이 디자인(이미지)을 교체할 수 있습니다.
 */
@Entity
@Table(name = "popups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Popup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "popup_seq")
    @SequenceGenerator(name = "popup_seq", sequenceName = "popup_seq", allocationSize = 1)
    private Long id;

    /** 관리용 제목 (화면에는 노출되지 않음) */
    @Column(nullable = false, length = 100)
    private String title;

    /** 팝업 이미지 경로 (/uploads/... 또는 외부 URL) */
    @Column(length = 500)
    private String imageUrl;

    /** 팝업 클릭 시 이동할 URL (비우면 링크 없음) */
    @Column(length = 500)
    private String linkUrl;

    /** 새 탭에서 링크 열기 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean newTab = true;

    /** 노출 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** 노출 시작 일시 (null = 즉시) */
    private java.time.LocalDateTime startAt;

    /** 노출 종료 일시 (null = 무기한) */
    private java.time.LocalDateTime endAt;

    /** 팝업 가로 폭(px) */
    @Column(nullable = false)
    @Builder.Default
    private int width = 420;

    /** 정렬 순서 (작을수록 먼저) */
    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
