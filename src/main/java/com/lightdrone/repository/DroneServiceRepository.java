package com.lightdrone.repository;

import com.lightdrone.domain.DroneService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DroneServiceRepository extends JpaRepository<DroneService, Long> {
    List<DroneService> findByVisibleTrueOrderBySortOrderAsc();
}
