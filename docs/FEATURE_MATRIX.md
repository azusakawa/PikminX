# Feature Matrix — Current Production Inventory

## Method

This inventory covers all **101** production Java type files in
`code/app/src/main/java/com/pikminx/helper`; test, generated and APK artifact
paths are excluded.  Classification follows the current responsibility and
production callers, not the filename.  `Service` below means
`PetalAccessibilityService`; `none-prod` means the R0 production reference
search found no caller outside the type itself (tests may still call it).

## R0.5 product-domain annotations

The 101-type inventory below remains source-traced and unchanged.  This
supplement records product terminology without renaming a type, changing its
current responsibility, or treating official documentation as implementation
evidence.

| Current technical owner | Official game domain | Target PikminX domain | Boundary / evidence limit |
|---|---|---|---|
| `PetalAccessibilityService` | None: cross-domain Android orchestration | platform/workflow coordination | The Service is not an official game feature and remains the current central owner. |
| `FeedHoldLifecycle`, `FeedScreenAnalyzer`, feed handlers | Care: nectar, feeding, petals | `Care` | Current `Feed` naming maps to care; no behavior change implied. |
| `PlantingScreenAnalyzer`, `PlantingFlowPolicy`, `PetalSelection` | Flower Planting | `FlowerPlanting` | Separate from Big Flower direct-detail rewards. |
| `ExpeditionDispatchSession`, `ExpeditionScreenAnalyzer`, `Dispatch*` | Expedition | `Expedition` | `Dispatch` is the technical name for the official item-retrieval flow. |
| `ReturnReward*` | Returned item/reward receipt; upstream domain unresolved | `RewardReceipt` | Independent ROI collector; source does not tie its counts to Expedition completion. |
| `PostcardAutomation`, `MapPostcardBubbleDetector`, `FlowerDetailActionDetector` | Likely Big Flower direct-petal postcard acquisition | `BigFlowerPostcardAcquisition` (technical owner remains `postcard`) | Source sequence is a strong match, but runtime screenshots must identify the target screen. |
| `PostcardMatcher`, receipt/return guards | Postcard artifact/reward | `PostcardArtifact` | A postcard can have several official upstream sources. |
| `MushroomPageGate`, `MushroomDetector`, workflow state/policy | Mushroom discovery/detail evidence | `MushroomDiscoveryScan` | No source evidence of automatic Join, `GO`, challenge, or official result parsing. |
| Mushroom overlay map/location/patrol types | PikminX support tooling, not proof of Bird's-Eye View | `MushroomPatrol` | Do not conflate the overlay map with the official game map. |
| `PlantingScreenAnalyzer`, `PostcardMatcher`, `MushroomPageGate` | Walk View versus Bird's-Eye View | no new target state | Their independent map-like evidence does not create a shared `HOME_OR_MAP` source state. |
| `MapFlowerDetector`, `MapSceneDetector`, `MapScaleTransform` | UNRESOLVED / legacy naming | `legacy.map-analysis` | No active Service caller proves a Big Flower or other live game interpretation. |


## Platform — 4

| Type | Observed current responsibility and callers |
|---|---|
| `MainActivity.java` | Android launcher/permission/settings/update shell; Android calls it, and it uses `SettingsStore`, update/config classes and static Service overlay bridge methods. |
| `SettingsStore.java` | SharedPreferences settings persistence; called by Activity and Service. |
| `RemoteConfigClient.java` | Fetches/caches feature configuration and gates starts; called by Activity, Service and update-related UI. |
| `RemoteConfigStateModel.java` | Config lifecycle/value state; owned by `RemoteConfigClient`. |

## Capture — 8

| Type | Observed current responsibility and callers |
|---|---|
| `CaptureGeometry.java` | Capture window/display bounds and action-safety geometry; used by Service, OCR and diagnostics. |
| `ScreenCoordinateTransform.java` | Converts screenshot/window/display coordinates; used by Service, OCR and reward ROI logic. |
| `ScreenshotOverlayMask.java` | Masks overlay regions in a captured image; invoked by Service capture path. |
| `ScreenshotRequestQueue.java` | Serializes queued screenshot requests and active request; owned by Service. |
| `ScreenshotRetryPolicy.java` | Decides retry/backoff for screenshot failures; called by Service. |
| `DeferredCleanup.java` | Retain/release cleanup for asynchronously analyzed capture resources; called by Service jobs. |
| `FrameAnalysisExecutor.java` | Bounded single-worker frame analysis executor; called by Service planting/Mushroom jobs. |
| `GeometryValidation.java` | Validates capture/OCR geometry discrepancies; externally called by `OcrScanner`. |

