$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:TRUCARE_OBSERVABILITY_BEARER_TOKEN)) {
    throw 'Set TRUCARE_OBSERVABILITY_BEARER_TOKEN to a valid locally issued bearer token.'
}
if ([string]::IsNullOrWhiteSpace($env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD)) {
    throw 'Set TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD to a file containing a valid claim JSON payload.'
}
if (-not (Test-Path -LiteralPath $env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD -PathType Leaf)) {
    throw 'The claim payload file was not found.'
}

$gatewayUrl = if ($env:TRUCARE_OBSERVABILITY_GATEWAY_URL) {
    $env:TRUCARE_OBSERVABILITY_GATEWAY_URL.TrimEnd('/')
} else {
    'http://localhost:8080'
}
$jaegerUrl = if ($env:TRUCARE_OBSERVABILITY_JAEGER_URL) {
    $env:TRUCARE_OBSERVABILITY_JAEGER_URL.TrimEnd('/')
} else {
    'http://localhost:16686'
}

$traceBytes = New-Object byte[] 16
$parentSpanBytes = New-Object byte[] 8
$correlationBytes = New-Object byte[] 16
$idempotencyBytes = New-Object byte[] 16
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($traceBytes)
    $random.GetBytes($parentSpanBytes)
    $random.GetBytes($correlationBytes)
    $random.GetBytes($idempotencyBytes)
} finally {
    $random.Dispose()
}
$traceId = [BitConverter]::ToString($traceBytes).Replace('-', '').ToLowerInvariant()
$parentSpanId = [BitConverter]::ToString($parentSpanBytes).Replace('-', '').ToLowerInvariant()
$correlationId = [Guid]::NewGuid().ToString('N')
$idempotencyKey = [Guid]::NewGuid().ToString('N')
$traceParent = "00-$traceId-$parentSpanId-01"
$payload = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD))

$httpClient = [System.Net.Http.HttpClient]::new()
try {
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$gatewayUrl/api/claims")
    $request.Headers.Authorization =
        [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
            'Bearer',
            $env:TRUCARE_OBSERVABILITY_BEARER_TOKEN)
    $request.Headers.Add('X-Correlation-ID', $correlationId)
    $request.Headers.Add('traceparent', $traceParent)
    $request.Headers.Add('Idempotency-Key', $idempotencyKey)
    $request.Content = [System.Net.Http.StringContent]::new(
        $payload,
        [System.Text.Encoding]::UTF8,
        'application/json')
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $status = [int]$response.StatusCode
    $response.Dispose()
    $request.Dispose()
} finally {
    $httpClient.Dispose()
}

if ($status -lt 200 -or $status -ge 300) {
    throw "Claim request returned HTTP $status. The response body is omitted to avoid exposing claim data."
}

$trace = $null
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try {
        $query = Invoke-RestMethod -Method Get -Uri "$jaegerUrl/api/traces/$traceId"
        if ($query.data -and $query.data.Count -gt 0) {
            $trace = $query.data[0]
            break
        }
    } catch {
        if ($attempt -eq 19) {
            throw "Jaeger did not return trace $traceId after 40 seconds."
        }
    }
    Start-Sleep -Seconds 2
}
if (-not $trace) {
    throw "Jaeger did not return trace $traceId after 40 seconds."
}

$servicesByProcess = @{}
foreach ($property in $trace.processes.PSObject.Properties) {
    $servicesByProcess[$property.Name] = $property.Value.serviceName
}
$spansById = @{}
foreach ($span in $trace.spans) {
    $spansById[$span.spanID] = $span
}
$connectedChainFound = $false
foreach ($span in $trace.spans) {
    $chain = New-Object System.Collections.Generic.List[string]
    $current = $span
    $visited = @{}
    while ($current -and -not $visited.ContainsKey($current.spanID)) {
        $visited[$current.spanID] = $true
        $chain.Insert(0, $servicesByProcess[$current.processID])
        $parentReference = $current.references |
            Where-Object { $_.refType -eq 'CHILD_OF' } |
            Select-Object -First 1
        if (-not $parentReference -or -not $spansById.ContainsKey($parentReference.spanID)) {
            break
        }
        $current = $spansById[$parentReference.spanID]
    }
    $gatewayIndex = $chain.IndexOf('api-gateway')
    $claimIndex = if ($gatewayIndex -ge 0) {
        $chain.IndexOf('claims-service', $gatewayIndex + 1)
    } else {
        -1
    }
    if ($claimIndex -ge 0 -and $chain.IndexOf('member-service', $claimIndex + 1) -ge 0) {
        $connectedChainFound = $true
        break
    }
}

if (-not $connectedChainFound) {
    $observedServices = @($servicesByProcess.Values | Sort-Object -Unique) -join ', '
    throw "Trace $traceId is missing a connected Gateway -> Claim -> Member parent-child chain. Services present: $observedServices"
}

Write-Output "Claim request returned HTTP $status."
Write-Output "Verified one connected parent-child trace across api-gateway -> claims-service -> member-service."
Write-Output "Trace ID: $traceId"
Write-Output "Correlation ID: $correlationId"
