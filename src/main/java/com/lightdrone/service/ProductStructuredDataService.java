package com.lightdrone.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightdrone.domain.Product;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 상세 페이지용 schema.org 구조화 데이터(JSON-LD) 생성기.
 *
 * <p>핵심 안전 원칙(요구사항 6): JSON 은 <b>문자열 연결로 만들지 않고</b> Jackson 으로
 * 직렬화한 뒤, {@code <script>} 컨텍스트에 안전하도록 {@code <}, {@code >}, {@code &} 및
 * 라인 구분자(U+2028/U+2029)를 유니코드 이스케이프({@code \\u003c} 등)로 치환한다.
 * 따라서 상품명에 {@code </script>} 같은 문자열이 들어와도 스크립트가 깨지거나
 * XSS 가 발생하지 않는다. 템플릿에서는 {@code th:utext} 로 출력하되, 이미 HTML-안전하게
 * 이스케이프된 문자열이므로 안전하다.
 *
 * <p>요구사항 4: 존재하지 않는 평점/리뷰 점수는 만들지 않는다 → {@code aggregateRating},
 * {@code review} 필드는 절대 포함하지 않는다.
 */
@Service
public class ProductStructuredDataService {

    public static final String IN_STOCK = "https://schema.org/InStock";
    public static final String OUT_OF_STOCK = "https://schema.org/OutOfStock";

    /** 라인 구분자(JSON 에선 합법이나 JS 문자열에선 줄바꿈으로 해석되어 깨질 수 있음) */
    private static final int LINE_SEPARATOR = 0x2028;
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    /** description 길이 상한 (구조화 데이터 과대화 방지) */
    private static final int MAX_DESCRIPTION = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Product JSON-LD ─────────────────────────────────────────────

    /**
     * Product schema.org JSON-LD (HTML-안전 문자열). 직렬화 실패 시 null.
     *
     * <p>구글 'Product' 리치 결과는 {@code offers}/{@code review}/{@code aggregateRating}
     * 중 하나가 반드시 있어야 유효하다. "가격문의요망" 상품은 실제 가격이 없어 offers 를
     * 만들 수 없고(가짜 가격을 만들지 않음 — 요구사항 4), 평점도 없으므로 유효한 Product 를
     * 구성할 수 없다. 이 경우 깨진(Invalid) 구조화 데이터를 내보내 검색콘솔 오류를 만드는 대신
     * Product JSON-LD 자체를 생략(null 반환)한다. BreadcrumbList·업체 정보는 그대로 유지된다.
     */
    public String productJsonLd(Product product, String siteUrl) {
        if (!hasValidOffer(product)) {
            return null;
        }
        return toHtmlSafeJson(buildProductNode(product, siteUrl));
    }

    /** 구글 Product 리치 결과 유효성을 만족하는 offer(실제 가격)를 만들 수 있는지 여부. */
    private boolean hasValidOffer(Product product) {
        return !product.isPriceOnRequest()
                && product.getPrice() != null
                && product.getPrice() > 0;
    }

    /** Product JSON-LD 의 직렬화 전 노드(Map). 테스트·검증용으로 공개. */
    public Map<String, Object> buildProductNode(Product product, String siteUrl) {
        String base = normalizeBase(siteUrl);
        String productUrl = base + "/products/" + product.getId();

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@context", "https://schema.org");
        node.put("@type", "Product");
        node.put("name", product.getName());

        String image = absoluteUrl(product.getImageUrl(), base);
        if (image != null) {
            node.put("image", image);
        }

        String description = plainDescription(product);
        if (!description.isBlank()) {
            node.put("description", description);
        }

        if (hasText(product.getProductCode())) {
            node.put("sku", product.getProductCode().trim());
        }

        if (hasText(product.getManufacturer())) {
            Map<String, Object> brand = new LinkedHashMap<>();
            brand.put("@type", "Brand");
            brand.put("name", product.getManufacturer().trim());
            node.put("brand", brand);
        }

        node.put("url", productUrl);

        Map<String, Object> offer = buildOffer(product, productUrl);
        if (offer != null) {
            node.put("offers", offer);
        }
        return node;
    }

