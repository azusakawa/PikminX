# Traceability Matrix — R6 Migration, Coupling and Retirement Evidence

## Reading this matrix

Every production Java type file has one type-level row below.  A type's
cohesive public/behavioral method family is the migration unit; generated
record accessors, enum constants and trivial getters inherit the containing
type's decision.  `PetalAccessibilityService` is the exception: its 12
independent responsibility families have separate rows because they must not
move together.

`P1` means a gesture/capture/session safety boundary or a broad runtime owner;
`P2` means cross-feature/async compatibility risk; `P3` means an isolated
value, policy or utility.  `DELETE-CANDIDATE` is evidence of a static
disconnect, not authority to delete.

## R0.5 evidence provenance for target boundaries

The existing rows below remain `SOURCE` mapping.  This section makes the
additional reconciliation evidence explicit.  A blank/not-applicable official
cell is intentional: not every Android safety boundary has an official-game
meaning.

| Target boundary | `SOURCE` | `USER_REQUIREMENT` | `OFFICIAL_GAME_BEHAVIOR` | `RUNTIME_EVIDENCE` | R0.5 decision |
|---|---|---|---|---|---|
| Immutable `GameStateSnapshot` | Existing frame, geometry, admission, and feature analyzers provide facts from one capture. | Keep workflow stage, overlay visibility, requested location, patrol point, and settings out. | Walk View/Bird's-Eye distinction explains why a guessed map surface is unsafe. | No distinguishing Walk View/Bird's-Eye frame pair. | Retain a conservative proposal and `UNKNOWN`; do not add state. |
| Capture/action seam | One Service queue, capture transaction, geometry, and final `ActionAdmission` gate. | Preserve behavior/timing and keep R1 characterization-only. | Not applicable. | Device gesture behavior remains a separate gate. | Preserve R1/R2 ordering. |
| Care and Flower Planting | Separate Feed and Planting source paths; shared petals are parser/catalog evidence, not one state machine. | Do not change workflow behavior, OCR, timing, or selection order. | Nectar -> petals; petals -> Flower Planting; planting can affect Big Flowers. | No new R0.5 device run. | Keep `care` and `planting` boundaries separate. |
| Walk View / Bird's-Eye View | No shared `HOME_OR_MAP` enum or Bird's-Eye classifier; map-like evidence remains feature-local. | Review the broad R0 surface without inventing a state. | The views are distinct; Bird's-Eye supports distant inspection but not remote join/collection. | Required screenshot/transition fixture gap. | Document the distinction only; retain `HOME_OR_MAP` as low-confidence proposal. |
| Big Flower / Postcard acquisition | Current Postcard sequence is map bubble -> flower detail -> petals -> Pikmin -> `GO` -> receipt; legacy `MapFlowerDetector` has no active Service caller. | Audit Flower/map flower/Postcard flower/Reward flower labels without moving code. | Direct Big Flower petals can yield nectar and/or postcard rewards. | Need a live screenshot sequence to identify the active target. | Technical owner stays `postcard`; product annotation is `BigFlowerPostcardAcquisition`. |
| Expedition / Return Reward | Dispatch owns verified list/detail/selection/result/return count; Return Reward is an independent ROI collector and does not mutate that count. | Determine the relationship without merging implementations. | Expedition returns items for Garden collection; Mushroom returns can also carry rewards. | Need upstream-event-to-receipt trace. | Not A: do not call Return Reward Expedition completion.  Official upstream remains unresolved. |
| Mushroom scan scope | Page gate, action-free detector, result publication, and separate overlay map/location/patrol code. | Do not modify detector thresholds, templates, timing, tests, or workflow behavior. | Full game flow includes discovery/detail/challenge/result, which is broader than the scanner. | Known negative map fixture has false positives; full acceptance remains blocked. | Keep scan/patrol boundaries; do not imply Join/`GO`/result support. |
| Mushroom map/location versus Bird's-Eye | `MushroomMapView` is an overlay projection and location/patrol are PikminX tools. | Review Bird's-Eye explicitly without adding a state absent a consumer/fixture. | Bird's-Eye is a game map inspection view with proximity limits. | No proof that a current game capture is Bird's-Eye. | Do not conflate the overlay map with the official map. |
| R1 start condition | R1 moves no production code and characterizes existing contracts. | Do not start R1 automatically. | Reconciliation exposed no current boundary that changes R1's safety scope. | No additional R1 runtime prerequisite created by R0.5. | R1 remains safe to start when separately authorized. |

For source links and the official terminology behind the fourth column, see
[OFFICIAL_GAME_MODEL.md](OFFICIAL_GAME_MODEL.md).  Official documentation
describes game behavior only; it never upgrades a source claim into a current
PikminX implementation claim.


## PetalAccessibilityService method-family matrix

