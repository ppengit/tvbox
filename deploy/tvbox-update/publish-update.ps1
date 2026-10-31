<#
.SYNOPSIS
把最新 APK 发布到更新目录，并生成 TV 端读取的 update.json。

.DESCRIPTION
只写入本地更新目录，不会上传 GitHub，也不会重启容器。默认目录是
D:\Portable\TvBoxUpdate，可用 -Root 覆盖。APK 文件名带版本号，旧版本保留，
方便回滚；update.json 以原子方式替换，避免客户端读到半个文件。

.EXAMPLE
.\publish-update.ps1 -Apk .\app\build\outputs\apk\release\app-release.apk `
  -VersionCode 10400 -VersionName 1.4.0 `
  -Changelog @("电视端同步桌面体验", "切换热更新服务器")
#>
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [string[]]$Changelog = @(),
    [string]$Root = "D:\Portable\TvBoxUpdate",
    [string]$BaseUrl = "https://tvbox.pfac.fun:8443"
)

$ErrorActionPreference = "Stop"
$apkFile = Resolve-Path -LiteralPath $Apk
$apiDir = Join-Path $Root "api"
New-Item -ItemType Directory -Force -Path $apiDir | Out-Null

$safeVersion = $VersionName -replace '[^A-Za-z0-9._-]', '_'
$fileName = "TVBox-v$safeVersion.apk"
$target = Join-Path $apiDir $fileName
Copy-Item -LiteralPath $apkFile -Destination $target -Force

$hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $target).Length

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = "$BaseUrl/api/$fileName"
    apkSha256 = $hash
    apkSize = $size
    force = $false
    changelog = @($Changelog)
}
$json = $manifest | ConvertTo-Json -Depth 6
$temp = Join-Path $apiDir "update.json.tmp"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($temp, $json, $utf8NoBom)
Move-Item -LiteralPath $temp -Destination (Join-Path $apiDir "update.json") -Force

Write-Host "已发布 $fileName"
Write-Host "SHA-256: $hash"
Write-Host "大小: $size"
Write-Host "清单: $(Join-Path $apiDir 'update.json')"
