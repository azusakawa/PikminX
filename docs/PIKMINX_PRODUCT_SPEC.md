# PikminX Product & Architecture Specification

## R0.5 status and evidence discipline

This is the Phase R0 baseline reconciled with official Pikmin Bloom gameplay
terminology.  It changes no implementation, tests, templates, thresholds,
timing, or workflow behavior.

The evidence types below stay separate throughout the R0 documentation:

| Evidence type | What it may establish | What it may not establish |
|---|---|---|
| `SOURCE` | Current PikminX responsibilities, call paths, state, and names. | Official game meaning or successful device execution. |
| `USER_REQUIREMENT` | Requested product/reconciliation boundary and R0 guardrails. | A source behavior or runtime result. |
| `OFFICIAL_GAME_BEHAVIOR` | Meaning and relationships in the current Pikmin Bloom game. | That PikminX implements, detects, or automates it. |
| `RUNTIME_EVIDENCE` | What a particular build/device/fixture execution showed. | General behavior beyond that evidence set. |

The current implementation under
`code/app/src/main/java/com/pikminx/helper` remains the truth for current
PikminX behavior.  Target architecture remains a proposal until a later
refactor phase implements and verifies it.  The known negative-map Mushroom
false positive remains unresolved; R0.5 does not propose detector, scoring,
template, threshold, or fixture-oracle changes.

## Official Game Model

`OFFICIAL_GAME_BEHAVIOR` is documented in
[OFFICIAL_GAME_MODEL.md](OFFICIAL_GAME_MODEL.md), including source links,
research date, displayed freshness, official relationships, and unresolved
screen evidence.  Its core model is:

```text
Care: fruit -> nectar -> feeding -> petals
Flower Planting: petals -> planting while walking -> Big Flower growth / possible fruit expedition
Expedition: found item -> list/detail -> Pikmin selection -> GO -> return -> collect
Mushroom: discovery -> detail -> Join -> Pikmin selection -> GO -> result/rewards
Postcard: a possible artifact/reward from several upstream game paths
```

Officially, Walk View and Bird's-Eye View are distinct: Bird's-Eye supports
distant spot inspection, pan, zoom, rotation, and destination selection, but
not remote Mushroom joining or distant Big Flower nectar collection.  Big
Flowers are also separate from Mushrooms.  These are game-domain facts, not
PikminX capability claims.

| Official concept | Current PikminX name(s) | Target PikminX domain | R0.5 rule |
|---|---|---|---|
| Fruit | `ExpeditionTargetMode.FRUIT_AND_POT` and Expedition target handling; no dedicated fruit-receipt owner | `Expedition` / unresolved receipt | Fruit is an official item concept; do not infer that `ReturnReward` is its collector. |
| Nectar | `NectarTemplateMatcher`, `FeedScreenAnalyzer`, feed handlers | `Care` | Preserve the current source names and use care as the product-domain name. |
| Petals | `PetalMatcher`, `PetalCatalog`, `PetalSelection`, and Postcard petal selection | `Care` / `FlowerPlanting` | Shared resource/evidence, not a standalone game workflow. |
| Feeding nectar / collecting petals | `FeedStep`, `FeedHoldLifecycle`, `FeedScreenAnalyzer` | `Care` | Preserve source names; feeding is the current technical route across the official nectar/petal relationship. |
| Flower Planting | `AutomationStep`, `PlantingScreenAnalyzer`, `PetalSelection` | `FlowerPlanting` | Preserve the current Planting implementation label. |
| Expedition | `Dispatch*`, `ExpeditionDispatchSession`, `ExpeditionScreenAnalyzer` | `Expedition` | `Dispatch` is the implementation term, not a different game domain. |
| Returned item / reward receipt | `ReturnReward*` | `RewardReceipt` | Do not infer an upstream official workflow from the detector name. |
| Big Flower direct-petal postcard reward | `PostcardAutomation`, map-bubble/detail detectors | `BigFlowerPostcardAcquisition` | Source sequence is compatible with the official flow; live target identity remains a runtime gap. |
| Postcard artifact | `PostcardMatcher`, receipt/return guards | `PostcardArtifact` | An artifact is not proof of an independent top-level workflow. |
| Mushroom discovery/detail scan | `MushroomPageGate`, `MushroomDetector` | `MushroomDiscoveryScan` | Current scanner does not imply join/challenge/result support. |
| Walk View versus Bird's-Eye View | proposed `HOME_OR_MAP` aggregate | no new state | Keep `UNKNOWN` until a source consumer and distinguishing evidence justify a split. |

