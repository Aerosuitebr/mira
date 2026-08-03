# Importa oficinas MRO RJ (CNAE principal 33163*) direto no PostgreSQL.
# Corrige registros ignorados pelo bug de índice de colunas UF na RF.
param(
    [string]$DataRoot = "C:\prospect-portal-data"
)

$ErrorActionPreference = "Stop"
$rfRoot = Join-Path $DataRoot "rf\extracted"
$encoding = [System.Text.Encoding]::GetEncoding("ISO-8859-1")
$ufs = @("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO")

function Only-Digits([string]$v) { if ([string]::IsNullOrEmpty($v)) { return "" }; return ($v -replace '\D', '') }
function Unquote([string]$v) {
    if ([string]::IsNullOrEmpty($v)) { return "" }
    $t = $v.Trim()
    if ($t.Length -ge 2 -and $t.StartsWith('"') -and $t.EndsWith('"')) { return $t.Substring(1, $t.Length - 2) }
    return $t
}
function Field([string[]]$cols, [int]$idx) { if ($idx -lt $cols.Count) { return Unquote $cols[$idx] }; return "" }
function Detect-UfIndex([string[]]$cols) {
    foreach ($idx in 19, 20) {
        $v = (Field $cols $idx).ToUpper()
        if ($v.Length -eq 2 -and $ufs -contains $v) { return $idx }
    }
    return 19
}
function Sql-Escape([string]$v) {
    if ([string]::IsNullOrEmpty($v)) { return "NULL" }
    return "'" + ($v -replace "'", "''") + "'"
}

$rows = New-Object System.Collections.Generic.List[string]
$seen = @{}

Get-ChildItem $rfRoot -Directory | Where-Object { $_.Name -like "Estabelecimentos*" } | Sort-Object Name | ForEach-Object {
    $file = Get-ChildItem $_.FullName -File | Where-Object { $_.Name -like "*ESTABELE*" } | Select-Object -First 1
    if (-not $file) { return }
    Write-Host "Lendo $($file.Name)..."
    $reader = [System.IO.StreamReader]::new($file.FullName, $encoding)
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            $cols = $line.Split(';', [System.StringSplitOptions]::None)
            if ((Field $cols 5) -ne "02") { continue }

            $ufIdx = Detect-UfIndex $cols
            $uf = (Field $cols $ufIdx).ToUpper()
            if ($uf -ne "RJ") { continue }

            $cnaeMain = Only-Digits (Field $cols 11)
            if (-not $cnaeMain.StartsWith("33163")) { continue }

            $basico = (Only-Digits (Field $cols 0)).PadLeft(8, '0')
            $ordem = (Only-Digits (Field $cols 1)).PadLeft(4, '0')
            $dv = (Only-Digits (Field $cols 2)).PadLeft(2, '0')
            $cnpj = $basico + $ordem + $dv
            if ($cnpj.Length -ne 14 -or $seen.ContainsKey($cnpj)) { continue }
            $seen[$cnpj] = $true

            $municipio = Only-Digits (Field $cols ($ufIdx + 1))
            $bairro = Field $cols ($ufIdx - 2)
            $cep = Only-Digits (Field $cols ($ufIdx - 1))
            $ddd = Only-Digits (Field $cols ($ufIdx + 2))
            $tel = Only-Digits (Field $cols ($ufIdx + 3))
            $phone = if ($ddd -and $tel) { $ddd + $tel } else { $null }
            $trade = Field $cols 4
            $cnaeSec = Field $cols 12
            $tipoLog = Field $cols 13
            $logradouro = Field $cols 14
            $numero = Field $cols 15
            $email = Field $cols 27
            $opened = Field $cols 10
            $street = (@($tipoLog, $logradouro, $numero) | Where-Object { $_ } | ForEach-Object { $_.Trim() } | Where-Object { $_ }) -join ' '

            $rows.Add(@"
SELECT upsert_mro_row(
  $(Sql-Escape $cnpj), $(Sql-Escape $basico), $(Sql-Escape $trade), $(Sql-Escape $cnaeMain),
  $(Sql-Escape $cnaeSec), $(Sql-Escape $municipio), $(Sql-Escape $uf), $(Sql-Escape $bairro),
  $(Sql-Escape $street), $(Sql-Escape $cep), $(Sql-Escape $email), $(Sql-Escape $phone), $(Sql-Escape $opened)
);
"@)
        }
    }
    finally { $reader.Close() }
}

