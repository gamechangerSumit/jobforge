# REQ-20261010 - Contract and infra additions implemented in code (needs contract-change PR)

| Change | Where | Notes |
|---|---|---|
| GET /admin/recruiters/{userId} | API_CONTRACT 12.3 | ADMIN only; returns profile fields, phone, approval status/reason, company name + id. 404 when no such recruiter. |
| `clearEndDate: true` on PATCH education/experience | API_CONTRACT 12.2 | Removes a stored endDate; absent/null keeps it. Experience with current=true always has no endDate. |
| Compose profiles tools, monitoring, storage, ollama | ARCHITECTURE 21 | kafka-ui, prometheus, grafana, minio, ollama. Grafana requires GRAFANA_ADMIN_PASSWORD; MinIO requires S3_ACCESS_KEY/S3_SECRET_KEY. |
| Backend healthcheck in compose | ARCHITECTURE 21 | bash /dev/tcp probe of /actuator/health/readiness; follows MANAGEMENT_PORT when set. |
| micrometer-registry-prometheus (runtime) | ADR needed (CLAUDE.md rule 10) | Free OSS; actuator exposes prometheus only. Set MANAGEMENT_PORT=9091 for Prometheus scraping on jobforge-net. |
| GitHub Actions workflows | ARCHITECTURE 22 | backend-ci, ai-ci, frontend-ci, infra-ci, security. integration.yml and release.yml not written. |
