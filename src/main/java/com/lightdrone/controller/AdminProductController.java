package com.lightdrone.controller;

import com.lightdrone.dto.ProductDto;
import com.lightdrone.service.ProductCategoryService;
import com.lightdrone.service.ProductImportService;
import java.util.ArrayList;
import java.util.List;
import com.lightdrone.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ProductCategoryService productCategoryService;
    private final ProductImportService productImportService;

    // ─── 외부몰 상품 일괄 import (관리자 전용, 멱등) ───────────────
    // 공급사 상품 데이터(classpath:import/*.json 번들)를 일괄 등록한다.
    // 이미 같은 제품명이 있으면 건너뛰므로 여러 번 호출해도 안전하다.
    @GetMapping(value = "/products/import/{bundle}", produces = "text/plain; charset=UTF-8")
    @ResponseBody
    public String importBundle(@PathVariable String bundle,
                               @RequestParam(defaultValue = "false") boolean reset) {
        // 경로 조작 방지: 영문/숫자/하이픈만 허용
        if (!bundle.matches("[a-zA-Z0-9_-]+")) {
            return "허용되지 않는 번들 이름입니다.";
        }
        // reset=true 면 번들과 일치하는 기존 상품을 삭제 후 새로 등록 (잘못된 배치 교체용)
        return productImportService.importFromBundle("import/" + bundle + ".json", reset);
    }

    // ─── 제품 관리 ───────────────────────────────────────────────
    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String subcategory,
                           @RequestParam(required = false) String keyword,
                           Model model) {
        var productPage = productService.findForAdmin(category, subcategory, keyword, PageRequest.of(page, 10));
        model.addAttribute("productPage", productPage);
        model.addAttribute("categories", productCategoryService.getAllCategories());
        model.addAttribute("subcategories", (category != null && !category.isBlank())
                ? productCategoryService.getSubcategoryNames(category)
                : List.of());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedSubcategory", subcategory);
        model.addAttribute("keyword", keyword);
        return "admin/products";
    }

    @GetMapping("/products/{id}")
    public String viewProduct(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.findById(id));
        return "admin/product-view";
    }

    @GetMapping("/products/new")
    public String writeProductForm(Model model) {
        model.addAttribute("productDto", new ProductDto());
        model.addAttribute("categories", productCategoryService.getAllCategories());
        return "admin/product-write";
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute ProductDto productDto,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", productCategoryService.getAllCategories());
            return "admin/product-write";
        }
        try {
            productService.create(productDto);
            redirectAttributes.addFlashAttribute("successMsg", "제품이 등록되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/products";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String subcategory,
                                  @RequestParam(required = false) String keyword,
                                  Model model) {
        var product = productService.findById(id);
        var dto = new ProductDto();
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setPriceOnRequest(product.isPriceOnRequest());
        dto.setFreeShipping(product.isFreeShipping());
        dto.setBest(product.isBest());
        dto.setNewProduct(product.isNewProduct());
        dto.setShowImageLabel(product.isShowImageLabel());
        dto.setImageLabel(product.getImageLabel());
        dto.setDiscountPercent(product.getDiscountPercent());
        List<String> cats = new ArrayList<>(product.getCategories());
        if (cats.isEmpty() && product.getCategory() != null) {
            cats.add(product.getCategory());
        }
        dto.setCategories(cats);
        dto.setSubcategory(product.getSubcategory());
        dto.setStock(product.getStock());
        dto.setAvailable(product.isAvailable());
        dto.setSortOrder(product.getSortOrder());
        dto.setVideoUrl(product.getVideoUrl());
        dto.setManufacturer(product.getManufacturer());
        dto.setProductCode(product.getProductCode());
        dto.setDisplayPrice(product.getDisplayPrice());
        model.addAttribute("product", product);
        model.addAttribute("productDto", dto);
        model.addAttribute("categories", productCategoryService.getAllCategories());
        model.addAttribute("returnPage", page);
        model.addAttribute("returnCategory", category);
        model.addAttribute("returnSubcategory", subcategory);
        model.addAttribute("returnKeyword", keyword);
        return "admin/product-edit";
    }

    /**
     * 상품 복사: 즉시 DB에 저장하지 않고, 원본 정보로 채운 신규 등록 폼을 보여준다.
     * 관리자가 '등록'을 눌러야 비로소 새 상품이 저장된다.
     * (예전에는 클릭 즉시 복제본이 저장돼, 저장하지 않고 나가도 복제본이 남는 문제가 있었다.)
     */
    @GetMapping("/products/{id}/copy")
    public String copyProduct(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("productDto", productService.buildCopyDto(id));
            model.addAttribute("categories", productCategoryService.getAllCategories());
            model.addAttribute("copyMode", true);
            return "admin/product-write";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "복사 실패: " + e.getMessage());
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") int returnPage,
                              @RequestParam(required = false) String returnCategory,
                              @RequestParam(required = false) String returnSubcategory,
                              @RequestParam(required = false) String returnKeyword,
                              @Valid @ModelAttribute ProductDto productDto,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("product", productService.findById(id));
            model.addAttribute("categories", productCategoryService.getAllCategories());
            model.addAttribute("returnPage", returnPage);
            model.addAttribute("returnCategory", returnCategory);
            model.addAttribute("returnSubcategory", returnSubcategory);
            model.addAttribute("returnKeyword", returnKeyword);
            return "admin/product-edit";
        }
        try {
            productService.update(id, productDto);
            redirectAttributes.addFlashAttribute("successMsg", "제품이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return buildListRedirect(returnPage, returnCategory, returnSubcategory, returnKeyword);
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id,
                                @RequestParam(defaultValue = "0") int returnPage,
                                @RequestParam(required = false) String returnCategory,
                                @RequestParam(required = false) String returnSubcategory,
                                @RequestParam(required = false) String returnKeyword,
                                RedirectAttributes redirectAttributes) {
        productService.delete(id);
        redirectAttributes.addFlashAttribute("successMsg", "제품이 삭제되었습니다.");
        return buildListRedirect(returnPage, returnCategory, returnSubcategory, returnKeyword);
    }

    /**
     * 목록으로 돌아갈 URL 조합.
     * <p>한글 카테고리/검색어를 그대로 넣으면 리다이렉트 Location 헤더가 비ASCII 문자를
     * 담지 못해 헤더가 누락되고 → 빈 화면이 뜬다. 반드시 UTF-8 URL 인코딩한다.</p>
     */
    private String buildListRedirect(int page, String category, String subcategory, String keyword) {
        category = firstParamValue(category);
        subcategory = firstParamValue(subcategory);
        keyword = firstParamValue(keyword);
        StringBuilder url = new StringBuilder("redirect:/admin/products?page=").append(page);
        if (category != null && !category.isBlank()) url.append("&category=").append(encode(category));
        if (subcategory != null && !subcategory.isBlank()) url.append("&subcategory=").append(encode(subcategory));
        if (keyword  != null && !keyword.isBlank())  url.append("&keyword=").append(encode(keyword));
        return url.toString();
    }

    /**
     * 잘못 중첩된 form 이나 브라우저 자동 보정 때문에 같은 파라미터가 중복 전송되면
     * Spring 이 String 값을 "A,A" 또는 ","처럼 합칠 수 있다. 목록 복귀용 필터는
     * 단일 값만 의미가 있으므로 첫 값만 사용하고 빈 값은 null 로 정규화한다.
     */
    private String firstParamValue(String value) {
        if (value == null) return null;
        String normalized = value;
        int comma = normalized.indexOf(',');
        if (comma >= 0) {
            normalized = normalized.substring(0, comma);
        }
        normalized = normalized.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
