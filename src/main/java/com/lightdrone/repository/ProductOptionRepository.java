package com.lightdrone.repository;

import com.lightdrone.domain.Product;
import com.lightdrone.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByProductOrderBySortOrderAscIdAsc(Product product);
    void deleteAllByProduct(Product product);
}
