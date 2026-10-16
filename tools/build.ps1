[CmdletBinding()]
param([string]$JavaHome='', [string]$SdkRoot='', [switch]$Offline, [switch]$SkipLint)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $JavaHome) {
        foreach ($candidate in @($env:JAVA_HOME,'D:\Java\jdk-21.0.12.1+1',"$env:ProgramFiles\Android\Android Studio\jbr")) {
            if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe'))) { $JavaHome=$candidate; break }
        }
    }
    if (-not $JavaHome) { throw 'Set -JavaHome to a JDK 17 or newer directory.' }
    if (-not $SdkRoot) {
        foreach ($candidate in @($env:ANDROID_HOME,$env:ANDROID_SDK_ROOT,"$env:LOCALAPPDATA\Android\Sdk")) {
            if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platforms\android-35\android.jar'))) { $SdkRoot=$candidate; break }
        }
    }
    if (-not $SdkRoot) { throw 'Install Android SDK Platform 35, then pass -SdkRoot.' }
    $env:JAVA_HOME=$JavaHome
    $env:GRADLE_USER_HOME=Join-Path $projectRoot '.tools\gradle-home'
    New-Item -ItemType Directory -Force '.tools','dist' | Out-Null
    $sdkProperty=$SdkRoot.Replace('\','/').Replace(':','\:')
    [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'),"sdk.dir=$sdkProperty`n",[Text.UTF8Encoding]::new($false))
    if (-not (Test-Path -LiteralPath '.tools\debug.keystore')) {
        & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -keystore '.tools\debug.keystore' -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Q07 Mirror Test,O=Local Development,C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Could not create local development signing key.' }
    }
    $buildArgs=@(':shared:test',':phone:assembleDebug',':car:assembleDebug','--console=plain')
    if (-not $SkipLint) { $buildArgs+=@(':phone:lintDebug',':car:lintQ07Debug',':car:lintStandardDebug') }
    if ($Offline) { $buildArgs+='--offline' }
    & '.\gradlew.bat' @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Build/test failed. APKs have not been repackaged.' }
    Copy-Item -LiteralPath 'phone\build\outputs\apk\debug\phone-debug.apk' -Destination 'dist\q07-mirror-phone.apk'
    Copy-Item -LiteralPath 'car\build\outputs\apk\q07\debug\car-q07-debug.apk' -Destination 'dist\q07-mirror-car-q07.apk'
    Copy-Item -LiteralPath 'car\build\outputs\apk\standard\debug\car-standard-debug.apk' -Destination 'dist\q07-mirror-car-standard.apk'
    Copy-Item -LiteralPath 'docs\TESTING.md' -Destination 'dist\TESTING.md'
    Copy-Item -LiteralPath 'vendor\scrcpy-LICENSE','vendor\shizuku-LICENSE' -Destination 'dist'
    $hashes=Get-ChildItem -LiteralPath 'dist' -Filter '*.apk' | Get-FileHash -Algorithm SHA256 | ForEach-Object { $_.Hash.ToLower()+'  '+[IO.Path]::GetFileName($_.Path) }
    $hashes | Set-Content -LiteralPath 'dist\SHA256SUMS.txt' -Encoding ascii
    Write-Host 'Build complete: dist\ contains phone, Q07 car, and standard Android test APKs.' -ForegroundColor Green
} finally { Pop-Location }
