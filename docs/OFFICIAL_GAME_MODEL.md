# Official Pikmin Bloom Gameplay Model — R0.5

## Purpose and evidence boundary

This document records current **official game meaning**, not PikminX behavior.
The R0 source inventory remains authoritative for what PikminX currently
observes, automates, or leaves unsupported.  An official game page must never
be used as evidence that PikminX implements its matching feature.

The labels in backticks below are R0.5 documentation labels.  They are not
claimed to be official UI labels, source enums, or `GameStateSnapshot` values
unless another R0 document independently says so.

## Research record

Research date: **2026-09-15**.  Help Center freshness is recorded exactly as
the page displayed it on that date because those pages expose relative
"Last Updated" values.

| ID | Current official source | Freshness shown when checked | Used for |
|---|---|---|---|
| O1 | [Pikmin Bloom — How to Play](https://pikminbloom.com/en/gameplay) | No publication/update date shown | Official high-level care, petals, planting, and expedition overview. |
| O2 | [How to play Pikmin Bloom](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2854-how-to-play-pikmin-bloom/) | Last Updated: 21d | Map, fruit-to-nectar, petals, Flower Planting, and Expeditions. |
| O3 | [Bird's-Eye View](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/5119-bird-s-eye-view/) | Last Updated: 21d | Walk View switch, distant spots, pan, zoom, rotation, destination, and proximity limits. |
| O4 | [Items and the Effects](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3054-items-and-the-effects-1782190479/) | Last Updated: 21d | Nectar, petals, detectors, and expedition-item relationship. |
| O5 | [Planting flowers](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2855-planting-flowers/) | Last Updated: 4d | Flower Planting, Big Flowers, fruit expeditions, and nearby nectar collection. |
| O6 | [Use flower petals to make a Big Flower bloom](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/4878-use-the-flower-petals-to-make-a-big-flower-bloom/) | Last Updated: 2d | Direct Big Flower petal use, Pikmin selection, `GO`, nectar, and postcard rewards. |
| O7 | [Expeditions](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2860-expeditions/) | Last Updated: 20d | Item discovery, list/detail, Pikmin selection, `GO`, return, and garden collection. |
| O8 | [Mushrooms](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3039-mushrooms-1676271507/) | Last Updated: 4d | Mushroom discovery, details, joining, Pikmin selection, challenge, results, and rewards. |
| O9 | [Postcards](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2858-postcards/) | Last Updated: 21d | Postcards as keepsakes/rewards from Mushrooms, sometimes Expeditions, and friend exchange. |
| O10 | [Storage size of each item](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3040-storage-size-of-each-item/) | Last Updated: 21d | Pikmin, nectar, petal, seedling, postcard, and item capacity limits. |
| O11 | [Event Challenges](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3968-event-challenges/) | Last Updated: 4d | Home-screen entry, event rewards, storage outcomes, and level/account constraints. |
| O12 | [Required permissions to play Pikmin Bloom](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/4189-required-permissions-to-play-pikmin-bloom/) | Last Updated: 3d | Android location and physical-activity prerequisites. |
| O13 | [Release Information - v151](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/5539-release-information---v151/) | Last Updated: 17d | Current Mushroom-list and participant-detail UI changes. |

No release note was needed for a historical-version claim: the current Help
Center pages above directly describe every reconciled gameplay relationship.

### P1 Flower Planting official recheck — 2026-09-16

`OFFICIAL_DOCUMENTED`: the current [Planting flowers help page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2855-planting-flowers/)
showed “Last Updated: 4d.” It describes the path Home → lower-right flower
button → select petals → ▶️ start, and says planting continues until petals run
out or the user presses the game’s end button. It also warns that petals can be
consumed while flowers are not placed when stationary or in an ineligible area.
The current [I can't plant flowers help page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2877-i-can-t-plant-flowers-1636017814/)
showed “Last Updated: 21d” and documents lack of petals, movement speed, area,
and GPS/map conditions as possible game-side reasons that flowers do not
appear.

`UNRESOLVED`: official guidance does not define PikminX overlay automation,
screen coordinates, a guaranteed active-state visual marker, or atomic safe
stop semantics. A game gesture callback alone is not functional acceptance;
P1 requires the visible game transition and bounded Global Stop evidence.

### P2 Care / Feed official recheck — 2026-09-16

`OFFICIAL_DOCUMENTED`: the current [How to play Pikmin Bloom help page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2854-how-to-play-pikmin-bloom/)
describes the production path Squad → whistle → Pikmin Garden → fruit → nectar
→ feed Pikmin → flower bloom → collect petals. The current
[Pikmin help page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2857-pikmin/)
states that a flower's type and the collected petals depend on the nectar fed;
the current [Items and the Effects page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3054-items-and-the-effects-1782190479/)
states that non-white nectar takes one leaf-to-flower feed and yields one
petal. These establish the red/yellow acceptance relationship, not a claim
about PikminX matching or screen coordinates.

`DEVICE_OBSERVED`: authorized Xiaomi evidence on `2026-09-16` showed the real
normal Care carousel with basic Red nectar `337` / Red petals `1,149` and
basic Yellow nectar `819` / Yellow petals `1,094`. The Red controller opened
the real search surface, entered `紅色`, and reached its same-card count-matching
stage. Two bounded active runs ended through the existing Global Stop at
`26.17` and `28.69` seconds; neither selected a card or changed those Red
counts.
`UNRESOLVED`: selected Red/Yellow nectar, feed hold, bloom, collection,
progression, and actual feed safety remain unproven. The P2 correction budget
is exhausted, so this evidence does not authorize another build or a Yellow
attempt.

### R7 device-gate recheck — 2026-09-16

`OFFICIAL_DOCUMENTED`: the current [Mushrooms help page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3039-mushrooms-1676271507/)
showed “Last Updated: 4d” and describes Join, Pikmin selection, `GO`, results,
and rewards; the bounded R7 session must not cross those controls. The current
[required permissions page](https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/4189-required-permissions-to-play-pikmin-bloom/)
also showed “Last Updated: 4d” and identifies Android location services and
location permission as play prerequisites. These official statements establish
the test boundary and prerequisite only; they are not `DEVICE_OBSERVED` proof
of PikminX functionality or safety.

### R7 bounded device attempt — 2026-09-16

`DEVICE_OBSERVED`: the authorized Xiaomi accepted the signed 3.1.41/351
upgrade with `adb install -r`; Pikmin Bloom and the idle Mushroom overlay were
visibly opened. Two on-screen 地圖-tab attempts remained on 掃描, preserved in
[the device evidence](../releases/r7-3.1.41-351-20260916T072903Z/device-evidence/15-session-outcome.json).
No Mushroom Join, Pikmin selection, `GO`, ticket spend, purchase, mock patrol,
or other resource-changing action was invoked. `UNRESOLVED`: the source has a
Map-tab listener, but this evidence does not identify why the visible tab did
not activate; map-dependent R7 behavior remains untested.

## Task evidence vocabulary

| Label | Meaning |
|---|---|
| `OFFICIAL_DOCUMENTED` | Current official source meaning, URLs, and research date recorded here. It is not proof that PikminX supports the feature. |
| `DEVICE_OBSERVED` | A fresh, attributable physical-device observation through an authorized production path. ADB navigation or a log line alone is not a PikminX action/result. |
| `UNRESOLVED` | A required screen identity, prerequisite, account/version difference, outcome, or recovery path lacks the matching official or device evidence. |

For an authorized functional task, add only its relevant entry/exit path,
prerequisite/cost, visible controls, expected change, completion evidence, and
cancellation/recovery path to this document. Each scenario must separately
record the actual device/game/APK identity, permission/service state, and
bounded Stop/cleanup evidence. Set a maximum attempt count and duration before
starting; use fresh identifiable controls and before/after visible evidence.
ADB may assist navigation, screenshots, recording, and logs, but an ADB action
cannot count as a PikminX action and a log alone cannot prove the game effect.
Do not convert a unit, fixture, or instrumentation result into
`DEVICE_OBSERVED`.

## Official gameplay domain map

| Official domain | Official meaning relevant to this project | Relationship boundary |
|---|---|---|
| Walk View / map | The map shows the player's real-world location and visited places.  Walk View is the ordinary nearby map surface; it can lead to Flower Planting, nearby Big Flowers, Expeditions, and the Bird's-Eye toggle. | It is not interchangeable with Bird's-Eye View. |
| Bird's-Eye View | A distinct view entered from Walk View.  It displays distant Mushrooms, Big Flowers, and Special Spots; players can pan, zoom, rotate, inspect a spot, and set a destination.  It does not expand the range for joining a Mushroom or collecting Big Flower nectar. | It is an inspection/navigation surface, not remote completion authority. |
| Squad / seedlings | Plucked Pikmin join the squad; seedlings grow in the planter pack through walking. Squad selection can affect planting and task strength. | Do not infer a supported PikminX screen or action from the official relationship alone. |
| Pikmin care | Fruit can become nectar.  Feeding nectar blooms flowers on Pikmin; collecting those flowers produces petals. | Care produces the petal resource used by Flower Planting and can improve Mushroom strength indirectly through flowered Pikmin. |
| Flower Planting | Players select petals and start planting while walking.  Petals are consumed; planting can grow nearby Big Flowers and produce benefits such as fruit expeditions. | Flower Planting is separate from a Big Flower's detail/reward interaction. |
| Big Flowers | A nearby Big Flower can grow from Flower Planting.  A bloomed Big Flower can provide nectar, and the game also supports direct petal use on its detail screen. | Big Flowers are separate from Mushrooms.  Direct petals on a leaf/bud yield nectar plus a postcard; direct petals on an already-bloomed flower yield a postcard only. |
| Expeditions | A found item appears in the expedition list/map or Walk View.  The player selects it, selects Pikmin, taps `GO`, waits for the return, then collects returned items in the Pikmin Garden. | An expedition may yield a postcard, but a postcard is not guaranteed. |
| Mushrooms | A Mushroom can be found in the expedition list or adventure view.  The player opens its detail, joins, selects Pikmin, may feed nectar, taps `GO`, and later receives a result/rewards. | Mushroom rewards include fruit and a special result postcard; this does not make Mushroom and Big Flower the same domain. |
| Postcards | A digital keepsake that can come from Mushroom results, sometimes Expeditions, Big Flower direct-petal rewards, or friend exchange. | A postcard is a reward/artifact, not proof of one independent top-level game workflow. |
| Inventory / capacity | Pikmin, nectar, petals, seedlings, postcards, and some items have account-dependent storage rules; capacity can change the available reward outcome. | Do not dispose of, upgrade, purchase, or otherwise alter inventory in a test without explicit authorization. |
| Levels, events, settings, and permissions | Official level gates include Expedition (3), detector (4), Event Challenges (5), and Mushrooms (8). Event rewards and current UI can change; the current v151 note changes Mushroom-list/participant interactions. Android location and physical-activity permissions are gameplay prerequisites. | Installed game version, account level/capacity, event state, settings, and permission state must be observed for a real-device claim rather than inferred from this table. |
| Rewards / results | Mushroom results have star/reward semantics; Expedition and Big Flower rewards have their own return/claim surfaces. | Similar reward imagery alone does not establish a shared PikminX workflow. |

## Official relationships

```text
Care
  fruit -> nectar -> feeding -> petals

Flower Planting
  petals -> planting while walking
  planting near a Big Flower -> growth / possible fruit expedition

Big Flower
  nearby detail -> collect nectar when eligible
  direct petals -> choose petals -> choose up to five Pikmin -> GO
                -> postcard always; nectar too when leaf/bud

Expedition
  discovered item -> expedition list/map or Walk View -> detail
  -> choose Pikmin -> GO -> return -> collect item in Pikmin Garden

Mushroom
  discovery in expedition list/adventure view -> detail -> Join
  -> choose Pikmin -> optional nectar -> GO -> challenge completes
  -> result -> fruit/special-nectar/postcard reward

Postcard
  may be produced by Mushroom, sometimes Expedition, Big Flower reward,
  or friend exchange; it is not proof of a standalone upstream workflow.
```

## Mushroom screen relationship labels

The following labels let R0.5 discuss the full official game flow without
claiming a source enum or a currently supported PikminX automation:

| R0.5 label | Official relationship | Current PikminX claim allowed by R0 evidence |
|---|---|---|
| `MUSHROOM_DISCOVERY` | A Mushroom becomes discoverable in the expedition list or adventure view. | The page gate can admit list, generic detail, or map-like pages for scanning; it does not prove a complete discovery workflow. |
| `MUSHROOM_MAP` | A game map/adventure discovery surface may show a Mushroom. | No current PikminX game-map state is proven to distinguish this from other map views.  The PikminX overlay map is not the official game map. |
| `MUSHROOM_DETAIL` | Tapping a Mushroom shows details and a Join action. | The page gate also admits generic `ExpeditionScreenAnalyzer.Screen.DETAIL` without proving a Mushroom detail; no automatic join is documented. |
| `MUSHROOM_CHALLENGE` | After selection/optional feeding and `GO`, the battle proceeds. | Unsupported by the current Mushroom scanner scope. |
| `MUSHROOM_RESULT` | Completion shows stars and rewards, including a postcard. | Unsupported by the current Mushroom scanner scope. |

## Three-vocabulary reconciliation

| Official Pikmin Bloom concept | Current PikminX concept/class | Target PikminX domain | R0.5 disposition |
|---|---|---|---|
| Fruit | `ExpeditionTargetMode.FRUIT_AND_POT` and Expedition target handling; no dedicated fruit-receipt owner | `Expedition` / unresolved receipt | Do not infer that `ReturnReward` is the fruit collector. |
| Nectar | `NectarTemplateMatcher`, `FeedScreenAnalyzer`, feed handlers | `Care` | Current code's nectar evidence belongs to care. |
| Petals | `PetalMatcher`, `PetalCatalog`, `PetalSelection`, and Postcard petal selection | `Care` / `FlowerPlanting` | Shared resource/evidence, not a standalone official workflow. |
| Feeding nectar / collecting petals | `FeedStep`, `FeedHoldLifecycle`, `FeedScreenAnalyzer` | `Care` | Keep current source names; document the process as care. |
| Flower Planting | `AutomationStep`, `PlantingScreenAnalyzer`, `PetalSelection` | `FlowerPlanting` | Direct terminology match apart from code naming. |
| Expedition | `ExpeditionDispatchSession`, `Dispatch*`, `ExpeditionScreenAnalyzer` | `Expedition` | `Dispatch` is a current implementation term for the official Expedition flow. |
| Returned item / reward receipt | `ReturnReward*` | `RewardReceipt` | The collector is separate and ROI-based; its official upstream workflow is unresolved. |
| Big Flower direct-petal postcard reward | `PostcardAutomation`, `MapPostcardBubbleDetector`, `FlowerDetailActionDetector` | `BigFlowerPostcardAcquisition` | Source flow is consistent with this official sequence, but real screenshots are still required to prove the target screen. |
| Postcard artifact | `PostcardMatcher`, receipt/return guards | `PostcardArtifact` | Do not treat artifact handling as proof of a standalone official workflow. |
| Mushroom discovery/detail scan | `MushroomPageGate`, `MushroomDetector`, `MushroomWorkflowState` | `MushroomDiscoveryScan` | Current scope is page-gated detection/result publication, not a full challenge workflow. |
| Mushroom map/location/patrol tooling | `MushroomMapView`, `MushroomLocationController`, `MushroomPatrolController` | `MushroomPatrol` | PikminX ownership/tooling; it does not prove official Bird's-Eye or a full Mushroom phase. |
| Walk View versus Bird's-Eye View | Proposed `HOME_OR_MAP` evidence aggregation | No new target state yet | Officially distinct; current source has no shared surface enum or distinguishing fixture. |

## Big Flower naming audit

This audit classifies repository wording by evidence rather than renaming code.

| Current label / evidence | R0.5 classification | Reason |
|---|---|---|
| `Flower` in `allowedFlowers`/`currentFlower`/`targetFlower`, `PetalCatalog`, and Planting/Care workflows | Planting dependency | These are petal/flower-choice terms for Care/Flower Planting, not Big Flower detail evidence. |
| `FlowerDetailActionDetector` during active `PostcardAutomation` flower-navigation steps | Big Flower domain evidence, runtime-unconfirmed | The source sequence matches the official direct-petal Big Flower route, but the detector identifies a generic detail action and does not itself name the live object. |
| `map flower`: `MapFlowerDetector`, `MapSceneDetector`, `MapScaleTransform`, and inactive `MapSceneInventory.BIG_FLOWER` | legacy naming / UNRESOLVED | The current Service has no active production caller for this historical map-analysis cluster; its pixel candidates/inventory label do not prove a live Big Flower target. |
| `Postcard flower`: `MapPostcardBubbleDetector` comment plus active Postcard map/detail/receipt flow | Postcard automation evidence; likely Big Flower acquisition | The bubble is documented as belonging to the flower used for the most recently received postcard.  The full source sequence is consistent with direct Big Flower acquisition, but screenshots must identify it. |
| `Reward flower`: no active literal source owner; `ReturnReward*` has postcard and nectar-warning branches | UNRESOLVED | No active source type proves a Big Flower-specific reward collector.  These branches are insufficient to classify it as Big Flower, Expedition, or Mushroom completion. |


## Official game relationship versus PikminX automation ownership

| Question | Official game relationship | PikminX ownership conclusion |
|---|---|---|
| Does feeding create petals? | Yes: nectar feeding can bloom Pikmin and yield petals. | Care owns the current feed/collect route. |
| Does Flower Planting affect Big Flowers? | Yes: planting nearby can grow a Big Flower and can yield a fruit expedition. | Planting owns its current start/monitor/stop flow only; it does not own Big Flower reward acquisition. |
| Are Big Flowers Mushrooms? | No. | Keep their source evidence separate. |
| Is Dispatch an Expedition? | Officially, sending selected Pikmin with `GO` to retrieve an item is an Expedition. | The current `Dispatch` workflow maps to Expedition. |
| Is Return Reward an Expedition completion? | Official docs describe returned Expedition items being collected in Pikmin Garden, but they do not identify this PikminX detector. | Do not merge it into Dispatch: current source has an independent ROI collector and does not change Dispatch completion counters. |
| Is a Postcard a top-level game workflow? | No: it can be output from several paths or friend exchange. | Preserve `PostcardAutomation` as a source name; its current sequence is documented separately as likely Big Flower postcard acquisition. |
| Does current Mushroom scanning cover an official challenge? | No official page establishes that. | Current scope is scan/page gate/publication with no automatic Mushroom tap, join, `GO`, or result parser. |

## Bird's-Eye View reconciliation

Officially, `WALK_MAP` and `BIRD_EYE` are distinct concepts.  R0.5 does not
add either as a `GameStateSnapshot` surface: current source has no common
`HOME_OR_MAP` enum, no proven Bird's-Eye consumer, and no fixture/runtime
trace that separates the two views.  `HOME_OR_MAP` therefore remains a
conservative proposed aggregate in `GAME_STATE_MODEL.md`, with an explicit
fixture gap.  Add a split only after a concrete consumer needs different
behavior and a same-device Walk View/Bird's-Eye evidence pair can distinguish
them from `UNKNOWN`.

## Unresolved evidence needed from screenshots or a device

- A matched Walk View and Bird's-Eye View screenshot/trace, including the
  switch transition, to establish any screen classifier boundary.
- A screenshot sequence for the active `PostcardAutomation` route showing
  whether its map bubble and flower detail are the official Big Flower
  direct-petal flow; source sequencing alone cannot identify the live target.
- A Return Reward trace from its source event through Pikmin Garden/receipt to
  establish whether it is one generic return collector or spans multiple
  official upstream workflows.
- Mushroom screenshots for discovery, map, detail, challenge, and result,
  plus a verified device run.  The known negative-map false positives remain
  detector/runtime evidence and do not establish official game semantics.
- Evidence that an existing PikminX consumer needs a Bird's-Eye-specific
  action; official destination selection alone is not authorization to add
  automation.
