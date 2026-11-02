param([string]$JdkPath=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $JdkPath){$JdkPath='C:\Program Files\Java\jdk-17'}
Push-Location $projectRoot
try {
  New-Item -ItemType Directory -Force -Path vendor,test/build | Out-Null
  if(-not (Test-Path vendor/json.jar)){Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar' -OutFile vendor/json.jar}
  $sources=@('test/stubs/android/content/Context.java','test/stubs/android/content/SharedPreferences.java','android/app/src/main/java/com/wristmarkets/sync/MarketRepository.java','test/ExtendedMarketTest.java','test/RepositoryStoreTest.java')
  & (Join-Path $JdkPath 'bin/javac.exe') -encoding UTF-8 -cp vendor/json.jar -d test/build @sources
  if($LASTEXITCODE){throw 'Core test compilation failed'}
  foreach($testClass in @('ExtendedMarketTest','RepositoryStoreTest')){
    & (Join-Path $JdkPath 'bin/java.exe') -cp ('test/build'+[IO.Path]::PathSeparator+'vendor/json.jar') $testClass
    if($LASTEXITCODE){throw "$testClass failed"}
  }
} finally {Pop-Location}
