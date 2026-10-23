param([string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.local\android-user'
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.local') | Out-Null
$debugKey = Join-Path $projectRoot '.local\debug.keystore'
if (!(Test-Path -LiteralPath $debugKey)) {
    $keytoolCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
    if (!(Test-Path -LiteralPath $keytoolCommand -PathType Leaf)) { throw 'JDK keytool was not found. Set JAVA_HOME to your JDK installation.' }
    & $keytoolCommand -genkeypair -keystore $debugKey -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create debug signing key.' }
}
$gradleCommand = Join-Path $projectRoot 'gradlew.bat'
if ($env:XINGSILU_GRADLE_HOME) {
    $gradleCommand = Join-Path $env:XINGSILU_GRADLE_HOME 'bin\gradle.bat'
    if (!(Test-Path -LiteralPath $gradleCommand -PathType Leaf)) { throw 'XINGSILU_GRADLE_HOME must point to a Gradle installation containing bin/gradle.bat.' }
}
Push-Location $projectRoot
try {
    & $gradleCommand @Tasks '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
} finally { Pop-Location }
