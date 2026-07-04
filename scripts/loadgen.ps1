# Traffic generator for the ledger service.
# Produces a realistic mix: successful payments, retried duplicates
# (idempotency hits), balance reads, and the occasional insufficient-funds 422
# so every RED panel in Grafana has data.
#
# Usage:  .\scripts\loadgen.ps1                # run for 5 minutes
#         .\scripts\loadgen.ps1 -DurationSeconds 60
param(
    [int]$DurationSeconds = 300,
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Send-Payment([string]$Key, [string]$Body) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/payments" `
        -Headers @{ "Idempotency-Key" = $Key } -ContentType "application/json" -Body $Body
}

$accounts = Invoke-RestMethod "$BaseUrl/api/accounts"
$wallets  = @($accounts | Where-Object { $_.type -eq "WALLET" })
$merchant = $accounts | Where-Object { $_.type -eq "MERCHANT" } | Select-Object -First 1
$treasury = $accounts | Where-Object { $_.type -eq "TREASURY" } | Select-Object -First 1
if (-not $wallets -or -not $merchant -or -not $treasury) {
    throw "Expected seeded accounts (wallets + merchant + treasury). Is the app running?"
}

Write-Host "Generating traffic against $BaseUrl for $DurationSeconds seconds (Ctrl+C to stop)..."
$deadline = (Get-Date).AddSeconds($DurationSeconds)
$lastKey = $null
$lastBody = $null
$stats = @{ created = 0; replayed = 0; rejected = 0; errors = 0; topups = 0 }

while ((Get-Date) -lt $deadline) {
    $wallet = $wallets | Get-Random
    $roll = Get-Random -Minimum 0 -Maximum 100

    try {
        if ($roll -lt 10 -and $lastKey) {
            # 10%: retry the previous payment with the same idempotency key -> replay
            Send-Payment $lastKey $lastBody | Out-Null
            $stats.replayed++
        }
        elseif ($roll -lt 15) {
            # 5%: absurd amount -> 422 insufficient funds (4xx signal)
            $body = @{ fromAccountId = $wallet.id; toAccountId = $merchant.id
                       amountMinor = 99999999; description = "doomed oversized order" } | ConvertTo-Json
            try { Send-Payment ([guid]::NewGuid().ToString()) $body | Out-Null }
            catch { $stats.rejected++ }
        }
        elseif ($roll -lt 20) {
            # 5%: treasury top-up so wallets never run dry
            $body = @{ fromAccountId = $treasury.id; toAccountId = $wallet.id
                       amountMinor = 20000; description = "wallet top-up" } | ConvertTo-Json
            Send-Payment ([guid]::NewGuid().ToString()) $body | Out-Null
            $stats.topups++
        }
        elseif ($roll -lt 30) {
            # 10%: balance read
            Invoke-RestMethod "$BaseUrl/api/accounts/$($wallet.id)" | Out-Null
        }
        else {
            # 70%: normal payment, $1.00 - $20.00
            $lastKey = [guid]::NewGuid().ToString()
            $lastBody = @{ fromAccountId = $wallet.id; toAccountId = $merchant.id
                           amountMinor = (Get-Random -Minimum 100 -Maximum 2000)
                           description = "order-$(Get-Random -Maximum 100000)" } | ConvertTo-Json
            Send-Payment $lastKey $lastBody | Out-Null
            $stats.created++
        }
    }
    catch {
        # 5xx during chaos experiments lands here — that's the point
        $stats.errors++
    }

    Start-Sleep -Milliseconds (Get-Random -Minimum 150 -Maximum 450)
}

Write-Host ("Done. created={0} replayed={1} rejected-422={2} topups={3} errors-5xx={4}" -f `
    $stats.created, $stats.replayed, $stats.rejected, $stats.topups, $stats.errors)
