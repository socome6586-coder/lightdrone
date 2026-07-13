package com.lightdrone.controller;

import com.lightdrone.domain.Product;
import com.lightdrone.domain.ProductCategory;
import com.lightdrone.service.DroneServiceService;
import com.lightdrone.service.HomePanelSettingService;
import com.lightdrone.service.HomeVideoUrlResolver;
import com.lightdrone.service.NoticeService;
import com.lightdrone.service.PopupService;
import com.lightdrone.service.ProductCategoryService;
import com.lightdrone.service.ProductService;
import com.lightdrone.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DroneServiceService droneServiceService;
    private final NoticeService noticeService;
    private final ProductService productService;
    private final ProductCategoryService productCategoryService;
    private final HomePanelSettingService homePanelSettingService;
    private final HomeVideoUrlResolver homeVideoUrlResolver;
    private final ReviewService reviewService;
    private final PopupService popupService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("services", droneServiceService.getVisibleServices());
        model.addAttribute("notices", noticeService.getRecentNotices());
        var products = productService.getAvailableProducts();
        var allCategories = productCategoryService.getAllCategories();
        var categories = allCategories.stream()
                .filter(com.lightdrone.domain.ProductCategory::isHomeVisible)
                .toList();
        Map<String, List<com.lightdrone.domain.Product>> homeProductsByCategory = new LinkedHashMap<>();
        for (var category : categories) {
            int productLimit = hasVisibleHomeBanner(category) ? 6 : 8;
            List<com.lightdrone.domain.Product> categoryProducts = products.stream()
                    .filter(product -> belongsToCategory(product, category.getName()))
                    .limit(productLimit)
                    .toList();
            if (!categoryProducts.isEmpty()) {
                homeProductsByCategory.put(category.getName(), categoryProducts);
            }
        }
        List<com.lightdrone.domain.Product> homeProducts = homeProductsByCategory.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        model.addAttribute("homeProductsByCategory", homeProductsByCategory);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("homeCategories", categories.stream()
                .filter(category -> homeProductsByCategory.containsKey(category.getName()))
                .toList());
        // 상품별 구매후기 수 (홈 카드 표시용)
        List<Long> productIds = homeProducts.stream()
                .map(com.lightdrone.domain.Product::getId).toList();
        model.addAttribute("reviewCounts", reviewService.getReviewCounts(productIds));

        // 홈 패널 설정 — 노출(visible) + 이미지 있는 슬롯만 슬라이드로 전달
        model.addAttribute("heroPanels", homePanelSettingService.getVisiblePanels());

        // 메인 동영상 — URL이 설정되고 노출 ON 일 때만 자동재생 섹션 노출
        var videoPanel = homePanelSettingService.getVideoPanel();
        String videoRaw = videoPanel.getLinkUrl();
        if (videoPanel.isVisible() && videoRaw != null && !videoRaw.isBlank()) {
            String embedUrl = homeVideoUrlResolver.toEmbedUrl(videoRaw);
            if (embedUrl != null) {
                model.addAttribute("homeVideoEmbedUrl", embedUrl);
            } else if (homeVideoUrlResolver.isDirectVideoFile(videoRaw)) {
                model.addAttribute("homeVideoFileUrl", videoRaw.trim());
            } else {
                // 알 수 없는 형식은 그대로 iframe 으로 시도
                model.addAttribute("homeVideoEmbedUrl", videoRaw.trim());
            }
            model.addAttribute("homeVideoTitle", videoPanel.getLabel());
        }

        // 메인 팝업 (노출 기간 내 active 팝업)
        model.addAttribute("popups", popupService.getActivePopups());
        return "index";
    }

    @GetMapping("/drone-law")
    public String droneLaw() {
        return "drone-law";
    }

    private boolean belongsToCategory(Product product, String categoryName) {
        if (product == null || categoryName == null) {
            return false;
        }
        if (product.getCategories() != null && product.getCategories().contains(categoryName)) {
            return true;
        }
        return categoryName.equals(product.getCategory());
    }

    private boolean hasVisibleHomeBanner(ProductCategory category) {
        return category != null
                && category.isHomeBannerVisible()
                && category.getHomeBannerImageUrl() != null
                && !category.getHomeBannerImageUrl().isBlank();
    }

}
