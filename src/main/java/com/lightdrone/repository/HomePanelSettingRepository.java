package com.lightdrone.repository;

import com.lightdrone.domain.HomePanelSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HomePanelSettingRepository extends JpaRepository<HomePanelSetting, Long> {
    Optional<HomePanelSetting> findBySlot(String slot);
    boolean existsBySlot(String slot);
}
