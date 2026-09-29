# PikminX R4.5 UI State / Command Matrix

**Status:** `R4.5 PASS (documentation / source-evidence only)`  
**Applies to:** later presentation work only; R5 is not started by this document.

## 1. Reading this matrix

| Marker | Meaning |
| --- | --- |
| `CURRENT / SOURCE` | Existing code establishes this behavior. |
| `TARGET / R5` | Required UI command/presentation contract for a separately authorized extraction. |
| `N/A` | Do not invent a command or state. |
| `NOT TESTED` | No device or gameplay validation is implied. |

The engine/service is the source of truth for `workflowRunning` and workflow phase. UI-local `overlayPanelExpanded`, selected tab, selected map sub-tab, and visibility are never evidence that a workflow ran. The UI sends an explicit command once, then waits for a source projection.

S1 adds one cross-workflow command boundary: `isAnyWorkflowActive()`, `whichWorkflowIsActive()`, and `requestGlobalStop(reason)` are Service-owned. An idle floating-icon tap keeps its presentation behavior; an active floating-icon tap emits global Stop once, and the Service waits for the actual owner cleanup before projecting idle. Duplicate same-owner and cross-owner Start commands are rejected without scheduling or mutating the active owner. Mushroom scan-to-patrol is an internal same-owner cadence transition. Drag, Collapse, workflow-tab changes, and scan/map-tab changes do not emit Stop.

## 2. Non-negotiable command semantics

| Operation | TARGET / R5 meaning | Current source qualification |
| --- | --- | --- |
| Collapse / close panel | Presentation only; retain engine state. | Current visibility/overlay opening paths can invoke broad terminal `pause()`. This is a divergence to remove only in later authorized implementation, never to mislabel. |
| Switch workflow tab / scan-map tab | Presentation only; preserve current engine state and result projection. | Source tab selection is presentation, but opening a non-`蘑菇` panel can currently clean up a running mode. |
| Pause | Preserve a resumable engine session and expose Resume. | Only patrol establishes this contract. |
| Resume | Continue the retained, source-authoritative session after re-admission. | Only patrol establishes this contract. |
| Stop | Terminal cleanup: cancel pending work and require fresh Start. | Broad current `pause()` behaves as terminal cleanup for several workflows. |
| Foreground lost | No game action while backgrounded; render engine wait/terminal state and require fresh evidence on return. | Mushroom scan has bounded wait/retry; no universal auto-resume promise exists. |
| Accessibility disconnected | Block or terminally stop; never automatically restart. | Service interrupt/destruction calls cleanup. |
| Capture masking | Temporarily hide exact pre-capture overlay snapshot and restore it. | Existing Mushroom capture policy already snapshots visibility. |

**Rule:** R5 MUST NOT call `pause()` solely because presentation changes. `collapse != pause != stop`.

## 3. Every runtime workflow: event comparison

The target column is the presentation contract. The source caveat prevents a UI label from claiming functionality the engine has not established.

| Workflow | Collapse / close / tab switch | Pause / Resume | Stop | Foreground loss | Accessibility disconnect | Duplicate Start |
| --- | --- | --- | --- | --- | --- | --- |
| 自動種花 | TARGET: no command, preserve source state. CURRENT: overlay paths may terminally clean up. | N/A; do not render resumable Pause. | Explicit terminal Stop only if source command is available. | Project source-safe terminal/wait result; no automatic resume. | Block/terminal; no restart. | Suppress UI duplicate; await source result. |
| 花瓣生產 | Same as 自動種花. | N/A. | Same. | Source-safe terminal/wait only. | Same. | Same. |
| 明信片收集 | Same as 自動種花. | N/A. | Same. | Source-safe terminal/wait only. | Same. | Same. |
| 自動派遣 | Same as 自動種花. | N/A. | Same. | Source-safe terminal/wait only. | Same. | Same. |
| 回程領取 | Same as 自動種花; ROI selection is not a run-state change. | N/A. | Same. | Source-safe terminal/wait only. | Same. | Same. |
| 蘑菇 scan | TARGET: close/switch only changes presentation. | N/A; current Stop is terminal. | Explicit Stop publishes terminal stopped projection. | `WAITING_FOR_GAME` with bounded retry; no game action. | Terminal/block; no restart. | Source reports already running; UI suppresses duplicate. |
| 蘑菇 patrol | TARGET: close map/switch tab keeps patrol and route cursor. | Supported: Pause retains cursor/observations; Resume re-admits fresh movement/location work. | Explicit terminal Stop discards resumable patrol session. | No actions while backgrounded; wait/re-admit only when source allows. | Terminal/block; no restart. | Suppress duplicate; active patrol owns route. |

