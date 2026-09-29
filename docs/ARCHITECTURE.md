# Architecture — R6 Current Map and Target Boundary

## Scope

This document maps the active production tree:
`code/app/src/main/java/com/pikminx/helper`. It contains 107 Java type files.
R3 adds the OCR runtime ownership boundary while preserving the R2
capture/action owners. R4 records the current platform configuration, update,
and diagnostics ownership boundary. R5 records the overlay presentation
ownership boundary. R6 records the Mushroom normal-scan ownership boundary;
neither phase makes a device or gameplay claim.

Android registers three production components: `MainActivity`,
`PetalAccessibilityService`, and `UpdateInstallReceiver`.  The service is the
current runtime hub; `MainActivity` reaches it only through static
connection/overlay visibility bridge methods, while feature starts come from
the service's own overlay/settings callbacks.

## Current runtime topology

```text
MainActivity ────────────────> SettingsStore / Update / Remote Config
      │                                  │
      └─ static service visibility bridge │
                                         ▼
PetalAccessibilityService (Android adapter + workflows)
  ├─ Android lifecycle, foreground/window checks, accessibility gestures
  ├─ Handler scheduling + CaptureCoordinator/ActionGateway platform adapters
  ├─ OcrRuntime transaction / frame-analysis handoff / OCR cleanup
  ├─ Planting, Care, Expedition, Reward, Postcard workflow state machines
  ├─ Mushroom map selection, location confirmation, patrol compatibility seam
  ├─ MushroomWorkflowCoordinator / MushroomScanner normal scan ownership
  ├─ WindowManager overlay, settings forms, notices, status rendering
  └─ platform settings/config, update adapter, diagnostics and telemetry
```

The source has useful pure policies and analyzers already.  The architectural
problem is ownership concentration, not absence of helpers: one Android
service currently joins observation, decisions, workflow state, UI and
actions in the same owner.

## PetalAccessibilityService responsibility map

**Responsibility count: 12 major areas.**  This is a responsibility count,
not a count of helper methods or anonymous callbacks.  The service has about
470 source-level method declarations when nested UI/callback methods are
included; R0 groups them by behavior because a method-by-method split would
hide the meaningful ownership boundary.

| Area | Main current methods / source region | State owned now | Detector or policy used | Scheduler/executor | Gesture / platform path | R0 target owner |
|---|---|---|---|---|---|---|
| Lifecycle and connected-service bridge | `onServiceConnected`, events, interrupt, destroy (1329–1472) | connection, active run, generation | foreground/window checks | main `Handler` | `AccessibilityService`, `WindowManager` teardown | Service adapter keeps lifecycle only |
| Feature/config admission | `featureFor`, remote-config checks, `startAutomation` (1473–1568) | mode/start eligibility | `RemoteConfigClient`, `AutomationStartGuard` | delayed start tasks | none | per-workflow command entry + platform config |
| Mushroom scan adapter and patrol seam | explicit scan commands, map/location/patrol UI methods, and narrow capture/OCR adapters | route, pending patrol point, location confirmation; not normal scan session/cadence/result state | `MushroomWorkflowCoordinator`, `MushroomScanner`, `MushroomPageGate`, detector/policies | coordinator scheduler adapter, bounded frame worker, location callback | mock-location driver; overlay command callbacks | normal scan: coordinator/scanner; patrol/location: R7 |
| Care command/session | `startFeedAutomation` and Feed handlers (2501–2570, 4206–5737) | `FeedStep`, candidates, counters, hold/harvest state | `FeedScreenAnalyzer`, matcher, `FeedHoldLifecycle` | OCR/capture callbacks; handler | tap, pinch, hold, whistle, back | Care workflow |
| Reward session and armed ROI | ROI/anchor methods (2679–2854); reward handler (8091–8274) | ROI, warning/receipt/tap confirmation counters | `ReturnRewardDetector`, guards, `PostcardMatcher` | handler and shared capture | user anchor, target tap, ROI overlay | Reward workflow |
| Screenshot transaction | `requestScan`, Service capture adapter and handoff | busy flag, capture presentation | `CaptureCoordinator`, `CaptureGeometry`, retry/mask classes | `Handler`, Android screenshot API | `takeScreenshot` / window screenshot | `CaptureCoordinator` |
| OCR and analysis jobs | planting/Mushroom jobs (836–1328); profiles/transactions (3473–3989, 9494–9798) | active transaction, retain/release state, analysis job | `OcrScanner`, `OcrScan`, admissions | OCR recognizer and frame worker | none directly | OCR runtime + feature analysis adapter |
| Action admission and geometry | frame context, geometry, current-window adapter methods | action context/thread-local geometry | `ActionGateway`, `ActionAdmission`, transforms | gesture callbacks | dispatch occurs after final gate | `ActionGateway` |
| Planting workflow | monitor/search/start/stop methods (4008–4205, 6766–7983) | step, configured flowers, stability/search counters | planting analyzers/policies | scan schedule, focused OCR | node click, taps, swipe, keyboard/back | Planting workflow |
| Expedition and Postcard workflows | dispatch (5737–6765); postcard (8275–9207) | dispatch session/flags; postcard state/guards/counters | respective analyzers/matchers | scan schedule, focused OCR | tap, swipe, keyboard/back | Expedition and Postcard workflows |
| Action primitives | node helpers and gesture paths (7973–8050, 9208–9465) | transient action context | `ActionAdmission`, coordinate transform | accessibility gesture callback | tap/path/global back/node action | Action gateway behind service adapter |
| Overlay, settings, status and telemetry | overlay/settings (9880–11127); terminal/status/UI helpers (11128–12387) | views, UI notice/status, settings forms | `MushroomUiStore`, status models | main `Handler` | `WindowManager`, Android views | Overlay host; settings platform UI; diagnostics |

