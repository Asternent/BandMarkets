$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$vendorRoot=Join-Path $projectRoot 'vendor'
New-Item -ItemType Directory -Force -Path $vendorRoot,(Join-Path $projectRoot 'android\libs') | Out-Null
$download=Join-Path $vendorRoot 'xiaomi-demo.zip'
$demo=Join-Path $vendorRoot 'demo'
Invoke-WebRequest -Uri 'https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip' -OutFile $download
Expand-Archive -LiteralPath $download -DestinationPath $demo -Force
$aar=Get-ChildItem -LiteralPath $demo -Filter 'xms-wearable-lib_1.4_release.aar' -Recurse | Select-Object -First 1
Copy-Item -LiteralPath $aar.FullName -Destination (Join-Path $vendorRoot 'sdk.zip')
Expand-Archive -LiteralPath (Join-Path $vendorRoot 'sdk.zip') -DestinationPath (Join-Path $vendorRoot 'sdk') -Force
Copy-Item -LiteralPath (Join-Path $vendorRoot 'sdk\classes.jar') -Destination (Join-Path $projectRoot 'android\libs\wearable-classes.jar')
Write-Output 'Official Xiaomi interconnect SDK is ready.'
