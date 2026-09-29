# R5 — Overlay presentation extraction

## Status and boundary

R5 extracts Android overlay attachment, rendering, settings-panel construction,
status projection, notices, and Mushroom capture-presentation masking from
`PetalAccessibilityService` into `OverlayHost`. The service remains the
Android lifecycle/workflow adapter and supplies narrow callbacks for commands.
This is a presentation-ownership change; it is not R6 Mushroom scan
coordination.

The frozen R5 target keeps `overlayPanelExpanded`, `floatingIconVisible`,
`workflowEnabled`, `workflowRunning`, `workflowPhase`, and `captureMaskActive`
as distinct facts. Expanding/collapsing, tab selection, map open/close, and
overlay visibility do not start, pause, resume, or stop a workflow. Explicit
Mushroom Scan, Rescan, Stop, Patrol, Pause, and Resume controls delegate one
named command. Workflow state is projected into the panel/icon/status.

Capture masking/restoration remains tokenized and stale-safe; clear,
cancellation, failure, and service destruction restore prior presentation state.
The existing MainActivity static visibility bridge and settings persistence stay
compatible.

## Ownership map

`OverlayHost` owns `WindowManager` attachment/removal, the floating icon,
settings pages and tab presentation, status/notice rendering, Mushroom
panel/map presentation wiring, and the Mushroom capture-mask presentation
token. `OverlayWindowPolicy` and `OverlayRunStatus` are consumed by this owner.
`MushroomOverlayPanel`, `MushroomMapView`, `MushroomMapBridge`, and
`MushroomUiStore` remain presentation collaborators.

`PetalAccessibilityService` retains lifecycle, capture/OCR/action adapters,
workflow state/transitions, Mushroom scan/patrol/location state, and named
command callbacks. The Return Reward ROI anchor/frame attachment, arming, and
reward capture geometry remain in the service because those facts participate
in workflow evidence and admitted actions; `OverlayHost` owns the Return
Reward settings-form presentation only. R5 performs no geographic-to-screen
conversion.

## Extraction inventory

`PetalAccessibilityService` changed from 12,426 to 10,289 lines. The 2,137-line
reduction is reported as an ownership consequence, not an optimization target.

| Extracted presentation family | `OverlayHost` fields / methods |
|---|---|
| Overlay roots and lifecycle | `WindowManager`, icon/settings/notice roots and layout params; `attach`, `detach`, safe add/remove |
| Shell and forms | `showSettingsOverlay`, `closeSettingsOverlay`, settings-form construction, tab selection and control rendering |
| User-visible projection | status/toggle references; `setPanelStatus`, `setRunStatus`, `renderWorkflow`, `renderPrimaryAction`, notices and icon status |
| Mushroom presentation | `MushroomOverlayPanel` listener/wiring, map-command adapter, coordinate-assignment presentation |
| Capture presentation | token/snapshot state; `prepareMushroomCaptureMask`, identity-checked restore, abort cleanup |

The Service deliberately retains `returnRewardWindowManager`,
`returnRewardAnchorOverlay`, `returnRewardRoiOverlay`, `ReturnRewardRoi`, and
their capture geometry/arming methods. They are hybrid workflow-evidence and
geometry state, not generic overlay presentation.

## Conflict reconciliation

The old source coupled presentation operations to engine transitions: hiding
the overlay, tapping the floating icon, or opening settings could call the
service pause path. That contradicted the frozen R5 separation. R5 removes
those silent pause side effects; only explicit workflow controls or
workflow-owned safety/lifecycle code changes engine state. This is a source
correction required to meet the already-authorized frozen target.

## Scope labels and non-goals

| Item | R5 disposition |
|---|---|
| Mushroom normal continuous scan | `FEATURE NOT COMPLETE`; remains service-owned until R6 |
| Panel-close persistence of a running Mushroom session | `FEATURE NOT COMPLETE`; R5 prevents UI-triggered state changes only |
| Detector/template accuracy or negative Xiaomi map fixture | `OUT OF SCOPE FOR R5` |
| Mushroom patrol/location end-to-end behavior | `OUT OF SCOPE FOR R5` |
| Device overlay composition, accessibility gestures, gameplay | Separate runtime/device gate |

R5 must not tune detector thresholds, alter fixture oracles, move Mushroom
normal-scan coordination, or begin R6 work.

## Verification record

Record evidence by gate; do not promote one gate into another.

| Gate | Evidence |
|---|---|
| Source | `OverlayHost` has no Service, capture, OCR, action-gateway, or direct gameplay dependency; only the production Service references it |
| Unit tests | Baseline: 622 tests, 0 failures/errors. Final: 631 tests, 0 failures/errors; new `OverlayHostTest` has 9 pure projection/command/capture cases |
| Build/lint | 2026-09-15: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and `assembleRelease` all passed |
| Artifact/signature | `com.pikminx.helper` 3.1.29/339; v2 verified; signer SHA-256 `7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`; APK SHA-256 `793cc76942544db13a3666ad72951a274e124781cebfc45ba1526984c7af0caf` |
| MainActivity bridge | Source-characterized: static visibility bridge routes to presentation-only `applyOverlayVisibility`; it is remote-config gated and has no workflow transition |
| Runtime/device | `NOT TESTED — DEVICE UNAVAILABLE`: SDK `adb devices -l` returned no attached device; no install or gameplay smoke was attempted |

R5 source/build/artifact evidence is `PASS`. Runtime evidence remains separate;
no R6 implementation is part of this record and R6 readiness is reported
separately.
