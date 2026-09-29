# Workflow State Machines — Current R0 Execution Graphs

## Reading rule

These are current source flows, not intended behavior diagrams.  All active
automation begins from a callback in the service-owned overlay/settings UI.
The shared capture path serializes screenshots and associates OCR/visual work
with a run generation, capture sequence, geometry and final action admission.
An arrow labelled `admitted action` means the source rechecks freshness,
foreground/window and geometry before sending an Android gesture or node
action.

## Shared frame/action backbone

```text
workflow command
  -> service mode/session state
  -> Handler schedule()/requestScan()
  -> ScreenshotRequestQueue -> takeGameScreenshot()
  -> CaptureGeometry + overlay mask/hide/restore as required
  -> OcrScanner / focused OCR / bounded FrameAnalysisExecutor job
  -> feature handler consumes immutable evidence
  -> ActionAdmission just before action
  -> accessibility node / gesture / global-back dispatch
  -> Handler schedules next frame, terminal state, or bounded retry
```

The individual workflows below do not own a second screenshot queue.  They do
own their local state, counters and feature-specific timing in
`PetalAccessibilityService` today.

## Care / Feed

**Official-game interpretation:** This is the current technical route for the
official care relationship: nectar feeding can bloom Pikmin and produce petals.
The graph is source truth for PikminX; it does not claim every official
care/fruit effect is automated.

**Current owner:** Service `FeedStep`, `FeedHoldLifecycle`, and Feed counters.
**Primary source regions:** `startFeedAutomation` (2501–2570),
`handleFeedTokens` (4206–4496), search/selection (4533–4926), hold
(5354–5704), and collection/switching (4989–5333).

```text
settings callback
  -> startFeedAutomation(input)
  -> WAITING_GAME_READY
  -> scan + FeedScreenAnalyzer evidence
  -> OPENING_NECTAR
  -> OPENING_NECTAR_SEARCH / CLEARING_NECTAR_SEARCH
  -> ENTERING_NECTAR_SEARCH -> CONFIRMING_NECTAR_SEARCH
  -> CLOSING_NECTAR_KEYBOARD
  -> SELECTING_NECTAR -> WAITING_NECTAR_PANEL_CLOSE
  -> ZOOMING_OUT -> READING_NECTAR_COUNT -> READING_CONSUMED_COUNT
  -> FEEDING
       -> admitted multi-slice hold gesture
       -> frame observations drive FeedHoldLifecycle release/continue
  -> COLLECTING
       -> locate bloom -> spiral harvest -> receipt/petal-gain observations
  -> SWITCHING
       -> next squad / next configured flower / terminal completion
```

Failure/recovery branches are local: missing target/search confirmation,
keyboard guard, maximum nectar-tap attempts, no-effect timeout, missing bloom
frames and hold-cleanup failure.  They end in `pause`, `stopWithError`, or a
bounded rescan; they are not global game states.

## Flower Planting

**Official-game interpretation:** This graph corresponds to official Flower
Planting: select petals, start/monitor/switch/stop while walking.  It is
separate from the official Big Flower direct-petal reward flow, which this
workflow does not own.

**Current owner:** Service `AutomationStep` plus planting search/stability
counters and `H10aPlantingAnalysis` job state.
**Primary source regions:** `startAutomation` (1517–1568), asynchronous
planting analysis (906–1055), token handling (4008–4205), and planting
navigation/start/stop (6766–7983).

```text
settings callback
  -> startAutomation()
  -> CHECKING_PLANTING_ENTRY
  -> shared screenshot + H10aPlantingAnalysis / PlantingScreenAnalyzer
  -> WAITING_INITIAL_PLANTING_MENU
  -> REVEALING_SEARCH_PANEL -> OPENING_SEARCH -> CLEARING_SEARCH
  -> ENTERING_SEARCH -> CLOSING_SEARCH_KEYBOARD
  -> SELECTING_SEARCH_RESULT -> VERIFYING_SELECTION
  -> CLOSING_SEARCH_AFTER_SELECTION -> WAITING_START -> VERIFYING_START
  -> WAITING_MENU_AFTER_START -> MONITORING
  -> low-count confirmation
       -> search next configured flower, or WAITING_STOP -> VERIFYING_STOP
  -> success / pause / error
```