## PikminX Product Scope

### Five-main-functions stabilization override — 2026-09-16

The active stabilization queue is Flower Planting, Care / Feed, Expedition,
Postcard, then Return Reward. It is a functional-stability loop, not an
ownership migration: R7 and R8–R14 do not continue automatically.

`MUSHROOM = TEMPORARILY_DISABLED`. Keep the 蘑菇 entry visible but disabled;
it must not transition into scan or map UI, start a scan or patrol, or invoke
location/mock-location behavior. Retain the existing Mushroom implementation,
templates, settings, and data for a future explicitly authorized decision.
S1 Global Stop remains able to terminate a stale Mushroom workflow.

The following table is grounded in `SOURCE` plus the stated R0/R0.5 user
requirements.  Official terms orient the product boundary but do not rewrite
the existing source evidence. Its Mushroom rows are retained historical source
description only; `MUSHROOM = TEMPORARILY_DISABLED` makes none of their
commands currently available.

| Major PikminX feature | Official game domain | User goal | Current implementation | Target architecture | Out of scope | Runtime evidence status |
|---|---|---|---|---|---|---|
| Care / Feed | Care: nectar, feeding, petals | Feed configured squads, collect petals, and progress through current bounded rounds. | Service feed handlers, `FeedScreenAnalyzer`, `FeedHoldLifecycle`, and `NectarTemplateMatcher`. | `care` workflow owns feed rounds/hold lifecycle; vision remains evidence-only. | New care mechanics, altered hold timing, or a generic game-state machine. | Source/target mapping only in R0.5; device gesture behavior is a separate gate. |
| Flower Planting | Flower Planting; petal resource relation to Big Flowers | Choose configured petals, start/resume, monitor, switch, and stop the existing planting flow. | Service `AutomationStep`, `PlantingScreenAnalyzer`, `H10aPlantingAnalysis`, `PetalMatcher`, and `PlantingFlowPolicy`. | `planting` owns planting session/state and requests observations/actions. | Big Flower direct-reward automation, changed flower order, OCR, geometry, or timing. | Source evidence only for this reconciliation. |
| Expedition / Dispatch | Expedition | Find an existing target, select Pikmin, dispatch, verify result/return, and repeat to current count. | Service dispatch family plus `ExpeditionDispatchSession` and `ExpeditionScreenAnalyzer`. | `expedition` owns dispatch session and target confirmation. | A separate generic reward collector, changed selection/recovery semantics, or automatic non-Expedition flows. | Source evidence shows verified screen transitions; no new R0.5 device claim. |
| Return Reward | Returned item/reward receipt; exact upstream domain unresolved | Collect current user-armed ROI targets while preserving postcard and nectar-capacity branches. | Independent Service ROI/counter path plus `ReturnRewardDetector`, `ReturnRewardRoi`, and `ReturnRewardScanGuard`.  It does not advance Expedition counters. | `reward` owns only its ROI/session and uses named evidence adapters where later characterization proves a need. | Merging into Expedition, asserting a universal return domain, or changing warning/receipt behavior. | Source proves a separate collector shape; screenshots/device evidence must identify upstream game source(s). |
| Postcard acquisition | Likely Big Flower direct-petal postcard acquisition; postcard itself is an artifact | Run the current map-bubble/detail/petal/Pikmin/receipt route with verified counts. | `PostcardAutomation`, `MapPostcardBubbleDetector`, `FlowerDetailActionDetector`, `PostcardMatcher`, and receipt guards. | Preserve the technical `postcard` owner while documenting its product boundary as `BigFlowerPostcardAcquisition`. | Renaming source classes, assuming all postcards use this route, or changing receipt timing. | Source sequence matches the official direct-petal pattern; target-screen identity needs runtime screenshots. |
| Mushroom scan / continuous scan | Mushroom discovery/detail evidence only | Scan eligible game frames, publish immutable detections, and optionally repeat at the existing cadence. | `MushroomPageGate`, `MushroomDetector`, `MushroomWorkflowState/Policy`, and UI store; no automatic tap. | `mushroom.scan` owns page gate, scan session, and publication; detector stays action-free. | Automatic Join/GO, challenge/result parsing, detector tuning, or fixture-oracle changes. | Known negative-map false positives remain unresolved; static/build/fixture/device gates remain distinct. |
| Mushroom map, location, and patrol | PikminX map/location tooling; not proof of official Bird's-Eye View | Present results and coordinate assignments; patrol-only mock-location movement and scans remain behind explicit patrol Start. | Overlay map plus `MushroomLocationController`, `MushroomPatrolController`, and location/patrol data. | Separate map presentation, location adapter, patrol state machine, and scanner. | Claiming Bird's-Eye support, changing mock-location rules, or conflating game and overlay maps. | Runtime confirmation/location behavior remains a separate device gate. |

