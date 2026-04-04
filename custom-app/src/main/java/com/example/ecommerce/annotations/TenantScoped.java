package com.example.ecommerce.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that all data access within this service is scoped to the current tenant.
 * The TenantScopeAspect intercepts repository calls and injects a tenant filter so that
 * queries never return data belonging to another tenant. Services handling cross-tenant
 * data (e.g. platform admin) must NOT use this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScoped {
}
