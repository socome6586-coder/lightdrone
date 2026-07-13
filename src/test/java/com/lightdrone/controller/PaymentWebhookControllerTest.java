package com.lightdrone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookControllerTest {

    private final PaymentWebhookController controller =
            new PaymentWebhookController(null, null, new ObjectMapper());

    @Test
    void extractsOrderIdFromPaymentStatusChangedPayload() {
        String payload = """
                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"orderId":"ORD-ABC123","status":"DONE"}}
                """;

        assertThat(controller.extractOrderId(payload)).isEqualTo("ORD-ABC123");
    }

    @Test
    void malformedOrUnrelatedPayloadHasNoOrderId() {
        assertThat(controller.extractOrderId("not-json")).isNull();
        assertThat(controller.extractOrderId("{\"eventType\":\"OTHER\"}")).isNull();
        assertThat(controller.extractOrderId(" ")).isNull();
    }
}
