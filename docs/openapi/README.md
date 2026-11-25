# Generated OpenAPI contract

The canonical runtime document is exported from the running backend at `/v3/api-docs`.

Run the stack with `OPENAPI_ENABLED=true`, then execute:

```bash
./scripts/export-openapi.sh
cd frontend
pnpm install --no-frozen-lockfile
pnpm gen:api
```

The JSON snapshot is written to `docs/openapi/openapi.v1.json`; generated TypeScript is written to `frontend/src/types/generated-api.ts`. Do not hand-edit either generated artifact. See [ADR-014](../adr/ADR-014-springdoc-openapi.md).

The integration workflow exports both artifacts as a build artifact. The checked-in baseline is intentionally not fabricated; review and commit the first generated snapshot from a successful CI artifact before enabling a drift gate.