| Current method family | Current owner / responsibility | Target module | Decision | Risk | Dependencies |
|---|---|---|---|---|---|
| Lifecycle and connected-service bridge: `onServiceConnected`, event/interrupt/destroy | Android Service owns connection, teardown and current run lifetime | `platform.android.PetalAccessibilityService` | SPLIT; retain adapter portion | P1 | Accessibility lifecycle, Handler, WindowManager, location/overlay teardown |
| Capture: `requestScan`, screenshot adapter and completion handoff | `CaptureCoordinator` owns sequence, queue, retry, timeout and callback identity; Service supplies Android/overlay primitives | `com.pikminx.helper.CaptureCoordinator` | R2 COMPLETE; retain Service adapter | P1 | screenshot API, queue/retry/mask, geometry, cleanup, every workflow |
| OCR transaction: profile handoff, recognizer/callback/timeout | `OcrRuntime` owns common transaction identity, invocation, terminality, and cleanup; Service retains profile choice and feature analysis jobs | `OcrRuntime` plus existing feature analysis adapters | R3 COMPLETE for runtime; analysis adapters retained | P1 | OcrScanner, OcrScan, FrameAnalysisExecutor, capture lifetime |
| Action context/gestures: admission, node helpers, paths/global back | `ActionGateway` owns final admission and dispatch decision; Service remains the Android endpoint | `com.pikminx.helper.ActionGateway` | R2 COMPLETE; retain Service adapter | P1 | ActionAdmission, geometry/transform, foreground window, gesture callbacks |
| Planting family: token/search/start/monitor/stop methods | Service owns `AutomationStep`, settings and recovery counters | `planting.PlantingWorkflow` | SPLIT | P1 | planting analyzers/policies, OCR, keyboard/node/gesture actions |
| Care family: Feed token/search/hold/collect/switch methods | Service owns `FeedStep`, hold and harvest state | `care.CareWorkflow` | SPLIT | P1 | feed analyzer/lifecycle, OCR, ActionGateway |
| Expedition family: dispatch/list/detail/selection/result methods | Service owns session wiring and search flags | `expedition.ExpeditionWorkflow` | SPLIT | P1 | dispatch session/analyzer, focused OCR, ActionGateway |
| Postcard family: map/detail/petal/receipt/return methods | Service owns Postcard session callbacks/counters | `postcard.PostcardWorkflow` | SPLIT | P1 | postcard types, map-bubble detector, OCR, ActionGateway |
| Reward family: ROI/target/warning/postcard branch methods | Service owns armed ROI and confirmation state | `reward.RewardWorkflow` | SPLIT | P1 | reward types, Postcard/Care evidence, capture mask, ActionGateway |
| Mushroom normal scan family: explicit start/stop/rescan, page gate, analysis/result | `MushroomWorkflowCoordinator` owns scan identity/cadence/state/publication; `MushroomScanner` owns page gate, bounded job, stale delivery and diagnostics; Service is the Android adapter | `MushroomWorkflowCoordinator` and `MushroomScanner` | R6 COMPLETE; source/JVM/build gates separate from device | P1 | CaptureCoordinator, OcrRuntime, page gate/detector, UI store, overlay suppression |
| Mushroom map/location/patrol family | Service owns map selection, location confirmation and patrol callbacks | `mushroom.MushroomPatrolCoordinator` and `MushroomLocationGateway` | SPLIT | P1 | map/UI, mock location, patrol controller, scan session |
| Overlay/settings/status/config/telemetry methods | Service constructs UI and mixes projection with engine terminal state | `overlay.OverlayHost`, `platform.settings`, `diagnostics` | SPLIT | P2 | WindowManager, SettingsStore, config, telemetry, all workflow states |

## Platform, update and diagnostics types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `MainActivity`: lifecycle, permissions, update/settings UI, overlay bridge | Android Activity launcher shell | `platform.ui` | KEEP | P2 | SettingsStore, update/config, static Service bridge |
| `SettingsStore`: typed preference read/write | `platform.settings.SettingsStore` | `platform.settings` | R4 COMPLETE; unit/build PASS | P2 | SharedPreferences, feature input values |
| `RemoteConfigClient`: fetch/cache/feature gate | `platform.config.RemoteConfigClient` | `platform.config` | R4 COMPLETE; unit/build PASS | P1 | network, cache/state model, workflow starts |
| `RemoteConfigStateModel`: state transitions | `platform.config.RemoteConfigStateModel` | `platform.config` | R4 COMPLETE; unit/build PASS | P3 | RemoteConfigClient |
| `ApkUpdateManager`: download/verify/install session | `platform.update.ApkUpdateManager` | `platform.update` | R4 COMPLETE; unit/build PASS | P1 | PackageInstaller, Activity, receiver, network/storage |
| `InstallStateModel`: install state validation | `platform.update.InstallStateModel` | `platform.update` | R4 COMPLETE; unit/build PASS | P2 | update manager/activity/receiver |
| `UpdateInstallReceiver`: install broadcast forwarding | Android manifest receiver | `platform.android.update` | R4 adapter; one-way into update owner | P1 | Android broadcast contract, update manager |
| `AdmissionDiagnostics`: record/snapshot | `platform.diagnostics.AdmissionDiagnostics` | `platform.diagnostics` | R4 COMPLETE; unit/build PASS | P2 | ActionAdmission, latency stats, Service |
| `MushroomDiagnostics`: queue/result/stale metrics | `platform.diagnostics.MushroomDiagnostics` | `platform.diagnostics` | R4 COMPLETE; unit/build PASS | P2 | Mushroom scanner/session |
| `UsageTelemetryClient`: upload operation events | `platform.diagnostics.UsageTelemetryClient` | `platform.diagnostics` | R4 COMPLETE; unit/build PASS | P1 | network/privacy behavior, Service terminal outcomes |
| `WorkflowDiagnostics`: counters/timing snapshot | `platform.diagnostics.WorkflowDiagnostics` | `platform.diagnostics` | R4 COMPLETE; unit/build PASS | P2 | latency stats, Service workflow events |

