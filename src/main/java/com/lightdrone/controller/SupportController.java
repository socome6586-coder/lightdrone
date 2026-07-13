package com.lightdrone.controller;

import com.lightdrone.service.ManualService;
import com.lightdrone.service.SupportVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class SupportController {

    private final ManualService manualService;
    private final SupportVideoService supportVideoService;

    /** 제품지원 및 메뉴얼 목록 */
    @GetMapping("/support")
    public String support(@RequestParam(defaultValue = "") String category,
                          @RequestParam(defaultValue = "") String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "0") int videoPage,
                          Model model) {
        model.addAttribute("manuals",  manualService.getManuals(category, keyword, PageRequest.of(page, 10)));
        model.addAttribute("videos", supportVideoService.getVisibleVideos(category, keyword, PageRequest.of(videoPage, 6)));
        model.addAttribute("category", category);
        model.addAttribute("keyword",  keyword);
        return "support";
    }

    /** 메뉴얼 상세 */
    @GetMapping("/support/{id}")
    public String supportDetail(@PathVariable Long id, Model model) {
        model.addAttribute("manual", manualService.getManual(id));
        return "support-detail";
    }

    /** A/S 안내 */
    @GetMapping("/as")
    public String asCenter() {
        return "as-center";
    }

    /** 개인정보처리방침 */
    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    /** 환불/반품 정책 */
    @GetMapping("/refund")
    public String refund() {
        return "refund";
    }
}
