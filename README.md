# BandMarkets · 腕上股市

中國版 Xiaomi Band 10 Pro 的全球股市與自選股票工具，搭配原生 Android 同步器。**目前版本：0.3.1。**

原始碼依 **PolyForm Noncommercial License 1.0.0** 公開，僅授權協議所定義的非商業用途。可以閱讀、修改與分享；商業使用未獲授權。這屬於 source-available，不是 OSI 定義的開放原始碼授權。詳見 [LICENSE](LICENSE) 與 [第三方聲明](NOTICE.md)。

## 功能

- 預設 20 個主要市場指數，涵蓋台灣、美國、中國、香港、日本、韓國、印度、澳洲、新加坡、歐洲、加拿大、巴西與南非。
- 在手機以公司名稱或股票代碼搜尋、驗證並加入股票／ETF／指數／基金；也可直接輸入 `AAPL`、`2330.TW`、`0700.HK` 等完整代碼。
- 最多關注 40 個項目，選擇要同步到手環的項目，並可移除自選股票。
- 雙端詳情頁顯示近五個交易日的**折線圖**、逐日收盤／暫定報價、開盤、前收、當日高低、成交量、52 週高低與報價時間。
- 手機採用原生 Android Views，跟隨系統淺色／深色；手環使用 Vela 原生元件與 Material 深色風格。
- 手動更新、Android 背景排程、雙向請求、離線快取、完整資料確認與失敗重試。

## 安裝與升級

1. 在 Android 8 或以上安裝 `BandMarkets-Sync-0.3.1.apk`。
2. 用已設定好的 AstroBox 或相容工具安裝 `BandMarkets-Band-0.3.1.rpk`。同步器「手環同步 → 安裝手環端程式」也內含同一份 RPK。
3. 安裝後退出 AstroBox，讓**中國版小米運動健康**重新連上手環。
4. 在手環開啟「腕上股市」。手機點「連接並傳送現有行情」，首次使用再點「授權手環通訊」，接著「更新行情並同步」。
5. 顯示「手環已確認收到並儲存行情」才代表同步完成；僅顯示行情取得成功，代表手機已下載資料。

**0.3.1 使用同步協議 v2，請同時更新 APK 與 RPK。** 舊快取可以讀取，但不會把舊版的單日樣本誤標成五日圖；手機升級後會自動重新取得舊格式的行情。

兩端使用相同包名 `com.wristmarkets.sync` 與相同簽章。不同版本的小米運動健康或韌體可能仍需要開發者接入條件。若出現權限錯誤，先完成「授權手環通訊」；不要只反覆重傳。

## 搜尋、選取與移除

手機標題列點「加股票」→ 輸入名稱或代碼 → 搜尋 →「加入並同步」。搜尋服務暫時不可用時，可改用完整代碼直接加入；程式會先驗證標的與行情，不會直接接受無效代碼。

「設定」可控制同步勾選、紅漲綠跌或綠漲紅跌，以及移除自選項目。至少保留一個選取項目。手環的市場選單包含「自選股票」分類。

## 行情與圖表的意義

- 資料由手機直接向 Yahoo Finance 公開端點取得；這不是官方開發者 API，可能延遲、限流、變更或中斷，不提供商業行情使用權。
- 五日指**資料來源可提供的近五個交易日**。休市日、缺值不補 0、不插入假交易日；可用天數不足時如實顯示。
- 最後一點可能是交易日尚未收盤的暫定價格，逐日表以 `*` 標示。每日價格以交易所時區分日，處理週末與夏令時間。
- 當日漲跌以前一交易日收盤計算；「圖表區間漲跌」比較圖中的首末報價，兩者意義不同。
- 使用來源的日收盤欄位，本程式不另做股息或拆股調整。不同幣別的價格不會直接相加；股票顯示報價幣別，指數顯示點數。
- 缺乏的成交量、52 週資料或其他欄位顯示 `—`。南非預設項目為 `^JN0U.JO`，即 Top 40 USD Net TRI，包含美元與淨股息再投資因素。

背景排程可設為 15／30／60 分鐘，預設僅手動。Android 的省電、待機或程序清除可能延後或停止背景工作。手環主動請求更新需要手機同步器程序仍在執行，必要時重新開啟並連接同步器。

## 建置

需要 Node.js 20 或以上、JDK 17、PowerShell 7、Android SDK platform 34 或以上與 build-tools 34／35。以下在專案根目錄執行：

```powershell
./tools/prepare-sdk.ps1
# 新專案才建立簽章；升級既有安裝請還原你自己的私人簽章備份
./tools/create-signing.ps1 -JdkPath $env:JAVA_HOME
npm --prefix band ci
npm --prefix band run release
Copy-Item band/dist/com.wristmarkets.sync.release.0.3.1.rpk android/app/src/main/assets/band.rpk
./tools/build-android.ps1 -SdkPath $env:ANDROID_HOME -JdkPath $env:JAVA_HOME
```

`JAVA_HOME` 未設定時，腳本的 Windows 預設值為 `C:\Program Files\Java\jdk-17`。產物位於 `band/dist/` 與 `dist/`。

公開儲存庫不包含私人簽章、密碼、編譯產物或小米 SDK 二進位檔。`prepare-sdk.ps1` 從小米官方下載 SDK。保留自己的簽章才能直接升級既有安裝；更換簽章可能需要先移除舊版。

## 測試

```powershell
npm --prefix band test
./tools/test-core.ps1 -JdkPath $env:JAVA_HOME
# 可選：實際網路來源檢查，不會在離線測試中自動執行
./tools/test.ps1 -Live -JdkPath $env:JAVA_HOME
```

核心測試以合成資料檢查五日交易日期、夏令時間、前收基準、缺值、搜尋過濾、自選清單、同步確認、重傳、舊版本隔離及手環頁面資料。不需要手機或私鑰。

## 專案結構

| 路徑 | 內容 |
| --- | --- |
| `android/app/src/main/` | 原生 Android 介面、搜尋、行情與同步 |
| `band/src/` | Vela 手環程式、原生折線圖與協議 |
| `band/test/` | 手環頁面與同步交易測試 |
| `test/` | JVM 行情與清單行為測試；Android API 替身僅用於測試 |
| `tools/` | SDK 準備、簽章、建置與驗證 |

沒有廣告、登入或本專案的中繼伺服器，不讀取使用者的持倉、健康、聯絡人或通知。第三方行情服務會收到一般的網路連線資訊。

## 參考

- [小米 Vela JS](https://iot.mi.com/vela/quickapp/zh/guide/)
- [小米官方互通介面與 SDK](https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html)
- [PolyForm Noncommercial 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0)
