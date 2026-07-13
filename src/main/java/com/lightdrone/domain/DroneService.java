package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drone_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DroneService extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_seq")
    @SequenceGenerator(name = "service_seq", sequenceName = "service_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String imagePath;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(length = 50)
    private String icon;
}
