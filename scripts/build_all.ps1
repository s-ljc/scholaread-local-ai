# ============================================================
# ScholarRead Local AI - full build pipeline
#   original APK -> patched + signed APK
# Tested on Windows / PowerShell 7. Edit the paths below first.
# ============================================================
$ErrorActionPreference = 'Stop'
$ROOT  = Split-Path $PSScriptRoot -Parent
$OUT   = Join-Path $ROOT 'out'

# ---- EDIT THESE -------------------------------------------------
$OrigApk   = 'C:\path\to\scholaread_944159.apk'   # your own original APK (1.9.17 / build 944159)
$Apktool   = 'C:\path\to\apktool_2.10.0.jar'
$Signer    = 'C:\path\to\uber-apk-signer-1.3.0.jar'
$D8Jar     = 'C:\path\to\build-tools\lib\d8.jar'
$AndroidJar= 'C:\path\to\platform-34\android.jar'
$RxJava    = 'C:\path\to\rxjava-3.1.8.jar'
$OkHttp    = 'C:\path\to\okhttp-4.12.0.jar'
$Okio      = 'C:\path\to\okio-jvm-3.6.0.jar'
$Jdk       = 'C:\path\to\jdk-17-or-21'            # JAVA_HOME, needs keytool
# ----------------------------------------------------------------

if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory $OUT | Out-Null
$Work = Join-Path $OUT 'work'
$Java = Join-Path $OUT 'java'

Write-Host '== 1. apktool decode =='
java -jar $Apktool d -r -f -o $Work $OrigApk

Write-Host '== 2. apply patches (from apktool tree) =='
Push-Location $Work
git apply --verbose (Join-Path $ROOT 'patches\smali\AIChatDialogFragment.smali.patch')
git apply --verbose (Join-Path $ROOT 'patches\smali\c.smali.patch')
git apply --verbose (Join-Path $ROOT 'patches\smali\ChatStudySessionView.smali.patch')
git apply --verbose (Join-Path $ROOT 'patches\smali\SettingsActivity.smali.patch')
git apply --verbose (Join-Path $ROOT 'patches\translate.js.patch')
New-Item -ItemType Directory -Force "$Work\smali_classes2\com\scholaread\aicfg" | Out-Null
Copy-Item (Join-Path $ROOT 'patches\smali\AiCfgHook.smali') "$Work\smali_classes2\com\scholaread\aicfg\AiCfgHook.smali"
Pop-Location

Write-Host '== 3. apktool build =='
$Unsigned = Join-Path $OUT 'unsigned.apk'
java -jar $Apktool b $Work -o $Unsigned

Write-Host '== 4. javac + d8 (our classes -> classes5.dex) =='
New-Item -ItemType Directory "$Java\classes" | Out-Null
$cp = "$AndroidJar;$RxJava;$OkHttp;$Okio"
$srcs = @()
$srcs += Get-ChildItem (Join-Path $ROOT 'src')  -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$srcs += Get-ChildItem (Join-Path $ROOT 'stubs') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$Jdk\bin\javac.exe" --release 8 -encoding UTF-8 -nowarn -cp $cp -d "$Java\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
New-Item -ItemType Directory "$Java\dexout" | Out-Null
$cls = Get-ChildItem "$Java\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
$argfile = "$Java\d8args.txt"
@('--lib', $AndroidJar, '--release', '--min-api', '24', '--output', "$Java\dexout", '--classpath', $RxJava, '--classpath', $OkHttp, '--classpath', $Okio) + $cls | Set-Content $argfile
& "$Jdk\bin\java.exe" -cp $D8Jar com.android.tools.r8.D8 "@$argfile"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== 5. repack (restore res, inject classes5.dex) =='
python (Join-Path $ROOT 'scripts\fix_res.py') $OrigApk $Unsigned "$Java\dexout\classes.dex" (Join-Path $OUT 'repacked.apk')

Write-Host '== 6. sign (debug keystore, auto-created) =='
$Ks = Join-Path $OUT 'localai.keystore'
if (-not (Test-Path $Ks)) {
  & "$Jdk\bin\keytool.exe" -genkeypair -keystore $Ks -storepass android -keypass android `
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
    -dname 'CN=LocalAI,OU=,O=,L=,S=,C=US' | Out-Null
}
java -jar $Signer --apks (Join-Path $OUT 'repacked.apk') --overwrite `
  --ks $Ks --ksAlias androiddebugkey --ksPass android --ksKeyPass android
Write-Host "DONE: $(Join-Path $OUT 'repacked-debug-signed.apk')"
Write-Host 'NOTE: debug signature != official signature. Uninstall the official app first (data will be lost).'
