param(
    [switch]$Test,
    [switch]$DeviceTest,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$Proxy = ''
)
$ErrorActionPreference = 'Stop'
if ($JavaHome -and -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome 指向的 JDK 无效' }
if (-not $JavaHome -and -not (Get-Command java -ErrorAction SilentlyContinue)) { throw '请配置 JAVA_HOME 或使用 Android Studio 构建' }
if ($AndroidSdk -and -not (Test-Path -LiteralPath $AndroidSdk)) { throw 'AndroidSdk 指向的 SDK 无效' }
if (-not $AndroidSdk -and -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'local.properties'))) { throw '请配置 ANDROID_HOME / ANDROID_SDK_ROOT，或先用 Android Studio 打开项目' }
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
Push-Location $PSScriptRoot
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($AndroidSdk) { $env:ANDROID_HOME = $AndroidSdk }
    $tasks = @('assembleDebug', 'assembleRelease')
    if ($Test) { $tasks += 'testDebugUnitTest' }
    if ($DeviceTest) { $tasks += 'assembleDebugAndroidTest' }
    $options = @('--console=plain')
    if ($Proxy) {
        $proxyUri = [Uri]$Proxy
        if ($proxyUri.Scheme -notin @('http','https') -or -not $proxyUri.Host) { throw '代理应为有效 HTTP / HTTPS 地址' }
        $options += "-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)", "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)"
    }
    & .\gradlew.bat @tasks @options
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败，退出码 $LASTEXITCODE" }
    if ($DeviceTest) {
        if (-not $AndroidSdk) { throw '设备测试需要显式配置 AndroidSdk 或 ANDROID_HOME' }
        $adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
        & $adb install -r "$PSScriptRoot/app/build/outputs/apk/debug/app-debug.apk"
        if ($LASTEXITCODE -ne 0) { throw '测试应用覆盖安装失败' }
        & $adb install -r -t "$PSScriptRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        if ($LASTEXITCODE -ne 0) { throw '设备测试包安装失败' }
        $deviceResult = & $adb shell am instrument -w -r cn.miao.parttime.test/androidx.test.runner.AndroidJUnitRunner
        $deviceResult | Write-Output
        if (($deviceResult -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw '设备测试未全部通过' }
    }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
    Pop-Location
}
