package com.example.ecommerce.client;

import com.example.ecommerce.service.MetricsCollector;
import com.example.platform.ServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
public class FraudCheckClient extends ServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final MetricsCollector metricsCollector;

    public FraudCheckClient(RestTemplate restTemplate,
                            @Value("${app.external-services.fraud-check.base-url}") String baseUrl,
                            MetricsCollector metricsCollector) {
        super("fraud-api", Duration.ofSeconds(10), 2);
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.metricsCollector = metricsCollector;
    }

    public Map<String, Object> checkTransaction(Long orderId, BigDecimal amount) {
        return executeWithResilience("checkTransaction", () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAuthToken());

            Map<String, Object> body = Map.of(
                    "orderId", orderId,
                    "amount", amount
            );

            String url = baseUrl + "/check";

            Map<String, Object> result = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), Map.class).getBody();

            metricsCollector.incrementCounter("fraud.checks.total");
            String riskLevel = (String) result.get("riskLevel");
            if ("HIGH".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
                metricsCollector.incrementCounter("fraud.checks.flagged");
            }

            return result;
        });
    }
}