### Historical R7 map/location product contract (suspended)

This retained contract is not authorization for a current product action while
`MUSHROOM = TEMPORARILY_DISABLED`.

蘑菇 → 地圖 starts at the device's current real GPS when a usable fix is
confirmed; it never invents an initial coordinate. 目前位置, place/address
Search, 定位座標 / 前往座標, pan, zoom, and editing POINT, TWO_POINT, AREA,
or ROUTE are map-only operations. MAP_VIEW_CENTER, requested and confirmed
mock locations, patrol points, scan/player location, and Mushroom geographic
coordinates are separate facts. A Mushroom detected while scanning from
patrol point P is not assigned P automatically.

The only transition into location/mock-location workflow is explicit 開始巡航
after selection and readiness. The safe default first POINT may use current
confirmed real GPS. The established patrol order remains READY → MOVING →
WAITING_LOCATION_CONFIRMATION → LOCATION_CONFIRMED → STABILIZING → SCANNING,
with Pause/Resume and Global Stop retaining their existing semantics.

Search is a dedicated boundary outside MushroomMapView. The approved current
default is OpenStreetMap Nominatim, selected through the existing R4
RemoteConfig ownership (`provider`, `endpoint`, `enabled`) with a built-in
fallback. The native provider layer—not Leaflet—enforces explicit-submit-only,
one-at-a-time, one-request-per-second request spacing and a small in-memory
repeated-query cache. It identifies PikminX in its User-Agent, uses the
user/application locale, requests only `q`, `format=jsonv2`, `limit=5`, and
`addressdetails=1`, and maps valid results to display name, latitude,
longitude, and provider identity without retaining a Nominatim `place_id` as
PikminX identity.

The UI states that a submitted place/address/landmark query is sent to the
configured provider and displays OpenStreetMap/Nominatim attribution. It does
not send current GPS, device identifiers, patrol history, Mushroom scan
results, or unrelated user data. There is no autocomplete, periodic query,
bulk/systematic geocoding, or polygon request. A disabled/unsupported provider,
empty result, or network failure leaves the map unchanged; public Nominatim
must be remotely replaced by a centrally throttled proxy before multi-user
traffic needs an application-wide rate limit. Search changes only the viewport
and does not mutate Mushroom detection provenance. Historical records remain
historical, with source/build/artifact, OFFICIAL_DOCUMENTED, DEVICE_OBSERVED,
and UNRESOLVED evidence labelled separately.

## Target architecture principles

1. **One Android adapter, not one god object.**
   `PetalAccessibilityService` remains the `AccessibilityService` lifecycle
   adapter and Android gesture/screenshot endpoint.  It must not remain the
   owner of every feature state machine, detector decision, overlay layout,
   map, or location session.
2. **Observations before actions.** Capture/OCR/pixel/template code returns
   immutable evidence.  Workflows decide transitions.  A single action gateway
   enforces `ActionAdmission`, fresh capture geometry, foreground game window,
   and gesture dispatch.
3. **One shared capture transaction contract.** Screenshot sequence,
   run-generation, OCR request identity, overlay masking, timeout, cleanup,
   and admission epoch remain one contract.  Features do not create
   independent screenshot queues.
4. **Small global state; domain-local sessions.** A `GameStateSnapshot`
   describes a captured game frame and nullable evidence.  `FeedStep`,
   `AutomationStep`, `PostcardAutomation.Step`, `ExpeditionDispatchSession`,
   and patrol state remain workflow-local.
5. **Presentation is a projection.** Overlay tab/notice/visibility renders
   workflow state.  A button may issue an explicit command, but a rendered UI
   state must not silently become engine truth.
6. **Location is Mushroom-specific unless evidence proves otherwise.** Screen
   geometry, requested map coordinates, confirmed mock location, and a
   Mushroom's optional assigned geographic coordinate are distinct facts.
7. **No speculative abstraction.** The target modules in `ARCHITECTURE.md`
   correspond to concrete current responsibilities.  R0.5 proposes no generic
   workflow framework, universal detector hierarchy, new dependency, or code
   rename.

## Non-goals for R0 and the first migration boundary

- No behavior redesign, detector tuning, OCR language/profile change, screen
  coordinate change, timing change, or test-oracle change.
