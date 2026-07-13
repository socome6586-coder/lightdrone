package com.lightdrone.controller;

import com.lightdrone.domain.Product;
import com.lightdrone.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 상세 페이지가 구조화 데이터(JSON-LD)를 실제 HTML 로 안전하게 렌더링하는지 검증한다.
 *
 * <p>요구사항 6의 핵심: 상품명에 {@code </script>} 가 들어가도 스크립트가 깨지거나
 * XSS 가 발생하지 않아야 한다. 격리된 H2(test 프로파일)에 상품을 심고 익명으로 조회한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductDetailSeoRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void productDetail_rendersJsonLd_andIsInjectionSafe() throws Exception {
        String evilName = "테스트 드론</script><script>alert('xss')</script>";
        Product product = productRepository.saveAndFlush(Product.builder()
                .name(evilName)
                .description("<p>설명</p>")
                .price(100_000L)
                .discountPercent(10)
                .stock(5)
                .available(true)
                .productCode("LD-SEO-1")
                .build());

        String html = mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // JSON-LD 스크립트 태그가 렌더링됨
        assertThat(html).contains("application/ld+json");
        assertThat(html).contains("\"@type\":\"Product\"");
        assertThat(html).contains("\"@type\":\"BreadcrumbList\"");
        assertThat(html).contains("\"priceCurrency\":\"KRW\"");
        assertThat(html).contains("\"price\":90000");

        // 주입된 닫는 스크립트 태그가 그대로 노출되지 않아야 한다(이스케이프됨)
        assertThat(html).doesNotContain("<script>alert('xss')</script>");
        assertThat(html).contains("\\u003c/script\\u003e");
    }
}