The current action path uses accessibility node clicks where a labelled game
node is available and otherwise uses capture-derived tap/swipe paths.  The
source separately verifies start and stop; a state move must retain that
verification and must not alter configured flower order, thresholds, screen
geometry, OCR profile or H10a worker admission.

## Expedition / Dispatch

**Official-game interpretation:** The source stages align with the official
Expedition flow of item/list-detail selection, Pikmin selection, `GO`, return,
and collection.  `VERIFY_RETURN` verifies the Dispatch workflow's return-to-list
count; it is not evidence that the separate Return Reward collector is part of
the same session.

**Current owner:** `ExpeditionDispatchSession` plus Service selection/search
flags and counters.
**Primary source regions:** dispatch handler (5737–6765), focused-list
recovery (5924–6018), and `ExpeditionDispatchSession` stages.

```text
settings callback
  -> ExpeditionDispatchSession(target count, target mode, selection method)
  -> LIST_SEARCH
       -> scan list / settle bottom / bounded swipe / focused OCR recovery
       -> target evidence confirmed -> admitted target tap
  -> DETAIL
       -> detail action evidence -> admitted explore tap
       -> bounded unchanged-detail retry only
  -> SELECTION
       -> configured filter/type selection -> automatic or grid Pikmin choice
       -> admitted GO tap
  -> WAIT_RESULT
       -> result evidence -> dismiss/advance action
  -> VERIFY_RETURN
       -> list evidence confirms return and advances completed count
  -> LIST_SEARCH (next target) or terminal success
```

`ExpeditionDispatchSession.Stage` is exactly `LIST_SEARCH`, `DETAIL`,
`SELECTION`, `WAIT_RESULT`, and `VERIFY_RETURN`.  Its confirmation/timeouts
remain local even if snapshots later expose an expedition surface.

## Return Reward

**Official-game interpretation:** Officially, returned items can be collected
after flows such as Expeditions or Mushrooms, but no official page identifies
this ROI detector.  Current source makes it an independent collector with
postcard and nectar-warning branches and no Expedition count mutation.
Therefore its upstream official relationship remains unresolved; do not merge
it into Dispatch.

**Current owner:** Service ROI fields/counters plus `ReturnRewardRoi` and
`ReturnRewardScanGuard`.
**Primary source regions:** start/anchor/ROI methods (2679–2854) and reward
handler (8091–8274).

```text
settings callback
  -> show user anchor overlay
  -> user tap arms game-relative ReturnRewardRoi
  -> ROI overlay shown; each capture hides/restores it
  -> scan + ReturnRewardDetector inside armed ROI
  -> postcard-received branch?
       yes -> confirm postcard target -> admitted tap -> wait for exit
       no  -> nectar warning / squad-closeup / reward target evidence
               -> ReturnRewardScanGuard confirmation -> admitted tap
  -> settle delay / persistent-target rearm
  -> next scan until complete, timeout, pause, or error
```

The Reward path currently calls `PostcardMatcher` for its postcard branch and
`FeedScreenAnalyzer` for existing detail-page evidence.  That is current
cross-feature leakage, not a reason to make either workflow's state global.

## Postcard

**Official-game interpretation:** A postcard is an official reward/artifact
with several possible upstream sources.  The current source sequence of map
bubble -> flower detail -> petals -> Pikmin -> `GO` -> receipt is a strong
match for the official Big Flower direct-petal postcard route.  That live
screen identity still needs screenshot/device evidence; `PostcardAutomation` remains
the current source name.

**Current owner:** `PostcardAutomation`, `PostcardReturnGuard`, Service
search/recovery counters.
**Primary source regions:** start (2573–2618), handler (8275–8597), search
(8597–8845), receive/return (8846–9207).

```text
settings callback
  -> PostcardAutomation.start(limit, pot, Pikmin count)
  -> FIND_FLOWER -> map bubble evidence
  -> OPEN_FLOWER -> flower/detail evidence
  -> USE_PETALS -> optional ACCEPT_WARNING
  -> OPEN_PETAL_SEARCH -> ENTER_PETAL_SEARCH -> CLOSE_PETAL_KEYBOARD
  -> SELECT_PETAL -> TAP_NEXT / NEXT
  -> OPEN_SORT -> CHOOSE_FAVORITE -> SELECT_PIKMIN -> GO
  -> RECEIVE -> WAIT_RECEIPT_EXIT
  -> verified receipt exit increments count
  -> return to map / FIND_FLOWER, or terminal success
```