- No conversion away from Android Accessibility Service, Android `Handler`,
  ML Kit OCR, native overlay views, or the existing Leaflet asset path.
- No deletion based only on filename, age, or a unit-test-only caller.
- No claim that a static/build check proves real device gestures or in-game
  automation.
- No Bird's-Eye-specific state or automation without a current consumer and
  distinguishing fixture/runtime evidence.

## Durable invariants to preserve in every migration phase

| Invariant | Current mechanism | Why it remains a target constraint |
|---|---|---|
| A gesture is fresh and foreground-bound. | `ActionAdmission`, `CaptureGeometry`, current game-window checks. | Prevents a stale screenshot or changed window from triggering a game action. |
| Capture resources have one owner until all async users finish. | `DeferredCleanup`, OCR transaction lifecycle, analysis-job retains/releases. | Avoids bitmap use-after-cleanup and leaks. |
| Screenshot requests are serialized and retry only known transient failures. | `ScreenshotRequestQueue`, `ScreenshotRetryPolicy`, Service timeout path. | Ordering and retry timing affect every workflow. |
| Overlay pixels do not contaminate game detection. | `ScreenshotOverlayMask`, Mushroom/Reward overlay hide/restore paths. | Overlay presence must not create game evidence. |
| Mushroom scan is page-gated before template results are published. | `MushroomPageGate` before `MushroomDetector` delivery. | Template-like pixels exist on non-Mushroom game screens. |
| Patrol scan follows confirmed location and stabilization. | `MushroomLocationController`, confirmation policy, `MushroomPatrolController`. | A requested mock location is not proof of the device's confirmed location. |
| Receipt/return counts advance only after verification. | `PostcardAutomation.confirmReceiptExit`, Expedition session confirmation. | Avoids double counts after transitions or retries. |

## Proposed bounded target vocabulary

```text
platform  capture  vision  interaction  overlay  diagnostics  update
planting  care  expedition  reward  postcard  mushroom  location  map  patrol
```

These are logical ownership boundaries, not new Gradle modules, dependencies,
or package moves.  R0.5 adds the product-domain labels `FlowerPlanting`,
`BigFlowerPostcardAcquisition`, and `RewardReceipt` only to avoid
conflating official game concepts with the present technical names.  The first
safe implementation step remains a characterization boundary.

## Evidence gaps that constrain the proposal

- A matched Walk View/Bird's-Eye screenshot pair and a concrete consumer are
  required before splitting the proposed `HOME_OR_MAP` aggregate.
- The current Postcard route needs real screenshot/device evidence before it
  can be called a proven Big Flower direct-petal flow rather than a strong
  source-derived match.
- Return Reward needs an upstream-to-receipt trace before it can be mapped to
  Expedition completion, a generic collector, or multiple official flows.
- No unified bitmap-to-OCR-to-page-state fixture set exists across workflows.
- The known negative Xiaomi map fixture returns Mushroom hits despite no
  visible target.  It blocks a general detector-acceptance claim and does not
  justify source/test changes here.
- Device timing, overlay behavior, screenshot callbacks, location
  confirmation, and gesture execution remain runtime evidence rather than
  source-only facts.

## R0 repository mapping contract

This specification remains paired with the source-backed R0 mapping of the
active `code/app/src/main/java/com/pikminx/helper` tree.  The mapping covers
101 production Java type files.  It is an inventory and migration proposal,
not evidence that any package move has occurred.

| R0 document | Question answered | Evidence boundary |
|---|---|---|
| `ARCHITECTURE.md` | What owns work today, especially the Accessibility Service? | Production callers and method families. |
| `FEATURE_MATRIX.md` | Which observed product domain owns each current type? | Current runtime responsibility plus R0.5 domain annotations. |
| `GAME_STATE_MODEL.md` | What small cross-workflow frame state is justified? | Existing OCR, visual, geometry, and admission evidence only. |
| `WORKFLOW_STATE_MACHINES.md` | How do active workflows actually execute? | Current Service call paths; R0.5 adds interpretation without changing graphs. |
| `TRACEABILITY_MATRIX.md` | What should happen to each current type/method family? | R0 target ownership, risk, dependencies, and explicit evidence provenance. |
| `REFACTOR_PLAN.md` | What can move incrementally without changing behavior? | Existing tests plus explicit runtime gaps; R1 remains characterization-only. |

The implementation remains authoritative for current behavior.  In particular,
a `DELETE-CANDIDATE` label in an R0 document is not removal authorization; it
means static reference search found no active production caller and
runtime/reference evidence is still required.
