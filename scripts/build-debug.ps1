param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$StudioRoot = "C:\Program Files\Android\Android Studio"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$AppRoot = Join-Path $ProjectRoot "app\src\main"
$BuildRoot = Join-Path $ProjectRoot "build"
$GenRoot = Join-Path $BuildRoot "generated"
$ClassRoot = Join-Path $BuildRoot "classes"
$DexRoot = Join-Path $BuildRoot "dex"
$ClassesJar = Join-Path $BuildRoot "classes.jar"
$UnsignedApk = Join-Path $BuildRoot "personal-sop-unsigned.apk"
$AlignedApk = Join-Path $BuildRoot "personal-sop-aligned.apk"
$FinalApk = Join-Path $BuildRoot "personal-sop-debug.apk"
$KeystoreRoot = Join-Path $ProjectRoot ".keystore"
$DebugKeystore = Join-Path $KeystoreRoot "debug.keystore"

$JavaHome = Join-Path $StudioRoot "jbr"
$Java = Join-Path $JavaHome "bin\java.exe"
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"
$Keytool = Join-Path $JavaHome "bin\keytool.exe"
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome "bin") + ";" + $env:PATH

function Assert-Success([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

$Platform = Get-ChildItem -LiteralPath (Join-Path $SdkRoot "platforms") -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1
$BuildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot "build-tools") -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $Platform) { throw "No Android platform found under $SdkRoot\platforms" }
if (-not $BuildTools) { throw "No Android build-tools found under $SdkRoot\build-tools" }

$AndroidJar = Join-Path $Platform.FullName "android.jar"
$Aapt = Join-Path $BuildTools.FullName "aapt.exe"
$D8 = Join-Path $BuildTools.FullName "d8.bat"
$Zipalign = Join-Path $BuildTools.FullName "zipalign.exe"
$Apksigner = Join-Path $BuildTools.FullName "apksigner.bat"

Remove-Item -LiteralPath $BuildRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $GenRoot, $ClassRoot, $DexRoot, $KeystoreRoot | Out-Null

& $Aapt package -f -m `
    -J $GenRoot `
    -M (Join-Path $AppRoot "AndroidManifest.xml") `
    -S (Join-Path $AppRoot "res") `
    -I $AndroidJar
Assert-Success "Generate resources"

$Sources = @()
$Sources += Get-ChildItem -LiteralPath (Join-Path $AppRoot "java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$Sources += Get-ChildItem -LiteralPath $GenRoot -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

& $Javac `
    -encoding UTF-8 `
    -source 8 `
    -target 8 `
    -bootclasspath $AndroidJar `
    -classpath $AndroidJar `
    -d $ClassRoot `
    $Sources
Assert-Success "Compile Java"

Push-Location $ClassRoot
try {
    & $Jar cf $ClassesJar .
    Assert-Success "Create classes jar"
} finally {
    Pop-Location
}

& $D8 `
    --lib $AndroidJar `
    --min-api 26 `
    --output $DexRoot `
    $ClassesJar
Assert-Success "Build dex"

& $Aapt package -f `
    -M (Join-Path $AppRoot "AndroidManifest.xml") `
    -S (Join-Path $AppRoot "res") `
    -I $AndroidJar `
    -F $UnsignedApk
Assert-Success "Package unsigned APK"

Push-Location $DexRoot
try {
    & $Aapt add $UnsignedApk "classes.dex"
    Assert-Success "Add dex to APK"
} finally {
    Pop-Location
}

& $Zipalign -f -p 4 $UnsignedApk $AlignedApk
Assert-Success "Zipalign APK"

if (-not (Test-Path -LiteralPath $DebugKeystore)) {
    & $Keytool -genkeypair `
        -keystore $DebugKeystore `
        -storepass android `
        -keypass android `
        -alias androiddebugkey `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    Assert-Success "Create debug keystore"
}

& $Apksigner sign `
    --ks $DebugKeystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $FinalApk `
    $AlignedApk
Assert-Success "Sign APK"

& $Apksigner verify --verbose $FinalApk
Assert-Success "Verify APK"

Write-Output $FinalApk
