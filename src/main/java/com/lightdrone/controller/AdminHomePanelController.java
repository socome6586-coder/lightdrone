package com.lightdrone.controller;

import com.lightdrone.service.FileStorageService;
import com.lightdrone.service.HomePanelSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/home-panels")
@RequiredArgsConstructor
public class AdminHomePanelController {

    private final HomePanelSettingService homePanelSettingService;
    private final FileStorageService      fileStorageService;

    @GetMapping
    public String panelList(Model model) {
        model.addAttribute("panels", homePanelSettingService.getAll());
        model.addAttribute("videoPanel", homePanelSettingService.getVideoPanel());
        return "admin/home-panels";
    }

    @PostMapping("/video")
    public String updateVideo(@RequestParam(required = false, defaultValue = "") String linkUrl,
                              @RequestParam(required = false, defaultValue = "") String label,
                              @RequestParam(required = false, defaultValue = "false") boolean visible,
                              RedirectAttributes redirectAttributes) {
        try {
            homePanelSettingService.saveOrUpdate(
                    HomePanelSettingService.VIDEO_SLOT, null, linkUrl, label, null, visible);
            redirectAttributes.addFlashAttribute("successMsg", "메인 동영상이 저장되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "저장 실패: " + e.getMessage());
        }
        return "redirect:/admin/home-panels";
    }

    @PostMapping("/{slot}")
    public String updatePanel(@PathVariable String slot,
                              @RequestParam(required = false) MultipartFile imageFile,
                              @RequestParam(required = false, defaultValue = "") String linkUrl,
                              @RequestParam(required = false, defaultValue = "") String label,
                              @RequestParam(required = false, defaultValue = "") String description,
                              @RequestParam(required = false, defaultValue = "false") boolean visible,
                              RedirectAttributes redirectAttributes) {
        try {
            String imageUrl = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                imageUrl = fileStorageService.store(imageFile);
            }
            homePanelSettingService.saveOrUpdate(slot, imageUrl, linkUrl, label, description, visible);
            redirectAttributes.addFlashAttribute("successMsg", "패널이 저장되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "저장 실패: " + e.getMessage());
        }
        return "redirect:/admin/home-panels";
    }
}
