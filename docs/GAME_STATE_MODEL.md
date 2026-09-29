# Game-State Model — R0.5 Proposal

## Status and boundary

This remains a proposed, deliberately small frame-state model for the current
`versions/3.0` implementation.  It does not create a new state machine,
change a detector, or replace a workflow-local session.  The current source
has several independent classifiers: `PlantingScreenAnalyzer`,
`FeedScreenAnalyzer`, `ExpeditionScreenAnalyzer`, `PostcardMatcher`,
`ReturnRewardDetector`, and `MushroomPageGate`.  R0.5 preserves that
fact and reconciles their terms against
[OFFICIAL_GAME_MODEL.md](OFFICIAL_GAME_MODEL.md).

`GameStateSnapshot` is an immutable value created from exactly one completed
`OcrScan.Frame` and its associated capture transaction.  Its one purpose is to
give a workflow a description of one captured frame before that workflow
chooses a transition.  It is not a global controller, does not contain a
`Bitmap`, and must not turn an overlay tab, requested location, patrol point, or
setting into game truth.

The table below describes proposed values, not a source enum.  In particular,
no literal shared `HOME_OR_MAP` value exists in the current Java source; it is an
R0 evidence aggregate only.

## Smallest realistic snapshot

```text
GameStateSnapshot
  frame: frame identity, capture geometry, capture time, OCR profile, tokens
  foreground: current game package/window identity and ActionAdmission result
  surface: one conservative proposed classification or UNKNOWN
  evidence: optional, feature-relevant facts from that same frame
            (planting controls; expedition screen/target; postcard page/target;
             reward target/warning; Mushroom page-gate result)
```

| Snapshot fact | Current source evidence | Why it belongs globally |
|---|---|---|
| Frame identity and token geometry | `OcrScan.TransactionId`, `OcrScan.Frame`, `CaptureGeometry` | Prevents a detector result from being detached from the screenshot that produced it. |
| Foreground/window/freshness | `ActionAdmission.FrameContext`, `ActionAdmission.CurrentState`, Service `currentGameWindow()` | Every action path needs the same safety facts. |
| Conservative surface | Existing feature analyzers and `MushroomPageGate` | Lets a consumer explicitly distinguish evidence from an unknown page. |
| Optional feature evidence | Existing analyzer result records | Avoids rerunning and reinterpreting the same screenshot inside a workflow. |

Do not put `AutomationStep`, `FeedStep`, `PostcardAutomation.Step`,
`ExpeditionDispatchSession.Stage`, `MushroomWorkflowState`,
`MushroomPatrolController.State`, retry counters, overlay visibility, settings
form values, requested/confirmed locations, patrol points, or a Mushroom
result into the snapshot.  They are workflow progress, UI, or asynchronous
control state rather than a fact observed on one game frame.

## Proposed surface vocabulary

A surface is conservative: `UNKNOWN` is preferable to inferring a transition.
The official-game mapping explains terminology only; it never claims that the
corresponding PikminX workflow implements the official feature.

| Proposed state | Current evidence | Official-game mapping | Proposed consumer(s) | Confidence | Missing fixture/runtime evidence |
|---|---|---|---|---|---|
| `GAME_FOREGROUND` / `NOT_GAME_FOREGROUND` | `ActionAdmission`, `currentGameWindow()`, package/window capture geometry | Not a gameplay domain; it is an Android safety fact. | Every gesture-producing workflow | High | Device changes of window ID/bounds during a delayed gesture. |
| `HOME_OR_MAP` | R0 aggregation of planting home/map anchors, Postcard `MAP` evidence, and `MushroomPageGate` map eligibility.  No shared source enum/classifier exists. | Could be Walk View or Bird's-Eye View; official docs prove those are distinct. | Proposed Planting entry, Postcard map navigation, Mushroom page gate | Low | Same-device Walk View, Bird's-Eye View, overlay-obscured map, and transition fixtures. |
| `PLANTING_MENU` | `PlantingScreenAnalyzer.Screen.PLANTING_MENU`, `PlantingControlEvidence`, node checks | Flower Planting menu | Flower Planting | High | Foldable/window-capture variants of start/stop controls. |
| `FEED_SURFACE` | `FeedScreenAnalyzer` nectar/search/detail/bloom evidence | Care: nectar feeding and petal collection | Care; existing Reward detail-page guard | Medium | Full-frame fixtures across nectar search, squad detail, and receipt sequence. |
| `EXPEDITION_LIST` | `ExpeditionScreenAnalyzer` list classification and target extraction | Expedition list/discovery surface | Expedition / Dispatch | High | Real-device list-scroll and focused-OCR recovery traces. |
| `EXPEDITION_DETAIL` | Detail marker plus `FlowerDetailActionDetector` action evidence | Expedition item detail; it can lead to Pikmin selection. | Expedition / Dispatch | High | Detail variants with incomplete OCR. |
| `PIKMIN_SELECTION` | `ExpeditionScreenAnalyzer` selection controls/counter | Expedition Pikmin selection; also occurs in other official flows, but source classifier is Dispatch-specific. | Expedition / Dispatch | High | Device proof for each configured selection method/type. |
| `EXPEDITION_RESULT` | `ExpeditionScreenAnalyzer` result text | Expedition result/return progression | Expedition / Dispatch | Medium | Result dismissal and return-to-list device trace. |
| `FLOWER_DETAIL` | `PostcardMatcher` page/target parsing and map-bubble detector | Potential Big Flower detail/direct-petal route; not proven by name alone. | Postcard acquisition; Reward's existing postcard branch | Medium | Screenshot sequence proving the official Big Flower route. |
| `POSTCARD_RECEIPT` | `PostcardMatcher`, `PostcardReturnGuard`, `PostcardAutomation.confirmReceiptExit` | Postcard artifact/reward receipt, not a standalone official upstream flow. | Postcard acquisition; Return Reward | Medium | Real receipt-exit timing and duplicate-receipt evidence. |
| `REWARD_CANDIDATE` | `ReturnRewardDetector`, `ReturnRewardRoi`, `ReturnRewardScanGuard`; nullable target/warning/close-up evidence | Returned item/reward source unresolved. | Return Reward | Medium | Armed-ROI device trace from upstream game event through receipt. |
| `MUSHROOM_ELIGIBLE` / `MUSHROOM_INELIGIBLE` | `MushroomPageGate` admits list evidence, generic `ExpeditionScreenAnalyzer.Screen.DETAIL`, an optional Mushroom-detail helper, or world-map evidence before detector delivery. | Admission is not proof of an official Mushroom detail or full Mushroom phase. | Mushroom normal and patrol scans | Medium | Screen-sequence fixtures that separate official discovery, detail, challenge, result, generic Expedition detail, and non-Mushroom map. |
| `UNKNOWN` | Any incomplete OCR/capture/classifier result | Required safe fallback | Every workflow | High | None; retaining it is the safe behavior. |