## Capture, vision, state and shared types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `CaptureGeometry`: bounds/action-safety calculations | Shared capture model | `capture` | MOVE | P1 | Service, OCR, transforms, admission |
| `ScreenCoordinateTransform`: source/window conversions | Shared coordinate model | `interaction` | MOVE | P1 | capture geometry, Service actions, reward ROI |
| `ScreenshotOverlayMask`: region masking | Service capture helper | `capture` | MOVE | P2 | overlay views/regions, bitmap pixels |
| `ScreenshotRequestQueue`: enqueue/start/complete | Service capture queue | `capture` | MOVE | P1 | Handler, screenshot callback ordering |
| `ScreenshotRetryPolicy`: failure decision | Service retry helper | `capture` | MOVE | P2 | screenshot error codes, schedule |
| `DeferredCleanup`: retain/release/request cleanup | Service async bitmap lifecycle helper | `capture` | MOVE | P1 | OCR/analysis job completion |
| `FrameAnalysisExecutor`: submit/close bounded worker | Service analyzer worker | `capture` | MOVE | P1 | planting/Mushroom jobs, queue backpressure |
| `GeometryValidation`: validate/evidence snapshots | OCR/capture validation helper | `capture` | KEEP | P2 | CaptureGeometry, OcrScanner |
| `OcrScanner`: recognizer and scanner callback implementation | Composed by `OcrRuntime`; no Service transaction owner | `com.pikminx.helper.OcrRuntime` | R3 COMPOSE; type retained | P1 | ML Kit, OcrScan, diagnostics, bitmap lifetime |
| `OcrScan`: profile/transform/frame/action-safe facts | Immutable shared OCR evidence contract | `com.pikminx.helper` | KEEP | P1 | scanner, Service, capture geometry |
| `OcrRuntimeDiagnostics`: OCR timeline/recognizer metrics | Created and exposed by `OcrRuntime` | `com.pikminx.helper.OcrRuntime` | R3 COMPOSE; type retained | P2 | scanner, Service, latency stats |
| `PetalMatcher`: token parsing plus feature selection | Shared by Planting/Care/Postcard/Service | `vision.shared.tokens` plus domain selectors | SPLIT | P1 | TextNormalizer, PetalCatalog, OCR tokens, three workflows |
| `PetalPotDetector`: pot label/count evidence | Feed/Postcard visual helper | `vision.shared.petal` | MOVE | P2 | bitmap/tokens, matcher consumers |
| `NectarTemplateMatcher`: match/cache evidence | Care visual classifier | `care.vision` | MOVE | P2 | asset templates, cache, Service Care |
| `NectarTemplateMatchCache`: cache get/put/reset | Matcher-local cache | `care.vision` | KEEP | P3 | NectarTemplateMatcher |
| `FlowerDetailActionDetector`: detail action target | Postcard visual detector | `postcard.vision` | MOVE | P2 | bitmap geometry, Service Postcard |
| `MapFlowerDetector`: candidate detection | Only used by disconnected MapScene cluster | `legacy.map-analysis` | DELETE-CANDIDATE | P2 | MapSceneDetector/MapScaleTransform, tests |
| `MapPostcardBubbleDetector`: detect map bubble | Active Postcard detector | `postcard.map` | MOVE | P2 | Service/PostcardMatcher, bitmap geometry |
| `ActionAdmission`: frame/current state and admit | Shared final action safety policy | `interaction` | MOVE | P1 | Service actions, Feed hold, Dispatch, planting admission |
| `ObservationStability`: observe/reset/stable | Service Planting/Postcard confirmation helper | `state.evidence` | MOVE | P2 | local workflow counters |
| `SwitchGuard`: observe/cooldown decision | Planting/Care switching helper | `state.evidence` | MOVE | P2 | Service workflow state |
| `AutomationStartGuard`: start-generation predicate | Service delayed-start guard | `workflow.support` | MOVE | P3 | Service run generation |
| `BoundedLatencyStats`: add/snapshot percentiles | Diagnostics utility | `shared` | KEEP | P3 | diagnostics/OCR/queues |
| `SearchKeyboardGuard`: input visibility/focus decision | Four Service-owned search guards | `interaction.input` | MOVE | P2 | accessibility windows/nodes, Feed/Planting/Dispatch/Postcard |
| `SettingsInput`: bounded parse/value | Service settings helper | `shared` | KEEP | P3 | settings UI |
| `TextNormalizer`: normalize/compare text | OCR helper | `shared` | KEEP | P3 | PetalMatcher, PostcardMatcher |

## Care and Flower Planting types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `FeedHoldLifecycle`: begin/continue/release/finish | Current long-gesture lifecycle | `care` | MOVE | P1 | Service hold strokes, ActionAdmission |
| `FeedScreenAnalyzer`: page/nectar/bloom/receipt evidence | Current Care analyzer | `care.vision` | MOVE | P2 | bitmap/OCR, Service Care; Reward detail evidence |
| `FeedSettingsInput`: validate settings | Care input value | `care` | MOVE | P3 | SettingsStore, Service UI |
| `CardHighlight`: map/control pixel geometry | Planting entry/control evidence | `planting.vision` | MOVE | P2 | PlantingScreenAnalyzer, H10a, bitmap geometry |
| `H10aAnalysisAdmission`: async result admission | Planting worker guard | `planting` | MOVE | P2 | ActionAdmission frame/current state |
| `H10aPlantingAnalysis`: analyze/search work/result | Planting worker-side analyzer | `planting.vision` | MOVE | P1 | FrameAnalysisExecutor, OCR/capture frame |
| `PetalCatalog`: category/petal names | Shared flower catalog | `vision.shared.catalog` | MOVE | P2 | Care/Planting/Postcard/selection |
| `PetalSelection`: configured ordered selection | Planting settings/workflow value | `planting` | MOVE | P2 | PetalCatalog, SettingsStore, Service |
| `PlantingControlEvidence`: controls/screen value | Planting observation | `planting` | MOVE | P3 | analyzer, Service transitions |
| `PlantingFlowPolicy`: entry/start/low-count decisions | Planting policy | `planting` | MOVE | P2 | Service state, control evidence |
| `PlantingScreenAnalyzer`: home/map/menu/control analysis | Planting analyzer | `planting.vision` | MOVE | P2 | OCR tokens, CardHighlight, pixels |
| `PlantingSearchCloseGuard`: close/search decision | Planting retry guard | `planting` | MOVE | P3 | Service search state |

