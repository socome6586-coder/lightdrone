package com.lightdrone.service;

import com.lightdrone.domain.DroneService;
import com.lightdrone.repository.DroneServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DroneServiceService {

    private final DroneServiceRepository droneServiceRepository;

    public List<DroneService> getVisibleServices() {
        return droneServiceRepository.findByVisibleTrueOrderBySortOrderAsc();
    }

    public List<DroneService> getAllServices() {
        return droneServiceRepository.findAll();
    }

    public DroneService getService(Long id) {
        return droneServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("서비스를 찾을 수 없습니다."));
    }

    @Transactional
    public void save(DroneService droneService) {
        droneServiceRepository.save(droneService);
    }

    @Transactional
    public void delete(Long id) {
        droneServiceRepository.deleteById(id);
    }
}
