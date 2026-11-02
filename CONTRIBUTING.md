# Contributing

WristMarkets accepts contributions under the same PolyForm Noncommercial
License 1.0.0 used by the project. Keep third-party code under its own license
and document its provenance in NOTICE.md.

Before submitting a change, run:

```powershell
npm --prefix band test
./tools/test-core.ps1 -JdkPath $env:JAVA_HOME
```

Run live checks only when working on the market-data interface. Keep tests
deterministic with synthetic data whenever possible. Describe device/firmware
testing separately from build and unit-test results.

Never commit signing files, passwords, service credentials, device identifiers,
health data, private watchlists, or device logs. Do not place SDK binaries or
captured market-data responses in the repository.
