[CmdletBinding()]
param(
    [ValidateRange(1, 250)]
    [int]$Count = 100,

    [string]$GatewayBaseUrl = 'http://localhost:8080',

    [ValidatePattern('^[a-zA-Z0-9_]+$')]
    [string]$Prefix = 'loadtest',

    [string]$Password,

    [string]$OutputPath = (Join-Path $PSScriptRoot 'users.csv')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Password)) {
    $securePassword = Read-Host '请输入压测账号密码（不会写入 CSV）' -AsSecureString
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $Password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
    }
}

if ($Password.Length -lt 6 -or $Password.Length -gt 64) {
    throw '密码长度必须为 6 到 64 个字符。'
}

$baseUri = $GatewayBaseUrl.TrimEnd('/')
$records = [System.Collections.Generic.List[object]]::new()

for ($index = 1; $index -le $Count; $index++) {
    $username = "${Prefix}_{0:D4}" -f $index
    $payload = @{ username = $username; password = $Password } | ConvertTo-Json -Compress
    $request = @{
        Method      = 'Post'
        ContentType = 'application/json'
        Body        = $payload
    }
    $auth = $null

    try {
        $request.Uri = "$baseUri/api/user/auth/register"
        $auth = Invoke-RestMethod @request
    }
    catch {
        try {
            $request.Uri = "$baseUri/api/user/auth/login"
            $auth = Invoke-RestMethod @request
        }
        catch {
            throw "账号 $username 注册/登录失败：$($_.Exception.Message)"
        }
    }

    if ($auth.code -ne 0 -or [string]::IsNullOrWhiteSpace([string]$auth.data.token)) {
        throw "账号 $username 未返回有效 JWT。"
    }

    $records.Add([pscustomobject]@{
        userId   = [long]$auth.data.userId
        token    = [string]$auth.data.token
        username = $username
        clientIp = "198.51.100.$index"
    })

    if ($index % 10 -eq 0 -or $index -eq $Count) {
        Write-Host "已准备 $index / $Count 个账号"
    }
}

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$records | Export-Csv -LiteralPath $resolvedOutput -NoTypeInformation -Encoding utf8
$Password = $null

Write-Host "用户 CSV 已生成：$resolvedOutput"
Write-Host 'CSV 包含 JWT，已由 .gitignore 排除，请勿提交。'
