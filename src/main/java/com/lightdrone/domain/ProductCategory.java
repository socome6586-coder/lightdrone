package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_category_seq")
    @SequenceGenerator(name = "product_category_seq", sequenceName = "product_category_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Builder.Default
    private int displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean homeVisible = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean homeBannerVisible = true;

    @Column(length = 500)
    private String homeBannerImageUrl;

    @Column(length = 200)
    private String homeSectionDescription;

    @Column(length = 120)
    private String homeBannerTitle;

    @Column(length = 300)
    private String homeBannerSubtitle;

    @Column(length = 500)
    private String homeBannerLinkUrl;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC, name ASC")
    @Builder.Default
    private List<ProductSubcategory> subcategories = new ArrayList<>();
}
