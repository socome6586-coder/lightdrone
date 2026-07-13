package com.lightdrone.repository;

import com.lightdrone.domain.ProductCategory;
import com.lightdrone.domain.ProductSubcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSubcategoryRepository extends JpaRepository<ProductSubcategory, Long> {
    List<ProductSubcategory> findByCategoryOrderByDisplayOrderAscNameAsc(ProductCategory category);
    List<ProductSubcategory> findByCategoryNameOrderByDisplayOrderAscNameAsc(String categoryName);
}