## Vision/OCR — 10

| Type | Observed current responsibility and callers |
|---|---|
| `OcrScanner.java` | ML Kit recognizer, OCR transaction and callback lifecycle; instantiated by Service. |
| `OcrScan.java` | OCR profiles, transaction identity, token/frame/transform model; used by Service, scanner and diagnostics. |
| `OcrRuntimeDiagnostics.java` | OCR recognizer/queue/timeline evidence; used by scanner and Service. |
| `PetalMatcher.java` | OCR token normalization, flower/petal selection/count parsing; used by Service across Feed/Planting/Postcard and by OCR-facing analyzers. |
| `PetalPotDetector.java` | Finds petal-pot labels/counts in visual/OCR evidence; used by Feed/Postcard paths. |
| `NectarTemplateMatcher.java` | Template evidence for nectar selection; used by Service Care path. |
| `NectarTemplateMatchCache.java` | Cache backing nectar matching; owned by matcher. |
| `FlowerDetailActionDetector.java` | Pixel action-target evidence on a flower detail screen; called by Service Postcard path. |
| `MapFlowerDetector.java` | Map color/component candidate detector; used by the historical map-scene cluster. |
| `MapPostcardBubbleDetector.java` | Pixel detector for map postcard bubbles; called by Service and Postcard matcher. |

## Game State — 3

| Type | Observed current responsibility and callers |
|---|---|
| `ActionAdmission.java` | Immutable frame/current-state evidence and final action admission; used by Service, Feed hold, Dispatch, planting admission and diagnostics. |
| `ObservationStability.java` | Multi-frame candidate stabilization; Service owns instances for Planting/Postcard confirmation. |
| `SwitchGuard.java` | Repeated-observation/cooldown guard; used by Service Planting and Care switching. |

## Workflow Coordination — 2

| Type | Observed current responsibility and callers |
|---|---|
| `PetalAccessibilityService.java` | Current central coordinator for Android adaptation, capture/OCR/action infrastructure, all workflow state, overlay, Mushroom location/patrol, diagnostics and feature starts.  Android instantiates it; internal overlay callbacks start every workflow. |
| `AutomationStartGuard.java` | Guards delayed starts against stale runs; called by Service. |

## Care — 3

| Type | Observed current responsibility and callers |
|---|---|
| `FeedHoldLifecycle.java` | Multi-slice hold gesture lifecycle and cleanup window; Service Feed handler owns it. |
| `FeedScreenAnalyzer.java` | Feed/nectar/detail/bloom/receipt visual evidence; Service Feed path calls it; Reward currently reuses its detail evidence. |
| `FeedSettingsInput.java` | Validated feed configuration value; Service reads it from settings UI. |

## Flower Planting — 9

| Type | Observed current responsibility and callers |
|---|---|
| `CardHighlight.java` | Pixel/geometry evidence for map planting entry and controls; used by planting analysis and Service. |
| `H10aAnalysisAdmission.java` | Asynchronous planting-analysis admission guard; Service calls it before delivery. |
| `H10aPlantingAnalysis.java` | Bounded worker-side planting analysis and search-control evidence; dispatched by Service. |
| `PetalCatalog.java` | Current flower/petal category list; used by Planting, Care, Postcard and selection UI. |
| `PetalSelection.java` | Ordered configured flower selection; Service planting UI/workflow uses it. |
| `PlantingControlEvidence.java` | Immutable planting controls/screen evidence; Service consumes it. |
| `PlantingFlowPolicy.java` | Entry/start/low-count transition decisions; called by Service and planting analysis. |
| `PlantingScreenAnalyzer.java` | Home/map/menu/control classification; called by Service and H10a path. |
| `PlantingSearchCloseGuard.java` | Bounded close/search state guard; owned by Service planting path. |

