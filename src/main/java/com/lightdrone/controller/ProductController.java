package com.lightdrone.controller;

import com.lightdrone.service.CartService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.PricingService;
import com.lightdrone.service.ProductCategoryService;
import com.lightdrone.service.ProductService;
import com.lightdrone.service.ProductStructuredDataService;
import com.lightdrone.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import org.springframework.util.StringUtils;

import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lightdrone.domain.ProductOption;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private static final int OG_DESCRIPTION_MAX_LENGTH = 110;

    private final ProductService productService;
    private final ProductCategoryService productCategoryService;
    private final MemberService memberService;
    private final CartService cartService;
    private final ReviewService reviewService;
    private final ProductStructuredDataService structuredDataService;
    private final PricingService pricingService;

    /** 구조화 데이터(JSON-LD)의 절대 URL 생성을 위한 사이트 기본 URL */
    @Value("${site.url:https://lightdrone.co.kr}")
    private String siteUrl;

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "") String category,
                           @RequestParam(defaultValue = "") String subcategory,
                           @RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "0") int page,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        var pageable = PageRequest.of(page, 30); // 가로 5 × 세로 6 = 30개/페이지
        org.springframework.data.domain.Page<com.lightdrone.domain.Product> productPage;
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasCategory = StringUtils.hasText(category);
        boolean hasSubcategory = StringUtils.hasText(subcategory);
        if (hasKeyword && hasCategory && hasSubcategory) {
            // 선택한 카테고리·세부분류 안에서만 검색 (필터 유지)
            productPage = productService.searchByKeywordInCategoryAndSubcategory(category, subcategory, keyword, pageable);
        } else if (hasKeyword && hasCategory) {
            // 선택한 카테고리 안에서만 검색 (필터 유지)
            productPage = productService.searchByKeywordInCategory(category, keyword, pageable);
        } else if (hasKeyword) {
            productPage = productService.searchByKeyword(keyword, pageable);
        } else if (hasCategory && hasSubcategory) {
            productPage = productService.getByCategoryAndSubcategory(category, subcategory, pageable);
        } else if (hasCategory) {
            productPage = productService.getByCategory(category, pageable);
        } else {
            productPage = productService.getAvailableProducts(pageable);
        }
        model.addAttribute("productPage", productPage);
        // 상품별 옵션 그룹 맵 (목록 페이지 빠른 담기용 — 그룹별 1개씩 선택)
        Map<Long, Map<String, List<ProductOption>>> optionGroupsByProduct = new LinkedHashMap<>();
        for (var p : productPage.getContent()) {
            optionGroupsByProduct.put(p.getId(), buildOptionGroups(p.getOptions()));
        }
        model.addAttribute("optionGroupsByProduct", optionGroupsByProduct);
        // 상품별 구매후기 수 (목록 카드 표시용)
        List<Long> productIds = productPage.getContent().stream()
                .map(com.lightdrone.domain.Product::getId).toList();
        model.addAttribute("reviewCounts", reviewService.getReviewCounts(productIds));
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("subcategory", subcategory);
        model.addAttribute("allCategories", productCategoryService.getAllCategories());
        if (StringUtils.hasText(category)) {
            model.addAttribute("subcategories", productCategoryService.getSubcategoryNames(category));
        }
        if (userDetails != null) {
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
        }
        return "products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails userDetails,
                                Model model) {
        var product = productService.findById(id);
        if (!product.isAvailable()) {
            return "redirect:/products";
        }
        model.addAttribute("product", product);
        model.addAttribute("imageRows", buildImageRows(product.getContentImages(), product.getContentImageLayout()));
        model.addAttribute("optionGroups", buildOptionGroups(product.getOptions()));
        model.addAttribute("allCategories", productCategoryService.getAllCategories());
        model.addAttribute("reviewCount", reviewService.getProductReviewCount(id));
        model.addAttribute("productReviews", reviewService.getProductReviews(id));
        int gradeDiscountPercent = 0;
        if (userDetails != null) {
            var member = memberService.findByUsername(userDetails.getUsername());
            model.addAttribute("member", member);
            model.addAttribute("cartCount", cartService.getCartCount(userDetails.getUsername()));
            gradeDiscountPercent = pricingService.discountPercentFor(member);
        }
        model.addAttribute("gradeDiscountPercent", gradeDiscountPercent);
        // 회원 등급 할인 적용 단가(옵션 미포함 기준가) — 표시·JS 계산 기준
        model.addAttribute("gradeUnitPrice",
                pricingService.applyDiscount(product.getEffectivePrice(), gradeDiscountPercent));

        // schema.org 구조화 데이터 (JSON-LD) — 안전하게 직렬화된 문자열을 모델에 주입
        model.addAttribute("productJsonLd", structuredDataService.productJsonLd(product, siteUrl));
        model.addAttribute("breadcrumbJsonLd", structuredDataService.breadcrumbJsonLd(product, siteUrl));

        // OG 태그 (카카오톡·네이버밴드 공유 시 상품 정보 미리보기)
        model.addAttribute("ogType",  "product");
        model.addAttribute("ogTitle", product.getName() + " - 라이트드론");
        model.addAttribute("ogDescription", buildOgDescription(product));
        model.addAttribute("ogImage", product.getImageUrl() != null ? product.getImageUrl() : "/images/placeholder.svg");
        model.addAttribute("ogUrl",   "/products/" + product.getId());

        return "product-detail";
    }

    private String buildOgDescription(com.lightdrone.domain.Product product) {
        String desc = product.getDescription() != null
                ? HtmlUtils.htmlUnescape(product.getDescription())
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim()
                : "";
        String price = product.isPriceOnRequest()
                ? "가격문의"
                : String.format("%,d원", product.getEffectivePrice());
        String base = desc.isBlank()
                ? product.getName() + " | " + price
                : desc.substring(0, Math.min(desc.length(), OG_DESCRIPTION_MAX_LENGTH));
        return base + " | 라이트드론 농업용 드론 전문몰";
    }

    /**
     * 옵션 목록을 "옵션 제목(그룹)" 단위로 묶는다.
     * groupName이 비어있으면 "옵션" 그룹으로 처리. 입력 순서(삽입 순서) 유지.
     */
    private Map<String, List<ProductOption>> buildOptionGroups(List<ProductOption> options) {
        Map<String, List<ProductOption>> groups = new LinkedHashMap<>();
        if (options == null) return groups;
        for (ProductOption opt : options) {
            String key = (opt.getGroupName() != null && !opt.getGroupName().isBlank())
                    ? opt.getGroupName().trim() : "옵션";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(opt);
        }
        return groups;
    }

    /**
     * 이미지 URL 목록 + 레이아웃 문자열("full,half,half,full")을
     * 행 단위 List<List<String>>으로 변환.
     * half 2개가 연속이면 한 행에 2장, 나머지는 1장씩 한 행.
     */
    private List<List<String>> buildImageRows(List<String> images, String layoutStr) {
        if (images == null || images.isEmpty()) return Collections.emptyList();
        String[] layouts = (layoutStr != null && !layoutStr.isEmpty())
                ? layoutStr.split(",") : new String[0];

        List<List<String>> rows = new ArrayList<>();
        List<String> halfPair = null;

        for (int i = 0; i < images.size(); i++) {
            String layout = (i < layouts.length) ? layouts[i].trim() : "full";
            if ("half".equals(layout)) {
                if (halfPair == null) halfPair = new ArrayList<>();
                halfPair.add(images.get(i));
                if (halfPair.size() == 2) { rows.add(halfPair); halfPair = null; }
            } else {
                if (halfPair != null) { rows.add(halfPair); halfPair = null; }
                rows.add(Collections.singletonList(images.get(i)));
            }
        }
        if (halfPair != null) rows.add(halfPair); // 홀수 half는 단독 행
        return rows;
    }
}
