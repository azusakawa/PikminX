# PikminX 3.2.2

此專案包含 PikminX 3.2.2 版本的 Android 原始碼。

## 專案資訊

- Android application ID：`com.pikminx.helper`
- 版本名稱：`3.2.2`
- 版本代碼：`378`
- Android Gradle Plugin：`8.7.3`
- Java：`17`
- Android SDK：`compileSdk 35`、`minSdk 30`、`targetSdk 35`

## 建置與測試

請先安裝 JDK 17 與 Android SDK 35，並在終端機切換至 `code` 目錄：

```powershell
.\gradlew.bat -p code :app:assembleDebug
```

執行 JVM 單元測試：

```powershell
.\gradlew.bat -p code :app:testDebugUnitTest
```

Debug APK 會輸出至 `code/app/build/outputs/apk/debug/app-debug.apk`。Release 簽章設定請留在本機，不要提交簽章檔或密碼。

## 授權

本專案依 [PolyForm Noncommercial License](LICENSE) 與補充條款 [LICENSE.md](LICENSE.md) 授權，並非以 OSI 開源軟體呈現。
