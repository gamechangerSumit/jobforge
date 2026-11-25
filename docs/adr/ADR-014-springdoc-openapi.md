# ADR-014: Generate the REST API contract with Springdoc

- **Status:** Accepted
- **Date:** 2026-11-25
- **Decision owners:** JobForge backend maintainers

## Context

JobForge's REST API is implemented with Spring MVC controllers and Jakarta validation. The API contract and handwritten frontend clients have drift risk. A generated OpenAPI document is needed as a reviewable contract and as the input to TypeScript type generation.

## Decision

Use `org.springdoc:springdoc-openapi-starter-webmvc-api:2.10.1` with the existing Spring Boot 3.5.x application. Springdoc inspects the running MVC application and exposes the JSON document at `/v3/api-docs`. The Swagger UI is disabled.

The documentation endpoint is disabled by default (`OPENAPI_ENABLED=false`) and is enabled only in explicit contract-generation environments. The generated document is exported to `docs/openapi/openapi.v1.json`; frontend types are generated with the existing `openapi-typescript` package through `pnpm gen:api`.

The API document includes bearer authentication metadata, but does not globally mark every route as requiring authentication because public job discovery and other anonymous endpoints exist.

## Security and privacy

- OpenAPI output contains route and schema metadata only; it must never include secrets, environment values, or database records.
- The endpoint is read-only and disabled by default.
- Contract-generation CI uses disposable credentials and never exposes the API document to a public production endpoint.
- Do not add a global security requirement. Apply security annotations at the operation level as coverage is improved.

## Consequences

- The generated snapshot must be refreshed whenever controller contracts change.
- CI exports the runtime document and generated TypeScript as artifacts for review.
- A later gate should compare the committed snapshot with the freshly generated document once the baseline snapshot has been reviewed and checked in.
- Generated TypeScript must not be edited manually; update controller DTOs and regenerate instead.

## Alternatives considered

- Hand-maintained OpenAPI JSON: rejected because it duplicates controller and validation definitions.
- Swagger UI: not needed for the current workflow; JSON generation is sufficient.
- OpenAPI generation from annotations alone: rejected because it misses runtime Spring MVC mappings and validation metadata.