## Expedition, Postcard and Reward types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `DispatchPikminType`: enum configuration | Expedition selection input | `expedition` | MOVE | P3 | Service settings/selection |
| `DispatchSelectionMethod`: enum configuration | Expedition selection input | `expedition` | MOVE | P3 | Service settings/selection |
| `ExpeditionDispatchSession`: stage/confirmation/count/retry | Dispatch state machine | `expedition` | MOVE | P1 | Service, analyzer, ActionAdmission |
| `ExpeditionRemainingCount`: parse/value | Dispatch count input | `expedition` | MOVE | P3 | settings/Service |
| `ExpeditionScreenAnalyzer`: classify/find target | Dispatch visual/OCR analyzer | `expedition.vision` | MOVE | P1 | Service/session; MushroomPageGate |
| `ExpeditionTargetMode`: enum configuration | Dispatch target selector | `expedition` | MOVE | P3 | Service/analyzer/settings |
| `PostcardAutomation`: start/step/count | Postcard session state machine | `postcard` | MOVE | P1 | Service, receipt guard |
| `PostcardBubbleDetectionPolicy`: page/step gate | Postcard map detection policy | `postcard` | MOVE | P3 | Service, PostcardAutomation |
| `PostcardMatcher`: page/target/pot parsing | Postcard analyzer, also Reward receipt evidence | `postcard.vision` with reward evidence adapter | MOVE | P1 | Service, TextNormalizer, Reward |
| `PostcardPageRecovery`: recovery decision | Postcard page helper | `postcard` | MOVE | P3 | Service/Postcard state |
| `PostcardPotCatalog`: names/colors | Postcard catalog | `postcard` | MOVE | P2 | Service, matcher, PetalCatalog |
| `PostcardRemainingCount`: parse/value | Postcard count input | `postcard` | MOVE | P3 | settings/Service |
| `PostcardReturnGuard`: receipt return decision | Postcard receipt guard | `postcard` | MOVE | P2 | Service/PostcardMatcher |
| `PostcardSettingsInput`: validate settings | Postcard input value | `postcard` | MOVE | P3 | settings/Service |
| `PostcardTiming`: delay calculation | Postcard timing helper | `postcard` | MOVE | P3 | Service schedule |
| `ReturnRewardDetector`: find target/closeup | Reward visual evidence; Expedition analyzer references it | `reward.vision` | MOVE | P1 | Service, ROI/guard, Expedition analyzer |
| `ReturnRewardRoi`: arm/map ROI | Reward session value | `reward` | MOVE | P2 | Service overlay/capture geometry |
| `ReturnRewardScanGuard`: observe/rearm/complete | Reward confirmation policy | `reward` | MOVE | P2 | Service detector evidence |