## 4. Generic matrix: non-resumable workflows

This `TARGET / R5` projection applies to 自動種花, 花瓣生產, 明信片收集, 自動派遣, and 回程領取. It is deliberately generic because their engine internals are not being re-specified by R4.5.

| Projected state | Meaning / visible UI | Start | Pause / Resume | Stop | Collapse / switch tab | Foreground or accessibility loss |
| --- | --- | --- | --- | --- | --- | --- |
| `IDLE` | Configurable, no active session. | Allowed after local validation and engine admission. | N/A. | N/A. | Presentation only. | Remain idle; refresh readiness later. |
| `STARTING` | Command emitted, awaiting engine projection. | Disabled / idempotent. | N/A. | Only if source can safely cancel. | Presentation only. | Show source outcome; do not retry automatically. |
| `RUNNING` | Engine reports active work. | Disabled. | N/A; never call terminal cleanup Pause. | State-gated explicit terminal command. | Presentation only. | No game action; show source-safe wait/terminal result. |
| `WAITING` | Engine holds a permissible wait (for example a source-defined precondition). | Disabled. | N/A. | State-gated. | Presentation only. | Continue only if engine publishes eligibility; otherwise terminal/block. |
| `PAUSED` | **Not a valid presentation state for these workflows.** | N/A. | N/A; do not fabricate Resume. | N/A. | N/A. | N/A. |
| `BLOCKED` | Required permission, service, location, page, or admission condition is absent. | Disabled; show next safe route. | N/A. | N/A or state-gated. | Presentation only. | Recheck only on explicit return/refresh or engine update. |
| `ERROR` | Source terminal/error projection. | Fresh Start only if source permits. | N/A. | Dismiss/terminal cleanup only. | Presentation only. | No automatic restart. |
| `COMPLETED` | Terminal success projection. | Fresh Start. | N/A. | N/A. | Presentation only; preserve result summary. | No change. |
| `STOPPED` | Explicit terminal stop projection. | Fresh Start. | N/A. | Idempotent / unavailable. | Presentation only. | No change. |

No generic `Pause` button is permitted merely because an internal method is named `pause()`: current source uses that method for terminal reset/cleanup outside the patrol controller.

## 5. Historical R7 蘑菇 scan state-command matrix (suspended)

`MUSHROOM = TEMPORARILY_DISABLED` overrides this retained matrix: no scan,
Rescan, tab, map, patrol, location, or mock-location command is currently
admissible. S1 Global Stop remains the sole Mushroom-related action for a
stale active owner.

