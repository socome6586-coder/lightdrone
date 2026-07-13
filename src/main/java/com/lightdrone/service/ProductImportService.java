package com.lightdrone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightdrone.dto.ProductDto;
import com.lightdrone.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 공급사 상품 데이터(JSON 번들)를 일괄 등록하는 일회성 import 서비스.
 *
 * 동작:
 *   1) classpath 의 JSON 번들(import/xxx.json)을 읽는다.
 *   2) 제품명이 이미 존재하면 건너뛴다(멱등 — 여러 번 실행해도 중복 생성 안 됨).
 *   3) 원본 이미지 URL을 다운로드해 FileStorageService 로 재호스팅한다.
 *      (운영은 https 라서 외부 http 이미지를 핫링크하면 mixed-content 로 차단되므로 반드시 재호스팅)
 *   4) ProductService.create() 로 상품을 저장한다.
 *
 * 관리자 전용 엔드포인트에서만 호출된다.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /** JSON 번들 1건의 구조 */
    public static class ImportItem {
        public String name;
        public Long price;
        public String category;
        public String subcategory;
        public String imageUrl;
        public List<String> mainImages;
        public List<String> contentImages;
        public String sourceNumber;
    }

    /** 일반 import (기존 상품 보존, 중복 건너뜀) */
    public String importFromBundle(String classpathJson) {
        return importFromBundle(classpathJson, false);
    }

    /**
     * classpath JSON 번들을 읽어 상품을 일괄 등록한다.
     *
     * @param reset true 면, 번들에 포함된 제품명·세부카테고리와 일치하는 기존 상품을
     *              먼저 삭제한 뒤 새로 등록한다(잘못 등록된 배치를 깨끗이 교체할 때 사용).
     *              false 면 이미 존재하는 제품명은 건너뛴다(멱등).
     * @return 사람이 읽을 수 있는 결과 요약
     */
    public String importFromBundle(String classpathJson, boolean reset) {
        ImportItem[] items;
        try (InputStream is = new ClassPathResource(classpathJson).getInputStream()) {
            items = objectMapper.readValue(is, ImportItem[].class);
        } catch (Exception e) {
            return "번들 로드 실패: " + classpathJson + " — " + e.getMessage();
        }

        int created = 0, skipped = 0, failed = 0, deleted = 0;
        List<String> createdNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        Set<String> existingKeys = buildExistingKeySet(items);

        // reset 모드: 번들의 제품명+세부카테고리와 일치하는 기존 상품을 먼저 삭제
        if (reset) {
            for (ImportItem item : items) {
                if (item.name == null || item.name.isBlank()) continue;
                String itemKey = duplicateKey(item.name.trim(), item.subcategory);
                List<com.lightdrone.domain.Product> candidates =
                        item.subcategory == null || item.subcategory.isBlank()
                                ? productRepository.findAll()
                                : productRepository.findBySubcategory(item.subcategory.trim());
                for (var p : candidates) {
                    if (!duplicateKey(p.getName(), p.getSubcategory()).equals(itemKey)) {
                        continue;
                    }
                    try { productService.delete(p.getId()); deleted++; }
                    catch (Exception e) { log.warn("기존 상품 삭제 실패: {} — {}", p.getName(), e.toString()); }
                }
            }
            existingKeys = buildExistingKeySet(items);
        }

        for (ImportItem item : items) {
            if (item.name == null || item.name.isBlank()) { failed++; continue; }
            String name = item.name.trim();

            // 중복 제외: 같은 부품이라도 카테고리(세부카테고리)가 다르면 각각 등록되어야 하므로,
            // "제품명 + 세부카테고리"가 모두 같을 때만 중복으로 보고 건너뛴다.
            // (세부카테고리가 없는 번들은 기존처럼 제품명만으로 중복 판정)
            String sub = (item.subcategory == null) ? null : item.subcategory.trim();
            boolean dup = existingKeys.contains(duplicateKey(name, sub));
            if (dup) {
                skipped++; skippedNames.add(name);
                continue;
            }

            try {
                ProductDto dto = new ProductDto();
                dto.setName(name);
                dto.setPrice(item.price != null ? item.price : 0L);
                List<String> cats = new ArrayList<>();
                if (item.category != null && !item.category.isBlank()) cats.add(item.category);
                dto.setCategories(cats);
                dto.setSubcategory(item.subcategory);
                dto.setStock(999); // 원본몰은 재고 정보를 노출하지 않으므로 판매 가능하도록 충분한 재고로 등록
                dto.setAvailable(true);
                dto.setSortOrder(0);
                dto.setAutoGenerateProductCode(true);
                dto.setContentBgColor("#ffffff"); // 상세 이미지 배경 흰색 (null이면 템플릿이 검정으로 폴백)

                // 이미지 다운로드 후 재호스팅 → existing* 필드로 넘겨 새 파일 업로드 없이 저장
                dto.setExistingImageUrl(downloadAndStore(item.imageUrl));
                dto.setExistingMainImages(downloadAll(item.mainImages));
                dto.setExistingContentImages(downloadAll(item.contentImages));

                productService.create(dto);
                existingKeys.add(duplicateKey(name, sub));
                created++; createdNames.add(name);
            } catch (Exception e) {
                failed++; failedNames.add(name + " (" + e.getMessage() + ")");
                log.warn("상품 import 실패: {} — {}", name, e.toString());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 상품 일괄 등록 결과 ===\n");
        sb.append("번들: ").append(classpathJson)
          .append(reset ? "  (reset 모드)" : "").append('\n');
        sb.append("전체: ").append(items.length)
          .append(" | 등록: ").append(created)
          .append(" | 중복건너뜀: ").append(skipped)
          .append(" | 기존삭제: ").append(deleted)
          .append(" | 실패: ").append(failed).append("\n\n");
        if (!createdNames.isEmpty()) {
            sb.append("[등록됨]\n");
            createdNames.forEach(n -> sb.append("  + ").append(n).append('\n'));
        }
        if (!skippedNames.isEmpty()) {
            sb.append("\n[중복 건너뜀]\n");
            skippedNames.forEach(n -> sb.append("  = ").append(n).append('\n'));
        }
        if (!failedNames.isEmpty()) {
            sb.append("\n[실패]\n");
            failedNames.forEach(n -> sb.append("  ! ").append(n).append('\n'));
        }
        return sb.toString();
    }

    private Set<String> buildExistingKeySet(ImportItem[] items) {
        Set<String> subcategories = new HashSet<>();
        for (ImportItem item : items) {
            if (item == null || item.subcategory == null || item.subcategory.isBlank()) {
                continue;
            }
            subcategories.add(item.subcategory.trim());
        }

        Set<String> keys = new HashSet<>();
        if (subcategories.isEmpty()) {
            for (var p : productRepository.findAll()) {
                keys.add(duplicateKey(p.getName(), p.getSubcategory()));
            }
            return keys;
        }

        for (String subcategory : subcategories) {
            for (var p : productRepository.findBySubcategory(subcategory)) {
                keys.add(duplicateKey(p.getName(), p.getSubcategory()));
            }
        }
        return keys;
    }

    private String duplicateKey(String name, String subcategory) {
        return normalizeSubcategory(subcategory) + "::" + normalizeName(name);
    }

    private String normalizeSubcategory(String subcategory) {
        return subcategory == null ? "" : subcategory.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        String normalized = name.toLowerCase(Locale.ROOT);
        normalized = normalized.replace("\uB9C8\uC2A4\uD130\uCD1D\uD310", "\uB9C8\uC2A4\uD130");
        normalized = normalized.replace("\uB4DC\uB860\uC6A9", "");
        normalized = normalized.replace("\uB9C8\uC2A4\uD130", "");
        normalized = normalized.replace("\uBC1C\uC804\uAE30", "");
        normalized = normalized.replaceAll("[\\[\\]\\(\\)\\{\\},._/\\-+]", " ");
        normalized = normalized.replaceAll("\\s+", "");
        return normalized;
    }

    /** URL 목록을 모두 다운로드·재호스팅하여 저장된 URL 목록 반환 (실패분은 제외) */
    private List<String> downloadAll(List<String> urls) {
        List<String> stored = new ArrayList<>();
        if (urls == null) return stored;
        for (String u : urls) {
            String s = downloadAndStore(u);
            if (s != null) stored.add(s);
        }
        return stored;
    }

    /** 단일 이미지 URL 다운로드 후 저장, 저장된 내부 URL 반환 (실패 시 null) */
    private String downloadAndStore(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) return null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("이미지 다운로드 응답 {}: {}", code, urlStr);
                return null;
            }
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                data = in.readAllBytes();
            }
            if (data.length == 0) return null;
            return fileStorageService.store(data, extOf(urlStr));
        } catch (Exception e) {
            log.warn("이미지 다운로드 실패: {} — {}", urlStr, e.toString());
            return null;
        }
    }

    /** URL 경로에서 확장자 추출 (쿼리스트링 제거) */
    private String extOf(String urlStr) {
        String path = urlStr;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash && dot >= 0) {
            return path.substring(dot + 1).toLowerCase();
        }
        return "jpg";
    }
}
