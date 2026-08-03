# Monitora job de geocodificação em massa
param(
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [int]$IntervalSec = 15
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "Monitorando geocodificação (Ctrl+C para sair)" -ForegroundColor Cyan

while ($true) {
    $jobs = Invoke-RestMethod -Headers $headers -Uri "$ApiBase/api/admin/import/rf/status"
    $geo = $jobs.recentJobs | Where-Object { $_.jobType -eq 'RF_ENRICH' -and $_.status -eq 'RUNNING' } | Select-Object -First 1
    if (-not $geo) {
        $geo = $jobs.recentJobs | Where-Object { $_.jobType -eq 'RF_ENRICH' } | Select-Object -First 1
    }

    $pg = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
        "SELECT COUNT(*) FILTER (WHERE geocoded AND location_precision='CEP'), COUNT(*) FILTER (WHERE geocoded), COUNT(*) FROM companies;" 2>$null
    $parts = $pg -split '\|'
    $cep = [long]$parts[0]
    $total = [long]$parts[1]
    $all = [long]$parts[2]
    $pct = if ($all -gt 0) { [math]::Round(100 * $total / $all, 2) } else { 0 }

    $line = "$(Get-Date -Format HH:mm:ss) | geocodificadas: $total / $all ($pct%) | precisão CEP: $cep"
    if ($geo) {
        $line += " | job $($geo.status) | processadas $($geo.processedRows)"
        if ($geo.status -eq 'COMPLETED') {
            Write-Host $line -ForegroundColor Green
            break
        }
        if ($geo.status -eq 'FAILED') {
            Write-Host "$line | ERRO: $($geo.errorMessage)" -ForegroundColor Red
            break
        }
    } else {
        if ($total -ge $all -and $all -gt 0) {
            Write-Host "$line | CONCLUÍDO" -ForegroundColor Green
            break
        }
    }
    Write-Host $line
    Start-Sleep -Seconds $IntervalSec
}
