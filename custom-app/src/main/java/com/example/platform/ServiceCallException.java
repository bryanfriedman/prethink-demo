package com.example.platform;

/**
 * Thrown when a service-to-service call fails after exhausting retries
 * or when the circuit breaker is open.
 */
public class ServiceCallException extends RuntimeException {

    private final String serviceName;
    private final String operation;

    public ServiceCallException(String serviceName, String operation, Throwable cause) {
        super("Service call failed: " + serviceName + "/" + operation, cause);
        this.serviceName = serviceName;
        this.operation = operation;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOperation() {
        return operation;
    }
}
