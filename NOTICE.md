# Third-party components and data

The PolyForm Noncommercial license at the root of this repository applies to
BandMarkets' original source code and documentation. It does not relicense
third-party components or market data.

- **Xiaomi XMS Wearable SDK 1.4:** obtained from Xiaomi's official interconnect
  demonstration package. The SDK binary is excluded from this repository.
  `tools/prepare-sdk.ps1` downloads it from Xiaomi for local builds. Its use and
  redistribution are governed by Xiaomi's applicable terms.
- **Xiaomi aiot-toolkit 2.0.5 and npm dependencies:** build-time tools retain
  their respective licenses. Exact dependencies are recorded in
  `band/package-lock.json`.
- **JSON-java:** used only by JVM tests, downloaded from Maven Central; its
  license is available at https://github.com/stleary/JSON-java/blob/master/LICENSE.
- **Yahoo Finance:** quotation, search, and chart data are fetched from public
  endpoints by the phone. The endpoints are unofficial developer interfaces;
  availability, delay, and permitted use depend on the data provider's terms.
  The project license does not grant market-data redistribution or commercial
  data-use rights.

Private signing certificates, passwords, device identifiers, SDK binaries,
captured device logs, and raw live-response fixtures are excluded from the
public repository.
