package com.lightdrone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightdrone.domain.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 상품 구조화 데이터(JSON-LD) 생성기 단위 테스트 (요구사항 10).
 *
 * <p>검증 항목: 유효한 JSON 직렬화, KRW·할인 적용 후 가격, 재고/판매가능 상태,
 * canonical(상품 URL) 정확성, 평점/리뷰 미생성(요구사항 4), 그리고 {@code </script>}
 * 주입에도 깨지지 않는 안전성(요구사항 6).
 */
class ProductStructuredDataServiceTest {

    private final ProductStructuredDataService service = new ProductStructuredDataService();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String SITE = "https://lightdrone.co.kr/"; // 끝 슬래시 정규화 검증 포함

    private Product baseProduct() {
        return Product.builder()
                .id(42L)
                .name("테스트 드론 K20")
                .description("<p>고성능 <b>농업용</b> 드론</p>")
                .price(100_000L)
                .discountPercent(10)          // 할인 적용 후 90,000원
                .stock(5)
                .available(true)
                .priceOnRequest(false)
                .productCode("LD-K20")
                .manufacturer("라이트드론")
                .category("농업용")
                .imageUrl("/uploads/k20.png")
                .build();
    }

    @Test
    void productJsonLd_isValidJson_withCorrectPriceStockAndCanonicalUrl() throws Exception {
        String json = service.productJsonLd(baseProduct(), SITE);

        // 유효한 JSON 인지 (파싱 예외 없음)
        assertThatCode(() -> mapper.readTree(json)).doesNotThrowAnyException();
        JsonNode node = mapper.readTree(json);

        assertThat(node.get("@context").asText()).isEqualTo("https://schema.org");
        assertThat(node.get("@type").asText()).isEqualTo("Product");
        assertThat(node.get("name").asText()).isEqualTo("테스트 드론 K20");
        assertThat(node.get("sku").asText()).isEqualTo("LD-K20");
        // 이미지는 절대 URL 로 변환 (siteUrl 끝 슬래시 정규화 확인)
        assertThat(node.get("image").asText()).isEqualTo("https://lightdrone.co.kr/uploads/k20.png");
        // canonical 상품 URL
        assertThat(node.get("url").asText()).isEqualTo("https://lightdrone.co.kr/products/42");

        JsonNode offer = node.get("offers");
        assertThat(offer.get("priceCurrency").asText()).isEqualTo("KRW");
        // 할인 적용 후 실제 가격
        assertThat(offer.get("price").asLong()).isEqualTo(90_000L);
        assertThat(offer.get("availability").asText()).isEqualTo(ProductStructuredDataService.IN_STOCK);
        assertThat(offer.get("url").asText()).isEqualTo("https://lightdrone.co.kr/products/42");
    }

    @Test
    void offer_outOfStock_whenStockZero() throws Exception {
        Product p = baseProduct();
        p.setStock(0);
        JsonNode offer = mapper.readTree(service.productJsonLd(p, SITE)).get("offers");
        assertThat(offer.get("availability").asText()).isEqualTo(ProductStructuredDataService.OUT_OF_STOCK);
    }

    @Test
    void priceOnRequest_omitsOffersInsteadOfFabricatingPrice() throws Exception {
        Product p = baseProduct();
        p.setPriceOnRequest(true);
        assertThat(service.productJsonLd(p, SITE)).isNull();
    }

    @Test
    void doesNotFabricateRatingsOrReviews() throws Exception {
        JsonNode node = mapper.readTree(service.productJsonLd(baseProduct(), SITE));
        assertThat(node.has("aggregateRating")).isFalse();
        assertThat(node.has("review")).isFalse();
        assertThat(node.has("ratingValue")).isFalse();
    }

    @Test
    void scriptInjectionInName_isEscaped_andStillValidJson() throws Exception {
        Product p = baseProduct();
        String evil = "드론</script><script>alert('xss')</script>";
        p.setName(evil);

        String json = service.productJsonLd(p, SITE);

        // 원시 출력에 닫는 스크립트 태그나 '<' 가 절대 나타나지 않아야 한다
        assertThat(json).doesNotContain("</script>");
        assertThat(json).doesNotContain("<");
        assertThat(json).doesNotContain(">");
        // 그럼에도 유효한 JSON 이며, 디코딩하면 원래 문자열과 동일
        JsonNode node = mapper.readTree(json);
        assertThat(node.get("name").asText()).isEqualTo(evil);
    }

    @Test
    void breadcrumb_isValidJson_withOrderedItemsAndCorrectUrls() throws Exception {
        String json = service.breadcrumbJsonLd(baseProduct(), SITE);
        assertThatCode(() -> mapper.readTree(json)).doesNotThrowAnyException();

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("@type").asText()).isEqualTo("BreadcrumbList");
        JsonNode items = node.get("itemListElement");
        // 홈 / 상품 / 카테고리(농업용) / 상품명 = 4개
        assertThat(items).hasSize(4);
        assertThat(items.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(items.get(0).get("item").asText()).isEqualTo("https://lightdrone.co.kr/");
        assertThat(items.get(1).get("item").asText()).isEqualTo("https://lightdrone.co.kr/products");
        // 마지막 항목 = 현재 상품, position 4, canonical URL
        JsonNode last = items.get(3);
        assertThat(last.get("position").asInt()).isEqualTo(4);
        assertThat(last.get("name").asText()).isEqualTo("테스트 드론 K20");
        assertThat(last.get("item").asText()).isEqualTo("https://lightdrone.co.kr/products/42");
    }

    @Test
    void breadcrumb_withoutCategory_hasThreeItems() throws Exception {
        Product p = baseProduct();
        p.setCategory(null);
        p.setCategories(new java.util.ArrayList<>());
        JsonNode items = mapper.readTree(service.breadcrumbJsonLd(p, SITE)).get("itemListElement");
        assertThat(items).hasSize(3);
    }
}
