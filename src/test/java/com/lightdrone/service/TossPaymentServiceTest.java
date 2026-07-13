package com.lightdrone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentServiceTest {

    private MockRestServiceServer server;
    private TossPaymentService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new TossPaymentService(
                restTemplate, new ObjectMapper(), "https://api.test", "test_secret");
    }

    @Test
    void confirmSendsIdempotencyKeyAndValidatesResponse() {
        String auth = "Basic " + Base64.getEncoder().encodeToString(
                "test_secret:".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://api.test/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", auth))
                .andExpect(header("Idempotency-Key", "confirm-ORD-123456"))
                .andExpect(content().json("""
                        {"paymentKey":"pay_key","orderId":"ORD-123456","amount":10000}
                        """))
                .andRespond(withSuccess("""
                        {"paymentKey":"pay_key","orderId":"ORD-123456","status":"DONE",
                         "totalAmount":10000,"balanceAmount":10000,"method":"카드"}
                        """, MediaType.APPLICATION_JSON));

        TossPaymentService.TossPaymentResult result =
                service.confirm("pay_key", "ORD-123456", 10_000L);

        assertThat(result.isDone()).isTrue();
        assertThat(result.totalAmount()).isEqualTo(10_000L);
        server.verify();
    }

    @Test
    void confirmRejectsMissingOrDifferentAmountInResponse() {
        server.expect(requestTo("https://api.test/v1/payments/confirm"))
                .andRespond(withSuccess("""
                        {"paymentKey":"pay_key","orderId":"ORD-123456","status":"DONE"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.confirm("pay_key", "ORD-123456", 10_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("금액");
        server.verify();
    }

    @Test
    void fullCancellationRequiresCanceledStatusAndZeroBalance() {
        server.expect(requestTo("https://api.test/v1/payments/pay_key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "cancel-pay_key"))
                .andRespond(withSuccess("""
                        {"paymentKey":"pay_key","orderId":"ORD-123456","status":"PARTIAL_CANCELED",
                         "totalAmount":10000,"balanceAmount":3000}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.cancelFull("pay_key", "고객 요청"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전체취소");
        server.verify();
    }

    @Test
    void fullCancellationReturnsCanceledAmount() {
        server.expect(requestTo("https://api.test/v1/payments/pay_key/cancel"))
                .andRespond(withSuccess("""
                        {"paymentKey":"pay_key","orderId":"ORD-123456","status":"CANCELED",
                         "totalAmount":10000,"balanceAmount":0}
                        """, MediaType.APPLICATION_JSON));

        TossPaymentService.TossPaymentResult result = service.cancelFull("pay_key", "고객 요청");

        assertThat(result.isFullyCanceled()).isTrue();
        assertThat(result.canceledAmount()).isEqualTo(10_000L);
        server.verify();
    }
}
