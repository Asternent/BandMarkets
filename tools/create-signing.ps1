param([string]$JdkPath='C:\Program Files\Java\jdk-17')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$keyRoot=Join-Path $projectRoot 'signing'
if(Test-Path -LiteralPath (Join-Path $keyRoot 'sync.p12')){throw 'A signing key already exists. Keep it to preserve app update compatibility.'}
New-Item -ItemType Directory -Force -Path $keyRoot | Out-Null
$password=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
[IO.File]::WriteAllText((Join-Path $keyRoot 'password.txt'),$password)
& (Join-Path $JdkPath 'bin\keytool.exe') -genkeypair -alias wristmarkets -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -keystore (Join-Path $keyRoot 'sync.p12') -storepass $password -keypass $password -dname 'CN=BandMarkets Personal,O=Personal,C=TW'
if($LASTEXITCODE -ne 0){throw 'Key generation failed'}
& openssl pkcs12 -in (Join-Path $keyRoot 'sync.p12') -passin ('file:'+(Join-Path $keyRoot 'password.txt')) -nocerts -nodes -out (Join-Path $keyRoot 'private.pem')
if($LASTEXITCODE -ne 0){throw 'Private key export failed'}
& openssl pkcs12 -in (Join-Path $keyRoot 'sync.p12') -passin ('file:'+(Join-Path $keyRoot 'password.txt')) -clcerts -nokeys -out (Join-Path $keyRoot 'certificate.pem')
if($LASTEXITCODE -ne 0){throw 'Certificate export failed'}
foreach($mode in @('debug','release')){
  $destination=Join-Path $projectRoot "band\sign\$mode"
  New-Item -ItemType Directory -Force -Path $destination | Out-Null
  Copy-Item -LiteralPath (Join-Path $keyRoot 'private.pem'),(Join-Path $keyRoot 'certificate.pem') -Destination $destination
}
Write-Output 'New personal key created. Keep signing/ private and backed up.'