## Mushroom, map, location and patrol types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `MushroomCaptureOverlayPolicy`: visibility snapshot | Mushroom capture overlay helper | `mushroom.scan` | MOVE | P2 | Service overlay/mask behavior |
| `MushroomDetectionId`: scan/capture/hit identity | Mushroom domain value | `mushroom` | KEEP | P3 | UI results/map callbacks |
| `MushroomDetectionResult`: hits/stats value | Detector output | `mushroom.vision` | MOVE | P2 | MushroomDetector, Service worker |
| `MushroomDetector`: template detect | Action-free detector | `mushroom.vision` | MOVE | P1 | template catalog, worker bitmap |
| `MushroomHit`: hit value | Detector-domain value | `mushroom.vision` | KEEP | P3 | detector/result |
| `MushroomObservation`: detection/location context | Patrol observation value | `mushroom.patrol` | MOVE | P2 | result, confirmed location, controller |
| `MushroomPageGate`: eligible/ineligible decision | Scan safety gate | `mushroom.scan` | MOVE | P1 | OCR tokens, ExpeditionScreenAnalyzer, map evidence |
| `MushroomScreenBounds`: capture bounds value | Result screen geometry value | `mushroom.vision` | KEEP | P3 | UI result/observation |
| `MushroomTemplateCatalog`: load/validate templates | Detector asset catalog | `mushroom.vision` | MOVE | P2 | Android assets, detector |
| `MushroomWorkflowPolicy`: delivery/transition decisions | Scanner policy | `mushroom.scan` | MOVE | P2 | Service scanner state |
| `MushroomWorkflowState`: scanner enum | Scanner local state | `mushroom.scan` | MOVE | P3 | Service/workflow policy |
| `MushroomWorkflowCoordinator`: explicit session/cadence/state/publication | Mushroom normal scan owner | `mushroom.scan` | R6 COMPLETE | P1 | scheduler adapter, UI projection, page/detector outcome callbacks |
| `MushroomScanner`: page gate/bounded job/stale delivery | Mushroom normal scan owner | `mushroom.scan` | R6 COMPLETE | P1 | OcrRuntime source holds, bounded worker, main dispatcher, diagnostics |
| `MushroomOverlayPanel`: render/callbacks | Current Mushroom panel | `overlay.mushroom` | MOVE | P2 | UI store, Service command bridge |
| `MushroomUiState`: scan/map/result projection | Current overlay state value | `overlay.mushroom` | MOVE | P2 | UI store, map/location result data |
| `MushroomUiStore`: publish/listen/prune UI state | Static UI projection store | `overlay.mushroom` | MOVE | P2 | Service, overlay panel |
| `OverlayRunStatus`: validate/render run status | Shared overlay presentation value | `overlay` | MOVE | P3 | Service status rendering |
| `OverlayWindowPolicy`: window/keyboard/notice policy | Service overlay layout helper | `overlay` | MOVE | P2 | WindowManager, Service UI helpers |
| `MapSelection`: selection/area/route model | Mushroom map state | `map` | MOVE | P2 | Service UI, route generator, UI store |
| `MapTileProvider`: Leaflet config value | Map presentation config | `map` | KEEP | P3 | MushroomMapView |
| `MushroomMapBridge`: JS/native commands | WebView map bridge | `map.overlay` | MOVE | P2 | MushroomMapView/overlay |
| `MushroomMapView`: Leaflet WebView rendering | Map presentation | `map.overlay` | MOVE | P2 | tile provider, bridge, overlay |
| `AndroidMockLocationDriver`: mock provider operations | Android location adapter | `location.android` | MOVE | P1 | LocationManager, MushroomLocationController |
| `MapCoordinate`: lat/lon value | Mushroom map/location coordinate | `location` | KEEP | P3 | selection, patrol, UI result |
| `MockLocationReadiness`: permission/provider evidence | Android location readiness | `location.android` | MOVE | P2 | Activity/Service/Android settings |
| `MushroomCoordinateSource`: provenance enum | Assigned-coordinate metadata | `location` | KEEP | P3 | MushroomUiState result |
| `MushroomLocationConfirmationPolicy`: confirmation decision | Location tolerance helper | `mushroom.location` | MOVE | P2 | Service/controller, locations |
| `MushroomLocationController`: request/observe/stop | Location session controller | `mushroom.location` | MOVE | P1 | driver, Service callbacks |
| `PatrolPoint`: indexed route value | Patrol point | `mushroom.patrol` | KEEP | P3 | route/controller/map |
| `PatrolRouteGenerator`: generate bounded route | Patrol route generator | `mushroom.patrol` | MOVE | P2 | MapSelection, MapCoordinate |
| `MushroomPatrolController`: host state machine | Patrol move/scan progression | `mushroom.patrol` | MOVE | P1 | Service host, location/scanner result |

## Legacy/disconnected types

| Current type / method family | Current owner / responsibility | Target module | Decision | Risk | Direct dependencies |
|---|---|---|---|---|---|
| `MapSceneDetector`: `detect` scene inventory | Disconnected map-analysis detector | `legacy.map-analysis` | DELETE-CANDIDATE | P2 | MapFlowerDetector, tests; no active production entry caller |
| `MapSceneInventory`: count/object model | Disconnected map-analysis model | `legacy.map-analysis` | DELETE-CANDIDATE | P3 | tests; no active production caller |
| `MapScaleTransform`: scale/to-source helpers | Disconnected map-analysis transform | `legacy.map-analysis` | DELETE-CANDIDATE | P3 | MapFlowerDetector type; no active production caller |
| `PostcardPotScanGuard`: pot-scan decision | Superseded-looking postcard guard | `legacy.postcard` | DELETE-CANDIDATE | P2 | dedicated tests; no active Service/Postcard caller |

## Cross-feature leakage ranking

| Rank | Evidence in current source | Why it is leakage | R0 disposition |
|---|---|---|---|
| P1 | `PetalAccessibilityService` owns capture, OCR, action dispatch, UI, every feature session, Mushroom location/patrol and diagnostics | Every workflow must coexist in one owner and can alter global mode/busy/overlay state | Split by phased ownership; do not make a big-bang move. |
| P1 | Reward handler consumes `FeedScreenAnalyzer` detail-page evidence | Reward depends on Care's screen semantics | Preserve via named evidence adapter only in R12. |
| P1 | Service couples detector result, workflow transition and gesture dispatch in one method family | A detector boundary cannot be independently tested/moved at the Service boundary | Move decision/action routing to the workflow plus ActionGateway; detectors remain evidence-only. |
| P1 | Mushroom adds overlay hide/restore and patrol/location handling to global capture lifecycle | Scanner-specific capture semantics are embedded in the shared engine | Extract after shared capture characterization, not into Planting. |
| P2 | Reward consumes `PostcardMatcher`; Mushroom page gate consumes `ExpeditionScreenAnalyzer`; Expedition analyzer references reward evidence | Feature analyzers are used as cross-domain page evidence | Name/characterize the evidence adapters in R10–R12; do not duplicate classifiers. |
| P2 | Four Service-owned `SearchKeyboardGuard` instances and four feature-specific search methods | Same input/focus concept has repeated local counters/actions | Keep guard as shared input evidence; retain feature transition policy. |
| P2 | Service mixes `ScreenCoordinateTransform`, `CaptureGeometry`, detector coordinates and direct bitmap fractions | Geometry contract is spread across capture and feature action methods | Centralize only after R1/R2 tests preserve mappings. |
| P2 | UI store/panel callbacks issue commands while Service also renders engine status | Presentation and engine state share owner | Make overlay a projection in R5. |
| P2 | Mushroom requested, confirmed, scan and assigned coordinates are stored across Service/UI/controller types | Location facts are logically distinct but coordinated by global Service fields | Move to Mushroom location/patrol owner without conflating coordinate meanings. |
| P3 | Feature-specific status wrappers and timing constants | Similar presentation/retry mechanics occur in multiple branches | Consolidate only when a moved workflow proves a concrete common contract. |

