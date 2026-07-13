package com.lightdrone.controller;

import com.lightdrone.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 활동(감사) 로그 조회 페이지.
 */
@Controller
@RequestMapping("/admin/activity-logs")
@RequiredArgsConstructor
public class AdminActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", activityLogService.getLogs(PageRequest.of(page, 30)));
        return "admin/activity-logs";
    }
}