    /**
     * Offer 노드. 요구사항 3: KRW 가격 + 할인 적용 후 실제 가격 + 판매가능/재고 상태.
     * "가격문의요망" 상품은 실제 가격이 없으므로 임의 가격을 만들지 않고 offers 를 생략한다(요구사항 4 취지).
     */
    private Map<String, Object> buildOffer(Product product, String productUrl) {
        if (!hasValidOffer(product)) {
            return null;
        }
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("priceCurrency", "KRW");
        // 할인 적용 후 실제 판매가 (getEffectivePrice 가 할인율을 반영)
        offer.put("price", product.getEffectivePrice());
        offer.put("availability",
                (product.isAvailable() && product.getStock() > 0) ? IN_STOCK : OUT_OF_STOCK);
        offer.put("url", productUrl);
        return offer;
    }

    // ── BreadcrumbList JSON-LD ──────────────────────────────────────

    /** BreadcrumbList schema.org JSON-LD (HTML-안전 문자열). 직렬화 실패 시 null. */
    public String breadcrumbJsonLd(Product product, String siteUrl) {
        return toHtmlSafeJson(buildBreadcrumbNode(product, siteUrl));
    }

    /** BreadcrumbList JSON-LD 의 직렬화 전 노드(Map). 테스트·검증용으로 공개. */
    public Map<String, Object> buildBreadcrumbNode(Product product, String siteUrl) {
        String base = normalizeBase(siteUrl);

        List<Map<String, Object>> items = new ArrayList<>();
        int position = 1;
        items.add(crumb(position++, "홈", base + "/"));
        items.add(crumb(position++, "상품", base + "/products"));
        if (hasText(product.getCategory())) {
            String category = product.getCategory().trim();
            items.add(crumb(position++, category,
                    base + "/products?category=" + urlEncode(category)));
        }
        items.add(crumb(position, product.getName(), base + "/products/" + product.getId()));

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@context", "https://schema.org");
        node.put("@type", "BreadcrumbList");
        node.put("itemListElement", items);
        return node;
    }

    private Map<String, Object> crumb(int position, String name, String item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "ListItem");
        m.put("position", position);
        m.put("name", name);
        m.put("item", item);
        return m;
    }

    // ── 직렬화 + HTML 안전 처리 ──────────────────────────────────────

    /** 노드를 Jackson 으로 직렬화한 뒤 {@code <script>} 안전 이스케이프 적용. */
    public String toHtmlSafeJson(Object node) {
        try {
            return htmlSafe(objectMapper.writeValueAsString(node));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * JSON 문자열을 HTML {@code <script>} 컨텍스트에 안전하게 만든다.
     * JSON 구조 문자({@code {}[]":,})에는 {@code <>&} 가 없으므로, 이 치환은 오직
     * 문자열 값 안의 위험 문자만 유니코드 이스케이프로 바꾼다(JSON 의미는 동일하게 유지).
     */
    public static String htmlSafe(String json) {
        if (json == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(json.length() + 16);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '<') {
                sb.append("\\u003c");
            } else if (c == '>') {
                sb.append("\\u003e");
            } else if (c == '&') {
                sb.append("\\u0026");
            } else if (c == LINE_SEPARATOR) {
                sb.append("\\u2028");
            } else if (c == PARAGRAPH_SEPARATOR) {
                sb.append("\\u2029");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── helpers ─────────────────────────────────────────────────────

    /** HTML 태그 제거 + 공백 정리 + 길이 제한. */
    private String plainDescription(Product product) {
        String raw = product.getDescription();
        if (raw == null) {
            return "";
        }
        String text = raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION).trim() : text;
    }

    private static String normalizeBase(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return "";
        }
        return siteUrl.replaceAll("/+$", "");
    }

    private static String absoluteUrl(String url, String base) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return base + (url.startsWith("/") ? url : "/" + url);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