### Required negative findings

- **Mushroom logic in Planting:** none found in the active token branch.
  Planting routes through `handlePlantingTokens...`; Mushroom has its own
  capture/page-gate/analysis path.  Shared capture is infrastructure, not a
  Planting-to-Mushroom leak.
- **Planting workflow used by unrelated workflows:** no active unrelated
  workflow calls Planting's state machine.  `PetalMatcher`/`PetalCatalog` are
  shared vision/catalog utilities and should be split only at their proven
  parser-versus-selector boundary.
- **Detector directly dispatching actions:** no pure detector does so;
  `MushroomDetector` is explicitly action-free.  The P1 issue is Service
  co-location of detection, transition and action, not detector side effects.

## Duplicate infrastructure audit

| Concern | Current fact | Rank | R0 conclusion |
|---|---|---|---|
| Screenshot handling | One `ScreenshotRequestQueue` exists, but Mushroom/Reward add special overlay hide/restore and feature paths invoke capture through Service | P2 | Do not create a second queue; characterize special policies first. |
| OCR/state classification | One `OcrScanner`/transaction model, plus per-feature analyzers and focused OCR recoveries | P2 | Keep feature analyzers; share immutable frame delivery only. |
| Schedulers | One Service main `Handler`, plus `schedule`, `scheduleNext`, direct delayed tasks, gesture callbacks and screenshot timeout | P2 | Do not introduce a universal scheduler; move timing with its workflow. |
| Retry policies | Screenshot retry is centralized; feature confirmation/missing-frame/retry counters are separate | P2 | Keep source-specific retry policies until behavior is characterized. |
| Coordinates/geometry | Capture geometry/transform exist, but direct fractions and detector coordinates remain in workflow methods | P2 | R2 centralizes the contract, not detector thresholds or feature coordinates. |
| Status/UI state | Service both updates engine fields and renders overlay/state store | P2 | R5 separates projection from command/state ownership. |

## Delete-candidate proof ledger

| Candidate | Static evidence | What must be proven before deletion |
|---|---|---|
| `MapSceneDetector`, `MapSceneInventory`, `MapScaleTransform`, `MapFlowerDetector` | The active Service has no caller; the remaining references form a disconnected historical map-analysis cluster and tests reference parts of it | Search all production/build/reflection paths, preserve/review affected tests, inspect runtime/APK references, then delete one candidate/change set at a time. |
| `PostcardPotScanGuard` | No active Service/Postcard production caller; dedicated tests remain | Establish which newer guard replaced it and run/review its test before a one-file deletion. |
| `MushroomPatrolController.locationReady` | Source labels it a compatibility shim for old host/test callers; the active Service uses explicit location confirmation instead | Audit test/external callers and controller sequencing before deleting the method separately from its active controller. |
| `MushroomDetectionId.legacy` and legacy `MushroomUiState.Result` / `MushroomObservation` constructors | They preserve old result construction paths inside Mushroom data types | Audit tests/serialized or reflective callers; retain until the active result identity migration is proven complete. |
| Historical MainActivity Mushroom UI | Current `MainActivity` has no Mushroom workflow reference; current source says Service overlay is canonical | There is no current standalone UI class to delete.  Inspect historical resources/APKs/runtime references before removing any Activity-era artifact. |

The matrix deliberately contains no `DELETE` row.  R0 authorizes only
documentation and evidence collection.

## R2 capture/action extraction boundary

This additive record maps the first ownership move. It does not claim runtime
or gameplay acceptance.

| Boundary | R2 owner | Service retains | Compatibility / evidence status |
|---|---|---|---|
| Shared screenshot transaction | `CaptureCoordinator` | Android screenshot API, current window/root and lifecycle | `ScreenshotRequestQueue`, retry, geometry, overlay token and cleanup contracts retained; runtime callback/composition evidence pending |
| OCR handoff | `OcrRuntime` | `OcrScanner`, `OcrScan`, profile selection, and feature consumers | R3 extracts the common lifecycle while preserving the frame/identity handoff; source/build/runtime evidence remains separately reported |
| Final game action safety | `ActionGateway` | Android node action, gesture and global-action primitives | Final live-state `ActionAdmission` retained for node/editable/tap/path/continued gesture/Back; platform invocation counts require tests |
| Workflow state | Existing feature handlers | All Planting, Care, Expedition, Postcard, Reward and Mushroom state | No state-machine migration or timing change authorized |
| Overlay presentation | Existing Service/UI collaborators | WindowManager and current overlay presentation | `OverlayHost` deferred; capture suppression/restore token behavior retained |

