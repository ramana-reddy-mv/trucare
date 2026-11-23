$payload = Get-Content .\member-payload.json -Raw

$headers = @{
    Authorization = "Bearer $env:TRUCARE_OBSERVABILITY_BEARER_TOKEN"
    "X-Correlation-ID" = [guid]::NewGuid().ToString("N")
}

$response = Invoke-RestMethod `
    -Method POST `
    -Uri "http://localhost:8080/api/members/addMember" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $payload

$response | ConvertTo-Json -Depth 5