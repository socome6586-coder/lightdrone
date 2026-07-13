package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 메인 홈페이지 3분할 패널 설정을 저장합니다.
 *
 * slot 값:
 *   LEFT      — 왼쪽 패널
 *   CENTER_1  — 중앙 슬라이드 1번
 *   CENTER_2  — 중앙 슬라이드 2번
 *   CENTER_3  — 중앙 슬라이드 3번
 *   RIGHT     — 오른쪽 패널
 */
@Entity
@Table(name = "home_panel_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomePanelSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "home_panel_seq")
    @SequenceGenerator(name = "home_panel_seq", sequenceName = "home_panel_seq", allocationSize = 1)
    private Long id;

    /** 패널 슬롯 식별자 (LEFT | CENTER_1 | CENTER_2 | CENTER_3 | RIGHT) */
    @Column(nullable = false, unique = true, length = 20)
    private String slot;

    /** 표시할 이미지 경로 (/uploads/... 또는 /images/...) */
    @Column(length = 500)
    private String imageUrl;

    /** 패널 클릭 시 이동할 URL */
    @Column(length = 500)
    private String linkUrl;

    /** 중앙 슬라이드 제품명 캡션 */
    @Column(length = 100)
    private String label;

    /** 패널 하단 제품 설명 문구 */
    @Column(length = 300)
    private String description;

    /** 메인 슬라이드 노출 여부 (true = 노출, false = 숨김) */
    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;
}
