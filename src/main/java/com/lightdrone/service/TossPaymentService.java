package com.lightdrone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 토스페이먼츠 결제 연동(승인·취소·조회).
 *
 * <p>설계 원칙(운영 안전):
 * <ul>
 *   <li><b>응답을 신뢰하기 전에 검증</b>: 승인 응답의 status/orderId/총금액/paymentKey 를
 *       서버가 보유한 값과 모두 대조한다(요구 3).</li>
 *   <li><b>멱등성</b>: 승인·취소 요청에 Idempotency-Key 헤더를 실어 동일 요청이
 *       재전송돼도 결제사 측에서 한 번만 처리되도록 한다(요구 4).</li>
 *   <li><b>테스트 가능성</b>: RestTemplate 을 주입받아 MockRestServiceServer 로
 *       실제 토스 호출 없이 응답을 모킹할 수 있다(요구 14).</li>
 *   <li><b>비밀키</b>: secret-key 는 환경변수(TOSS_SECRET_KEY)로만 주입(요구 16).</li>
 * </ul>
 *
 * 최신 토스페이먼츠 결제위젯/표준 API(v1) 기준:
 *  - 승인:  POST {base}/v1/payments/confirm   {paymentKey, orderId, amount}
 *  - 취소:  POST {base}/v1/payments/{paymentKey}/cancel  {cancelReason[, cancelAmount]}
 *  - 조회:  GET  {base}/v1/payments/{paymentKey}
 *  인증: HTTP Basic, username=secretKey, password 공란("secretKey:").
 */
@Service
public class TossPaymentService {

    /** 토스 결제 상태 문자열 */
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_PARTIAL_CANCELED = "PARTIAL_CANCELED";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final String secretKey;

    /** 운영/일반 실행용 — RestTemplate 을 타임아웃 설정과 함께 생성한다. */
    @Autowired
    public TossPaymentService(
            @Value("${toss.payments.secret-key:}") String secretKey,
            @Value("${toss.payments.api-base-url:https://api.tosspayments.com}") String apiBaseUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.secretKey = secretKey;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** 테스트용 — RestTemplate/ObjectMapper/baseUrl/secretKey 를 직접 주입한다. */
    TossPaymentService(RestTemplate restTemplate, ObjectMapper objectMapper,
                       String apiBaseUrl, String secretKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.secretKey = secretKey;
    }

    /**
     * 결제 승인. 성공 시 검증을 통과한 결과를 반환한다.
     *
     * <p>토스가 200 을 주더라도 다음을 모두 확인한 뒤에만 정상으로 간주한다(요구 3):
     * status==DONE, 응답 orderId==요청 orderId, 응답 totalAmount==서버 주문금액,
     * 응답 paymentKey==요청 paymentKey. 하나라도 어긋나면 예외를 던져 승인 실패로 처리한다.
     *
     * @param amount 서버가 계산한 주문 금액(클라이언트 파라미터가 아니라 DB 의 주문 총액)
     */
    public TossPaymentResult confirm(String paymentKey, String orderId, Long amount) {
        requireText(paymentKey, "결제 키");
        requireText(orderId, "주문번호");
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        // Idempotency-Key: 동일 주문에 대한 승인 재전송이 중복 결제되지 않도록 함
        String json = post(apiBaseUrl + "/v1/payments/confirm", body, "confirm-" + orderId);
        TossPaymentResult result = parse(json);

        // 응답 무결성 검증 — 신뢰 전에 반드시 대조
        if (!STATUS_DONE.equals(result.status())) {
            throw new IllegalStateException("결제가 승인 완료(DONE) 상태가 아닙니다: " + result.status());
        }
        if (!orderId.equals(result.orderId())) {
            throw new IllegalStateException("승인 응답의 주문번호가 일치하지 않습니다.");
        }
        if (!paymentKey.equals(result.paymentKey())) {
            throw new IllegalStateException("승인 응답의 결제키가 일치하지 않습니다.");
        }
        if (!amount.equals(result.totalAmount())) {
            throw new IllegalStateException("승인 금액이 주문 금액과 일치하지 않습니다.");
        }
        return result;
    }

    /** 결제 전체취소. cancelAmount 를 지정하지 않으면 토스가 잔액 전체를 취소한다. */
    public TossPaymentResult cancelFull(String paymentKey, String cancelReason) {
        requireText(paymentKey, "결제 키");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", (cancelReason == null || cancelReason.isBlank())
                ? "고객/관리자 요청에 의한 전체취소" : cancelReason);
        // Idempotency-Key: 동일 결제건 취소 재전송이 이중취소되지 않도록 함
        String json = post(apiBaseUrl + "/v1/payments/" + paymentKey + "/cancel", body,
                "cancel-" + paymentKey);
        TossPaymentResult result = parse(json);
        if (!paymentKey.equals(result.paymentKey())) {
            throw new IllegalStateException("취소 응답의 결제 키가 일치하지 않습니다.");
        }
        if (!result.isFullyCanceled()) {
            throw new IllegalStateException("전체취소가 완료되지 않았습니다. 결제 상태와 잔액을 확인해주세요.");
        }
        return result;
    }

