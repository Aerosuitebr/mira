# Monitora importação RF até concluir ou falhar.
param(
    [string]$ApiBase = 'http://localhost:8082',
    [string]$Email = 'demo@prospectportal.com',
    [string]$Password = 'demo123',
    [int]$IntervalSeconds = 60
)

$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'prospect-portal-rf-import-watch.log'

function Write-Log([string]$Message) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message"
    Add-Content -Path $log -Value $line
    Write-Host $line
}

try {
    $login = Invoke-RestMethod -Method POST -Uri "$ApiBase/api/auth/login" `
        -ContentType 'application/json' `
        -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
    $headers = @{ Authorization = "Bearer $($login.token)" }
} catch {
    Write-Log "Falha ao autenticar: $($_.Exception.Message)"
    exit 1
}

Write-Log 'Monitor de importação RF iniciado.'

while ($true) {
    try {
        $status = Invoke-RestMethod -Uri "$ApiBase/api/admin/import/rf/status" -Headers $headers
        $job = $status.recentJobs | Select-Object -First 1
        if (-not $job) {
            Write-Log 'Nenhum job recente encontrado.'
            break
        }

        $mroCount = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
            "SELECT COUNT(*) FROM companies WHERE state='RJ' AND (cnae_main LIKE '33163%' OR COALESCE(cnae_secondary,'') LIKE '%33163%');" 2>$null
        Write-Log ("{0} | processadas={1} inseridas={2} ignoradas={3} | MRO RJ={4}" -f `
            $job.status, $job.processedRows, $job.insertedRows, $job.skippedRows, $mroCount)

        if ($job.status -in @('COMPLETED', 'FAILED')) {
            if ($job.status -eq 'FAILED') {
                Write-Log "Importação falhou: $($job.errorMessage)"
                exit 1
            }
            Write-Log 'Importação concluída com sucesso.'
            break
        }
    } catch {
        Write-Log "Erro ao consultar status: $($_.Exception.Message)"
    }

    Start-Sleep -Seconds $IntervalSeconds
}

exit 0
