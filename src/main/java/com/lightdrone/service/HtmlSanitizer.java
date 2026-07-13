package com.lightdrone.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 사용자가 리치 에디터(Summernote)로 입력한 HTML을 안전하게 정제한다.
 * 서식 태그(글자 크기/색상/굵기 등)는 허용하되, script·onclick 등
 * 악성 코드 삽입(저장형 XSS)은 제거한다.
 */
@Component
public class HtmlSanitizer {

    /** 인라인 style 속성에서 허용할 CSS 속성 화이트리스트 */
    private static final Set<String> ALLOWED_STYLE_PROPS = Set.of(
            "font-size", "color", "background-color", "font-weight",
            "font-style", "text-decoration", "text-align", "font-family"
    );

    /** "prop: value" 안전성 검증 — 위험한 값(url(), expression() 등) 차단 */
    private static final Pattern DANGEROUS_VALUE =
            Pattern.compile("(?i)(url\\s*\\(|expression\\s*\\(|javascript:|<|>)");

    private final Safelist safelist;

    public HtmlSanitizer() {
        this.safelist = Safelist.basic()
                .addTags("span", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                        "u", "s", "strike", "font", "hr")
                .addAttributes(":all", "style")
                .addAttributes("font", "color", "size", "face")
                .addAttributes("a", "target")
                .addProtocols("a", "href", "http", "https", "mailto");
    }

    /**
     * HTML을 정제한다. null/빈 값은 그대로 반환.
     */
    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        String cleaned = Jsoup.clean(html, "", safelist,
                new Document.OutputSettings().prettyPrint(false));
        // style 속성에 대한 2차 필터링 (CSS 속성 화이트리스트)
        Document doc = Jsoup.parseBodyFragment(cleaned);
        for (Element el : doc.getAllElements()) {
            String style = el.attr("style");
            if (!style.isEmpty()) {
                String safe = filterStyle(style);
                if (safe.isEmpty()) {
                    el.removeAttr("style");
                } else {
                    el.attr("style", safe);
                }
            }
        }
        return doc.body().html();
    }

    /** 인라인 style 문자열에서 허용된 속성만 남긴다. */
    private String filterStyle(String style) {
        StringBuilder sb = new StringBuilder();
        for (String decl : style.split(";")) {
            int idx = decl.indexOf(':');
            if (idx <= 0) continue;
            String prop = decl.substring(0, idx).trim().toLowerCase();
            String value = decl.substring(idx + 1).trim();
            if (prop.isEmpty() || value.isEmpty()) continue;
            if (!ALLOWED_STYLE_PROPS.contains(prop)) continue;
            if (DANGEROUS_VALUE.matcher(value).find()) continue;
            sb.append(prop).append(": ").append(value).append("; ");
        }
        return sb.toString().trim();
    }
}
