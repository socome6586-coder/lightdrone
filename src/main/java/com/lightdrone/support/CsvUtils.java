package com.lightdrone.support;

/**
 * 관리자 CSV 내보내기용 유틸리티.
 * <p>
 * Excel(특히 한글 Windows)에서 UTF-8 CSV 를 바로 열어도 한글이 깨지지 않도록
 * BOM(﻿) 을 파일 맨 앞에 붙이고, 셀 값은 RFC 4180 규칙으로 escape 한다.
 */
public final class CsvUtils {

    /** Excel 이 UTF-8 로 인식하게 하는 BOM */
    public static final String BOM = "﻿";

    private CsvUtils() {}

    /** CSV 한 셀 값을 escape 한다. 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다. */
    public static String escape(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        boolean needsQuote = s.contains(",") || s.contains("\"")
                || s.contains("\n") || s.contains("\r");
        if (s.contains("\"")) {
            s = s.replace("\"", "\"\"");
        }
        return needsQuote ? "\"" + s + "\"" : s;
    }

    /** 여러 셀을 쉼표로 join 하고 끝에 CRLF 를 붙여 한 줄을 만든다. */
    public static String row(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