| `MushroomUiState.Status` | Visible user state | Start | Rescan | Stop | Collapse / tab switch | Foreground loss / return |
| --- | --- | --- | --- | --- | --- | --- |
| `IDLE` | 等待開始 | Allowed after source admission. | N/A. | N/A. | Presentation only. | Recheck only on explicit Start. |
| `STARTING` | 正在準備掃描 | Disabled. | Disabled. | Allowed. | Presentation only. | Source may publish wait/error/stopped; no UI retry. |
| `WAITING_FOR_GAME` | 等待 Pikmin Bloom | Disabled. | State-gated only. | Allowed. | Presentation only. | Source bounded retry (1.5 s); fresh foreground/page evidence required. |
| `INVALID_PAGE` | 請先進入蘑菇／地圖畫面 | Disabled until source allows. | Allowed after user changes page. | Allowed. | Presentation only. | Return does not auto-tap/navigate; page gate runs again. |
| `CAPTURING` | 正在擷取畫面 | Disabled. | Disabled. | Allowed. | Presentation only; capture snapshot owns visibility. | Source handles outcome; never claim a result. |
| `ANALYZING` | 正在分析 | Disabled. | Disabled. | Allowed. | Presentation only. | Source handles outcome; no UI retry. |
| `RESULTS` | 已完成掃描 + result/provenance list | Fresh Start only as source permits. | Allowed. | Allowed. | Preserve results and selected scan/map tab. | On return, retain historical projection until source updates. |
| `NO_RESULTS` | 未找到蘑菇 | Fresh Start only as source permits. | Allowed. | Allowed. | Preserve no-result outcome and reason/time where available. | Same. |
| `RETRY_WAIT` | 準備重新掃描 | Disabled. | Disabled while retry in flight. | Allowed. | Presentation only. | Source schedules retry; no UI timing promise. |
| `STOPPING` | 正在停止 | Disabled. | Disabled. | Idempotent / disabled. | Presentation only. | Await terminal projection. |
| `STOPPED` | 已停止 | Fresh Start. | N/A. | Idempotent / unavailable. | Presentation only. | No auto-restart. |
| `ERROR` | 掃描錯誤 | Fresh Start if admitted. | Only if source exposes retry safely. | Allowed / dismiss. | Presentation only. | No auto-restart. |

Page-gate failures are not detector `NO_RESULTS`. `UNAVAILABLE`,
`USER_SELECTED`, `AUTHORITATIVE`, and `CALIBRATED_ESTIMATE`
coordinate provenance remains attached to results across scan/map presentation
changes. A map-view navigation command may be offered only for
`USER_SELECTED` or `AUTHORITATIVE` values; it never starts
patrol or injects mock location.

## 6. Historical R7 蘑菇 patrol state-command matrix (suspended)

| `MushroomPatrolController.State` | Visible user state | Map editing | Start / Pause / Resume | Stop | Collapse / close map / tab switch | Foreground and location rule |
| --- | --- | --- | --- | --- | --- | --- |
| `IDLE` | No patrol; route editable. | Allowed. | Start after route/location readiness. | N/A. | Presentation only. | Show readiness; no action. |
| `PREPARING` | Checking route and location readiness. | Locked. | Start disabled; no Pause yet. | Allowed. | Presentation only. | Await source readiness. |
| `MOVING` | Moving/requesting current route point. | Locked. | Pause allowed if controller exposes it; Resume N/A. | Allowed. | Presentation only. | Do not call game actions while not foreground. |
| `WAITING_LOCATION_CONFIRMATION` | Requested location awaits confirmation. | Locked. | Pause allowed; Resume N/A. | Allowed. | Presentation only. | Requested is not confirmed. |
| `LOCATION_CONFIRMED` | Source confirmed route-point location. | Locked. | Pause allowed; Resume N/A. | Allowed. | Presentation only. | Confirmation is tolerance-based source fact. |
| `LOCATION_CONFIRMATION_FAILED` | Confirmation failed. | Locked until terminal/retry decision. | Pause N/A. | Allowed. | Presentation only. | Explain source failure; no fake confirmation. |
| `STABILIZING` | Source stabilization delay. | Locked. | Pause allowed. | Allowed. | Presentation only. | No progress completion claim. |
| `SCANNING` | Scan at route point. | Locked. | Pause allowed. | Allowed. | Presentation only. | Fresh page/foreground evidence still controls scan. |
| `WAITING_RESULT` | Awaiting scan result. | Locked. | Pause allowed. | Allowed. | Presentation only. | Await source outcome. |
| `PAUSED` | Cursor/observations retained. | Locked. | Resume allowed; Start disabled. | Allowed. | Presentation only. | Resume returns through fresh movement/location admission. |
| `COMPLETED` | Route terminal completion. | Route editable after session fully terminal. | Fresh Start with route. | N/A. | Presentation only. | Preserve observations. |
| `STOPPING` | Terminal patrol cleanup. | Locked. | Disabled. | Idempotent / disabled. | Presentation only. | Await stopped projection. |
| `STOPPED` | Explicit terminal stop. | Route editable. | Fresh Start. | Idempotent / unavailable. | Presentation only. | No auto-resume. |
| `ERROR` | Source terminal/blocking error. | Locked until terminal resolution. | Fresh Start only when source allows. | Allowed / dismiss. | Presentation only. | No automatic restart. |