### Responsibilities that remain outside R2

The service must retain Android API endpoints: lifecycle, screenshot API,
current-window lookup, final admitted gesture dispatch, overlay attachment,
and permission/settings intents.  Everything below is an application concern
that should no longer be owned by the service itself:

- per-feature transition state and retry counters;
- OCR job routing and screenshot transaction orchestration;
- overlay form construction and feature rendering;
- Mushroom location/patrol state and map presentation;
- workflow diagnostics/telemetry aggregation;
- detector-to-action decision code.

R3 moves the common OCR transaction lifecycle to `OcrRuntime`; CaptureCoordinator
remains the sole capture owner and ActionGateway remains the final action
boundary. Workflow state and feature decisions remain in the Service for this
phase. See [R3_OCR_RUNTIME_EXTRACTION.md](R3_OCR_RUNTIME_EXTRACTION.md).

## R2 ownership boundary (implemented; runtime evidence pending)

R2 extracts only the shared capture transaction and final action-safety
orchestration. `CaptureCoordinator` owns request identity (including
request-time `admissionEpoch`), FIFO/one-dispatch lifecycle, timeout/retry,
callback validation, overlay preparation/restoration tokens, geometry and
completion/cleanup handoff. `ActionGateway` owns live-state retrieval, final
`ActionAdmission`, and node/editable/tap/path/continued-gesture/game-Back
dispatch coordination. The Service remains the Android adapter for screenshot
and action primitives through minimal platform delegates.

Workflow state machines, OCR ownership (`OcrScanner`/`OcrScan`), feature
detectors, and `OverlayHost` remain in the Service for R2. See
[R2_CAPTURE_ACTION_EXTRACTION.md](R2_CAPTURE_ACTION_EXTRACTION.md). Runtime
and device evidence remain pending and are not implied by this source map.

## Target logical module graph

These are logical ownership modules, not a proposal for new Gradle modules or
new dependencies.  Each target class corresponds to a concrete current
responsibility.

```text
platform
  PetalAccessibilityService (thin Android adapter)
  platform.settings / platform.config / platform.update owners
  UpdateInstallReceiver (manifest-bound update adapter)
       │
       ├── capture: CaptureCoordinator, CaptureGeometry, queue/retry/cleanup
       ├── vision: OcrRuntime, OcrScan, token/template analyzers
       ├── interaction: ActionGateway + ActionAdmission + coordinate transform
       ├── overlay: OverlayHost, MushroomOverlayPanel, MushroomMapView/Bridge
       └── workflows
             ├── PlantingWorkflow
             ├── CareWorkflow
             ├── ExpeditionWorkflow
             ├── RewardWorkflow
             ├── PostcardWorkflow
             └── mushroom
                   MushroomWorkflowCoordinator / MushroomScanner
                   MushroomPatrolCoordinator / MushroomLocationGateway
                   detector, map and patrol data classes
```

