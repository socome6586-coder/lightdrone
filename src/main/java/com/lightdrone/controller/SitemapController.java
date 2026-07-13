package com.lightdrone.controller;

import com.lightdrone.domain.Product;
import com.lightdrone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class SitemapController {

    private final ProductRepository productRepository;

    @Value("${site.url:https://lightdrone.co.kr}")
    private String siteUrl;

    /** sitemap.xml - 검색엔진 크롤러에게 페이지 목록 제공 */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String base = siteUrl.replaceAll("/$", "");
        String today = LocalDate.now().toString();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // ── 고정 페이지 ──────────────────────────────────────
        addUrl(sb, base + "/",            today, "daily",   "1.0");
        addUrl(sb, base + "/products",    today, "daily",   "0.9");
        addUrl(sb, base + "/notice",      today, "weekly",  "0.7");
        addUrl(sb, base + "/qna",         today, "weekly",  "0.6");
        addUrl(sb, base + "/company",     today, "monthly", "0.6");
        addUrl(sb, base + "/inquiry",     today, "monthly", "0.5");
        addUrl(sb, base + "/support",     today, "monthly", "0.5");
        addUrl(sb, base + "/as",          today, "monthly", "0.5");
        addUrl(sb, base + "/drone-law",   today, "monthly", "0.5");
        addUrl(sb, base + "/privacy",     today, "yearly",  "0.3");
        addUrl(sb, base + "/refund",      today, "yearly",  "0.3");

        // ── 상품 상세 페이지 (동적) ──────────────────────────
        List<Product> products = productRepository.findByAvailableTrueOrderBySortOrderAscIdDesc();
        for (Product p : products) {
            addUrl(sb, base + "/products/" + p.getId(), today, "weekly", "0.8");
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    private void addUrl(StringBuilder sb, String loc, String lastmod, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }
}
