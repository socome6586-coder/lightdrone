package com.lightdrone.repository;

import com.lightdrone.domain.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    /** 관리자 목록: 정렬순서 → 최신순 */
    List<Popup> findAllByOrderBySortOrderAscIdDesc();

    /** 노출 대상 후보: active=true 만, 정렬순서 → 최신순 (기간 필터는 서비스에서) */
    List<Popup> findByActiveTrueOrderBySortOrderAscIdDesc();
}
