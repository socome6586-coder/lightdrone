package com.lightdrone.controller;

import com.lightdrone.service.FileStorageService;
import com.lightdrone.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * 메인 팝업(공지·이벤트 배너) 관리 콘솔.
 * 이미지·링크·노출 기간을 코드 수정 없이 등록/수정/삭제할 수 있습니다.
 */
@Controller
@RequestMapping("/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

    private final PopupService popupService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("popups", popupService.getAllForAdmin());
        return "admin/popups";
    }

    @PostMapping("/save")
    public String create(@RequestParam String title,
                         @RequestParam(required = false) MultipartFile imageFile,
                         @RequestParam(required = false, defaultValue = "") String linkUrl,
                         @RequestParam(required = false, defaultValue = "false") boolean newTab,
                         @RequestParam(required = false, defaultValue = "false") boolean active,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                         @RequestParam(required = false, defaultValue = "420") int width,
                         @RequestParam(required = false, defaultValue = "0") int sortOrder,
                         RedirectAttributes ra) {
        try {
            String imageUrl = storeIfPresent(imageFile);
            if (imageUrl == null) {
                ra.addFlashAttribute("errorMsg", "팝업 이미지를 업로드해 주세요.");
                return "redirect:/admin/popups";
            }
            popupService.create(title, imageUrl, linkUrl, newTab, active, startAt, endAt, width, sortOrder);
            ra.addFlashAttribute("successMsg", "팝업이 등록되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "등록 실패: " + e.getMessage());
        }
        return "redirect:/admin/popups";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam(required = false) MultipartFile imageFile,
                         @RequestParam(required = false, defaultValue = "") String linkUrl,
                         @RequestParam(required = false, defaultValue = "false") boolean newTab,
                         @RequestParam(required = false, defaultValue = "false") boolean active,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                         @RequestParam(required = false, defaultValue = "420") int width,
                         @RequestParam(required = false, defaultValue = "0") int sortOrder,
                         RedirectAttributes ra) {
        try {
            String imageUrl = storeIfPresent(imageFile);
            popupService.update(id, title, imageUrl, linkUrl, newTab, active, startAt, endAt, width, sortOrder);
            ra.addFlashAttribute("successMsg", "팝업이 저장되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "저장 실패: " + e.getMessage());
        }
        return "redirect:/admin/popups";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        popupService.toggleActive(id);
        ra.addFlashAttribute("successMsg", "노출 상태가 변경되었습니다.");
        return "redirect:/admin/popups";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        popupService.delete(id);
        ra.addFlashAttribute("successMsg", "팝업이 삭제되었습니다.");
        return "redirect:/admin/popups";
    }

    private String storeIfPresent(MultipartFile imageFile) throws java.io.IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            return fileStorageService.store(imageFile);
        }
        return null;
    }
}
