package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "visitor_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"visit_date", "ip_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VisitorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    /** IP를 SHA-256 해시로 저장 (개인정보 보호) */
    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;
}
