package com.lightdrone.service;

import com.lightdrone.domain.ProductCategory;
import com.lightdrone.domain.ProductSubcategory;
import com.lightdrone.repository.ProductCategoryRepository;
import com.lightdrone.repository.ProductSubcategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductSubcategoryRepository subcategoryRepository;
    private final LinkUrlNormalizer linkUrlNormalizer;
    private final ProductService productService;

    public List<ProductCategory> getAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc();
    }

    public boolean hasAnyCategory() {
        return categoryRepository.count() > 0;
    }

    public List<String> getCategoryNames() {
        return getAllCategories().stream()
                .map(ProductCategory::getName)
                .toList();
    }

    public List<String> getSubcategoryNames(String categoryName) {
        return subcategoryRepository.findByCategoryNameOrderByDisplayOrderAscNameAsc(categoryName)
                .stream()
                .map(ProductSubcategory::getName)
                .toList();
    }

    public ProductCategory findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
    }

    public ProductSubcategory findSubcategoryById(Long id) {
        return subcategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("세부 카테고리를 찾을 수 없습니다."));
    }

    @Transactional
    public void createCategory(String name, int displayOrder) {
        String trimmedName = normalizeRequiredName(name, "카테고리명을 입력해 주세요.");
        if (categoryRepository.existsByName(trimmedName)) {
            throw new IllegalArgumentException("이미 존재하는 카테고리명입니다: " + trimmedName);
        }
        categoryRepository.save(ProductCategory.builder()
                .name(trimmedName)
                .displayOrder(displayOrder)
                .build());
    }

    @Transactional
    public void updateCategory(Long id,
                               String name,
                               int displayOrder,
                               boolean homeVisible,
                               boolean homeBannerVisible,
                               String homeSectionDescription,
                               String homeBannerImageUrl,
                               String homeBannerTitle,
                               String homeBannerSubtitle,
                               String homeBannerLinkUrl,
                               boolean removeHomeBannerImage) {
        ProductCategory cat = findById(id);
        String oldName = cat.getName();
        String newName = normalizeRequiredName(name, "카테고리명을 입력해 주세요.");
        categoryRepository.findByName(newName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("이미 존재하는 카테고리명입니다: " + newName);
                });
        cat.setName(newName);
        cat.setDisplayOrder(displayOrder);
        cat.setHomeVisible(homeVisible);
        cat.setHomeBannerVisible(homeBannerVisible);
        cat.setHomeSectionDescription(StringUtils.hasText(homeSectionDescription) ? homeSectionDescription.trim() : null);

        if (removeHomeBannerImage) {
            cat.setHomeBannerImageUrl(null);
        } else if (StringUtils.hasText(homeBannerImageUrl)) {
            cat.setHomeBannerImageUrl(homeBannerImageUrl.trim());
        }

        cat.setHomeBannerTitle(StringUtils.hasText(homeBannerTitle) ? homeBannerTitle.trim() : null);
        cat.setHomeBannerSubtitle(StringUtils.hasText(homeBannerSubtitle) ? homeBannerSubtitle.trim() : null);
        cat.setHomeBannerLinkUrl(linkUrlNormalizer.normalize(homeBannerLinkUrl));

        if (!oldName.equals(newName)) {
            productService.renameCategoryReferences(oldName, newName);
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    @Transactional
    public void createSubcategory(Long categoryId, String name, int displayOrder) {
        ProductCategory cat = findById(categoryId);
        subcategoryRepository.save(ProductSubcategory.builder()
                .category(cat)
                .name(normalizeRequiredName(name, "세부 카테고리명을 입력해 주세요."))
                .displayOrder(displayOrder)
                .build());
    }

    @Transactional
    public void updateSubcategory(Long subId, String name, int displayOrder) {
        ProductSubcategory sub = findSubcategoryById(subId);
        String oldName = sub.getName();
        String categoryName = sub.getCategory().getName();
        String newName = normalizeRequiredName(name, "세부 카테고리명을 입력해 주세요.");
        sub.setName(newName);
        sub.setDisplayOrder(displayOrder);

        if (!oldName.equals(newName)) {
            productService.renameSubcategoryReferences(categoryName, oldName, newName);
        }
    }

    @Transactional
    public void deleteSubcategory(Long subId) {
        subcategoryRepository.deleteById(subId);
    }

    @Transactional
    public void seedCategoryIfAbsent(String categoryName, int order, List<String> subcategoryNames) {
        ProductCategory cat = categoryRepository.findByName(categoryName).orElseGet(() ->
                categoryRepository.save(ProductCategory.builder()
                        .name(categoryName)
                        .displayOrder(order)
                        .build())
        );

        if (cat.getDisplayOrder() != order) {
            cat.setDisplayOrder(order);
        }

        List<String> existing = cat.getSubcategories().stream()
                .map(ProductSubcategory::getName)
                .toList();

        int subOrder = existing.size();
        for (String subName : subcategoryNames) {
            if (!existing.contains(subName)) {
                subcategoryRepository.save(ProductSubcategory.builder()
                        .category(cat)
                        .name(subName)
                        .displayOrder(subOrder++)
                        .build());
            }
        }
    }

    private String normalizeRequiredName(String name, String message) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException(message);
        }
        return name.trim();
    }
}