| Target module / concrete owner | Concrete current responsibility collected there | Current sources that can move without inventing a framework |
|---|---|---|
| `com.pikminx.helper.CaptureCoordinator` | Serialize screenshot request, allocate/associate capture sequence, retry, timeout, overlay token and bitmap handoff | Service capture adapter plus `ScreenshotRequestQueue`, `ScreenshotRetryPolicy`, `DeferredCleanup`, `ScreenshotOverlayMask`, `FrameAnalysisExecutor` |
| `com.pikminx.helper.OcrRuntime` | OCR transaction/recognizer lifecycle, callback terminality, identity propagation, and immutable frame delivery | Existing `OcrScanner`, `OcrScan`, `OcrRuntimeDiagnostics`, `GeometryValidation`, and Service OCR transaction methods |
| `platform.settings`, `platform.config`, `platform.update`, `platform.diagnostics` | Settings, remote-config, update/install, and diagnostic/telemetry ownership | Current migrated types under those packages; `UpdateInstallReceiver` remains the Android update adapter |
| `com.pikminx.helper.ActionGateway` | Final admission, coordinate conversion and service-delegated gestures | `ActionAdmission`, `ScreenCoordinateTransform`, service action-context and dispatch methods |
| `planting.PlantingWorkflow` | Current planting transition/search/start/monitor/stop state | planting policies/analyzers and the Service planting method family |
| `care.CareWorkflow` | Feed rounds, nectar selection, hold and harvest lifecycle | care analyzers and the Service Feed family |
| `expedition.ExpeditionWorkflow` | Dispatch session, selection and return verification | `ExpeditionDispatchSession`, analyzer, settings enums and Service dispatch family |
| `reward.RewardWorkflow` | Armed ROI, reward confirmation, warning/postcard branch | reward detector/ROI/guard and Service reward family |
| `postcard.PostcardWorkflow` | Bubble/detail/petal/receipt/return workflow state | postcard types and Service postcard family |
| `mushroom.MushroomWorkflowCoordinator` + `MushroomScanner` | Session/page gate/analysis/result publication | existing Mushroom workflow state/policy/detector classes and Service scan family |
| `mushroom.MushroomPatrolCoordinator` + `MushroomLocationGateway` | Patrol ownership, mock-location request/confirmation and stabilization | current patrol/location controllers and their Service callback family |
| `overlay.OverlayHost` | Overlay attachment, settings/presentation rendering and status projection | `OverlayWindowPolicy`, run status, existing overlay panel/map view and Service UI family |
| `diagnostics` | Capture/OCR/workflow/Mushroom metrics and optional telemetry | diagnostics types plus Service terminal reporting |

No generic `Workflow`, universal detector hierarchy, global retry abstraction,
or broad state manager is proposed.  Existing domain policy classes are the
smaller seam.

## R5 overlay presentation ownership boundary

`OverlayHost` is the concrete presentation owner for overlay attachment,
settings forms/tabs, floating icon, status and notice projection, Mushroom
panel/map wiring, and capture-mask presentation tokens. The service supplies
narrow workflow and Mushroom command callbacks; it retains workflow state,
Android lifecycle/screenshot/action adapters, and the hybrid Return Reward ROI
capture geometry path. `MainActivity` continues to use the existing static
service visibility bridge.

The R5 projection contract keeps panel expansion, icon visibility, workflow
enabled/running/phase, and capture-mask state independent. Presentation-only
operations do not silently pause, resume, start, or stop a workflow. Explicit
buttons route named commands exactly once. Capture restoration remains
request-token based so a late callback cannot overwrite a newer presentation.
See [R5_OVERLAY_PRESENTATION_EXTRACTION.md](R5_OVERLAY_PRESENTATION_EXTRACTION.md)
for scope labels and separate source/build/device evidence gates.

## R6 Mushroom normal scan ownership boundary

`MushroomWorkflowCoordinator` owns explicit normal start/stop/rescan, immutable
session and scan-generation identity, normal cadence, scan state and
`MushroomUiStore` scan publication. `MushroomScanner` owns page-gate admission,
one bounded detector job, deferred OCR source holds, stale delivery rejection,
and Mushroom queue/result diagnostics. The service supplies only its existing
Android capture/OCR/foreground/overlay adapters and the final evidence
admission check; it does not own competing normal scan session or result state.

`MushroomDetector` remains action-free and unchanged. `CaptureCoordinator`,
`OcrRuntime`, and `OverlayHost` retain their prior ownership. Map selection,
location confirmation, mock-location, route/stabilization and patrol controller
ownership are deliberately still the R7 compatibility seam. Patrol uses the
shared scanner without creating a second normal cadence and restores normal
scanning only if it was enabled before patrol began.

The source contract is documented in
[R6_MUSHROOM_SCAN_EXTRACTION.md](R6_MUSHROOM_SCAN_EXTRACTION.md). JVM/build
evidence and device/accessibility/gameplay evidence remain separate gates.

## Architectural findings to preserve

1. `MushroomDetector` is action-free; it returns results.  Preserve that
   boundary.
2. `Mushroom` does not execute through Planting's token branch.  Both share
   capture infrastructure but have distinct scan routing.
3. A final action admission is safety-critical even after a fresh analysis.
4. Current location types are practically Mushroom-specific; screen geometry,
   requested coordinates, confirmed location, and result coordinates remain
   different facts.
5. `MushroomUiStore` is a presentation projection. R6 scan state originates in
   `MushroomWorkflowCoordinator`; an overlay must render workflow state rather
   than become workflow state.

Detailed type classification is in `FEATURE_MATRIX.md`; target decisions,
coupling, duplicates and delete candidates are in `TRACEABILITY_MATRIX.md`.
