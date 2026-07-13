package com.lightdrone.config;

import com.lightdrone.service.HomePanelSettingService;
import com.lightdrone.service.ProductCategoryService;
import com.lightdrone.service.ProductService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private final ProductCategoryService productCategoryService;
    private final HomePanelSettingService homePanelSettingService;
    private final ProductService productService;

    public DataInitializer(ProductCategoryService productCategoryService,
                           HomePanelSettingService homePanelSettingService,
                           ProductService productService) {
        this.productCategoryService = productCategoryService;
        this.homePanelSettingService = homePanelSettingService;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 메인 패널 기본 설정
        homePanelSettingService.seedIfAbsent("LEFT", null, "/products", "전체 상품");
        homePanelSettingService.seedIfAbsent("CENTER_1", null, "/products", "Z80");
        homePanelSettingService.seedIfAbsent("CENTER_2", null, "/products", "Z30");
        homePanelSettingService.seedIfAbsent("CENTER_3", null, "/products", "Z30S");
        homePanelSettingService.seedIfAbsent("RIGHT", null, "/inquiry", "견적문의");
        homePanelSettingService.seedIfAbsent("SLIDE_6", null, "/products", "");
        homePanelSettingService.repairLegacyMissingImages();

        // 상품 카테고리 초기 데이터는 테이블이 완전히 비어 있을 때만 1회 생성
        if (!productCategoryService.hasAnyCategory()) {
            productCategoryService.seedCategoryIfAbsent("농업용", 0,
                    List.of("조립완성기체", "조립용 키트기체"));
            productCategoryService.seedCategoryIfAbsent("산업용", 1,
                    List.of());
            productCategoryService.seedCategoryIfAbsent("교육용", 2,
                    List.of());
            productCategoryService.seedCategoryIfAbsent("기종별 부품", 3,
                    List.of("K20", "G1700", "Z50", "Z30", "E410P", "E416P", "E610S", "E616P", "E616S"));
            productCategoryService.seedCategoryIfAbsent("배터리/충전기/발전기", 4,
                    List.of("배터리", "충전기", "발전기"));
            productCategoryService.seedCategoryIfAbsent("FC/송수신기", 5,
                    List.of("조종기", "컨트롤", "레이더", "카메라"));
            productCategoryService.seedCategoryIfAbsent("드론부품 및 기자재", 6,
                    List.of("모터", "프로펠러", "암접/프롭/테포기", "케이블/커넥터", "카본/알루미늄 파이프", "볼트/너트/셋팅"));
        }

        // 기존 단일 category -> categories 마이그레이션
        productService.migrateCategoryToCategories();

        // 기존 상품 코드 없던 상품 코드 백필
        productService.backfillProductCodes();
    }
}
