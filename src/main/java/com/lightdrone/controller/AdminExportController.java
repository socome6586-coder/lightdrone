package com.lightdrone.controller;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.OrderItem;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.OrderService;
import com.lightdrone.support.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 CSV 내보내기 — 주문/회원/매출 데이터를 Excel 로 바로 열 수 있는 CSV(UTF-8 + BOM)로 내려준다.
 * 모든 경로는 /admin/** 이므로 SecurityConfig 에서 ADMIN 권한으로 보호된다.
 */
@Controller
@RequestMapping("/admin/export")
@RequiredArgsConstructor
public class AdminExportController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final OrderService orderService;
    private final MemberService memberService;

    /** 주문 내역 CSV */
    @GetMapping("/orders.csv")
    public void exportOrders(HttpServletResponse response) throws IOException {
        Writer w = openCsv(response, "orders");
        w.write(CsvUtils.row(
                "주문번호", "주문일시", "주문자", "연락처", "이메일",
                "결제수단", "주문상태", "상품", "상품금액", "배송비", "총결제금액",
                "수령인", "우편번호", "주소", "택배사", "송장번호"));
        for (Order o : orderService.findAllForExport()) {
            String items = o.getItems().stream()
                    .map(this::itemLabel)
                    .collect(Collectors.joining(" / "));
            w.write(CsvUtils.row(
                    o.getOrderNumber(),
                    o.getCreatedAt() != null ? o.getCreatedAt().format(DT) : "",
                    o.getBuyerName(),
                    o.getBuyerPhone(),
                    o.getBuyerEmail(),
                    o.getPaymentMethod() != null ? o.getPaymentMethod().getLabel() : "",
                    o.getStatus() != null ? o.getStatus().getLabel() : "",
                    items,
                    o.getTotalPrice() != null && o.getShippingFee() != null
                            ? (o.getTotalPrice() - o.getShippingFee()) : o.getTotalPrice(),
                    o.getShippingFee(),
                    o.getTotalPrice(),
                    o.getReceiverName(),
                    o.getZipCode(),
                    fullAddress(o),
                    o.getCourierCompany(),
                    o.getTrackingNumber()));
        }
        w.flush();
    }

    /** 회원 목록 CSV */
    @GetMapping("/members.csv")
    public void exportMembers(HttpServletResponse response) throws IOException {
        Writer w = openCsv(response, "members");
        w.write(CsvUtils.row(
                "아이디", "이름", "이메일", "연락처", "회원등급",
                "권한", "가입경로", "상태", "가입일시"));
        for (Member m : memberService.findAll()) {
            w.write(CsvUtils.row(
                    m.getUsername(),
                    m.getName(),
                    m.getEmail(),
                    m.getPhone(),
                    m.getBusinessGrade() != null ? m.getBusinessGrade().getLabel() : "",
                    m.getRole() != null ? m.getRole().name() : "",
                    m.getProvider() != null ? m.getProvider() : "일반",
                    m.isEnabled() ? "활성" : "비활성",
                    m.getCreatedAt() != null ? m.getCreatedAt().format(DT) : ""));
        }
        w.flush();
    }

    /** 기간 매출 CSV — 취소 제외 주문을 일자별 묶음으로 내려준다. start/end 미지정 시 최근 30일 */
    @GetMapping("/sales.csv")
    public void exportSales(@RequestParam(required = false) String start,
                            @RequestParam(required = false) String end,
                            HttpServletResponse response) throws IOException {
        LocalDate startDate = (start != null && !start.isBlank()) ? LocalDate.parse(start) : LocalDate.now().minusDays(29);
        LocalDate endDate = (end != null && !end.isBlank()) ? LocalDate.parse(end) : LocalDate.now();
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        List<Order> orders = orderService.findForSales(from, to);
        Writer w = openCsv(response, "sales_" + startDate + "_" + endDate);
        w.write(CsvUtils.row("주문번호", "주문일시", "주문자", "결제수단", "주문상태", "상품", "총결제금액"));
        long total = 0L;
        for (Order o : orders) {
            String items = o.getItems().stream().map(this::itemLabel).collect(Collectors.joining(" / "));
            w.write(CsvUtils.row(
                    o.getOrderNumber(),
                    o.getCreatedAt() != null ? o.getCreatedAt().format(DT) : "",
                    o.getBuyerName(),
                    o.getPaymentMethod() != null ? o.getPaymentMethod().getLabel() : "",
                    o.getStatus() != null ? o.getStatus().getLabel() : "",
                    items,
                    o.getTotalPrice()));
            if (o.getTotalPrice() != null) total += o.getTotalPrice();
        }
        w.write(CsvUtils.row("합계", "", "", "", "", orders.size() + "건", total));
        w.flush();
    }

    // ── 헬퍼 ─────────────────────────────────────────────
    private Writer openCsv(HttpServletResponse response, String baseName) throws IOException {
        String filename = baseName + "_" + LocalDateTime.now().format(FILE_TS) + ".csv";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        Writer w = response.getWriter();
        w.write(CsvUtils.BOM);
        return w;
    }

    private String itemLabel(OrderItem i) {
        String name = i.getProductName();
        if (i.getSelectedOptionName() != null && !i.getSelectedOptionName().isBlank()) {
            name += "(" + i.getSelectedOptionName() + ")";
        }
        return name + " x" + i.getQuantity();
    }

    private String fullAddress(Order o) {
        StringBuilder sb = new StringBuilder();
        if (o.getAddress() != null) sb.append(o.getAddress());
        if (o.getAddressDetail() != null && !o.getAddressDetail().isBlank()) {
            sb.append(' ').append(o.getAddressDetail());
        }
        return sb.toString();
    }
}
