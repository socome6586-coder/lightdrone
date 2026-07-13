package com.lightdrone.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LinkUrlNormalizer {

    public String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        String url = rawUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("//")) {
            return null;
        }

        if (lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || url.startsWith("/")
                || url.startsWith("#")) {
            return url;
        }

        return "/" + url;
    }
}