    /** 결제 단건 조회(원장 재검증용). 웹훅·관리자 동기화에서 권위 있는 상태를 가져온다. */
    public TossPaymentResult getByPaymentKey(String paymentKey) {
        HttpHeaders headers = baseHeaders();
        try {
            ResponseEntity<String> res = restTemplate.exchange(
                    apiBaseUrl + "/v1/payments/" + paymentKey,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parse(res.getBody());
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("결제 조회 실패: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new IllegalStateException("결제 조회 중 통신 오류가 발생했습니다.");
        }
    }

    /** orderId 로 결제 조회(웹훅이 paymentKey 없이 orderId 만 줄 때 사용). */
    public TossPaymentResult getByOrderId(String orderId) {
        HttpHeaders headers = baseHeaders();
        try {
            ResponseEntity<String> res = restTemplate.exchange(
                    apiBaseUrl + "/v1/payments/orders/" + orderId,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parse(res.getBody());
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("결제 조회 실패: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new IllegalStateException("결제 조회 중 통신 오류가 발생했습니다.");
        }
    }

    // ─── 내부 ──────────────────────────────────────────────────────

    private String post(String url, Map<String, Object> body, String idempotencyKey) {
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        try {
            ResponseEntity<String> res = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);
            return res.getBody();
        } catch (HttpClientErrorException e) {
            // 4xx: 토스가 거절한 명확한 사유(금액 불일치, 이미 처리됨 등)
            throw new IllegalStateException("토스 요청 실패: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // 타임아웃/5xx: 결과 불명. 호출 측이 DB 변경 없이 중단하도록 예외 전파.
            throw new IllegalStateException("토스 통신 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private HttpHeaders baseHeaders() {
        requireText(secretKey, "토스 시크릿 키");
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    private TossPaymentResult parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("토스 응답이 비어 있습니다.");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Long total = node.hasNonNull("totalAmount") ? node.get("totalAmount").asLong() : null;
            Long balance = node.hasNonNull("balanceAmount") ? node.get("balanceAmount").asLong() : null;
            return new TossPaymentResult(
                    text(node, "paymentKey"),
                    text(node, "orderId"),
                    text(node, "status"),
                    total,
                    balance,
                    text(node, "method"),
                    json
            );
        } catch (Exception e) {
            throw new IllegalStateException("토스 응답을 해석할 수 없습니다.");
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "가 설정되지 않았습니다.");
        }
    }

    /**
     * 토스 결제 응답의 핵심 필드만 추린 불변 결과.
     * @param rawJson 원본 JSON(감사/로그용) — 민감정보가 없도록 호출 측에서 취급 주의
     */
    public record TossPaymentResult(
            String paymentKey,
            String orderId,
            String status,
            Long totalAmount,
            Long balanceAmount,
            String method,
            String rawJson) {

        public boolean isDone() { return STATUS_DONE.equals(status); }

        public boolean isCanceled() {
            return STATUS_CANCELED.equals(status) || STATUS_PARTIAL_CANCELED.equals(status);
        }

        public boolean isFullyCanceled() {
            return STATUS_CANCELED.equals(status) && Objects.equals(balanceAmount, 0L);
        }

        public Long canceledAmount() {
            if (totalAmount == null || balanceAmount == null || balanceAmount < 0 || balanceAmount > totalAmount) {
                return null;
            }
            return totalAmount - balanceAmount;
        }
    }
}
