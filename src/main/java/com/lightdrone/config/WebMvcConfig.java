package com.lightdrone.config;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 MVC 설정 (WebConfig.java와 통합 — 중복 리소스 핸들러 제거)
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final VisitorLogInterceptor visitorLogInterceptor;
    private final ActivityLogInterceptor activityLogInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/** 요청을 실제 업로드 폴더로 매핑
        // 업로드 파일은 저장 시 고유 파일명이 부여되어 같은 URL의 내용이 바뀌지 않으므로 장기 캐시 안전
        String location = "file:" + uploadDir.replace("\\", "/");
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        // 정적 리소스(css/js/images): 파일명 버전 해시가 없으므로 장기 캐시 시 배포 후 stale 위험.
        // no-cache(=캐시하되 매 요청 재검증)로 두면 Last-Modified 조건부 요청으로 변경 없을 땐 304,
        // 변경 시 즉시 새 파일을 받는다. 자산이 작아(로고 18KB·배너 <10KB) 재검증 비용도 미미.
        CacheControl staticCache = CacheControl.noCache().cachePublic();
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/").setCacheControl(staticCache);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/").setCacheControl(staticCache);
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/").setCacheControl(staticCache);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visitorLogInterceptor);
        registry.addInterceptor(activityLogInterceptor).addPathPatterns("/admin/**");
    }
}
