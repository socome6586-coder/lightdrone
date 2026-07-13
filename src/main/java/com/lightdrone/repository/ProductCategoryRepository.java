package com.lightdrone.repository;

import com.lightdrone.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByOrderByDisplayOrderAscNameAsc();
    Optional<ProductCategory> findByName(String name);
    boolean existsByName(String name);
}