## Expedition — 6

| Type | Observed current responsibility and callers |
|---|---|
| `DispatchPikminType.java` | Dispatch Pikmin filter enum; Service settings/selection reads it. |
| `DispatchSelectionMethod.java` | Dispatch selection-mode enum; Service settings/selection reads it. |
| `ExpeditionDispatchSession.java` | Dispatch stage, confirmation, count and bounded retry state machine; Service owns a session. |
| `ExpeditionRemainingCount.java` | Validated remaining dispatch count; used by settings/Service. |
| `ExpeditionScreenAnalyzer.java` | List/detail/selection/result classification and target detection; Service/dispatch session use it; Mushroom page gate currently depends on it. |
| `ExpeditionTargetMode.java` | Target-category enum; Service and analyzer use it. |

## Mushroom — 11

| Type | Observed current responsibility and callers |
|---|---|
| `MushroomCaptureOverlayPolicy.java` | Mushroom-specific capture overlay visibility snapshot/policy; Service uses it around scans. |
| `MushroomDetectionId.java` | Stable scan/capture/hit identity; UI result and Service map actions use it. |
| `MushroomDetectionResult.java` | Immutable detector result/stats; passed from detector worker to Service. |
| `MushroomDetector.java` | Template/color Mushroom detection; Service worker calls `detect`; it dispatches no actions. |
| `MushroomHit.java` | Detector hit value; returned by detector/result classes. |
| `MushroomObservation.java` | Result plus confirmed scan location/patrol context; Service/patrol use it. |
| `MushroomPageGate.java` | Eligible-page check before Mushroom detection; Service scan path calls it. |
| `MushroomScreenBounds.java` | Capture-relative Mushroom screen bounds; held by UI result/observations. |
| `MushroomTemplateCatalog.java` | Loads and validates Mushroom templates; constructed by detector. |
| `MushroomWorkflowPolicy.java` | Scanner transition/delivery policy; Service calls it. |
| `MushroomWorkflowState.java` | Scanner state enum; Service owns the current value. |

## Postcard — 9

| Type | Observed current responsibility and callers |
|---|---|
| `PostcardAutomation.java` | Postcard step, configured limit and receipt count state machine; Service owns it. |
| `PostcardBubbleDetectionPolicy.java` | Gates map-bubble detection by current Postcard step/page; Service uses it. |
| `PostcardMatcher.java` | Page/target/petal OCR matching; Service Postcard path uses it and Reward reuses its receipt evidence. |
| `PostcardPageRecovery.java` | Page recovery decision helper; Service Postcard path uses it. |
| `PostcardPotCatalog.java` | Canonical postcard pot names/colors; used by Service, matcher, settings and Petal catalog. |
| `PostcardRemainingCount.java` | Validated remaining postcard count; Service/settings use it. |
| `PostcardReturnGuard.java` | Receipt-return frame guard; Service owns it. |
| `PostcardSettingsInput.java` | Validated Postcard settings value; Service reads it. |
| `PostcardTiming.java` | Receipt-return timing calculation; Service uses it for delays. |

## Reward — 3

| Type | Observed current responsibility and callers |
|---|---|
| `ReturnRewardDetector.java` | Reward/squad-closeup target pixel evidence; Service Reward path uses it; Expedition analyzer also references it. |
| `ReturnRewardRoi.java` | User-armed game-relative ROI model; Service Reward path owns it. |
| `ReturnRewardScanGuard.java` | Multi-frame reward/complete/target confirmation guard; Service owns it. |

## Overlay UI — 5

| Type | Observed current responsibility and callers |
|---|---|
| `OverlayRunStatus.java` | Validated overlay run-status projection; Service renders it. |
| `OverlayWindowPolicy.java` | Overlay window/keyboard/notice layout policy; Service uses it. |
| `MushroomOverlayPanel.java` | Current Mushroom overlay UI and command callbacks; Service creates/disposes it. |
| `MushroomUiState.java` | Immutable overlay-facing Mushroom scan/map/result state; held by UI store. |
| `MushroomUiStore.java` | Process-local Mushroom UI state/listeners; Service publishes, overlay listens. |

