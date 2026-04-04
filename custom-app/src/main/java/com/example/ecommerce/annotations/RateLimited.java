package com.example.ecommerce.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces rate limiting on the annotated method. Tracked per-user by default,
 * or per-IP when scope is set to "ip". Platform team convention — all public-facing
 * endpoints that accept writes or expensive queries must be annotated.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    int requestsPerMinute() default 60;
    String scope() default "user";
}
