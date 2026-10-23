# Optional fallback for hosts where Java cannot download Maven artifacts.
# Downloads original Google Maven publications into an ignored local repository.
$ErrorActionPreference = 'Stop'
$mirror = Join-Path (Split-Path -Parent $PSScriptRoot) '.local\maven'
$seen = @{}
function Fetch-Module([string]$artifact) {
    if ($seen.ContainsKey($artifact)) { return }
    $seen[$artifact] = $true
    $relative = "androidx/datastore/$artifact/1.1.4"
    $folder = Join-Path $mirror $relative
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    $base = "https://dl.google.com/dl/android/maven2/$relative"
    foreach ($extension in @('pom','module')) {
        $filename = "$artifact-1.1.4.$extension"
        $path = Join-Path $folder $filename
        if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest -Uri "$base/$filename" -OutFile $path -TimeoutSec 30 }
    }
    $module = Get-Content (Join-Path $folder "$artifact-1.1.4.module") -Raw | ConvertFrom-Json
    foreach ($variant in $module.variants) {
        if ($variant.attributes.'org.gradle.category' -eq 'documentation') { continue }
        $platform = $variant.attributes.'org.jetbrains.kotlin.platform.type'
        if ($platform -and $platform -notin @('androidJvm','jvm','common')) { continue }
        foreach ($entry in $variant.files) {
            $target = Join-Path $folder (Split-Path -Leaf $entry.url)
            if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri "$base/$($entry.url)" -OutFile $target -TimeoutSec 30 }
            if ($entry.sha256 -and (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw "Checksum failed: $target" }
        }
        if ($variant.'available-at'.group -eq 'androidx.datastore') { Fetch-Module $variant.'available-at'.module }
        foreach ($dependency in $variant.dependencies) {
            if ($dependency.group -eq 'androidx.datastore') { Fetch-Module $dependency.module }
        }
    }
    Write-Output "Cached $artifact"
}
Fetch-Module 'datastore-preferences'
foreach ($runtime in @('kotlin-parcelize-runtime','kotlin-android-extensions-runtime')) {
$parcelizePath = "org/jetbrains/kotlin/$runtime/1.9.22"
$parcelizeFolder = Join-Path $mirror $parcelizePath
New-Item -ItemType Directory -Force -Path $parcelizeFolder | Out-Null
foreach ($extension in @('pom','jar')) {
    $filename = "$runtime-1.9.22.$extension"
    $target = Join-Path $parcelizeFolder $filename
    if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/$parcelizePath/$filename" -OutFile $target -TimeoutSec 30 }
}
}