Current patrol Pause cancels pending work while retaining route cursor and observations. Resume deliberately returns through `moveCurrentPoint()` and therefore fresh location/capture admission. Stop is terminal and cancels the patrol session. Map geometry cannot be changed while patrol is active.

## 7.1 R7 map/navigation command contract

### Temporary Mushroom suspension override

`MUSHROOM = TEMPORARILY_DISABLED` supersedes this historical R7 map/navigation
contract. The visible 蘑菇 tab is disabled and cannot select `掃描` or `地圖`.
Start, Rescan, map, coordinate, Search, patrol, Pause, Resume, and any
mock-location path are unavailable. S1 Global Stop remains available only to
terminate a stale active Mushroom owner; it must not re-enable or resume that
owner. The retained R7 commands below are history, not currently admissible
product actions.

| Command / event | Required result | Forbidden side effect |
| --- | --- | --- |
| Open 蘑菇 → 地圖 | Obtain current real GPS when usable, centre Leaflet, and show current position; otherwise show loading/unavailable while keeping the map usable. | Random, hard-coded, stale, patrol, mock, or Mushroom coordinate fallback. |
| 目前位置 | Refresh/obtain current real GPS and recenter the viewport. | Create a patrol point, inject mock location, scan, or start patrol. |
| Place/address Search | Select a candidate and move only the Leaflet viewport. | Patrol, mock location, Mushroom coordinate creation, or provenance mutation. |
| 定位座標 / 前往座標 | Parse and validate latitude [-90,90], longitude [-180,180], then centre/zoom Leaflet. | Invalid movement, 0,0 fallback, or game/player movement. |
| Pan / zoom / select or edit POINT, TWO_POINT, AREA, ROUTE | Change only map presentation/selection. | Implicit patrol start or mock-location movement. |
| Explicit 開始巡航 | Start the existing patrol state sequence after selection and readiness checks. | Reinterpret as normal scan, Join, GO, generic teleport, or detector-only Start. |

Invariant: MAP_VIEW_CENTER != REQUESTED_MOCK_LOCATION !=
CONFIRMED_MOCK_LOCATION != PATROL_POINT != MUSHROOM_GEOGRAPHIC_LOCATION.
Search remains SEARCH_PROVIDER_DECISION_REQUIRED until a product-approved
provider boundary exists; no provider is approved by this matrix.

## 7. Command admission and idempotency

