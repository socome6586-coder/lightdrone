package com.lightdrone.controller;

import com.lightdrone.domain.Inquiry;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.Qna;
import com.lightdrone.domain.Review;
import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.repository.InquiryRepository;
import com.lightdrone.repository.OrderRepository;
import com.lightdrone.repository.QnaRepository;
import com.lightdrone.repository.ReviewRepository;
import com.lightdrone.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final MemberService memberService;
    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;
    private final QnaRepository qnaRepository;
    private final OrderRepository orderRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

    record NotificationItem(String type, String message, String link, String createdAt,
                            LocalDateTime sortKey) {}

    @GetMapping("/my")
    public ResponseEntity<List<NotificationItem>> myNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }

        Member member = memberService.findByUsername(userDetails.getUsername());
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        List<NotificationItem> items = new ArrayList<>();

        if (member.getRole() == Role.ADMIN) {
            List<Order> orders = orderRepository.findAdminNotificationCandidates(since);
            for (Order order : orders) {
                if (!isAdminNotifiableOrder(order)) {
                    continue;
                }

                LocalDateTime alertTime = adminOrderSortKey(order);
                String paymentLabel = order.getPaymentMethod() == PaymentMethod.CARD
                        ? "카드결제 완료"
                        : "무통장 주문";

                items.add(new NotificationItem(
                        "order",
                        "[" + paymentLabel + "] 주문번호 " + order.getOrderNumber() + " / " + order.getBuyerName(),
                        "/admin/orders/" + order.getId(),
                        alertTime != LocalDateTime.MIN ? alertTime.format(DATE_FMT) : "",
                        alertTime
                ));
            }
        }

        List<Inquiry> inquiries = inquiryRepository
                .findByMemberAndAnsweredTrueAndCreatedAtAfterOrderByCreatedAtDesc(member, since);
        for (Inquiry inquiry : inquiries) {
            String title = shorten(inquiry.getSubject(), 20);
            LocalDateTime answerTime = inquiry.getUpdatedAt() != null ? inquiry.getUpdatedAt()
                    : inquiry.getCreatedAt() != null ? inquiry.getCreatedAt() : LocalDateTime.MIN;
            items.add(new NotificationItem(
                    "inquiry",
                    "'" + title + "' 문의에 답변이 등록되었습니다.",
                    "/inquiry/" + inquiry.getId() + "/view",
                    answerTime != LocalDateTime.MIN ? answerTime.format(DATE_FMT) : "",
                    answerTime
            ));
        }

        List<Review> reviews = reviewRepository
                .findByMemberWithAdminReplyAfter(member, since);
        for (Review review : reviews) {
            String title = shorten(review.getTitle(), 20);
            LocalDateTime replyTime = review.getAdminReplyAt() != null ? review.getAdminReplyAt()
                    : review.getUpdatedAt() != null ? review.getUpdatedAt()
                    : review.getCreatedAt() != null ? review.getCreatedAt() : LocalDateTime.MIN;
            items.add(new NotificationItem(
                    "review",
                    "'" + title + "' 후기에 관리자 답글이 등록되었습니다.",
                    "/review/" + review.getId(),
                    replyTime != LocalDateTime.MIN ? replyTime.format(DATE_FMT) : "",
                    replyTime
            ));
        }

        List<Qna> qnaList = qnaRepository
                .findByMemberAndAnsweredTrueAndCreatedAtAfterOrderByCreatedAtDesc(member, since);
        for (Qna qna : qnaList) {
            String title = shorten(qna.getTitle(), 20);
            LocalDateTime answerTime = qna.getUpdatedAt() != null ? qna.getUpdatedAt()
                    : qna.getCreatedAt() != null ? qna.getCreatedAt() : LocalDateTime.MIN;
            items.add(new NotificationItem(
                    "qna",
                    "'" + title + "' 질문에 답변이 등록되었습니다.",
                    "/qna/" + qna.getId(),
                    answerTime != LocalDateTime.MIN ? answerTime.format(DATE_FMT) : "",
                    answerTime
            ));
        }

        items.sort(Comparator.comparing(NotificationItem::sortKey).reversed());
        if (items.size() > 20) {
            items = items.subList(0, 20);
        }

        return ResponseEntity.ok(items);
    }

    private String shorten(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    private boolean isAdminNotifiableOrder(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            return order.getStatus() == OrderStatus.PENDING_PAYMENT;
        }
        return order.getPaymentMethod() == PaymentMethod.CARD
                && (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING
                || order.getStatus() == OrderStatus.SHIPPING
                || order.getStatus() == OrderStatus.DELIVERED);
    }

    private LocalDateTime adminOrderSortKey(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.CARD) {
            return order.getUpdatedAt() != null ? order.getUpdatedAt()
                    : order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.MIN;
        }
        return order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.MIN;
    }
}
