package com.lightdrone.controller;

import com.lightdrone.domain.ProductCategory;
import com.lightdrone.service.FileStorageService;
import com.lightdrone.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ProductCategoryService productCategoryService;
    private final FileStorageService fileStorageService;

    @GetMapping("/categories")
    public String categories(Model model) {
        List<ProductCategory> cats = productCategoryService.getAllCategories();
        model.addAttribute("categories", cats);
        int maxOrder = cats.stream().mapToInt(ProductCategory::getDisplayOrder).max().orElse(-1);
        model.addAttribute("maxDisplayOrder", maxOrder);
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam String name,
                                 @RequestParam(defaultValue = "0") int displayOrder,
                                 RedirectAttributes ra) {
        try {
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("카테고리명을 입력해 주세요.");
            }
            productCategoryService.createCategory(name, displayOrder);
            ra.addFlashAttribute("successMsg", "카테고리 '" + name.trim() + "'가 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/edit")
    public String editCategory(@PathVariable Long id,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "0") int displayOrder,
                               @RequestParam(defaultValue = "false") boolean homeVisible,
                               @RequestParam(defaultValue = "false") boolean homeBannerVisible,
                               @RequestParam(required = false, defaultValue = "") String homeSectionDescription,
                               @RequestParam(required = false) MultipartFile homeBannerImageFile,
                               @RequestParam(required = false, defaultValue = "") String homeBannerTitle,
                               @RequestParam(required = false, defaultValue = "") String homeBannerSubtitle,
                               @RequestParam(required = false, defaultValue = "") String homeBannerLinkUrl,
                               @RequestParam(defaultValue = "false") boolean removeHomeBannerImage,
                               RedirectAttributes ra) {
        try {
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("카테고리명을 입력해 주세요.");
            }
            String homeBannerImageUrl = null;
            if (homeBannerImageFile != null && !homeBannerImageFile.isEmpty()) {
                homeBannerImageUrl = fileStorageService.store(homeBannerImageFile);
            }
            productCategoryService.updateCategory(
                    id,
                    name,
                    displayOrder,
                    homeVisible,
                    homeBannerVisible,
                    homeSectionDescription,
                    homeBannerImageUrl,
                    homeBannerTitle,
                    homeBannerSubtitle,
                    homeBannerLinkUrl,
                    removeHomeBannerImage
            );
            ra.addFlashAttribute("successMsg", "카테고리가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "카테고리 메인 배너 저장 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        try {
            productCategoryService.deleteCategory(id);
            ra.addFlashAttribute("successMsg", "카테고리가 삭제되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "카테고리 삭제에 실패했습니다.");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/subcategories")
    public String createSubcategory(@PathVariable Long id,
                                    @RequestParam String name,
                                    @RequestParam(defaultValue = "0") int displayOrder,
                                    RedirectAttributes ra) {
        try {
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("세부 카테고리명을 입력해 주세요.");
            }
            productCategoryService.createSubcategory(id, name, displayOrder);
            ra.addFlashAttribute("successMsg", "세부 카테고리 '" + name.trim() + "'가 추가되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{catId}/subcategories/{subId}/edit")
    public String editSubcategory(@PathVariable Long catId,
                                  @PathVariable Long subId,
                                  @RequestParam String name,
                                  @RequestParam(defaultValue = "0") int displayOrder,
                                  RedirectAttributes ra) {
        try {
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("세부 카테고리명을 입력해 주세요.");
            }
            productCategoryService.updateSubcategory(subId, name, displayOrder);
            ra.addFlashAttribute("successMsg", "세부 카테고리가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{catId}/subcategories/{subId}/delete")
    public String deleteSubcategory(@PathVariable Long catId,
                                    @PathVariable Long subId,
                                    RedirectAttributes ra) {
        try {
            productCategoryService.deleteSubcategory(subId);
            ra.addFlashAttribute("successMsg", "세부 카테고리가 삭제되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "세부 카테고리 삭제에 실패했습니다.");
        }
        return "redirect:/admin/categories";
    }
}
