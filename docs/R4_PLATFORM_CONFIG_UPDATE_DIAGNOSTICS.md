# R4 Platform Configuration, Update and Diagnostics Ownership

## Status

R4 source and build gates pass. The connected-device/runtime gate remains
separate and is `NOT TESTED — DEVICE UNAVAILABLE`.

R4 is an ownership migration only. It does not change workflow state machines,
capture/action ownership, overlay behavior, remote-config semantics, update
installation semantics, or diagnostic/telemetry payload meaning.

## Source ownership boundary

Before R4, these support types were physically colocated with the Android
adapters in `com.pikminx.helper`. R4 moves their ownership into focused
packages without moving the adapters or any workflow state.

| Ownership | Current source types | Boundary |
|---|---|---|
| Platform settings | `com.pikminx.helper.platform.settings.SettingsStore` | SharedPreferences-backed typed settings and existing preference keys/defaults |
| Platform configuration | `com.pikminx.helper.platform.config.RemoteConfigClient`, `RemoteConfigStateModel` | Remote-config fetch/cache/state and feature-gate status |
| Update | `com.pikminx.helper.platform.update.ApkUpdateManager`, `InstallStateModel` | APK download/verification/install coordination and install state transitions |
| Platform Android update adapter | `com.pikminx.helper.UpdateInstallReceiver` | Manifest-bound broadcast adapter; forwards install status through the update contract |
| Diagnostics | `com.pikminx.helper.platform.diagnostics.AdmissionDiagnostics`, `WorkflowDiagnostics`, `MushroomDiagnostics`, `UsageTelemetryClient` | Admission, workflow, Mushroom, and usage telemetry recording/snapshots |

`MainActivity` imports the settings, configuration, and update owners. The
Accessibility service imports configuration, settings, and diagnostics, while
the receiver imports only update owner types needed for its Android broadcast
contract. The receiver remains declared as `.UpdateInstallReceiver` in
`code/app/src/main/AndroidManifest.xml`; it supplies its explicit
`ComponentName` to the update owner, so the owner has no source dependency
back to the receiver.

## Preserved contracts

- `SettingsStore` remains the persistence API for existing settings values.
- `RemoteConfigClient` retains cached status, feature checks, bounded fetch,
  and `RemoteConfigStateModel` transitions.
- `ApkUpdateManager` retains download, package/version/hash verification,
  install-session association, and callback handling through
  `InstallStateModel`.
- `UpdateInstallReceiver` retains its action and attempt/session extras and
  delegates callback acceptance to `ApkUpdateManager`/`InstallStateModel`.
  The update owner creates the explicit mutable PackageInstaller callback
  intent from the adapter-supplied component, preserving that Android contract
  without a receiver/update source cycle.
- Diagnostic classes retain bounded snapshots and the existing telemetry
  session/event payload path in `UsageTelemetryClient`.
- `OcrRuntimeDiagnostics` remains composed by `OcrRuntime`, its established
  R3 owner; R4 does not move or alter OCR runtime behavior.
- No workflow transition, detector threshold, OCR/capture transaction, final
  action admission, or overlay state is part of this migration.

## Evidence matrix

| Gate | Required evidence | Status |
|---|---|---|
| Source | Package placement, imports, receiver/manifest wiring, and preserved contracts | PASS |
| Unit | `RemoteConfigClientTest`, `RemoteConfigStateModelTest`, `ApkUpdateManagerTest`, `InstallStateModelTest`, `RemoteInstallerCoordinationTest`, `AdmissionDiagnosticsTest`, `WorkflowDiagnosticsTest`, `MushroomDiagnosticsTest`, `UsageTelemetryClientTest`, and new R4 compatibility/adapter cases | PASS — 622 tests, 0 failures, 0 errors |
| Build/lint | `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` | PASS |
| Release artifact | `com.pikminx.helper` 3.1.28/338, v2 signer certificate and APK digest | PASS — certificate SHA-256 `7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`; APK SHA-256 `3d17a76b8dbc85222d8da9f6a3048f3d9a52e9ee77d9430b099f18e8a1929b11` |
| Install | Bounded `install -r` smoke | NOT TESTED — DEVICE UNAVAILABLE |
| Device/accessibility | Connected-device launch and adapter smoke | NOT TESTED — DEVICE UNAVAILABLE |
| Runtime | Bounded config/update/diagnostics behavior with no workflow regression | NOT TESTED — DEVICE UNAVAILABLE |

The source/build result does not infer a runtime result. The 3.1.27 install
remains historical `NOT CONFIRMED`; no install, self-update, or Mushroom scan
was attempted for R4 because no device was connected.
