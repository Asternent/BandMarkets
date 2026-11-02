param([switch]$Live,[string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $JdkPath){$JdkPath='C:\Program Files\Java\jdk-17'}
Push-Location $projectRoot
try {
  & npm.cmd --prefix band test
  if($LASTEXITCODE){throw 'Band tests failed'}
  & ./tools/test-core.ps1 -JdkPath $JdkPath
  if($Live){
    & node tools/live-check.cjs
    if($LASTEXITCODE){throw 'A live source failed'}
    & (Join-Path $JdkPath 'bin/javac.exe') -encoding UTF-8 -cp ('test/build'+[IO.Path]::PathSeparator+'vendor/json.jar') -d test/build test/MarketParserTest.java
    if($LASTEXITCODE){throw 'Live parser compilation failed'}
    & (Join-Path $JdkPath 'bin/java.exe') -cp ('test/build'+[IO.Path]::PathSeparator+'vendor/json.jar') MarketParserTest test/live-results.json test/ui-state.json
    if($LASTEXITCODE){throw 'Live parser validation failed'}
    & (Join-Path $JdkPath 'bin/javac.exe') -encoding UTF-8 -cp ('test/build'+[IO.Path]::PathSeparator+'vendor/json.jar') -d test/build test/LiveRepositoryTest.java
    if($LASTEXITCODE){throw 'Live repository compilation failed'}
    & (Join-Path $JdkPath 'bin/java.exe') -cp ('test/build'+[IO.Path]::PathSeparator+'vendor/json.jar') LiveRepositoryTest
    if($LASTEXITCODE){throw 'Live repository flow failed'}
  }
} finally {Pop-Location}
