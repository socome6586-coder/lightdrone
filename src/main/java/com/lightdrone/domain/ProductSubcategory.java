package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_subcategories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSubcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_subcategory_seq")
    @SequenceGenerator(name = "product_subcategory_seq", sequenceName = "product_subcategory_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder.Default
    private int displayOrder = 0;
}