Write-Host "Encontradas $($rows.Count) oficinas MRO RJ na RF."

$sql = @"
CREATE OR REPLACE FUNCTION upsert_mro_row(
  p_cnpj text, p_basico text, p_trade text, p_cnae text, p_cnae_sec text,
  p_municipio text, p_uf text, p_bairro text, p_street text, p_cep text,
  p_email text, p_phone text, p_opened text
) RETURNS void LANGUAGE plpgsql AS `$`$
DECLARE
  v_legal text;
  v_nature text;
  v_capital numeric;
  v_porte text;
  v_city text;
  v_cnae_desc text;
  v_opened date;
BEGIN
  SELECT legal_name, legal_nature_code, capital_social, company_size_code
    INTO v_legal, v_nature, v_capital, v_porte
  FROM rf_empresas WHERE cnpj_basico = p_basico;
  IF v_legal IS NULL THEN RETURN; END IF;

  SELECT name INTO v_city FROM rf_municipios WHERE code = p_municipio;
  IF v_city IS NULL THEN v_city := 'Desconhecido'; END IF;

  SELECT description INTO v_cnae_desc FROM rf_cnaes WHERE code = p_cnae;

  BEGIN v_opened := to_date(p_opened, 'YYYYMMDD'); EXCEPTION WHEN OTHERS THEN v_opened := NULL; END;

  INSERT INTO companies (
    cnpj, legal_name, trade_name, cnae_main, cnae_secondary, cnae_description, legal_nature,
    capital_social, opened_at, city, state, neighborhood, street, zip_code,
    estimated_revenue, email, phone, municipality_code, registration_status,
    company_size_code, data_source, geocoded, created_at, updated_at
  ) VALUES (
    p_cnpj, v_legal, NULLIF(p_trade, ''), p_cnae,
    NULLIF(regexp_replace(p_cnae_sec, '[^0-9,]', '', 'g'), ''),
    v_cnae_desc, v_nature, v_capital, v_opened, v_city, p_uf,
    NULLIF(p_bairro, ''), NULLIF(p_street, ''), NULLIF(p_cep, ''),
    CASE v_porte WHEN '01' THEN 'SMALL' WHEN '03' THEN 'MEDIUM' WHEN '05' THEN 'LARGE' ELSE 'SMALL' END,
    NULLIF(p_email, ''), NULLIF(p_phone, ''), NULLIF(p_municipio, ''),
    '02', v_porte, 'RECEITA_FEDERAL', FALSE, NOW(), NOW()
  )
  ON CONFLICT (cnpj) DO UPDATE SET
    legal_name = EXCLUDED.legal_name,
    trade_name = EXCLUDED.trade_name,
    cnae_main = EXCLUDED.cnae_main,
    cnae_secondary = EXCLUDED.cnae_secondary,
    cnae_description = EXCLUDED.cnae_description,
    email = COALESCE(EXCLUDED.email, companies.email),
    phone = COALESCE(EXCLUDED.phone, companies.phone),
    municipality_code = EXCLUDED.municipality_code,
    city = EXCLUDED.city,
    state = EXCLUDED.state,
    neighborhood = EXCLUDED.neighborhood,
    street = EXCLUDED.street,
    zip_code = EXCLUDED.zip_code,
    data_source = 'RECEITA_FEDERAL',
    updated_at = NOW();
END;
`$`$;

$($rows -join "`n")
DROP FUNCTION upsert_mro_row(text,text,text,text,text,text,text,text,text,text,text,text,text);
"@

$tmp = Join-Path $env:TEMP "import-mro-rj.sql"
$sql | Set-Content $tmp -Encoding UTF8
Get-Content $tmp | docker exec -i prospect-portal-postgres psql -U prospect -d prospect_portal -v ON_ERROR_STOP=1

$count = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
    "SELECT COUNT(*) FROM companies WHERE state='RJ' AND cnae_main LIKE '33163%';"
$contacts = docker exec prospect-portal-postgres psql -U prospect -d prospect_portal -t -A -c `
    "SELECT COUNT(*) FROM companies WHERE state='RJ' AND cnae_main LIKE '33163%' AND (COALESCE(email,'') <> '' OR COALESCE(phone,'') <> '');"
Write-Host ""
Write-Host "MRO RJ no banco: $count (com email ou telefone: $contacts)"
