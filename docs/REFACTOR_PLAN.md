# Refactor Plan — R0 to Incremental Migration

## Guardrails applying to every phase

- The active source remains `versions/3.0/code/app/src/main`.
- Preserve the final `ActionAdmission` and capture-geometry check immediately
  before every game action.
- Preserve serialized screenshot ownership, retry behavior, overlay masking,
  OCR transaction identity, cleanup/retain ordering, and existing timing
  constants unless a later phase explicitly changes behavior with evidence.
- Do not tune Mushroom templates, thresholds, models, scoring, or fixture
  expected values.  The negative Xiaomi map fixture remains unresolved.
- A static/unit pass is not a device/accessibility/gameplay pass.  Record those
  gates separately.
- Keep each phase independently revertible.  No phase combines two high-risk
  workflow migrations.

## R1 — Characterize the capture/action seam

**Code to move:** none.  R1 is intentionally a characterization boundary;
the Service retains all current calls.

**Behavior that must not change:** screenshot request serialization, action
admission rejection reasons, geometry transforms, retry decisions, frame
identity and bitmap cleanup order.

**Tests required before:** existing `ActionAdmissionTest`,
`CaptureGeometryTest`, `ScreenCoordinateTransformTest`,
`ScreenshotRequestQueueTest`, `ScreenshotRetryPolicyTest`,
`MultiStageGestureWorkflowTest`, `MultiStageFreshnessTest`, and current OCR
transaction tests.

**Tests required after:** the same tests unchanged plus narrowly scoped
characterization tests only where current behavior has no assertion.  Do not
rewrite fixture expectations to fit the proposed module names.

**Rollback boundary:** the characterization-test commit(s) only; no production
workflow state has moved.

**Expected risk:** low.  This is the only R1 that is safe to start now without
first making a behavior-moving architectural decision.

## R2 — Extract the shared capture and action transaction

**Code to move:** Service screenshot/timeout/cleanup/context method families
into logical `CaptureCoordinator` and `ActionGateway` owners, together with
`ScreenshotRequestQueue`, `ScreenshotRetryPolicy`, `DeferredCleanup`,
`CaptureGeometry`, `ScreenCoordinateTransform`, `ScreenshotOverlayMask`,
`FrameAnalysisExecutor`, and `ActionAdmission`.  The Service remains the sole
Android screenshot/gesture adapter.

**Behavior that must not change:** one queue; capture sequence/run generation;
display/window capture fallback; all overlay hide/mask/restore ordering;
final action recheck; transient screenshot retry timing; capture resource
ownership.

**Tests required before:** R1 suite plus `GeometryValidationTest`, OCR frame
and cleanup tests, and any current service-level freshness regression test.

**Tests required after:** same suite; an integration-style unit test proving a
queued capture cannot deliver an action after its run/window/admission epoch
changes.

**Rollback boundary:** one infrastructure-only change set.  No Planting,
Care, Dispatch, Postcard, Reward, or Mushroom transition moves in this phase.

**Expected risk:** medium-high because every feature depends on it, despite no
feature state machine moving.

**Current implementation boundary:** `CaptureCoordinator` owns the request,
identity, serialized dispatch, timeout/retry, callback validation, overlay
restore token, geometry and completion handoff. `ActionGateway` owns the
fresh-state final admission and coordinates node/editable/tap/path/continued
gesture/game-Back primitives through the Service adapter. OCR, workflows and
`OverlayHost` remain unmigrated. See
`R2_CAPTURE_ACTION_EXTRACTION.md`; source/build/device gates must be reported
separately, with runtime evidence pending.

## R3 — Extract the OCR runtime and shared evidence support

**Code to extract:** the Service OCR transaction request, recognizer handoff,
callback/timeout/cancellation lifecycle, diagnostics handoff, and terminal
cleanup into concrete `OcrRuntime`. `OcrScanner`, `OcrScan`,
`OcrRuntimeDiagnostics`, and `GeometryValidation` remain existing
package-local behavior contracts composed by that owner; this phase does not
relocate a type merely because it is OCR-related. `AutomationStartGuard`,
`ObservationStability`, `SwitchGuard`, and `SearchKeyboardGuard` remain in
place because no ownership cleanup requires their movement here.

**Behavior that must not change:** recognizer selection/profile behavior,
transaction/run/capture identity, callback thread and timeout behavior, OCR
queue/backpressure, geometry validation, text/token evidence, and all current
keyboard/focus decisions.

