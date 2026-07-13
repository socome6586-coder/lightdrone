package com.lightdrone.controller;

import com.lightdrone.domain.Order;
import com.lightdrone.domain.OrderItem;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 매출 통계 — 기간을 지정해 매출 합계, 일자별 추이, 인기 상품, 결제수단 분포를 보여준다.
 * 데이터 집계는 취소/거절 제외 주문(OrderRepository.findSalesBetween) 을 자바에서 묶어 계산한다.
 */
@Controller
@RequestMapping("/admin/sales")
@RequiredArgsConstructor
public class AdminSalesController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final OrderService orderService;

    @GetMapping
    public String sales(@RequestParam(required = false) String start,
                        @RequestParam(required = false) String end,
                        Model model) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = parseOr(start, today.minusDays(29));
        LocalDate endDate = parseOr(end, today);
        if (endDate.isBefore(startDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();
        List<Order> orders = orderService.findForSales(from, to);

        // ── 요약 ──
        long totalRevenue = orders.stream().mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0L).sum();
        int orderCount = orders.size();
        long avgOrder = orderCount > 0 ? totalRevenue / orderCount : 0L;
        long totalQuantity = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToLong(OrderItem::getQuantity).sum();

        // ── 일자별 매출 추이 (모든 날짜를 0으로 채워 빈 날도 표시) ──
        Map<LocalDate, Long> dailyRevenue = new LinkedHashMap<>();
        Map<LocalDate, Integer> dailyCount = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            dailyRevenue.put(d, 0L);
            dailyCount.put(d, 0);
        }
        for (Order o : orders) {
            if (o.getCreatedAt() == null) continue;
            LocalDate d = o.getCreatedAt().toLocalDate();
            dailyRevenue.merge(d, o.getTotalPrice() != null ? o.getTotalPrice() : 0L, Long::sum);
            dailyCount.merge(d, 1, Integer::sum);
        }
        model.addAttribute("chartLabels", dailyRevenue.keySet().stream()
                .map(d -> d.format(LABEL)).collect(Collectors.joining(",")));
        model.addAttribute("chartData", dailyRevenue.values().stream()
                .map(String::valueOf).collect(Collectors.joining(",")));

        // ── 인기 상품 TOP 10 (수량·매출 기준) ──
        Map<String, long[]> byProduct = new LinkedHashMap<>(); // [수량, 매출]
        for (Order o : orders) {
            for (OrderItem i : o.getItems()) {
                long[] agg = byProduct.computeIfAbsent(i.getProductName(), k -> new long[2]);
                agg[0] += i.getQuantity();
                agg[1] += i.getTotalPrice();
            }
        }
        List<BestSeller> bestSellers = byProduct.entrySet().stream()
                .map(e -> new BestSeller(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(BestSeller::revenue).reversed())
                .limit(10)
                .toList();
        model.addAttribute("bestSellers", bestSellers);

        // ── 결제수단 분포 ──
        List<PaymentBreakdown> paymentBreakdown = new ArrayList<>();
        for (PaymentMethod pm : PaymentMethod.values()) {
            long cnt = orders.stream().filter(o -> o.getPaymentMethod() == pm).count();
            long rev = orders.stream().filter(o -> o.getPaymentMethod() == pm)
                    .mapToLong(o -> o.getTotalPrice() != null ? o.getTotalPrice() : 0L).sum();
            paymentBreakdown.add(new PaymentBreakdown(pm.getLabel(), cnt, rev));
        }
        model.addAttribute("paymentBreakdown", paymentBreakdown);

        model.addAttribute("startDate", startDate.format(DATE));
        model.addAttribute("endDate", endDate.format(DATE));
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("orderCount", orderCount);
        model.addAttribute("avgOrder", avgOrder);
        model.addAttribute("totalQuantity", totalQuantity);
        return "admin/sales";
    }

    private LocalDate parseOr(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    public record BestSeller(String name, long quantity, long revenue) {}
    public record PaymentBreakdown(String label, long count, long revenue) {}
}
