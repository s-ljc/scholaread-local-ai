# Fast iteration: rebuild classes5.dex from src/ (Java-only changes), inject it
# into an already-patched APK, re-sign. Full first-time build: build_all.ps1.
# Edit the paths below, then:  .\scripts\build_dex.ps1
$ErrorActionPreference = 'Continue'
$env:PYTHONIOENCODING = 'utf-8'

# ---- EDIT THESE -------------------------------------------------
$Jdk         = 'C:\path\to\jdk-17-or-21'           # JAVA_HOME (javac/java)
$T           = 'C:\path\to\tools'                  # dir holding the jars below
$BaseApk     = 'C:\path\to\repacked.apk'           # patched, pre-sign APK from build_all
$Tmp         = 'C:\path\to\empty-tmp-dir'          # writable tmp for javac/d8
# jars expected under $T: platform-34\android.jar, build-tools36\lib\d8.jar,
#   rxjava-3.1.8.jar, okhttp-4.12.0.jar, okio-jvm-3.6.0.jar,
#   uber-apk-signer-1.3.0.jar, and a keystore (see below)
$Ks          = "$T\localai.keystore"               # same keystore as build_all
# ----------------------------------------------------------------
$ROOT = Split-Path $PSScriptRoot -Parent
$Java = Join-Path $ROOT 'out\java'
$env:JAVA_HOME = $Jdk
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$RxJava = "$T\rxjava-3.1.8.jar"; $OkHttp = "$T\okhttp-4.12.0.jar"; $Okio = "$T\okio-jvm-3.6.0.jar"

Remove-Item "$Java\classes" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$Java\classes" | Out-Null
$cp = "$T\platform-34\android.jar;$RxJava;$OkHttp;$Okio;$Java\stubclasses"
$srcs = @()
$srcs += Get-ChildItem (Join-Path $ROOT 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$srcs += Get-ChildItem (Join-Path $ROOT 'stubs') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "== javac =="
& "$env:JAVA_HOME\bin\javac.exe" --release 8 -encoding UTF-8 -nowarn -cp $cp -d "$Java\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Remove-Item "$Java\dexout" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$Java\dexout" | Out-Null
$cls = Get-ChildItem "$Java\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
$argfile = "$Java\d8args.txt"
@('--lib', "$T\platform-34\android.jar", '--release', '--min-api', '24', '--output', "$Java\dexout", '--classpath', $RxJava, '--classpath', $OkHttp, '--classpath', $Okio) + $cls | Set-Content $argfile
Write-Host "== d8 =="
& "$env:JAVA_HOME\bin\java.exe" -cp "$T\build-tools36\lib\d8.jar" com.android.tools.r8.D8 "@$argfile"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host "== repack =="
python (Join-Path $ROOT 'scripts\fix_res.py') $BaseApk $BaseApk "$Java\dexout\classes.dex" (Join-Path $ROOT 'out\repacked.apk')

Write-Host "== sign =="
java -jar "$T\uber-apk-signer-1.3.0.jar" --apks (Join-Path $ROOT 'out\repacked.apk') --overwrite `
  --ks $Ks --ksAlias androiddebugkey --ksPass android --ksKeyPass android
Write-Host "DONE: $(Join-Path $ROOT 'out\repacked-debug-signed.apk')"