`PostcardAutomation.Step` owns the named stages.  Page recovery, missing
control frames, keyboard focus checks, petal-pot stability and receipt exit
verification are local recovery mechanics.  A migration must not count a
postcard before `confirmReceiptExit` evidence.

## Mushroom scan: first frame of the normal session

**Official-game interpretation:** Official Mushroom gameplay includes
discovery, detail, Join, selection, `GO`, challenge, and result.  This graph covers
only page-gated visual scanning and result publication by PikminX; it does not
tap, join, select, challenge, or parse official Mushroom results.

**Current owner:** Service Mushroom session state and
`MushroomWorkflowState`; overlay commands enter Service methods around
2019–2230.

```text
Mushroom overlay Start/Rescan
  -> startMushroomFinder(enable normal scan as requested)
  -> AutomationMode.MUSHROOM, new session/run state
  -> shared scheduled screenshot
       (Mushroom overlay visibility is captured/restored specially)
  -> MushroomPageGate using OCR/map evidence
       ineligible -> status/retry policy; detector is not delivered
       eligible   -> one MushroomAnalysisJob on FrameAnalysisExecutor
  -> MushroomDetector.detect(bitmap) [no actions]
  -> freshness/admission check
  -> MushroomUiStore publish results/no-results/error
  -> normal scan remains enabled; no mushroom target tap
```

The detector produces `MushroomDetectionResult` only.  It does not send a
gesture; the current source has no automatic mushroom-tap branch.

## Mushroom continuous scan

**Official-game interpretation:** This is repeated ownership of the same
PikminX discovery/detail evidence scan, not an official Mushroom challenge or
result loop.  The three-second cadence remains source behavior and is not
derived from official game documentation.

**Current owner:** same scanner/session state as the normal scan, with
`mushroomScanEnabled` true.

```text
normal scan enabled
  -> schedule(SCAN_INTERVAL_MILLIS = 3000 ms)
  -> page gate -> bounded analysis -> publish result/no-result
  -> if same session remains enabled and not patrol-owned
       -> schedule next 3-second scan
  -> Stop / error / stale run / service teardown cancels analysis and work
```

An ineligible page is not a detector result.  It travels through page/retry
handling and preserves the current overlay restoration and session checks.

## Mushroom map / patrol

**Official-game interpretation:** The map, mock-location, and patrol flow is
PikminX tooling around scans.  It is not evidence that the game is in official
Bird's-Eye View, and it does not authorize remote Mushroom joining or Big
Flower reward collection.

**Current owner:** Service map selection and confirmation fields, with
`MushroomPatrolController`, `MushroomLocationController`, and overlay map
types.

```text
Mushroom overlay Map tab
  -> MapSelection: point | two-point | rectangle | circle | route
  -> PatrolRouteGenerator -> bounded PatrolPoint list (500 m spacing)
  -> MushroomPatrolController.PREPARING
  -> MOVING -> MushroomLocationController / AndroidMockLocationDriver
  -> WAITING_LOCATION_CONFIRMATION (five-second bound)
  -> LOCATION_CONFIRMED -> STABILIZING (1.5 seconds)
  -> SCANNING -> requestFreshScan(session, point)
  -> WAITING_RESULT
       -> scan result must match session, point, target coordinate and tolerance
       -> next point, COMPLETED, PAUSED, STOPPED, or ERROR
```

When patrol ends, the Service restores the prior normal-scan ownership state:
normal scanning resumes only if it was enabled before patrol; a patrol-owned
engine stops otherwise.  Requested location, confirmed device location, scan
location, screen coordinates and optional assigned mushroom coordinates are
separate values.

## Cross-flow rules exposed by the graphs

| Rule | Current enforcement | Migration consequence |
|---|---|---|
| One capture queue | `ScreenshotRequestQueue` inside the Service | Do not create feature queues. |
| Fresh action only | `ActionAdmission` + `CaptureGeometry` just before dispatch | A snapshot cannot bypass the final gate. |
| Detector evidence is not an action | Mushroom detector is pure; other detector calls are immediately orchestrated by Service | Move decision/action routing out of detector code, not into it. |
| Feature session stays local | `FeedStep`, `AutomationStep`, dispatch/postcard/patrol state machines | Do not flatten them into one enum. |
| Overlay is not game evidence | masks/hide-restore paths | Preserve ordering around capture. |
