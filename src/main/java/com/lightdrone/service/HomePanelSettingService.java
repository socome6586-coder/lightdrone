package com.lightdrone.service;

import com.lightdrone.domain.HomePanelSetting;
import com.lightdrone.repository.HomePanelSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomePanelSettingService {

    private static final List<String> SLOT_ORDER =
            List.of("LEFT", "CENTER_1", "CENTER_2", "CENTER_3", "RIGHT", "SLIDE_6");
    /** 메인 동영상 슬롯 (슬라이드 순서와 무관하게 별도 관리) */
    public static final String VIDEO_SLOT = "HOME_VIDEO";
    private static final Set<String> LEGACY_MISSING_IMAGES = Set.of(
            "/images/UniRc7.png", "/images/Z80.jpg", "/images/Z30.png",
            "/images/Z30S.jpg", "/images/K20PN.png");

    private final HomePanelSettingRepository repository;
    private final LinkUrlNormalizer linkUrlNormalizer;

    /** 슬롯 순서대로 정렬된 전체 목록 반환 (없는 슬롯은 빈 객체로 채움) */
    public List<HomePanelSetting> getAll() {
        Map<String, HomePanelSetting> map = repository.findAll().stream()
                .collect(Collectors.toMap(HomePanelSetting::getSlot, s -> s));
        return SLOT_ORDER.stream()
                .map(slot -> map.computeIfAbsent(slot, k -> emptyPanel(k)))
                .collect(Collectors.toList());
    }

    /** 슬롯 → 설정 맵 반환 (HomeController용) */
    public Map<String, HomePanelSetting> getAllAsMap() {
        Map<String, HomePanelSetting> map = repository.findAll().stream()
                .collect(Collectors.toMap(HomePanelSetting::getSlot, s -> s));
        // 없는 슬롯은 빈 객체로 채움
        SLOT_ORDER.forEach(slot -> map.computeIfAbsent(slot, k -> emptyPanel(k)));
        return map;
    }

    /**
     * 메인 히어로에 실제로 노출할 패널만 슬롯 순서대로 반환합니다.
     * 조건: visible == true 이고 이미지가 등록되어 있는 슬롯.
     * (이미지 없는 슬롯은 빈 슬라이드가 되므로 자동 제외)
     */
    public List<HomePanelSetting> getVisiblePanels() {
        Map<String, HomePanelSetting> map = repository.findAll().stream()
                .collect(Collectors.toMap(HomePanelSetting::getSlot, s -> s));
        return SLOT_ORDER.stream()
                .map(map::get)
                .filter(p -> p != null && p.isVisible()
                        && p.getImageUrl() != null && !p.getImageUrl().isBlank())
                .collect(Collectors.toList());
    }

    /** 특정 슬롯의 설정을 저장/갱신합니다. imageUrl 이 null 이면 기존 이미지를 유지합니다. */
    @Transactional
    public void saveOrUpdate(String slot, String imageUrl, String linkUrl, String label,
                             String description, boolean visible) {
        HomePanelSetting setting = repository.findBySlot(slot)
                .orElseGet(() -> {
                    HomePanelSetting s = new HomePanelSetting();
                    s.setSlot(slot);
                    return s;
                });
        if (imageUrl != null && !imageUrl.isBlank()) {
            setting.setImageUrl(imageUrl);
        }
        setting.setLinkUrl(linkUrlNormalizer.normalize(linkUrl));
        setting.setLabel(label          != null && !label.isBlank()        ? label        : null);
        setting.setDescription(description != null && !description.isBlank() ? description : null);
        setting.setVisible(visible);
        repository.save(setting);
    }

    /** 메인 동영상 슬롯 설정 반환 (없으면 빈 객체) */
    public HomePanelSetting getVideoPanel() {
        return repository.findBySlot(VIDEO_SLOT).orElseGet(() -> emptyPanel(VIDEO_SLOT));
    }

    /** DataInitializer 에서 초기값 심기용 */
    @Transactional
    public void seedIfAbsent(String slot, String imageUrl, String linkUrl, String label) {
        if (!repository.existsBySlot(slot)) {
            HomePanelSetting s = new HomePanelSetting();
            s.setSlot(slot);
            s.setImageUrl(imageUrl);
            s.setLinkUrl(linkUrlNormalizer.normalize(linkUrl));
            s.setLabel(label);
            repository.save(s);
        }
    }

    /** 저장소에서 제거된 과거 기본 이미지만 비워서 화면의 placeholder가 표시되게 합니다. */
    @Transactional
    public void repairLegacyMissingImages() {
        List<HomePanelSetting> repaired = repository.findAll().stream()
                .filter(setting -> setting.getImageUrl() != null)
                .filter(setting -> LEGACY_MISSING_IMAGES.contains(setting.getImageUrl()))
                .peek(setting -> setting.setImageUrl(null))
                .toList();
        if (!repaired.isEmpty()) {
            repository.saveAll(repaired);
        }
    }

    private HomePanelSetting emptyPanel(String slot) {
        HomePanelSetting s = new HomePanelSetting();
        s.setSlot(slot);
        return s;
    }
}
