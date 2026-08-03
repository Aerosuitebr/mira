# Varre sites das empresas e marca as contatáveis (web_contactable) para aparecer na Descoberta.
param(
    [string[]]$States = @('SP', 'RJ', 'MG', 'ES', 'DF', 'GO', 'MT', 'MS'),
    [int]$Limit = 500,
    [string]$ApiBase = 'http://localhost:8082',
    [string]$Email = 'demo@prospectportal.com',
    [string]$Password = 'demo123'
)

$login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
    -ContentType 'application/json' `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }
$body = @{ states = $States; limit = $Limit } | ConvertTo-Json

Write-Host "Iniciando varredura web para: $($States -join ', ') (limite $Limit)"
$result = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/admin/import/rf/probe-web" `
    -Headers $headers -ContentType 'application/json' -Body $body

Write-Host "Job ID: $($result.id) | Status: $($result.status)"
Write-Host "Acompanhe: .\scripts\watch-rf-import.ps1"
