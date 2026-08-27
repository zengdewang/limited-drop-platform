[CmdletBinding()]
param(
    [ValidateSet('inventory', 'same-user', 'ramp', 'limit-ip', 'limit-account')]
    [string]$Scenario = 'inventory',

    [string]$JMeterBin = 'C:\Users\lemon\Desktop\apache-jmeter-5.6.3\bin\jmeter.bat',

    [ValidateRange(1, 2147483647)]
    [int]$DropId = 1,

    [ValidateRange(0, 2147483647)]
    [int]$Stock = 0,

    [ValidateRange(1, 10000)]
    [int]$InventoryUsers = 50,

    [ValidateRange(1, 10000)]
    [int]$SameUserThreads = 30,

    [ValidateRange(1, 2147483647)]
    [long]$SameUserId = 10001,

    [string]$SameUserToken = '',

    [ValidateRange(1, 10000)]
    [int]$RampUsers = 100,

    [ValidateRange(1, 3600)]
    [int]$RampSeconds = 30,

    [ValidateRange(1, 10000)]
    [int]$LimitThreads = 30,

    [ValidateRange(1, 1000)]
    [int]$Loops = 1,

    [string]$LimitIp = '198.51.100.10',

    [string]$UsersFile = (Join-Path $PSScriptRoot 'users.csv'),

    [string]$GatewayHostName = 'localhost',

    [ValidateRange(1, 65535)]
    [int]$GatewayPort = 8080,

    [string]$DirectHostName = 'localhost',

    [ValidateRange(1, 65535)]
    [int]$DirectPort = 8083,

    [string]$OpsKey = 'limiteddrop-ops-2026',

    [switch]$SkipOpen,

    [switch]$CloseAfter,

    [switch]$OpenReport,

    [switch]$Gui
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$jmxPath = Join-Path $PSScriptRoot 'flashsale-concurrency.jmx'
$exampleUsersPath = Join-Path $PSScriptRoot 'users.csv.example'

if (-not (Test-Path -LiteralPath $JMeterBin -PathType Leaf)) {
    throw "找不到 JMeter：$JMeterBin"
}
if (-not (Test-Path -LiteralPath $jmxPath -PathType Leaf)) {
    throw "找不到测试计划：$jmxPath"
}

if (Test-Path -LiteralPath $UsersFile -PathType Leaf) {
    $resolvedUsersFile = [IO.Path]::GetFullPath($UsersFile)
}
else {
    $resolvedUsersFile = [IO.Path]::GetFullPath($exampleUsersPath)
    if ($Scenario -eq 'limit-ip') {
        throw "limit-ip 需要包含真实 JWT 的 users.csv。请先运行 .\loadtest\prepare-users.ps1"
    }
    Write-Warning "未找到 $UsersFile，当前场景改用 users.csv.example。"
}

$csvRows = @(Import-Csv -LiteralPath $resolvedUsersFile)
$threads = switch ($Scenario) {
    'inventory'     { $InventoryUsers }
    'same-user'     { $SameUserThreads }
    'ramp'          { $RampUsers }
    'limit-ip'      { $LimitThreads }
    'limit-account' { $LimitThreads }
}

$requiredCsvRows = $threads * $Loops
if ($Scenario -in @('inventory', 'ramp', 'limit-ip') -and $csvRows.Count -lt $requiredCsvRows) {
    throw "CSV 用户不足：场景需要 $requiredCsvRows 行，文件只有 $($csvRows.Count) 行。"
}

if ($Scenario -eq 'limit-ip') {
    $selectedRows = @($csvRows | Select-Object -First $requiredCsvRows)
    if ($selectedRows.Count -ne $requiredCsvRows) {
        throw "limit-ip 需要 $requiredCsvRows 个不同账号。"
    }
    if (@($selectedRows | Where-Object {
        [string]::IsNullOrWhiteSpace([string]$_.token) -or
        [string]$_.token -match '(?i)(REPLACE_WITH_JWT|<JWT>|PLACEHOLDER)'
    }).Count -gt 0) {
        throw 'limit-ip 的 CSV 含占位 token，请先运行 prepare-users.ps1 生成真实 JWT。'
    }
    if (@($selectedRows.userId | Sort-Object -Unique).Count -ne $requiredCsvRows) {
        throw 'limit-ip 必须使用不同账号，CSV 前 N 行存在重复 userId。'
    }
}

if ($Scenario -eq 'limit-account' -and [string]::IsNullOrWhiteSpace($SameUserToken)) {
    $tokenRow = $csvRows | Where-Object {
        -not [string]::IsNullOrWhiteSpace([string]$_.token) -and
        [string]$_.token -notmatch '(?i)(REPLACE_WITH_JWT|<JWT>|PLACEHOLDER)'
    } | Select-Object -First 1

    if ($null -eq $tokenRow) {
        throw 'limit-account 需要真实 JWT。请先运行 prepare-users.ps1，或传入 -SameUserToken。'
    }

    $SameUserToken = [string]$tokenRow.token
    $SameUserId = [long]$tokenRow.userId
}

$gatewayBaseUrl = 'http://{0}:{1}' -f $GatewayHostName, $GatewayPort
$openedByScript = $false

try {
    if (-not $SkipOpen) {
        $openRequest = @{
            Method  = 'Post'
            Uri     = "$gatewayBaseUrl/api/flashsale/drops/$DropId/open"
            Headers = @{ 'X-Ops-Key' = $OpsKey }
        }
        Invoke-RestMethod @openRequest | Out-Null
        $openedByScript = $true
    }

    $info = Invoke-RestMethod -Method Get -Uri "$gatewayBaseUrl/api/flashsale/drops/$DropId/info"
    if ($info.code -ne 0 -or $info.data.status -ne 'OPEN') {
        throw "Drop $DropId 未处于 OPEN 状态。"
    }

    $actualStock = [int]$info.data.stock
    if ($Stock -eq 0) {
        $Stock = $actualStock
    }
    elseif ($Stock -ne $actualStock) {
        throw "传入 Stock=$Stock 与 Drop 实际库存 $actualStock 不一致。"
    }

    $syncGroupSize = if ($Scenario -eq 'ramp') { 1 } else { 0 }
    $effectiveRampSeconds = if ($Scenario -eq 'ramp') { $RampSeconds } else { 1 }

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $runDirectory = Join-Path $PSScriptRoot "results\$Scenario-$timestamp"
    $reportDirectory = Join-Path $runDirectory 'html'
    $jtlPath = Join-Path $runDirectory 'results.jtl'
    $logPath = Join-Path $runDirectory 'jmeter.log'
    $summaryPath = Join-Path $runDirectory 'summary.json'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

    $propertyArguments = @(
        "-Jscenario=$Scenario"
        "-Jdrop_id=$DropId"
        "-Jstock=$Stock"
        "-Jthreads=$threads"
        "-Jloops=$Loops"
        "-Jramp_seconds=$effectiveRampSeconds"
        "-Jsync_group_size=$syncGroupSize"
        "-Jusers_file=$resolvedUsersFile"
        "-Jgateway_host=$GatewayHostName"
        "-Jgateway_port=$GatewayPort"
        "-Jdirect_host=$DirectHostName"
        "-Jdirect_port=$DirectPort"
        "-Jsame_user_id=$SameUserId"
        "-Jsame_user_token=$SameUserToken"
        "-Jlimit_ip=$LimitIp"
        "-Jsummary_file=$summaryPath"
    )

    Write-Host "开始场景：$Scenario"
    Write-Host "线程数：$threads；Drop：$DropId；库存：$Stock"

    if ($Gui) {
        $guiArguments = @('-t', $jmxPath, '-j', $logPath) + $propertyArguments
        Write-Host 'JMeter GUI 将打开。点击工具栏绿色启动按钮后，在左侧选择响应时间曲线或聚合图。'
        & $JMeterBin @guiArguments
        if ($LASTEXITCODE -ne 0) {
            throw "JMeter GUI 退出码为 $LASTEXITCODE，请查看 $logPath"
        }
        if (Test-Path -LiteralPath $summaryPath -PathType Leaf) {
            Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json |
                Select-Object scenario, checks, samples, success, soldout, duplicate,
                    rate_limited, oversell, average_ms, p95_ms | Format-List
        }
        return
    }

    $jmeterArguments = @(
        '-n'
        '-t', $jmxPath
        '-l', $jtlPath
        '-j', $logPath
        '-e'
        '-o', $reportDirectory
    ) + $propertyArguments

    & $JMeterBin @jmeterArguments
    $jmeterExitCode = $LASTEXITCODE

    if ($jmeterExitCode -ne 0) {
        throw "JMeter 退出码为 $jmeterExitCode，请查看 $logPath"
    }
    if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
        throw "未生成 summary.json，请查看 $logPath"
    }

    $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    $summary | Select-Object scenario, checks, samples, success, soldout, duplicate,
        rate_limited, oversell, average_ms, p95_ms | Format-List

    Write-Host "JTL：$jtlPath"
    Write-Host "HTML 报告：$reportDirectory\index.html"
    Write-Host "汇总：$summaryPath"

    if ($OpenReport) {
        Start-Process -FilePath (Join-Path $reportDirectory 'index.html')
    }

    if ($summary.checks -ne 'PASS') {
        $failureText = @($summary.failures) -join '; '
        throw "场景判定失败：$failureText"
    }
}
finally {
    if ($CloseAfter -and $openedByScript) {
        try {
            $closeRequest = @{
                Method  = 'Post'
                Uri     = "$gatewayBaseUrl/api/flashsale/drops/$DropId/close"
                Headers = @{ 'X-Ops-Key' = $OpsKey }
            }
            Invoke-RestMethod @closeRequest | Out-Null
            Write-Host "Drop $DropId 已关闭。"
        }
        catch {
            Write-Warning "关闭 Drop $DropId 失败：$($_.Exception.Message)"
        }
    }
}