## Bird's-Eye View reconciliation

Officially, Bird's-Eye View is a distinct surface from Walk View.  The smallest
candidate distinction is:

```text
WALK_MAP
BIRD_EYE
```

R0.5 does **not** add either value.  Existing source has neither a Bird's-Eye
classifier nor a consumer that needs different behavior, and no fixture/runtime
pair distinguishes the views.  `HOME_OR_MAP` remains a conservative proposed
aggregate with low confidence.  A future change may add a split only when all
three conditions hold:

1. a concrete current/future PikminX consumer needs the distinction;
2. a frame-level classifier can identify it without using workflow or overlay
   state; and
3. Walk View/Bird's-Eye fixtures or device traces prove the distinction from
   `UNKNOWN`.

Bird's-Eye destination selection in official documentation is not authorization
to add navigation or remote-action automation.

## Mushroom official-screen reconciliation

The following labels are useful in the official domain map but are **not**
added to the snapshot vocabulary now:

| Official/R0.5 screen label | Why it is not a new snapshot state yet |
|---|---|
| `MUSHROOM_DISCOVERY` | The page gate can admit list/map-like evidence, but current scanner ownership does not establish a game discovery workflow. |
| `MUSHROOM_MAP` | The current Mushroom overlay map is a PikminX presentation surface, not evidence of an official game-map classification. |
| `MUSHROOM_DETAIL` | The page gate can admit generic Expedition detail without proving Mushroom detail; no current consumer needs a distinct state and no automatic join occurs. |
| `MUSHROOM_CHALLENGE` | Current scanner has no challenge/GO workflow or result parser. |
| `MUSHROOM_RESULT` | Current scanner publishes detector results, not official Mushroom challenge results/rewards. |

This preserves the useful source-level `MUSHROOM_ELIGIBLE` gate without
misrepresenting it as a full official Mushroom state model.

## Construction rules

```text
serialized screenshot request
  -> CaptureGeometry + transaction identity
  -> OCR/visual analysis of that same frame
  -> immutable GameStateSnapshot
  -> workflow-local state decides whether to request an admitted action
```

1. Only the shared capture transaction may create a snapshot.
2. Any action must recheck `ActionAdmission` immediately before dispatch; a snapshot
   is evidence, not permanent authorization.
3. A detector may populate evidence but cannot dispatch an action.
4. A workflow may request only analysis relevant to its local step; it cannot
   promote another workflow's local step into global state.
5. Overlay state is only masked/presented around capture.  It is never a
   surface-classifier input.

## Relation to current local state machines

| Local owner retained | Why it must remain local |
|---|---|
| Planting `AutomationStep` and search/stability counters | They encode retries, configured flower ordering, and start/stop verification, not a universally visible page. |
| Care `FeedStep` and `FeedHoldLifecycle` | Hold slices and harvest receipts are active gesture lifecycle state. |
| `ExpeditionDispatchSession` | Dispatch count, target confirmation key, selection method, and return verification span many frames. |
| `PostcardAutomation` and guards | Receipt counting/recovery is workflow progress and must not drive unrelated actions. |
| `ReturnRewardRoi` / `ReturnRewardScanGuard` | The ROI is user-armed, and target confirmation is session-scoped. |
| Mushroom workflow, patrol, and location controllers | Scan session, route point, location confirmation, and overlay projection have distinct asynchronous lifecycles. |

## Evidence gaps and adoption gate

R1 should not introduce a broad universal classifier.  It may characterize a
snapshot contract around existing `OcrScan.Frame`, `CaptureGeometry`, and
`ActionAdmission` tests.  Adding a new surface requires a source consumer plus
a fixture/runtime trace that distinguishes it from `UNKNOWN`.

The known `xiaomi_raw_game_map_no_mushroom_target` negative fixture is a
specific blocker for calling Mushroom detection generally accepted.  It does
not justify changing templates, thresholds, scoring, fixture expectations, or
this state vocabulary in R0.5.
