package com.example.ecommerce.aspect;

import com.example.ecommerce.annotations.Auditable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = auth != null ? auth.getName() : "anonymous";

        log.info("AUDIT: action={}, principal={}, method={}",
                auditable.action(), principal, joinPoint.getSignature().toShortString());

        Object result = joinPoint.proceed();

        log.info("AUDIT: action={} completed successfully", auditable.action());

        return result;
    }
}
