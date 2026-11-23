
$ErrorActionPreference = 'Stop'

Write-Host "`nLoading TruCare Observability Environment..." -ForegroundColor Cyan

# -----------------------------------------
# 1. Infrastructure URLs
# -----------------------------------------

$env:TRUCARE_OBSERVABILITY_GATEWAY_URL = "http://localhost:8080"
$env:TRUCARE_OBSERVABILITY_JAEGER_URL = "http://localhost:16686"

# -----------------------------------------
# 2. Grafana configuration
# -----------------------------------------

if ([string]::IsNullOrWhiteSpace($env:GRAFANA_ADMIN_PASSWORD)) {
    $securePassword = Read-Host "Enter Grafana admin password" -AsSecureString
    $passwordPointer = [IntPtr]::Zero

    try {
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $env:GRAFANA_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        if ($passwordPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        }
    }
}

# -----------------------------------------
# 3. JWT configuration
# -----------------------------------------

if ([string]::IsNullOrWhiteSpace($env:TRUCARE_OBSERVABILITY_BEARER_TOKEN)) {
    $secureToken = Read-Host "Enter TruCare JWT token" -AsSecureString
    $tokenPointer = [IntPtr]::Zero

    try {
        $tokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
        $env:TRUCARE_OBSERVABILITY_BEARER_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPointer)
    }
    finally {
        if ($tokenPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPointer)
        }
    }
}

# -----------------------------------------
# 4. Claim payload
# -----------------------------------------

$env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD = Join-Path $PSScriptRoot "claim-payload.json"

if (-not (Test-Path -LiteralPath $env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD -PathType Leaf)) {
    throw "Claim payload file not found: $env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD"
}

# -----------------------------------------
# 5. Environment validation
# -----------------------------------------

Write-Host "`nEnvironment configuration loaded." -ForegroundColor Green

Write-Host "Gateway URL: $env:TRUCARE_OBSERVABILITY_GATEWAY_URL"
Write-Host "Jaeger URL: $env:TRUCARE_OBSERVABILITY_JAEGER_URL"
Write-Host "Grafana password: Configured"
Write-Host "JWT token: Configured"
Write-Host "Claim payload: $env:TRUCARE_OBSERVABILITY_CLAIM_PAYLOAD"
