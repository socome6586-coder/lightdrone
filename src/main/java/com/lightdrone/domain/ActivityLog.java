package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 관리자 활동(감사) 로그 — 관리자 콘솔에서 발생한 변경 작업(등록/수정/삭제/처리)을 기록한다.
 * 누가(adminUsername), 언제(createdAt), 무엇을(menu/action), 어디서(ip) 했는지 남겨
 * 운영 추적과 사고 대응에 사용한다.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "activity_log_seq")
    @SequenceGenerator(name = "activity_log_seq", sequenceName = "activity_log_seq", allocationSize = 1)
    private Long id;

    /** 작업을 수행한 관리자 아이디 */
    @Column(length = 50)
    private String adminUsername;

    /** 작업 대상 메뉴 (예: 주문, 회원, 상품) */
    @Column(length = 40)
    private String menu;

    /** 작업 유형 (예: 등록, 수정, 삭제, 처리) */
    @Column(length = 40)
    private String action;

    /** HTTP 메서드 */
    @Column(length = 10)
    private String method;

    /** 요청 경로 */
    @Column(length = 300)
    private String requestUri;

    /** 사람이 읽을 수 있는 설명 */
    @Column(length = 300)
    private String description;

    /** 요청 IP */
    @Column(length = 64)
    private String ipAddress;
}