**Tests required before:** `OcrScanTest`, `OcrScannerTest`,
`OcrRuntimeDiagnosticsTest`, `OcrQueueBackpressureTest`,
`GeometryValidationTest`, `ObservationStabilityTest`, `SwitchGuardTest`,
`SearchKeyboardGuardTest`, and R1/R2 capture tests.

**Tests required after:** the same suite plus `OcrRuntimeTest` transaction
handoff coverage for immutable identity/profile/epoch propagation, setup and
recognizer failure, timeout/late/duplicate callback rejection, cancellation,
backpressure, capture-to-frame composition, stale-action rejection, and
exactly-once cleanup.

**Rollback boundary:** OCR/runtime owner extraction only; no feature handler
or shared-support helper changes its state machine or behavior in this phase.

**Expected risk:** high for OCR transaction identity, but confined to shared
evidence infrastructure rather than a user workflow migration.

**Concrete boundary:** `CaptureCoordinator` remains the sole capture owner and
passes the captured Bitmap plus immutable capture context to `OcrRuntime`.
`OcrRuntime` owns the OCR transaction lifecycle and returns immutable evidence
to the existing workflows. `ActionGateway` remains separate and is reached
only after workflow decisions. `PetalAccessibilityService` remains the
Android/service adapter and must not retain a competing full OCR runtime.

**Deferred evidence:** Xiaomi runtime availability is carried forward from
R2.1. A missing device is reported as `NOT TESTED — DEVICE UNAVAILABLE`, not
used to block source work; no positive Mushroom result is required for R3.

See [R3_OCR_RUNTIME_EXTRACTION.md](R3_OCR_RUNTIME_EXTRACTION.md) for the
identity, Bitmap, callback, compatibility, and exit contracts.

## R4 — Move platform configuration, update and diagnostics ownership

**Current source status:** the authorized ownership move is present in the
current source tree. `SettingsStore` is under `platform.settings`, remote
configuration under `platform.config`, update state/coordination under
`platform.update`, and diagnostics/telemetry under `platform.diagnostics`.
`UpdateInstallReceiver` remains the manifest-bound Android adapter. See
[R4_PLATFORM_CONFIG_UPDATE_DIAGNOSTICS.md](R4_PLATFORM_CONFIG_UPDATE_DIAGNOSTICS.md).

**Code to move:** `SettingsStore`, `RemoteConfigClient`,
`RemoteConfigStateModel`, `ApkUpdateManager`, `InstallStateModel`,
`AdmissionDiagnostics`, `WorkflowDiagnostics`, `MushroomDiagnostics`, and
`UsageTelemetryClient` into their logical platform/update/diagnostics owners.
`UpdateInstallReceiver` remains the manifest-bound Android adapter and is
rewired only through its existing contract.

**Behavior that must not change:** persisted preference keys/defaults,
remote-config feature gates, APK verification/install session handling,
receiver identifiers, diagnostic/telemetry payload behavior, and the absence
of any config-driven workflow semantic change.

**Tests required before:** `RemoteConfigClientTest`, `RemoteConfigStateModelTest`,
`ApkUpdateManagerTest`, `InstallStateModelTest`,
`RemoteInstallerCoordinationTest`, `AdmissionDiagnosticsTest`,
`WorkflowDiagnosticsTest`, `MushroomDiagnosticsTest`, and
`UsageTelemetryClientTest`.

**Tests required after:** the same suite; add an adapter-level test only if a
current manifest/config/install callback has no regression assertion.

**Rollback boundary:** platform/update/observability change set; no capture,
gesture, overlay or workflow transition moves.

**Expected risk:** medium-high because update/config/telemetry are external
side effects, even though no game workflow moves.

**Verification status:** R4 source/build PASS: `testDebugUnitTest` reports
622 tests, 0 failures, 0 errors; `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest`, and `assembleRelease` pass. The release artifact
is `com.pikminx.helper` 3.1.28/338 with the established v2 signer. Device and
runtime remain `NOT TESTED — DEVICE UNAVAILABLE`; source/build evidence does
not imply that runtime result.

## R5 — Make overlay presentation a projection

**Code to move:** overlay attachment/rendering/status helpers and
`OverlayWindowPolicy`/`OverlayRunStatus` into logical `OverlayHost`; retain
`MushroomOverlayPanel`, `MushroomMapView`, `MushroomMapBridge`, and
`MushroomUiStore` as presentation collaborators.

