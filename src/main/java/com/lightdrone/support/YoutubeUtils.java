package com.lightdrone.support;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeUtils {

    private static final Pattern[] YOUTUBE_PATTERNS = new Pattern[] {
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtube\\.com/embed/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})")
    };

    private YoutubeUtils() {
    }

    public static String extractVideoId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        for (Pattern pattern : YOUTUBE_PATTERNS) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    public static String thumbnailUrl(String url) {
        String videoId = extractVideoId(url);
        return videoId == null ? null : "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
    }

    public static String embedUrl(String url) {
        String videoId = extractVideoId(url);
        return videoId == null ? null : "https://www.youtube.com/embed/" + videoId;
    }
}
