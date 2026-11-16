$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Find-AndroidSdk {
    $Candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path $_) }
    return $Candidates | Select-Object -First 1
}

$Sdk = Find-AndroidSdk
if (-not $Sdk) {
    Write-Error 'Android SDK not found. Install Android Studio and Android SDK Platform 35, then run this script again.'
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $StudioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $StudioJava) { $env:JAVA_HOME = $StudioJava; $env:PATH = "$StudioJava\bin;$env:PATH" }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error 'JDK 17+ not found. Install JDK 17 or use Android Studio bundled JBR.'
}

$GradleVersion = '8.9'
$CacheRoot = Join-Path $env:LOCALAPPDATA 'TikTokCompanion\gradle'
$GradleHome = Join-Path $CacheRoot "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"
if (-not (Test-Path $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
    $Zip = Join-Path $CacheRoot "gradle-$GradleVersion-bin.zip"
    $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Downloading Gradle $GradleVersion..."
    Invoke-WebRequest -Uri $Url -OutFile $Zip
    Expand-Archive -Path $Zip -DestinationPath $CacheRoot -Force
}

Push-Location $ProjectRoot
try {
    if (-not (Test-Path '.\gradlew.bat')) {
        Write-Host 'Generating the standard Gradle Wrapper...'
        & $GradleExe wrapper --gradle-version $GradleVersion --distribution-type bin
        if ($LASTEXITCODE -ne 0) { throw 'Gradle Wrapper generation failed.' }
    }
    & '.\gradlew.bat' :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed. Check the Gradle output above.' }
    Write-Host ''
    Write-Host 'Build succeeded:' -ForegroundColor Green
    Write-Host (Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk')
}
finally {
    Pop-Location
}
