# PikminX 3.2.0

This repository contains the minimal buildable Android production source for
PikminX `3.2.0` (`versionCode 376`). It does not contain APKs, signing keys,
device captures, validation evidence, or development archives.

## Build

Requirements:

- JDK 17
- Android SDK Platform 35 and Build Tools 35
- Network access for the first dependency download

Windows PowerShell:

```powershell
.\gradlew.bat -p code :app:assembleDebug
```

macOS or Linux:

```bash
./gradlew -p code :app:assembleDebug
```

The APK is written to `code/app/build/outputs/apk/debug/app-debug.apk`.
Release signing material is intentionally not included.

## License

PikminX is source-available under the [PolyForm Noncommercial License](LICENSE)
and the supplemental terms in [LICENSE.md](LICENSE.md); it is not presented as
OSI open-source software.