The detailed contract, non-goals and separate source/build/runtime gates are in
`R2_CAPTURE_ACTION_EXTRACTION.md`. Until the required tests/builds/device gate
are evidenced, R2 remains an implementation in verification, not a device
PASS.

## R3 OCR runtime extraction boundary

This additive record authorizes OCR transaction ownership extraction without
changing OCR behavior, workflow state, or gameplay. Runtime/device evidence is
separate and Xiaomi availability remains deferred from R2.1.

| Boundary | R3 owner | Service retains | Compatibility / evidence status |
|---|---|---|---|
| Capture to OCR | `CaptureCoordinator` → `OcrRuntime` | Android screenshot/lifecycle adapter and workflow entry points | Captured Bitmap plus immutable context; CaptureCoordinator remains sole capture owner; source/build/runtime gates reported separately |
| OCR transaction | `OcrRuntime` | Service wiring only; no competing transaction lifecycle | Request/profile/transaction identity, recognizer invocation, timeout, cancellation, stale/duplicate rejection, queue behavior, diagnostics, and terminal cleanup preserve existing semantics |
| OCR evidence to workflow | Immutable `OcrScan.Frame` → existing workflows | Feature transitions and state machines | `runGeneration`, `captureSequence`, `admissionEpoch`, timestamp, package/window identity, geometry, and OCR transaction identity remain request-derived |
| Workflow to action | Existing workflow → `ActionGateway` | Android action primitives and lifecycle | OcrRuntime dispatches evidence only; no direct gesture, Back, node action, or mock-location path |
| Bitmap lifetime | `CaptureCoordinator` + `OcrRuntime` retain/release handoff | Outer service cleanup adapter where required | Setup failure, timeout, cancellation, late/duplicate callback, and shared-analysis paths require exactly-once cleanup |
| Deferred Xiaomi runtime | Separate runtime gate | No source ownership change | If unavailable: `R3 runtime gate = NOT TESTED — DEVICE UNAVAILABLE`; bounded smoke only when connected |

R3 does not move Feed, Planting, Expedition, Postcard, Reward, Mushroom, or
patrol state into OcrRuntime. Shared evidence helpers remain narrow and
behavior-preserving; PetalMatcher is not broadly split in this phase. See
[R3_OCR_RUNTIME_EXTRACTION.md](R3_OCR_RUNTIME_EXTRACTION.md).

## R5 overlay presentation extraction boundary

| Boundary | R5 owner | Service retains | Compatibility / evidence status |
|---|---|---|---|
| Overlay attachment and visibility | `OverlayHost` | Lifecycle and static bridge adapter | MainActivity bridge remains; device composition is a separate runtime gate |
| Settings forms and tabs | `OverlayHost` | Settings persistence and workflow callbacks | Tab/visibility changes are projection-only; no silent engine transition |
| Status and notices | `OverlayHost` | Workflow state and terminal decisions | `OverlayRunStatus` is projected; timing must remain source-compatible |
| Mushroom panel/map presentation | `OverlayHost` plus existing panel/map/store collaborators | Mushroom scan/patrol/location state and named commands | UI selection/map open-close do not start/pause/stop; R6 remains deferred |
| Capture presentation mask | `OverlayHost` token state plus `CaptureCoordinator` transaction | Android screenshot API and capture lifecycle | Late/stale restore rejected; clear/failure/destroy restore prior state |
| Return Reward ROI geometry | Existing Service hybrid path | ROI anchor, evidence, geometry and admitted action | Retained intentionally; no geographic-to-screen conversion |

The previous source presentation path could invoke pause while hiding the
overlay, tapping the floating icon, or opening settings. This is a reconciled
source defect against the frozen R5 target: presentation-only operations now
remain presentation-only. R5 does not authorize moving Mushroom normal scan
coordination, detector tuning, fixture changes, patrol, or location behavior.

R5 scope labels: Mushroom normal continuous scan and panel-close persistence
are `FEATURE NOT COMPLETE`; detector accuracy/negative Xiaomi fixture and
patrol/location E2E are `OUT OF SCOPE FOR R5`; runtime overlay/gameplay remains a
separate evidence gate. See
[R5_OVERLAY_PRESENTATION_EXTRACTION.md](R5_OVERLAY_PRESENTATION_EXTRACTION.md).

## R6 Mushroom scan extraction boundary

| Boundary | R6 owner | Service retains | Compatibility / evidence status |
|---|---|---|---|
| Explicit normal lifecycle and cadence | `MushroomWorkflowCoordinator` | Named UI command adapter and narrow scheduler bridge | Immutable session plus scan generation; 200 ms initial and 3 s normal-result cadence; source/JVM/build evidence only |
| Page gate and bounded detector job | `MushroomScanner` | OCR transaction adapter and existing detector instance | Page gate precedes detector; one job/source-hold pair; stale/cancelled delivery cannot publish current state |
| Scan UI projection and diagnostics | Coordinator / scanner | Overlay rendering and terminal diagnostic logging | `MushroomUiStore` remains a projection; `MushroomDiagnostics` semantics stay scanner-owned |
| Map, location and patrol | Existing Service/controller seam | Full ownership remains unchanged | R7 deferred; patrol requests the shared engine through current host callbacks and creates no parallel normal loop |
| Detector and gameplay action | Existing action-free `MushroomDetector` / no action owner | All current action boundaries | Templates/thresholds/fixtures unchanged; no auto tap, join, `GO`, or reward action |

