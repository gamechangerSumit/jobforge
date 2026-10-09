/**
 * Module {@code audit} (Dev 1). Read-only admin viewer over {@code core.audit_logs}
 * ({@code GET /admin/audit-logs}). Writes go through {@code shared.audit.AuditService}.
 * Layout: api / app / infra.
 */
package com.jobforge.backend.audit;