**Behavior that must not change:** overlay visibility bridge used by
`MainActivity`, touch targets, settings persistence, capture masking,
Mushroom map UI, notice timing, and the absence of UI-driven silent engine
state changes.

**Tests required before:** overlay policy/store/map bridge tests, capture mask
tests, and R2/R3 suite.

**Tests required after:** same tests plus a projection test: a workflow state
change renders UI, but changing a tab/visibility alone does not start a scan.

**Rollback boundary:** presentation-only owner move; workflows still command
the Service through the existing callbacks.

**Expected risk:** medium.  Device overlay behavior remains an explicit manual
runtime gate.

**Current implementation boundary:** `OverlayHost` owns WindowManager
attachment/removal, the floating icon, settings forms/tabs, status/notices,
Mushroom panel/map presentation wiring, and tokenized Mushroom capture masking.
`PetalAccessibilityService` retains lifecycle, capture/OCR/action adapters,
workflow state, and the hybrid Return Reward ROI/capture geometry path; it
exposes narrow named callbacks for explicit UI commands.

**Frozen-contract reconciliation:** the prior source allowed overlay hiding,
floating-icon interaction, or settings-panel opening to invoke pause. R5
removes those presentation side effects. Collapse, tab selection, map
open/close, and visibility changes therefore do not mutate workflow state;
only explicit workflow controls and workflow-owned safety/lifecycle paths do.

**Scope labels:** Mushroom normal continuous scan and panel-close persistence
remain `FEATURE NOT COMPLETE` pending R6/workflow ownership. Detector accuracy,
the negative Xiaomi map fixture, and Mushroom patrol/location E2E are `OUT OF
SCOPE FOR R5`. Device overlay composition and gameplay are separate runtime gates.

**Verification status:** source, unit/build, artifact, and runtime results are
recorded in [R5_OVERLAY_PRESENTATION_EXTRACTION.md](R5_OVERLAY_PRESENTATION_EXTRACTION.md)
from actual commands only. Do not treat static, JVM, lint, APK, or signature
evidence as device/gameplay evidence. R6 implementation is not authorized by
this phase.

## R6 — Move Mushroom scan coordination only

**Implementation status:** `COMPLETE — source/JVM/build evidence recorded in
R6_MUSHROOM_SCAN_EXTRACTION.md; device/accessibility/gameplay evidence remains
separate.` Mushroom normal continuous scan session, page-gate, bounded analysis
delivery and result publication now live in `MushroomWorkflowCoordinator` and
`MushroomScanner`. `MushroomDetector`, result/data types, and policy classes
remain in place and are composed through narrow contracts; they are not moved,
tuned, or rewritten. Map, location and patrol ownership remain deferred.

**Behavior that must not change:** page gate before detector publication;
single bounded analysis job; session/stale-result checks; 3-second normal scan
cadence; no automatic mushroom tap; capture overlay suppression/restoration.

**Tests required before:** `MushroomDetector*`, template catalog/page-gate,
workflow policy/state/UI store tests, queue/cleanup tests, and all existing
Mushroom instrumentation fixtures.

**Tests after:** existing Mushroom detector/page-gate/policy/UI/capture/OCR
coverage remains unchanged, plus `MushroomWorkflowCoordinatorTest` and
`MushroomScannerTest` for explicit lifecycle/cadence, one in-flight job,
page-gate-before-detector, source holds, cancellation, and stale S1/S2
delivery. The negative Xiaomi map fixture remains unchanged. Record
source/build/instrumentation/device gates separately; do not call detector
acceptance complete while the known false positive remains.

**Rollback boundary:** Mushroom scanner coordinator only; the Service continues
to own location/patrol callbacks and Android capture/gesture endpoints.

**Expected risk:** high due to asynchronous bitmap/session/overlay ordering.

**R7 readiness:** `R7_READY` for a separately authorized map/location/patrol
ownership extraction. R6 leaves the current controller-host commands intact;
no R7 production change is part of this phase.

## R7 — Move Mushroom map, location and patrol coordination

**Code to move:** Service map-selection, route, location-confirmation and
patrol callback families into `MushroomPatrolCoordinator` and
`MushroomLocationGateway`; move `MushroomLocationController`,
`AndroidMockLocationDriver`, `MushroomPatrolController`, map/route data and
their policies under Mushroom/Location/Map ownership.

