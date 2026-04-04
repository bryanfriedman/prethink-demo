package com.example.ecommerce.aspect;

import com.example.ecommerce.annotations.TenantScoped;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantScopeAspect.class);

    @Around("@within(tenantScoped)")
    public Object applyTenantScope(ProceedingJoinPoint joinPoint, TenantScoped tenantScoped) throws Throwable {
        // In production, this would:
        // 1. Extract the tenant ID from the security context or request header
        // 2. Set a Hibernate filter or thread-local tenant context
        // 3. Ensure all repository queries are scoped to the current tenant
        log.debug("Applying tenant scope for method: {}", joinPoint.getSignature().toShortString());

        return joinPoint.proceed();
    }
}