| User command | Preconditions displayed by UI | UI dispatch rule | Expected source projection |
| --- | --- | --- | --- |
| Start a non-Mushroom workflow | Valid saved/input configuration; readiness summary; no conflicting active automation. | Emit once; immediately show `STARTING`, not `RUNNING`. | Engine admits, blocks, errors, completes, or reports active phase. |
| Start 蘑菇 scan | Game/page admission is source-owned; detector availability exists. | Emit once; disable duplicate Start. | `STARTING`, then page-gated capture/analyze/result state or terminal error. |
| Rescan 蘑菇 | A state that permits rescan; prior stale analysis is invalidated by source epoch. | Emit once; disable while pending. | `RETRY_WAIT` / capture / analysis projection. |
| Stop 蘑菇 scan | Active scan state. | Emit once; render `STOPPING`. | `STOPPED` terminal projection. |
| Start patrol | Valid immutable route, no conflicting automation, fine location/system location/mock selection/provider readiness as required. | Emit once; lock map edits. | `PREPARING` then movement/confirmation. |
| Pause patrol | Active patrol state supported by controller. | Emit once; prevent repeated pause. | `PAUSED` with retained cursor/observations. |
| Resume patrol | `PAUSED` only. | Emit once; prevent repeated resume. | Fresh movement/location admission. |
| Stop patrol | Active / paused patrol state. | Emit once; render `STOPPING`. | `STOPPED` terminal projection. |
| Navigate map to coordinate | Result coordinate source is `USER_SELECTED` or `AUTHORITATIVE`. | Emit once through existing map/UI route. | Map/location presentation update only; not a patrol Start or mock-location movement. |
| Clear/remove map geometry | No active patrol; user confirms destructive change. | Mutate through existing selection owner only. | Updated selection projection. |

Every command stays visible with one of: enabled, disabled plus reason, in-flight, completed, or rejected. The UI does not fire speculative retries on timers. A source rejection is not hidden by resetting controls to idle.

## 8. Page gate, capture, and visibility restoration

| Moment | Required UI behavior |
| --- | --- |
| Game not foreground | Render `WAITING_FOR_GAME` for the source-supported Mushroom path; prompt user to return. Do not interact with the game. |
| Wrong page | Render `INVALID_PAGE`; distinguish it from no result. |
| Capture begins | Preserve exact icon/panel/notice visibility snapshot, hide capture overlays, retain state and semantic focus context. |
| Capture ends | Restore the captured snapshot exactly; do not infer a new collapsed/expanded state. |
| Analysis result | Publish result/no-result/error only from source data; retain coordinate provenance. |
| User collapses during capture | Presentation intent may be queued by later implementation, but must not corrupt the source capture snapshot or issue Stop. |

Active floating-icon tap is the one exception to ordinary presentation-only icon behavior: emit the narrow global Stop callback once; the overlay does not clean up locally. The Service stops every active owner, cancels stale callbacks/session work, and publishes stable idle only after cleanup.

## 9. Persistence and reattachment matrix

| Data | Authority / durability | Reopen or process reattach behavior |
| --- | --- | --- |
| SettingsStore configuration | Source-backed persisted preferences | Reload values; Save remains separate from Start. |
| Overlay visibility preference | Source-backed persisted preference | Restore only when platform/service attachment permits; it is not a workflow resume signal. |
| Selected UI tab / panel expansion | Presentation state | May restore opportunistically, but never alter engine state or claim a session. |
| Mushroom UI projection | Process-local store | Re-query service; do not claim persistence across process death. |
| Patrol cursor / observations | Controller-owned active session | Only call it resumable if source still projects `PAUSED`; otherwise show terminal/no active session. |
| Results and diagnostic history | Source-projected / policy-dependent | Mark historical/stale when freshness is unknown; do not fabricate a live result. |

## 10. Accessibility and error presentation rules

- Announce command transition, blocking cause, scan/patrol phase, result count, and terminal outcome with text in addition to colour.
- Each disabled command names its blocking precondition, especially accessibility, precise location, system location, mock-location selection, page gate, or active-workflow conflict.
- Destructive Stop is visually and semantically separate from Collapse/Close.
- Busy states retain a readable status and cannot be mistaken for a successful completion.
- Errors retain safe source detail and a precise next action; only expose Retry where the underlying source supports it.

## 11. R5 implementation guardrails

R5 may build a UI adapter around these matrices. It MUST NOT change detector/OCR/capture/timing/action logic, synthesize engine phases, treat a tab as Start, use `pause()` for presentation-only events, add auto-tap/navigation, or promise general resumable Pause. Runtime/device proof remains `NOT TESTED` until a separately authorized validation run produces it.