**Behavior that must not change:** point/route generation, mock provider name,
permission/readiness UX, five-second confirmation, 1.5-second stabilization,
session/point/tolerance validation, pause/resume/stop and normal-scan
ownership restoration.

**Tests required before:** patrol route/controller, location confirmation,
map selection/UI store and R6 scanner tests.

**Tests required after:** same suite plus controller-host sequencing tests for
confirmed, timeout, paused, stopped and stale-result paths.  Device mock
location and real overlay map remain required runtime evidence.

**Rollback boundary:** location/patrol coordinator move only; no detector
tuning or unrelated feature migration.

**Expected risk:** high because location callbacks and capture sessions race.

## R8 — Move Flower Planting

**Code to move:** Service Planting method family and its local fields into
`PlantingWorkflow`, with `H10aPlantingAnalysis`,
`H10aAnalysisAdmission`, `PlantingScreenAnalyzer`, `PlantingFlowPolicy`,
`PlantingSearchCloseGuard`, `PlantingControlEvidence` and planting-only
selection helpers.

**Behavior that must not change:** entry detection, flower search and keyboard
recovery, configured flower order, start/stop verification, monitoring,
low-count confirmation, H10a off-main analysis admission, node-vs-gesture
selection and all coordinates/timing.

**Tests required before:** `PlantingScreenAnalyzerTest`,
`PlantingFlowPolicyTest`, `PlantingSearchCloseGuardTest`,
`H10aPlantingAnalysisTest`, `H10aAnalysisAdmissionTest`, `PetalMatcherTest`,
and R2/R3 infrastructure tests.

**Tests required after:** same suite plus one characterization test for each
current start, switch, stop and stale-analysis terminal branch.

**Rollback boundary:** Planting coordinator and its constructor/wiring only.

**Expected risk:** high; it includes asynchronous analysis and gesture state,
but no other workflow moves with it.

## R9 — Move Care / Feed

**Code to move:** Service Feed method family/fields into `CareWorkflow`,
`FeedHoldLifecycle`, `FeedScreenAnalyzer`, `FeedSettingsInput`, and nectar
template matching/cache under Care ownership.

**Behavior that must not change:** search keyboard path, nectar selection
confirmation, panel-close checks, zoom, hold-slice cleanup/freshness,
no-effect timeout, bloom harvest spiral, receipt counting and squad switch
limits.

**Tests required before:** `FeedScreenAnalyzerTest`, `FeedHoldLifecycleTest`,
`FeedSettingsInputTest`, nectar matcher/cache tests and R2/R3 freshness tests.

**Tests required after:** same suite plus a current-behavior test covering
hold release on stale frame and a completed collection/squad-switch path.

**Rollback boundary:** Care workflow owner only.  Reward continues to call the
existing evidence helper until R12 changes that explicit dependency.

**Expected risk:** high because a long gesture spans multiple screenshot
cycles.

## R10 — Move Expedition / Dispatch

**Code to move:** Service dispatch family/fields into `ExpeditionWorkflow`,
with `ExpeditionDispatchSession`, `ExpeditionScreenAnalyzer`, target/selection
enums and remaining-count model.

**Behavior that must not change:** list settle/scroll limits, focused-OCR
recovery, capture-derived ActionContext binding, detail action retry bound,
selection method/type, result detection and verified return count.

**Tests required before:** `ExpeditionDispatchSessionTest`,
`ExpeditionScreenAnalyzerTest`, remaining-count/selection tests and the R2/R3
admission suite.

**Tests required after:** same suite plus regression coverage for focused OCR
list recovery with a bound action context.

**Rollback boundary:** Expedition coordinator move only.

**Expected risk:** high because it has staged OCR recovery and the recently
fixed action-context safety path.

## R11 — Move Postcard acquisition (Big Flower flow to be characterized)

**R0.5 domain note:** `PostcardAutomation` remains the source name.  Its
map-bubble/detail/petal/Pikmin/`GO`/receipt sequence is compatible with the
official Big Flower direct-petal postcard route, but a screenshot/device trace
must identify the target before treating that as proven.  This phase moves the
existing technical `postcard` owner; it neither renames source nor claims every
official postcard uses this route.

**Code to move:** Service Postcard family/fields into `PostcardWorkflow`, with
`PostcardAutomation`, matcher, catalog, recovery/return/bubble/timing/input
types, `MapPostcardBubbleDetector`, and `FlowerDetailActionDetector`.

