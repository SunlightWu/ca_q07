[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Serial, [string]$Adb='', [switch]$UpdateOurApp)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if (-not $Adb) { $Adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path -LiteralPath $Adb)) { throw 'Pass -Adb with your adb.exe path.' }
$apk=Join-Path $projectRoot 'dist\q07-mirror-car-q07.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Run tools\build.ps1 first.' }
$package='io.car07.mirror'
if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') { & $Adb connect $Serial }
$existing=& $Adb -s $Serial shell pm path $package 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot query the selected car. Check ADB authorization and serial.' }
$remotePaths=@($existing | Where-Object { "$_" -like 'package:*' })
if ($remotePaths.Count -gt 0) {
    if (-not $UpdateOurApp) { throw "Package $package is already installed. Nothing changed. For an update of THIS test app only, use -UpdateOurApp (certificate will be checked)." }
    $sdkRoot=Split-Path (Split-Path $Adb -Parent) -Parent
    $signer=Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Filter apksigner.bat -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $signer) { throw 'apksigner is required to verify an existing package before updating.' }
    $remote=([string]$remotePaths[0]).Substring(8).Trim()
    $local=Join-Path $projectRoot '.tools\installed-car.apk'
    & $Adb -s $Serial pull $remote $local
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read installed APK; refusing update.' }
    $oldCert=& $signer.FullName verify --print-certs $local
    if ($LASTEXITCODE -ne 0) { throw 'Installed APK verification failed.' }
    $newCert=& $signer.FullName verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'New APK verification failed.' }
    $oldDigest=@($oldCert | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' })
    $newDigest=@($newCert | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' })
    if ($oldDigest.Count -ne 1 -or $newDigest.Count -ne 1 -or $oldDigest[0] -ne $newDigest[0]) { throw 'Existing app has a different signing certificate. Nothing changed; refusing to overwrite.' }
}
& $Adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Do not uninstall existing vehicle apps. See docs\TESTING.md.' }
& $Adb -s $Serial shell appops set $package SYSTEM_ALERT_WINDOW allow
& $Adb -s $Serial shell am start -n "$package/io.github.q07mirror.car.MainActivity"
Write-Host 'Installed test mirror app. The original de.dsa.ford.otaflash project was not changed.'
