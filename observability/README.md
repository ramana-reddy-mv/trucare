# Local observability infrastructure

This Compose project runs Jaeger v2 with OpenSearch-backed trace storage,
Prometheus with a persistent TSDB, and Grafana with persistent data and
provisioned Prometheus and Jaeger data sources. It does not start or configure
TruCare application services.

Prometheus scrapes the local Gateway on port 8080, Claim Service on 8083, and
Member Service on 8085. Those services must be running on the Docker host with
their `/actuator/prometheus` endpoints enabled.

## Start

From the repository root in PowerShell, create a local-only Grafana password
file if one does not already exist, then start the stack:

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
docker compose --env-file $envFile -f observability\compose.yaml up -d --build
docker compose --env-file $envFile -f observability\compose.yaml ps
```

The password is supplied only to the Grafana container and its file is ignored
by Git. Keep the file private; all published ports bind to loopback.

## Access

- Jaeger UI: <http://localhost:16686>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000> (user `admin`, password from the environment variable)
- OpenSearch health: <http://localhost:9200/_cluster/health>

In Prometheus, check **Status → Targets** and query `up` to confirm scrape
success. Use Grafana's provisioned Prometheus data source for metrics and its
Jaeger data source to search traces.

## Stop

Stop and remove the containers while preserving stored data:

```powershell
docker compose --env-file observability\.env -f observability\compose.yaml down
```

To also permanently remove OpenSearch traces, Prometheus history, and Grafana
data, explicitly remove the named volumes:

```powershell
docker compose --env-file observability\.env -f observability\compose.yaml down --volumes
```