**Behavior that must not change:** bubble/detail evidence, warning choice,
keyboard focus handling, pot stability, Pikmin selection, receipt exit
confirmation and collection count.

**Tests required before:** `PostcardAutomationTest`, `PostcardMatcherTest`,
`PostcardPageRecoveryTest`, `PostcardReturnGuardTest`,
`PostcardRemainingCountTest`, map-bubble detector tests and R2/R3 suite.

**Tests required after:** same suite plus a receipt path proving count only
advances after the observed exit.

**Rollback boundary:** Postcard workflow owner only.  Return Reward still uses
the preserved matcher adapter until R12.

**Expected risk:** high because page recovery and receipt counting cross many
frames.

## R12 — Move Return Reward and resolve explicit shared evidence seams

**R0.5 domain note:** Official reconciliation does not tie this independent
ROI workflow to Dispatch completion.  Do not move it earlier or merge it into
Expedition; characterize the upstream game event(s) before any scope or naming
change.

**Code to move:** Service ROI/reward family into `RewardWorkflow` with
`ReturnRewardDetector`, `ReturnRewardRoi`, and `ReturnRewardScanGuard`.
Replace direct calls to Care/Postcard analyzers with named evidence adapters
only if the existing behavior is fully characterized; do not duplicate those
detectors.

**Behavior that must not change:** user ROI arm/overlay masking, target
confirmation/rearm, warning branch, postcard branch, timeout, settle timing
and final admitted action.

**Tests required before:** `ReturnRewardDetectorTest`, `ReturnRewardRoiTest`,
`ReturnRewardScanGuardTest`, relevant Postcard/Care evidence tests and R2/R3
suite.

**Tests required after:** same suite plus ROI, postcard and nectar-warning
branch characterizations without changing their source fixture oracle.

**Rollback boundary:** Reward coordinator and its two evidence adapters only.

**Expected risk:** high; it is the correct place to change the two explicitly
documented cross-feature dependencies, not an earlier speculative split.

## R13 — Cut over shared petal/token evidence after workflow moves

**Code to move:** split `PetalMatcher` into the existing shared OCR token/text
parser and the already-proven Planting, Care and Postcard selection policies;
place `PetalCatalog` and `PetalPotDetector` in shared catalog/evidence
ownership.  Replace temporary
cross-workflow evidence adapters only where R8–R12 characterization proves
the same input/output contract.

**Behavior that must not change:** normalized OCR matching, petal name/count
selection, configured catalog ordering, Planting search choice, Care nectar
selection, and Postcard pot selection.  Do not introduce a universal detector
or change OCR/model/template behavior.

**Tests required before:** `PetalMatcherTest`, `PetalCatalogTest`,
`PetalSelectionTest`, Feed/Planting/Postcard analyzer tests, and the completed
R8–R12 workflow regression suites.

**Tests required after:** the same tests with behavior-equivalence assertions
at each consumer boundary; no fixture/oracle rewrites just because ownership
changed.

**Rollback boundary:** shared parser/catalog cutover only, after each workflow
already has its own coordinator.

**Expected risk:** medium-high; the class is shared, but this phase avoids
combining the split with any workflow state migration.

## R14 — Prove or retain delete candidates

**Code to move:** none.  Audit `MapSceneDetector`, `MapSceneInventory`,
`MapScaleTransform`, `MapFlowerDetector`, `PostcardPotScanGuard`, the
documented `MushroomPatrolController.locationReady` compatibility shim, legacy
Mushroom result constructors, and any historical MainActivity Mushroom UI
evidence.

**Behavior that must not change:** all supported map/postcard/Mushroom
behavior and test fixtures.

**Tests required before and after:** production reference search, existing
unit/instrumentation tests that name each candidate, APK/runtime reference
inspection where static evidence is insufficient, and a full regression run
after any actual deletion.

**Rollback boundary:** one candidate per change set; no bulk delete.

**Expected risk:** low for proof work, unknown for deletion until runtime
evidence exists.

## Go / no-go summary

R1 is safe to begin as specified: it moves no production workflow and fixes
the current capture/action behavior in tests before any architectural move.
R2 onward require the phase-specific prechecks.  R0.5 changes no phase
ordering: it clarifies R11/R12 domain interpretation only.  R0 ends here; this
document does not begin R1.
