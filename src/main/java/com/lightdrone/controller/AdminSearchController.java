package com.lightdrone.controller;

import com.lightdrone.service.InquiryService;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.OrderService;
import com.lightdrone.service.ProductService;
import com.lightdrone.service.QnaService;
import com.lightdrone.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSearchController {

    private static final int LIMIT = 8;

    private final ProductService productService;
    private final OrderService orderService;
    private final MemberService memberService;
    private final InquiryService inquiryService;
    private final QnaService qnaService;
    private final ReviewService reviewService;

    /**
     * 관리자 통합검색.
     * <p>상단 검색창 한 곳에서 상품·주문·회원을 한 번에 조회한다.
     * 각 영역은 최대 {@value #LIMIT}건만 미리보기로 보여주고,
     * 상품은 전체 목록 검색으로 이어지는 '더 보기' 링크를 제공한다.</p>
     */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        String keyword = q == null ? "" : q.trim();
        model.addAttribute("keyword", keyword);

        if (!keyword.isBlank()) {
            var products = productService.findForAdmin(null, keyword, PageRequest.of(0, LIMIT));
            model.addAttribute("products", products.getContent());
            model.addAttribute("productTotal", products.getTotalElements());

            var orders = orderService.searchForAdmin(keyword, LIMIT);
            model.addAttribute("orders", orders);

            var members = memberService.searchForAdmin(keyword, LIMIT);
            model.addAttribute("members", members);

            var inquiries = inquiryService.searchForAdmin(keyword, LIMIT);
            model.addAttribute("inquiries", inquiries);

            var qnas = qnaService.searchForAdmin(keyword, LIMIT);
            model.addAttribute("qnas", qnas);

            var reviews = reviewService.searchForAdmin(keyword, LIMIT);
            model.addAttribute("reviews", reviews);

            model.addAttribute("resultTotal",
                    products.getTotalElements() + orders.size() + members.size()
                            + inquiries.size() + qnas.size() + reviews.size());
            model.addAttribute("previewLimit", LIMIT);
        }
        return "admin/search";
    }
}
