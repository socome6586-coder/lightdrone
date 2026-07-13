package com.lightdrone.service;

import com.lightdrone.domain.Order;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.dto.OrderFormDto;
import com.lightdrone.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 운영/로컬 DB가 아니라 격리된 인메모리 H2(test 프로파일)에서 실행한다.
// 모든 데이터를 테스트 내부에서 직접 생성하므로 DB 의존성이 없고, @Transactional 로 매번 롤백된다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void directOrderUsesServerPriceAndRestoresStockOnlyOnce() {
        Product product = saveProduct(true, 10, 10_000L, 10, false);

        Order order = orderService.createDirect(null, product.getId(), 2, orderForm());

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProductPrice()).isEqualTo(9_000L);
        assertThat(order.getTotalPrice()).isEqualTo(21_500L);
        assertThat(product.getStock()).isEqualTo(8);

        orderService.cancel(order.getId());
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderService.cancel(order.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 취소");
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void directOrderRejectsInvalidQuantityAndUnavailableProduct() {
        Product product = saveProduct(true, 10, 10_000L, 0, false);

        assertThatThrownBy(() -> orderService.createDirect(null, product.getId(), 0, orderForm()))
                .hasMessageContaining("1개 이상");
        assertThatThrownBy(() -> orderService.createDirect(null, product.getId(), -1, orderForm()))
                .hasMessageContaining("1개 이상");
        assertThat(product.getStock()).isEqualTo(10);

        product.setAvailable(false);
        productRepository.saveAndFlush(product);
        assertThatThrownBy(() -> orderService.createDirect(null, product.getId(), 1, orderForm()))
                .hasMessageContaining("판매 중");
    }

    @Test
    void paymentConfirmationRequiresKeyAndCanRunOnlyOnce() {
        Product product = saveProduct(true, 10, 10_000L, 0, true);
        Order order = orderService.createDirect(null, product.getId(), 1, orderForm());

        assertThatThrownBy(() -> orderService.confirmPayment(order.getId(), " "))
                .hasMessageContaining("결제 키");

        orderService.confirmPayment(order.getId(), "payment-test-key", order.getTotalPrice(), "DONE");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentKey()).isEqualTo("payment-test-key");
        assertThat(order.getPaidAmount()).isEqualTo(order.getTotalPrice());

        assertThat(orderService.confirmPayment(
                order.getId(), "payment-test-key", order.getTotalPrice(), "DONE"))
                .isEqualTo(OrderService.PaymentConfirmOutcome.ALREADY_CONFIRMED);

        orderService.updateStatus(order.getId(), OrderStatus.PREPARING, null, null, null);
        assertThat(orderService.confirmPayment(
                order.getId(), "payment-test-key", order.getTotalPrice(), "DONE"))
                .isEqualTo(OrderService.PaymentConfirmOutcome.ALREADY_CONFIRMED);

        assertThatThrownBy(() -> orderService.cancel(order.getId()))
                .hasMessageContaining("환불 절차");
        assertThat(product.getStock()).isEqualTo(9);

        assertThatThrownBy(() -> orderService.confirmPayment(order.getId(), "payment-test-key-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리");

        Product secondProduct = saveProduct(true, 10, 10_000L, 0, true);
        Order secondOrder = orderService.createDirect(null, secondProduct.getId(), 1, orderForm());
        assertThatThrownBy(() -> orderService.confirmPayment(
                secondOrder.getId(), "another-key", secondOrder.getTotalPrice() + 1, "DONE"))
                .hasMessageContaining("승인 금액");

        assertThatThrownBy(() -> orderService.updateStatus(
                order.getId(), OrderStatus.CANCELLED, null, null, null))
                .hasMessageContaining("전용 처리 버튼");
    }

    private Product saveProduct(boolean available, int stock, long price, int discountPercent, boolean freeShipping) {
        Product product = Product.builder()
                .name("주문 회귀 테스트 " + UUID.randomUUID())
                .productCode("TEST-" + UUID.randomUUID())
                .price(price)
                .discountPercent(discountPercent)
                .available(available)
                .stock(stock)
                .freeShipping(freeShipping)
                .build();
        return productRepository.saveAndFlush(product);
    }

    private OrderFormDto orderForm() {
        OrderFormDto dto = new OrderFormDto();
        dto.setBuyerName("테스트 구매자");
        dto.setBuyerPhone("010-1234-5678");
        dto.setReceiverName("테스트 수령인");
        dto.setReceiverPhone("010-1234-5678");
        dto.setAddress("서울시 테스트구");
        dto.setAddressDetail("테스트 101호");
        dto.setPaymentMethod(PaymentMethod.CARD);
        return dto;
    }
}
