# Acompanha job de reindexação Elasticsearch
param(
    [string]$ApiBase = "http://localhost:8082",
    [string]$Email = "demo@prospectportal.com",
    [string]$Password = "demo123",
    [int]$IntervalSec = 10
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host "Monitorando reindexação ES (Ctrl+C para sair)" -ForegroundColor Cyan

while ($true) {
    $status = Invoke-RestMethod -Headers $headers -Uri "$ApiBase/api/admin/search/status"
    $pct = if ($status.postgresCompanies -gt 0) {
        [math]::Round(100 * $status.indexedDocuments / $status.postgresCompanies, 1)
    } else { 0 }

    $line = "ES: $($status.indexedDocuments) / $($status.postgresCompanies) ($pct%)"
    if ($status.reindexRunning -and $status.runningJob) {
        $job = $status.runningJob
        $line += " | job $($job.status) | processadas $($job.processedRows)"
    } elseif ($status.inSync) {
        $line += " | SINCRONIZADO"
        Write-Host $line -ForegroundColor Green
        break
    }
    Write-Host $line
    if (-not $status.reindexRunning -and $status.indexedDocuments -gt 0 -and -not $status.inSync) {
        Write-Host "Job parou antes de sincronizar. Verifique logs do backend." -ForegroundColor Yellow
        break
    }
    if (-not $status.reindexRunning -and $status.indexedDocuments -eq 0) {
        Write-Host "Nenhum documento indexado ainda. Backend rodando?" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds $IntervalSec
}
