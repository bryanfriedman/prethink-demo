package com.example.ecommerce.client;

import com.example.platform.ServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Component
public class ShippingClient extends ServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ShippingClient(RestTemplate restTemplate,
                          @Value("${app.external-services.shipping-provider.base-url}") String baseUrl) {
        super("shipping-provider", Duration.ofSeconds(5), 3);
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getRates(String originZip, String destinationZip, double weightLbs) {
        return executeWithResilience("getRates", () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAuthToken());

            String url = baseUrl + "/rates?origin=" + originZip
                    + "&destination=" + destinationZip
                    + "&weight=" + weightLbs;

            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
        });
    }

    public Map<String, Object> createShipment(Map<String, Object> shipmentDetails) {
        return executeWithResilience("createShipment", () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAuthToken());

            String url = baseUrl + "/shipments";

            return restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(shipmentDetails, headers), Map.class).getBody();
        });
    }
}
