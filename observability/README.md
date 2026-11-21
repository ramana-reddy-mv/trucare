# TruCare observability

The local observability stack contains Jaeger v2, Prometheus, Grafana, and
OpenSearch as Jaeger's trace-storage backend. OpenSearch is used for traces,
not centralized application-log ingestion. Application logs remain structured
JSON on each service's console.

The initial Prometheus scrape set is the API Gateway (`8080`), Claim Service
(`8083`), and Member Service (`8085`). The provisioned Grafana dashboard
compares these services using only metrics confirmed on their Prometheus
endpoints.

## Start the infrastructure

Run these commands from the repository root in PowerShell. A local Grafana
password file is created only when absent; keep `observability\.env` private.

```powershell
$envFile = 'observability\.env'
if (-not (Test-Path $envFile)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    "GRAFANA_ADMIN_PASSWORD=$([Convert]::ToBase64String($bytes))" |
        Set-Content -Path $envFile -Encoding Ascii
}
docker compose --env-file $envFile -f observability\compose.yaml config -q
docker compose --env-file $envFile -f observability\compose.yaml up -d --wait --no-recreate
docker compose --env-file $envFile -f observability\compose.yaml ps
```

The command uses `--no-recreate` to avoid replacing existing containers. The
stack uses named persistent volumes for Grafana, Prometheus, and OpenSearch.
Published ports bind to loopback only.

The current infrastructure has been started and health-checked. Jaeger
receives OTLP on `4317`/`4318`, Prometheus scrapes `/actuator/prometheus`, and
Grafana has provisioned Prometheus and Jaeger data sources. When the dashboard
files or Compose mounts change, the running Grafana container must load the
dashboard through its API or be explicitly recreated; do not remove its data
volume.

## Run the services and verify metrics

Start Eureka and the Gateway, Claim, and Member services using the repository's
local run configuration. Each traced service needs
`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces`; use the
repository's ignored local configuration for credentials and database secrets.
Do not put credentials in this README, source, or shell history.

Check readiness and scrape targets:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8083/actuator/health
Invoke-RestMethod http://localhost:8085/actuator/health
Invoke-RestMethod 'http://localhost:9090/api/v1/targets?state=active'
```

Only health and Prometheus Actuator endpoints are exposed. The services
configure `http.server.requests` histogram buckets at 50ms, 100ms, 250ms,
500ms, 1s, 2.5s, and 5s. Confirm bucket series before using the latency
percentiles:

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/prometheus' |
    Select-String 'http_server_requests_seconds_bucket'
```

The overview dashboard is `observability\grafana\dashboards\trucare-overview.json`.
Its panels cover scrape health, request/error rates, p50/p95/p99 latency, JVM
heap and GC, Hikari connection pools, circuit-breaker state, and downstream
HTTP latency. It uses `http_server_requests_seconds_*`,
`jvm_memory_{used,max}_bytes`, `jvm_gc_pause_seconds_count`,
`hikaricp_connections_*`, `resilience4j_circuitbreaker_state`, and
`http_client_requests_seconds_{sum,count}`. Do not interpret a panel with no
series as proof of a problem when that service has not yet emitted the
corresponding metric.

## Verify a Gateway → Claim → Member trace

The smoke test requires a locally issued bearer token and a caller-provided
valid claim JSON file. It does not contain test credentials or claim data, and
it does not print the response body. The claim must reference valid local
member, provider, and policy data for the existing Claim Service flow to reach
Member Service.

```powershell
$env:TRUCARE_OBSERVABILITY_BEARER_TOKEN = '<locally issued bearer token>'
$env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD = 'C:\path\to\caller-provided-claim.json'
.\observability\smoke-test.ps1
```

The script sends one POST through the Gateway with a generated W3C trace ID,
separate correlation ID, and idempotency key. It checks Jaeger for one connected
parent-child trace containing `api-gateway`, `claims-service`, and
`member-service`. A successful smoke test prints the trace and correlation IDs.
Open <http://localhost:16686> and search by the printed trace ID to inspect the
span tree.

## Logs and trace correlation

Request logs use structured JSON key-values for `traceId`, `spanId`, and
`correlationId`. `X-Correlation-ID` is a business/request identifier; W3C
`traceparent` carries the separate distributed trace context. The Gateway
validates or generates correlation IDs and propagates both contexts to
downstream calls. Do not use claim, member, request, or correlation IDs as
Prometheus labels.

To investigate missing traces, verify that the application has a sampled
request, `OTEL_EXPORTER_OTLP_ENDPOINT` points to Jaeger's OTLP HTTP endpoint,
Jaeger is healthy, and the request's trace ID is present in the Jaeger query.
For log correlation, inspect the service console output for JSON fields
`traceId`, `spanId`, and `correlationId`; startup logs have no request context
and therefore will not contain those fields.

## Stop

Stop the containers while preserving all named volumes and their data:

```powershell
docker compose --env-file observability\.env -f observability\compose.yaml down
```
