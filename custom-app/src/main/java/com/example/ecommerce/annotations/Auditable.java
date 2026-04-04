package com.example.ecommerce.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for audit logging. The action string is recorded to
 * the centralized audit trail along with the authenticated principal and timestamp.
 * Required on all state-changing operations that affect orders, payments, or inventory.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
}
