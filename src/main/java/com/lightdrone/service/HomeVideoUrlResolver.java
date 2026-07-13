package com.lightdrone.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 관리자가 입력한 동영상 URL을 메인 화면에서 자동재생 가능한 형태로 변환합니다.
 * - YouTube / Vimeo: 자동재생(mute)·반복 임베드 URL (iframe)
 * - 직접 업로드/직링크(mp4 등): 원본 URL (HTML5 video)
 */
@Component
public class HomeVideoUrlResolver {

    private static final Pattern YOUTUBE = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");
    private static final Pattern VIMEO = Pattern.compile(
            "vimeo\\.com/(?:video/)?([0-9]+)");

    /** iframe 임베드용 URL (YouTube/Vimeo). 해당 없으면 null. */
    public String toEmbedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String url = rawUrl.trim();

        Matcher yt = YOUTUBE.matcher(url);
        if (yt.find()) {
            String id = yt.group(1);
            return "https://www.youtube.com/embed/" + id
                    + "?autoplay=1&mute=1&loop=1&controls=0&modestbranding=1"
                    + "&rel=0&playsinline=1&playlist=" + id;
        }

        Matcher vm = VIMEO.matcher(url);
        if (vm.find()) {
            String id = vm.group(1);
            return "https://player.vimeo.com/video/" + id
                    + "?autoplay=1&muted=1&loop=1&background=1&playsinline=1";
        }

        return null;
    }

    /** HTML5 video 태그로 직접 재생할 직링크인지 여부. */
    public boolean isDirectVideoFile(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        String lower = rawUrl.trim().toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0) {
            lower = lower.substring(0, q);
        }
        return lower.endsWith(".mp4") || lower.endsWith(".webm")
                || lower.endsWith(".ogg") || lower.endsWith(".mov") || lower.endsWith(".m4v");
    }
}
