package com.example.ecommerce.client;

import com.example.platform.ServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
public class TaxCalculationClient extends ServiceClient {

    private final WebClient webClient;

    public TaxCalculationClient(@Value("${app.external-services.tax-service.base-url}") String baseUrl) {
        super("tax-service", Duration.ofSeconds(3), 2);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + getAuthToken())
                .build();
    }

    public BigDecimal calculateTax(BigDecimal subtotal, String stateCode, String productCategory) {
        return executeWithResilience("calculateTax", () -> {
            Map<String, Object> response = webClient.post()
                    .uri("/calculate")
                    .bodyValue(Map.of(
                            "subtotal", subtotal,
                            "state", stateCode,
                            "category", productCategory
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return new BigDecimal(response.get("taxAmount").toString());
        });
    }
}
