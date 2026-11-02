param([string]$SdkPath=$env:ANDROID_HOME,[string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $SdkPath){$SdkPath=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if(-not $JdkPath){$JdkPath='C:\Program Files\Java\jdk-17'}
$bt=Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$platform=Get-ChildItem -LiteralPath (Join-Path $SdkPath 'platforms') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$androidJar=Join-Path $platform.FullName 'android.jar'
$appRoot=Join-Path $projectRoot 'android\app\src\main'
$buildRoot=Join-Path $projectRoot 'android\build'
$classes=Join-Path $buildRoot 'classes'
$dex=Join-Path $buildRoot 'dex'
$generated=Join-Path $buildRoot 'generated'
New-Item -ItemType Directory -Force -Path $buildRoot,$classes,$dex,$generated,(Join-Path $projectRoot 'dist') | Out-Null
function Run-Checked([string]$program,[string[]]$arguments){& $program @arguments;if($LASTEXITCODE -ne 0){throw "Build step failed: $program"}}
$sdkJar=Join-Path $projectRoot 'android\libs\wearable-classes.jar'
if(-not (Test-Path -LiteralPath $sdkJar)){throw 'Run tools/prepare-sdk.ps1 first.'}
$unsigned=Join-Path $buildRoot 'unsigned.apk'
Run-Checked (Join-Path $bt.FullName 'aapt.exe') @('package','-f','-M',(Join-Path $appRoot 'AndroidManifest.xml'),'-S',(Join-Path $appRoot 'res'),'-A',(Join-Path $appRoot 'assets'),'-I',$androidJar,'-J',$generated,'-F',$unsigned)
$sources=@(Get-ChildItem -LiteralPath (Join-Path $appRoot 'java') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName}) + @(Get-ChildItem -LiteralPath $generated -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
$argsFile=Join-Path $buildRoot 'sources.txt'
$sources | ForEach-Object {'"'+$_.Replace('\','/')+'"'} | Set-Content -LiteralPath $argsFile -Encoding utf8NoBOM
Run-Checked (Join-Path $JdkPath 'bin\javac.exe') @('-encoding','UTF-8','-source','8','-target','8','-classpath',($androidJar+';'+$sdkJar),'-d',$classes,('@'+$argsFile))
$classesJar=Join-Path $buildRoot 'app.jar'
Run-Checked (Join-Path $JdkPath 'bin\jar.exe') @('cf',$classesJar,'-C',$classes,'.')
Run-Checked (Join-Path $bt.FullName 'd8.bat') @('--lib',$androidJar,'--min-api','26','--output',$dex,$classesJar,$sdkJar)
$dexFile=Join-Path $dex 'classes.dex'
Push-Location $dex
try{Run-Checked (Join-Path $bt.FullName 'aapt.exe') @('add',$unsigned,'classes.dex')}finally{Pop-Location}
$aligned=Join-Path $buildRoot 'aligned.apk'
Run-Checked (Join-Path $bt.FullName 'zipalign.exe') @('-f','4',$unsigned,$aligned)
$final=Join-Path $projectRoot 'dist\WristMarkets-Sync-0.3.0.apk'
Run-Checked (Join-Path $bt.FullName 'apksigner.bat') @('sign','--ks',(Join-Path $projectRoot 'signing\sync.p12'),'--ks-pass',('file:'+(Join-Path $projectRoot 'signing\password.txt')),'--ks-key-alias','wristmarkets','--out',$final,$aligned)
Run-Checked (Join-Path $bt.FullName 'apksigner.bat') @('verify','--verbose',$final)
Write-Output $final