## Map — 4

| Type | Observed current responsibility and callers |
|---|---|
| `MapSelection.java` | Point/two-point/area/route selection state; Service, patrol and Mushroom UI use it. |
| `MapTileProvider.java` | Leaflet tile URL/attribution/zoom configuration; used by `MushroomMapView`. |
| `MushroomMapBridge.java` | WebView-to-native Mushroom map callback bridge; created by map view/overlay. |
| `MushroomMapView.java` | Leaflet WebView map presentation; owned by Mushroom overlay. |

## Location — 6

| Type | Observed current responsibility and callers |
|---|---|
| `AndroidMockLocationDriver.java` | Android mock provider/location adapter; constructed by Service and used by Mushroom location controller. |
| `MapCoordinate.java` | Latitude/longitude value object; used by Mushroom map, location and patrol types. |
| `MockLocationReadiness.java` | Permission/provider/mock-location readiness evidence; Activity and Service call it. |
| `MushroomCoordinateSource.java` | Provenance enum for an assigned Mushroom coordinate; held by UI result. |
| `MushroomLocationConfirmationPolicy.java` | Location confirmation/tolerance decision helper; Service/location controller use it. |
| `MushroomLocationController.java` | Requested/current mock-location state and listener orchestration; Service owns it. |

## Patrol — 3

| Type | Observed current responsibility and callers |
|---|---|
| `PatrolPoint.java` | Indexed geographic patrol point; map, route and controller use it. |
| `PatrolRouteGenerator.java` | Generates bounded route points for a `MapSelection`; Service invokes it. |
| `MushroomPatrolController.java` | Host-driven patrol state machine; Service supplies its host callbacks. |

## Update — 3

| Type | Observed current responsibility and callers |
|---|---|
| `ApkUpdateManager.java` | Download/verify/install APK session; Activity starts it and receiver completes it. |
| `InstallStateModel.java` | Update install/session validation state; manager, Activity and receiver use it. |
| `UpdateInstallReceiver.java` | Android install-status broadcast adapter; declared in manifest and forwards to update logic. |

## Diagnostics — 4

| Type | Observed current responsibility and callers |
|---|---|
| `AdmissionDiagnostics.java` | Action/capture admission and latency snapshots; Service records it. |
| `MushroomDiagnostics.java` | Mushroom queue/result/stale metrics; Service scanner records it. |
| `UsageTelemetryClient.java` | Emits workflow/OCR/gesture telemetry; Service owns it. |
| `WorkflowDiagnostics.java` | Workflow duration/counter snapshots; Service records it. |

## Shared Utility — 4

| Type | Observed current responsibility and callers |
|---|---|
| `BoundedLatencyStats.java` | Bounded latency statistics; diagnostics, OCR and queues use it. |
| `SearchKeyboardGuard.java` | Input-method/game-focus guard; Service creates distinct instances for Feed, Planting, Dispatch and Postcard. |
| `SettingsInput.java` | Bounded generic settings value/parser; Service settings UI uses it. |
| `TextNormalizer.java` | OCR text normalization helper; `PetalMatcher` and `PostcardMatcher` use it. |

## Legacy / Unknown — 4

| Type | Observed current responsibility and callers |
|---|---|
| `MapSceneDetector.java` | Historical map flower/Mushroom scene detector; `none-prod`, but tests reference it.  `DELETE-CANDIDATE` only. |
| `MapSceneInventory.java` | Historical map object inventory model; `none-prod`, but tests reference it.  `DELETE-CANDIDATE` only. |
| `MapScaleTransform.java` | Historical map-image scale helper; `none-prod`.  `DELETE-CANDIDATE` only. |
| `PostcardPotScanGuard.java` | Older postcard pot scan guard; `none-prod`, with dedicated tests.  `DELETE-CANDIDATE` only. |

## Inventory conclusions

- **101 / 101** production type files are classified above.
- No Mushroom scan path runs in the Planting token branch; the commonality is
  capture/OCR infrastructure, not a shared workflow.
- `PetalAccessibilityService` is the only type that crosses every major
  product domain and is the primary future split point.
- `Legacy / Unknown` is deliberately small and does not authorize deletion.