See [R6_MUSHROOM_SCAN_EXTRACTION.md](R6_MUSHROOM_SCAN_EXTRACTION.md). Device,
accessibility and gameplay remain separate runtime gates.

## R1 capture/action characterization evidence (2026-09-14)

This additive section records current behavior without changing the R0/R0.5
ownership decisions above. See `R1_CAPTURE_ACTION_CHARACTERIZATION.md` for the
complete source/test contract and its explicit gaps.

| Boundary | Evidence source | Concrete evidence | R1 result |
|---|---|---|---|
| Screenshot queue and retry | `SOURCE`, `TEST`, `USER_REQUIREMENT` | `ScreenshotRequestQueue`, `ScreenshotRetryPolicy`, Service callback/timeout paths, queue/retry JVM tests | FIFO queue entry, late callback, and retry policies characterized; Android callback reentry remains unproven. |
| Capture identity and geometry | `SOURCE`, `TEST`, `USER_REQUIREMENT` | `CaptureCoordinator.Request`, `CaptureGeometry`, `OcrScan`, `ActionAdmission`, geometry/OCR/admission tests | Generation/capture/OCR/window/timestamp checks are characterized; R2 retains the request-time epoch through capture/OCR delivery and final action admission. |
| Bitmap cleanup | `SOURCE`, `TEST` | `copyBitmap`, `ActiveOcrTransaction`, `DeferredCleanup`, analysis jobs, cleanup/OCR tests | Exactly-once logical cleanup characterized; no R1 device/runtime branch proof. |
| Overlay capture | `SOURCE`, `TEST`, `RUNTIME` | Mushroom snapshot/mask and Reward ROI paths; policy/mask tests | Suppression/restoration policy characterized; `RUNTIME` evidence is absent for actual screenshot composition/failure restoration. |
| Final action admission | `SOURCE`, `TEST`, `USER_REQUIREMENT` | `ActionGateway`, gesture/global/node adapters and named-reason `ActionAdmissionTest` assertions | Gesture, global Back, node click, editable focus, and editable set-text use final admission before a platform primitive. |
| Coordinates | `SOURCE`, `TEST`, `OFFICIAL_GAME_BEHAVIOR` | `ScreenCoordinateTransform`, `CaptureGeometry`, `MapCoordinate`, transform/integrity tests | Bitmap/window/display/ROI/geographic spaces remain separate; no geographic-to-screen conversion. |

R1 is `PARTIAL` and `R2_BLOCKED`: moving capture/action ownership would require
guessing whether to preserve or repair the editable-input admission bypass and
the unpropagated request-time admission epoch. No production source change is
authorized by this evidence record.

## R1.1 Safety Seam Closure evidence (2026-09-14)

This additive section records only completed R1.1 source and JVM-test evidence;
it does not alter the R0/R0.5 ownership decisions.

| Boundary | Evidence source | Concrete evidence | R1.1 result |
|---|---|---|---|
| Editable node admission | `SOURCE`, `TEST` | `PetalAccessibilityService.performGameEditableNodeAction`, `setEditableText`, `focusGameEditableText`, `ActionAdmission.performIfAllowed`, `NodeActionAdmissionTest` | Focus and set-text have an immediate final admission; valid payload is preserved and invalid contexts execute zero node actions. |
| Cross-workflow input coverage | `SOURCE` | Feed `enterFeedNectarSearch`, Planting `enterPlantingFlowerSearch`, Expedition `handleDispatchPikminFilter`, and Postcard `enterPostcardPetalSearch` all route through the common editable-node boundary | Closed without four feature-specific gates. |
| Request-time epoch identity | `SOURCE`, `TEST` | `CaptureCoordinator.Request.admissionEpoch`, request callback contexts, `startOcrTransaction`, `ActiveOcrTransaction`, `OcrScanner`, `OcrScan.Frame`, `PetalAccessibilityServiceWorkflowTest`, `OcrScanTest` | Epoch `E1` is retained through OCR delivery and is rejected against current `E2` before workflow handling/action. |
| Screenshot request ownership and dispatch multiplicity | `SOURCE`, `TEST` | `ScreenshotRequestQueue.claimNext`, `PetalAccessibilityService.dispatchNextScreenshot`, `completeScreenshotRequest`, `ScreenshotRequestQueueTest.dispatchClaimAllowsOnePlatformDispatchPerLogicalRequest`, `lateCallbackCannotRestoreCapturePresentationForNewActiveRequest` | Forced reentry yields one logical request, one active transition, one platform dispatch, and one terminal completion; a late callback cannot restore capture presentation for a newer active request. |
| Existing gesture/node/Back paths | `SOURCE`, `TEST` | `dispatchGestureSafely`, Feed hold fresh-context flow, `clickGameNode`, `performGameGlobalAction`, `ActionAdmissionTest`, multi-stage tests | Existing final boundaries retained. |
| Overlay capture composition | `SOURCE`, `TEST`, `RUNTIME` | Existing capture visibility/mask/restore policy and tests | `RUNTIME_EVIDENCE_PENDING`; this is not a source-level R1.1 blocker. |

`testDebugUnitTest` and `lintDebug` passed after the closure.  R1.1 source/test
ambiguity is closed for capture request ownership, request/frame identity,
final action admission, game-mutating node actions, and screenshot dispatch
multiplicity. That R1.1 readiness enabled the present R2 implementation; R2
source/build verification and separate runtime evidence are recorded in
`R2_CAPTURE_ACTION_EXTRACTION.md`.
