package com.example.ecommerce.aspect;

import com.example.ecommerce.annotations.RateLimited;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        int limit = rateLimited.requestsPerMinute();
        String scope = rateLimited.scope();

        log.debug("Rate limit check: method={}, limit={}/min, scope={}",
                joinPoint.getSignature().getName(), limit, scope);

        // In production, this would check a distributed rate limiter (Redis, etc.)
        // and throw a 429 TooManyRequests if the limit is exceeded.
        return joinPoint.proceed();
    }
}
