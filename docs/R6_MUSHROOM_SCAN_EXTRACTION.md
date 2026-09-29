# R6 — Mushroom scan coordination extraction

## Scope and status

R6 moves only normal Mushroom scan coordination from
`PetalAccessibilityService` into `MushroomWorkflowCoordinator` and
`MushroomScanner`. It is a source-ownership change, not a detector or
gameplay feature change.

`MushroomDetector`, its templates, thresholds, scoring, and fixtures are
unchanged. The known negative Xiaomi map fixture remains an unresolved detector
quality issue and is not treated as an R6 acceptance condition.

## Ownership boundary

| Owner | R6 responsibility |
|---|---|
| `MushroomWorkflowCoordinator` | Explicit normal start/stop/rescan, immutable session and scan-generation identity, normal 200 ms start / 3 s repeat cadence, capture/analysis/result state, retry state, and `MushroomUiStore` scan publication. |
| `MushroomScanner` | Page-gate evaluation before detector admission, one bounded detector job, worker/main source holds, stale delivery rejection, queue state/rejection, and `MushroomDiagnostics`. |
| `PetalAccessibilityService` | Android lifecycle, foreground/window evidence, `CaptureCoordinator` and `OcrRuntime` adapters, overlay hide/restore calls, final action-admission evidence check, and compatibility wiring to existing patrol callbacks. |
| `MushroomPatrolController` / location/map code | Unchanged R7 compatibility seam. Patrol requests the shared scan engine through its existing host callback; map selection, mock location, confirmation, stabilization, and route ownership remain in the service. |

The scanner has no action gateway, gesture, node-action, mock-location, map, or
overlay ownership. A detector result is still observation data only; R6 adds no
automatic Mushroom tap, join, `GO`, or reward action.

## Service extraction inventory

`PetalAccessibilityService` changed from 10,289 lines at the R5 baseline to
10,170 after R6. The 119-line decrease is an ownership observation, not an
optimization target. Eight former normal-scan fields and the inline analysis
job moved to the two R6 owners; the service retains only Android capture/OCR
adapters, command wiring, action-admission evidence checks, and the R7
map/location/patrol compatibility seam.

## Preserved contracts

- Normal start schedules one initial scan after 200 ms. A completed normal
  result schedules the next scan after 3 seconds; normal page/analysis retry
  remains 1.5 seconds.
- `Session(sessionId, scanGeneration, runGeneration, origin)` is checked at
  capture, page-gate, analysis delivery, result publication, rescan, and stop.
  A late S1 result cannot overwrite current S2 state.
- One `MushroomScanner` job can be active. It retains the OCR source once for
  the worker and once for main-thread delivery, and releases both on every
  terminal path.
- Page evidence is evaluated before the detector worker is submitted. Invalid
  page and game-not-foreground outcomes never invoke the detector.
- `CaptureCoordinator`, `OcrRuntime`, and `OverlayHost` retain their existing
  ownership. The coordinator invokes only the service's narrow scheduling and
  UI-publication adapters.
- Patrol has no parallel normal loop. It uses the same scanner through the
  existing controller callback and restores normal cadence only when normal
  scanning was enabled before patrol began.

## Focused regression coverage

`MushroomWorkflowCoordinatorTest` covers explicit start, one capture in
flight, 3-second normal-result cadence, stop/S1-to-S2 stale rejection, rescan
invalidation, and patrol suspension/restoration.

`MushroomScannerTest` covers page rejection before detector invocation, one
bounded job with two source holds, stopped-session stale delivery, and
cancellation after worker posting.

Existing Mushroom detector, page-gate, policy, UI-state, capture/overlay, OCR,
and patrol tests remain part of the full JVM suite. No instrumentation or
device test result is implied by source or JVM checks.

## R7 seam

R7 may extract map selection, location confirmation, mock-location driver,
route/stabilization, and patrol-controller ownership. It must preserve the
existing controller-host commands and must not move detector tuning or start
gameplay actions. R6 deliberately leaves that work unstarted.

## Evidence record

| Gate | Result |
|---|---|
| Source ownership | `PASS`: final review confirms scan session/cadence/publication live in `MushroomWorkflowCoordinator`; page gate/bounded job/stale delivery live in `MushroomScanner`. |
| JVM unit | `PASS`: 92 suites, 640 tests, 0 failures/errors/skips; the focused R6 suite has 9 tests. |
| Lint and APK build | `PASS`: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --rerun-tasks` completed; lint reports 0 issues and debug, Android-test, and release APKs were produced. |
| Artifact and signature | `PASS`: release `com.pikminx.helper` `3.1.30` (`340`), SHA-256 `86d7249b316c2d4d52a0221744bec68f71eb4fb4e607ec25a94ac25fbda5e2a9`; APK Signature Scheme v2 verifies with the existing signer certificate SHA-256 `7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`. |
| Instrumentation | `BUILD PASS ONLY`: `assembleDebugAndroidTest` produced the APK; no device test was executed. |
| Device/accessibility/gameplay | `NOT TESTED — DEVICE UNAVAILABLE`: `adb devices -l` showed no attached device, so no install, accessibility enablement, runtime, or gameplay result is claimed. |
