# PikminX R4.5 UI/UX Specification Freeze

**Freeze status:** `R4.5 PASS (documentation / source-evidence only)`  
**R5 handoff:** `R5_UI_READY` — do not begin R5 under this change.  
**Scope:** UI information architecture, screen ownership, visual-state contracts, and command semantics only. No production, test, resource, WebView asset, template, colour, string, build, detector, OCR, capture, coordinate, action, timing, or workflow-engine change is authorized by this document.

### Programme baseline carried into this freeze

`R0 PASS`; `R0.5 PASS`; `R1 PARTIAL`; `R1.1 PASS`; `R2 SOURCE PASS`; `R2.1 PARTIAL / R3_READY`; `R3 SOURCE/BUILD PASS`; `R4 SOURCE/BUILD PASS`; `R4 runtime NOT TESTED`; `R5_READY` was an earlier roadmap handoff. This document refines the UI-specific handoff to `R5_UI_READY`; it does not upgrade any runtime evidence.

## 1. Evidence and normative language

| Marker | Meaning |
| --- | --- |
| `CURRENT / SOURCE` | Read from the R4.5 source baseline. |
| `CURRENT / HISTORICAL SCREENSHOT` | A supplied older artifact; it is not fresh device validation. |
| `TARGET / R5` | Presentation contract for a later, separately authorized R5 extraction. |
| `UNRESOLVED` | Not established by source; R5 must preserve the current safe behavior rather than invent it. |
| `NOT TESTED` | No runtime, device, gameplay, callback, overlay-coordinate, or accessibility-assistive-technology claim. |

The source baseline places `MainActivity` at the app-navigation and system-settings boundary, while `PetalAccessibilityService` owns the floating overlay and workflow execution. `MushroomUiStore` is a process-local UI projection; it is not the workflow engine. Existing source uses a broad method named `pause()` as a terminal cleanup path for several workflows. This specification deliberately distinguishes that current implementation fact from the target words **Pause**, **Stop**, and **Collapse**.

| Source anchor | Evidence used here |
| --- | --- |
| `MainActivity.buildScreen`, `buildHomeScreen`, `buildAutomationScreen`, `buildSettingsScreen` | Current app tabs, entries, settings, permission/location/update routes. |
| `PetalAccessibilityService.applyOverlayVisibility`, `showOverlay`, `showSettingsOverlay` | Current icon/panel lifecycle and the `pause()` coupling divergence. |
| `MushroomOverlayPanel`, `MushroomUiState`, `MushroomUiStore` | Scan/map UI projection, states, results, and explicit commands. |
| `MushroomPageGate`, capture-overlay policy, coordinate source model | Page gate, snapshot masking, result provenance and jump eligibility. |
| `MushroomPatrolController`, location controller/confirmation policy, map bridge | Patrol phases, requested-vs-confirmed location, map/engine boundary. |

## 2. Current and target information architecture

### Current / source

```text
MainActivity
├─ 首頁
│  ├─ service readiness / open game
│  ├─ 自動種花, 花瓣生產, 明信片收集 entry cards
│  └─ accessibility, overlay, game-screen readiness
├─ 自動化
│  └─ 自動種花, 花瓣生產, 明信片收集, 自動派遣, 回程領取 entries
└─ 設定
   ├─ quick start and overlay visibility
   ├─ restrictions, accessibility, permission, system-location, mock-location routes
   ├─ remote-config update notice
   └─ community / help

PetalAccessibilityService floating icon
└─ expandable panel
   ├─ 種花 / 花瓣 / 明信片 / 探險 / 領取 tabs
   └─ 蘑菇 tab
      ├─ 掃描
      └─ 地圖 (Leaflet presentation)
```

### Target / R5 presentation IA

```text
App shell
├─ 首頁                 readiness, status summary, explicit game entry
├─ 自動化               workflow catalogue and explicit overlay entry; no implicit Start
└─ 設定
   ├─ 一般與懸浮視窗
   ├─ 權限與位置
   ├─ 限制與安全
   ├─ 更新與遠端設定
   └─ 診斷資訊

Overlay shell
├─ collapsed floating icon
└─ expanded workflow panel
   ├─ 種花 / 花瓣 / 明信片 / 探險 / 領取
   └─ 蘑菇 → 掃描 / 地圖
```

Opening an app tab, opening the overlay, switching a workflow tab, or switching `蘑菇` between `掃描` and `地圖` changes presentation only. A workflow begins only through its explicit Start command after the engine admits it.

## 3. MainActivity surface inventory

| Concern | CURRENT / source placement | TARGET / R5 placement | Ownership and rule |
| --- | --- | --- | --- |
| Service / accessibility readiness | 首頁 hero and 設定 status/action | 首頁 passive summary; 設定 > 權限與位置 has action | Android owns the settings screen; UI only launches it. |
| Overlay visibility preference | 設定 quick control | 設定 > 一般與懸浮視窗 | Persisted preference is distinct from an active workflow. Current hide path pauses work; target Collapse must not. |
| Workflow catalogue | 首頁 cards and 自動化 list | 自動化 list; cards may open overlay/game | Entry never silently starts automation. |
| Workflow configuration | Floating panel feature tabs | Same overlay workflow panel | `PetalAccessibilityService` remains engine authority. |
| Fine / coarse location | 設定 | 設定 > 權限與位置 | Runtime Android permission; show precise/coarse/none honestly. |
| System location service | 設定 | 設定 > 權限與位置 | User-owned Android setting; launch route only. |
| Mock-location app selection | 設定 developer-settings route | 設定 > 權限與位置 | Developer Options/AppOps selection, not an ordinary runtime permission or in-app picker. |
| Restrictions and risk acknowledgement | 設定 restriction block and transient checkbox | 設定 > 限制與安全 | Present limitation and acknowledgement state; do not imply a persistent grant when source does not persist one. |
| Update / install | 設定 remote-config notice and installer route | 設定 > 更新與遠端設定 | Source states are projected; Android unknown-sources and installer remain user-owned. |
| Remote config | Notice only for blocked/update paths | 更新頁 summary plus diagnostics projection | Never present stale/error as verified-current configuration. |
| Diagnostics | No dedicated current screen | 設定 > 診斷資訊 | TARGET: source-backed status only; no invented telemetry or runtime-success claim. |
| Persistent preferences | `SettingsStore` | Same settings surfaces | Configuration is persisted where source already persists it; runtime sessions are not assumed persistent. |

`KEEP`: MainActivity navigation, system-setting launch routes, readiness information, update entry, and workflow entry remain useful. `MOVE`: group scattered location/restriction/update material into the named target settings sections. `REMOVE`: no current source surface is removed by this freeze. `UNRESOLVED`: exact diagnostic field set and visual grouping are presentation work, bounded by the available source models.

## 4. UI state is not engine state

The R5 presentation boundary uses these separate concepts:

| State | Authority | Meaning | Forbidden inference |
| --- | --- | --- | --- |
| `overlayPanelExpanded` | UI shell | The workflow panel is open or collapsed. | It does not mean a workflow started, paused, or stopped. |
| `floatingIconVisible` | UI / persisted overlay preference | The draggable icon is renderable when overlays are allowed. | It is not a run-state indicator by itself. |
| `workflowEnabled` | User configuration / admission input | A workflow is configured and allowed to be requested. | It does not prove it is running. |
| `workflowRunning` | Workflow engine | The engine reports an active session. | UI must not synthesize it from a selected tab. |
| `workflowPhase` | Workflow engine projection | Idle, starting, waiting, blocked, terminal, or workflow-specific state. | UI must not advance it on a timer. |
| `captureMaskActive` | Capture policy projection | UI surfaces are temporarily hidden for capture. | It is not a Stop or an error. |

**Invariants:**

- `collapse != pause != stop`.
- Close panel, switch tab, close map, and reopen panel are presentation operations.
- Tab selection never sends Start, changes engine mode, or clears a result.
- A Start, Pause, Resume, Stop, retry, or map patrol command is explicit and state-gated.
- R5 MUST NOT call the current broad `pause()` merely because a panel collapsed, a tab changed, or a map closed.

### Current divergence to preserve honestly until R5 is implemented

`PetalAccessibilityService.applyOverlayVisibility(false)` currently invokes `pause()`. Tapping the floating icon while a non-`蘑菇` mode is running also invokes `pause()`, and opening the settings overlay for a non-`蘑菇` mode does so as well. The current normal `蘑菇` Stop command routes to the same terminal cleanup. Therefore current source does **not** establish resumable Pause for those workflows. R5 may make presentation independent only when it does not alter engine semantics; it must not relabel a terminal cleanup as resumable Pause.

## 5. Floating icon and panel contract

| Situation | CURRENT / source | TARGET / R5 presentation contract |
| --- | --- | --- |
| Default | Draggable 50 dp icon; default x=0, y=72. | Render collapsed icon with an accessible name and no claim of active work. |
| Tap / reopen | Opens settings panel unless a non-`蘑菇` active mode triggers current cleanup. | Open the last selected panel/tab without starting or stopping work. |
| Active workflow | Notice/content description is the compact status channel. | Show an unambiguous textual or accessible active-state indicator; do not rely only on colour. |
| Waiting / blocked | Source may show a notice or workflow state. | Keep the cause visible with the next permitted action; do not auto-resume on visibility changes. |
| Error | Source notice/state is available. | Keep the error and Retry/Stop eligibility visible until replaced by engine state. |
| Capture masking | Exact icon/panel/notice visibility snapshot is hidden then restored. | Preserve that exact-snapshot restoration; do not treat capture masking as user collapse. |
| Another app / surface | Accessibility service may overlay only when policy permits. | Never imply control over a system screen; use passive status or hide safely if overlay attachment is unavailable. |

Closing the panel restores the icon. During capture, restoration must return the exact visibility snapshot captured before masking rather than a newly inferred default. Dragging the icon is presentation-only and must not Stop.

### S1 safety amendment (implementation in progress)

The floating icon's completed tap is the single global Stop entry point when any workflow is active. Idle tap preserves the presentation behavior and opens the panel. `OverlayHost` emits only this narrow presentation callback; `PetalAccessibilityService` remains the owner of truth and cleanup. The Service common gate exposes `isAnyWorkflowActive()`, `whichWorkflowIsActive()`, and `requestGlobalStop(reason)` and rejects duplicate or cross-workflow starts without scheduling, capture, action, or state mutation. Normal Mushroom scan-to-patrol remains an internal transition of the same Mushroom owner, not a second active workflow. Collapse, tab changes, and map presentation remain non-Stop operations.

This amendment records the source/build target only. `REAL_DEVICE_FUNCTION` and `REAL_DEVICE_SAFETY` remain `NOT TESTED` pending the bounded S1 validation.

## 6. Workflow tabs, naming, and explicit starts

### Temporary Mushroom suspension

`MUSHROOM = TEMPORARILY_DISABLED` overrides the historical Mushroom scan and
map/patrol descriptions below until a separately authorized product decision.
The 蘑菇 tab remains visible as `蘑菇（暫停）`, is visibly disabled, and must not
change tabs or expose Scan, Rescan, map, patrol, location, or mock-location
commands. This is a product availability state, not removal of the retained
Mushroom implementation. If a stale Mushroom owner exists, the floating-icon
S1 Global Stop remains the sole active control and must retain terminal cleanup.

| Panel label | Product workflow | Explicit command surface | Tab-only behavior |
| --- | --- | --- | --- |
| 種花 | 自動種花 | Start after flower-order / threshold configuration | Show configuration only. |
| 花瓣 | 花瓣生產 | Start after feed / squad / limits configuration | Show configuration only. |
| 明信片 | 明信片收集 | Start after collection configuration | Show configuration only. |
| 探險 | 自動派遣 | Start after dispatch target / selection / Pikmin configuration | Show configuration only. |
| 領取 | 回程領取 | Start after postcard and ROI-related configuration | Show configuration only. |
| 蘑菇（暫停） | `MUSHROOM = TEMPORARILY_DISABLED` | No new Mushroom command; S1 Global Stop only for a stale active owner. | Disabled; no tab transition. |

Use **蘑菇** as the primary noun everywhere. Do not use `尋菇` as the primary user-facing feature name. Keep source-backed product labels (`自動種花`, `花瓣生產`, `明信片收集`, `自動派遣`, `回程領取`) where a user chooses a workflow.

## 7. 蘑菇 scan UX

### Scan phase vocabulary

The scan UI projects `MushroomUiState`; it does not reinterpret detector output.

| Engine-projected phase | User-facing treatment | Permitted user command |
| --- | --- | --- |
| `IDLE` | 等待開始 | Start when readiness permits. |
| `STARTING` | 正在準備掃描 | Stop only; suppress duplicate Start. |
| `WAITING_FOR_GAME` | 等待 Pikmin Bloom 回到前景 | Stop; no automatic tap or game navigation. |
| `INVALID_PAGE` | 請先進入蘑菇／地圖畫面 | Rescan after the user changes game page, or Stop. |
| `CAPTURING` | 正在擷取畫面 | Stop; capture masking is active. |
| `ANALYZING` | 正在分析 | Stop; do not claim a result early. |
| `RESULTS` | 已完成掃描 with result list | Rescan, inspect/map, or Stop. |
| `NO_RESULTS` | 未找到蘑菇 | Rescan, inspect provenance, or Stop. |
| `RETRY_WAIT` | 準備重新掃描 | Stop; show reason/timestamp when available. |
| `STOPPING` | 正在停止 | No new Start / Rescan. |
| `STOPPED` | 已停止 | Fresh Start only. |
| `ERROR` | 掃描錯誤 | Retry only when source exposes a safe retry path; otherwise Stop / fresh Start. |

`FEATURE NOT COMPLETE`: normal scan source schedules another analysis at a 3-second interval while `mushroomScanEnabled` remains true after `RESULTS` or `NO_RESULTS`. This freeze records it as current continuous behavior, not an accepted product promise, background guarantee, or complete scanning UX.

### Page gate and foreground rule

Before detector analysis, source requires Pikmin Bloom in the foreground and page evidence for a mushroom list, mushroom detail, or world-map state. A non-eligible page produces `INVALID_PAGE`; foreground loss produces `WAITING_FOR_GAME` and a bounded retry path. The UI must:

- say what the user must do (`回到 Pikmin Bloom` or `進入蘑菇／地圖畫面`),
- never tap, navigate, or select a mushroom for the user,
- never label a page-gate failure as `未找到蘑菇`, and
- require fresh page/capture evidence after foreground return.

### Results, coordinate provenance, and safety

Every displayed result must keep the fields source can provide separate:

| Field group | Display rule |
| --- | --- |
| Detection | type, size, confidence, screen coordinates / bounds; confidence is detector output, not a promise. |
| Scan location | location, accuracy, and timestamp when supplied; otherwise say unavailable. |
| Mushroom geographic coordinate | coordinate plus its `MushroomCoordinateSource`; never silently convert a screen coordinate into a geographic coordinate. |
| `UNAVAILABLE` | Display unavailable; no jump action. |
| `USER_SELECTED` | User supplied or selected location; jump may be offered. |
| `AUTHORITATIVE` | Source marks it authoritative; jump may be offered. |
| `CALIBRATED_ESTIMATE` | Display estimate and uncertainty; it is not eligible for coordinate map navigation. |

The result list needs explicit empty, stale, and busy states. A stale list remains historical output until a later engine publication replaces it; a fresh scan must not erase provenance merely to make the screen look clean.

## 8. Historical R7 蘑菇 map and patrol UX (suspended)

This retained R7 behavior is not currently reachable: `MUSHROOM =
TEMPORARILY_DISABLED` overrides every map, location, patrol, and scan command
in this section.

`MushroomMapView` / `mushroom_map.html` use Leaflet as a presentation renderer. Java owns validation, selection, location confirmation, patrol decisions, actions, and lifecycle. The WebView must not become a second workflow engine.

| Map concern | SOURCE / current | TARGET / R5 display rule |
| --- | --- | --- |
| Selection modes | single point, two points, area, route | Show selected mode and editable points only while patrol is not active. |
| Area modes | rectangle and circle | Source circle is fixed at 1000 m; a configurable radius/editor is `UNRESOLVED` and excluded. |
| Current location | Source-projected location may be shown | Clearly distinguish device current location from requested and confirmed patrol locations. |
| Mushroom result coordinate | Map-view navigation may use user-selected / authoritative source | Render provenance; never use result viewing to inject mock location or move the player. Unavailable / calibrated estimates remain non-actionable. |
| Geometry actions | clear, remove point, select mode | Confirmation is required for destructive geometry clearing; do not mutate an active route. |
| Patrol controls | start, pause, resume, stop | State-gated; close map and tab switching do not issue patrol commands. |

### Patrol phase vocabulary

| Phase | User-facing treatment |
| --- | --- |
| `IDLE` | no patrol active; map can be edited. |
| `PREPARING` | checking route and location readiness. |
| `MOVING` | current route point requested; map remains read-only. |
| `WAITING_LOCATION_CONFIRMATION` | requested location awaits provider confirmation. |
| `LOCATION_CONFIRMED` | source confirmation received; not merely requested. |
| `LOCATION_CONFIRMATION_FAILED` | show failure and safe retry/stop choice. |
| `STABILIZING` | source stabilization interval; no progress claim. |
| `SCANNING` | scan at current patrol point. |
| `WAITING_RESULT` | awaiting scan projection. |
| `PAUSED` | route cursor and observations retained by patrol controller; Resume remains explicit. |
| `COMPLETED` | route terminal outcome, preserving observations. |
| `STOPPING` / `STOPPED` | terminal stop cleanup / stopped result. |
| `ERROR` | blocking error with source cause and permitted recovery. |

Patrol Start first requires a valid route and location readiness: fine location, system location enabled, mock-location app selection where required by source, and provider initialization. Requested and confirmed location are separate fields. Source confirmation uses tolerance-based verification; UI must never mark a requested point as confirmed early. While patrol is active, selection changes are rejected rather than silently applied.

### Historical R7 map/location requirement update (suspended)

Opening 蘑菇 → 地圖 obtains a usable current real device GPS location and
centres Leaflet on it, with a current-position indication. There is no random,
hard-coded, stale, patrol, mock, or Mushroom coordinate fallback. If GPS is
temporarily unavailable, the map remains usable and shows a clear loading or
unavailable state; Search and coordinate navigation remain available.

Initial GPS centring, 目前位置 recenter, place/address Search,
定位座標 / 前往座標, pan, and zoom change only the Leaflet viewport. The
viewport is never a requested/confirmed mock location, patrol point, or
Mushroom geographic coordinate. Coordinate navigation validates latitude
[-90,90] and longitude [-180,180]; invalid input does not move the map and
never falls back to 0,0.

Map selection/editing for POINT, TWO_POINT, AREA, and ROUTE is harmless until
the user explicitly presses 開始巡航; this is the required user-facing label,
replacing 開始巡航找菇. The existing patrol sequence remains READY → MOVING →
WAITING_LOCATION_CONFIRMATION → LOCATION_CONFIRMED → STABILIZING → SCANNING.
Search changes only the viewport and never starts patrol, injects mock
location, creates a Mushroom coordinate, or mutates detection provenance.

Search provider selection is an explicit boundary outside MushroomMapView. The
approved default is OpenStreetMap Nominatim, selected through the existing
RemoteConfig `locationSearch` provider/endpoint/enabled configuration with a
built-in fallback. Native explicit-submit Search sends only the entered query,
uses a PikminX-identifying User-Agent and locale-aware `Accept-Language`, and
is limited to one in-process request at a time and at most one request per
second. It caches repeated queries, requests up to five `jsonv2` candidates,
and shows OpenStreetMap/Nominatim attribution. It sends no current GPS, device
identifier, patrol history, scan result, or unrelated user data; it has no
autocomplete, periodic, bulk, or polygon behavior. No-result or provider
failure leaves the viewport unchanged. The safe default first POINT test may
use current confirmed real GPS (or a nearby point), followed by explicit
開始巡航, confirmation, stabilization, fresh scan, Pause/Resume, and Global
Stop. Historical records retain provenance; source/build/artifact,
OFFICIAL_DOCUMENTED, DEVICE_OBSERVED, and UNRESOLVED evidence remain separate.

## 9. Other workflow UX contracts

These flows describe the existing configuration/entry surfaces and a target presentation discipline. They do not authorize changes to recognition, actions, timing, thresholds, or engine state graphs.

| Workflow | Configuration shown by source | Explicit workflow flow | Current pause / terminal fact |
| --- | --- | --- | --- |
| 自動種花 | flower order and threshold | configure → Save → Start → engine projection → completed / error / terminal stop | No source-backed resumable Pause contract. |
| 花瓣生產 | feed, squad, maximum switches, nectar minimum, petal limit, flower order | configure → Save → Start → engine projection → completed / error / terminal stop | No source-backed resumable Pause contract. |
| 明信片收集 | collection limit, Pikmin count, petal pot | configure → Save → Start → engine projection → completed / error / terminal stop | No source-backed resumable Pause contract. |
| 自動派遣 | dispatch count, target mode, selection method, Pikmin type | configure → Save → Start → engine projection → completed / error / terminal stop | No source-backed resumable Pause contract. |
| 回程領取 | postcard choice, continuing-nectar warning, ROI selection | configure → Save → explicit ROI / Start → engine projection → completed / error / terminal stop | ROI is independent of expedition; no source-backed resumable Pause contract. |
| 蘑菇 scan | scan page / current game context | Start → page gate → capture → analyze → results/no results/retry/stop | Current Stop is terminal. |
| 蘑菇 patrol | map geometry and location readiness | select route → Start → location confirmation → stabilize → scan → next point / terminal | Source supports true patrol Pause/Resume with retained cursor/observations. |

For all workflows, the UI must surface `IDLE`, `STARTING`, active/waiting, `BLOCKED`, `ERROR`, completion, and terminal stopped states as projections where source supplies them. It may not turn a local spinner, selected tab, or saved configuration into a claim that an engine session is running.

## 10. Pause, Stop, collapse, foreground loss, and capture

| User/system event | Target meaning | Current source qualification |
| --- | --- | --- |
| Collapse panel / close panel | Presentation only; keep engine state unchanged. | Current hide/open paths can call terminal `pause()`; this is a known divergence, not desired semantics. |
| Switch workflow tab / scan-map tab | Presentation only. | Source tab selection is UI projection; opening panel has non-`蘑菇` cleanup side effect. |
| Pause | Preserve a resumable engine session, cancel only safe pending work, and expose Resume. | Established only for `MushroomPatrolController`. |
| Stop | Terminal: cancel pending work, discard resumable session, and require a fresh Start. | Broad source `pause()` behaves this way for many workflows. |
| Foreground loss | No game action while backgrounded; project a source wait/terminal state and require fresh evidence on return. | Mushroom scan waits with bounded retry; other workflows do not have a generalized resume promise. |
| Accessibility service disconnect / interrupt | Surface blocked/terminal state; no automatic restart. | Source `onInterrupt()` / destruction call cleanup. |
| Capture masking | Temporarily hide exact overlay snapshot and restore it after capture. | Existing `MushroomCaptureOverlayPolicy` provides snapshot behavior. |

The detailed state-command truth table is [UI_STATE_COMMAND_MATRIX.md](UI_STATE_COMMAND_MATRIX.md). It is normative when this document uses the words Pause, Stop, or Collapse.

## 11. Restrictions, permissions, updates, and diagnostics

### Restrictions and device-owned settings

| Surface | Required UX treatment |
| --- | --- |
| Accessibility | Explain that the service is user-enabled in Android Settings; show current readiness without claiming a grant after the user leaves. |
| Overlay | Explain visibility/overlay constraints and return a user to the platform route when necessary. |
| Fine / coarse location | Request only through Android permission flow; distinguish precise, approximate, and absent. |
| System location | Show enabled/disabled and route to Android settings; do not toggle it on the user's behalf. |
| Mock location | State that the user selects the mock-location app under Developer Options. `ACCESS_MOCK_LOCATION` in the manifest is not a normal runtime-permission success signal. |
| Risk acknowledgement | Keep limitations and safe-use warning legible. Current checkbox is not established as durable persistence. |

### Update and RemoteConfig

`RemoteConfigStateModel` has `UNINITIALIZED`, `LOADING`, `VALID`, `STALE_USING_CACHE`, and `ERROR`. Current `MainActivity` displays a notice only for blocking/update cases. R5 diagnostics may expose a source-backed summary, but `STALE_USING_CACHE` and `ERROR` must remain visibly qualified. `InstallStateModel` has `IDLE`, `DOWNLOADING`, `VERIFIED`, `COMMITTED`, `AWAITING_CONFIRMATION`, `INSTALLING`, `SUCCESS`, and `FAILURE`; current activity rendering primarily shows download/installer-ready paths. Do not advertise update success solely because the installer was launched.

### Diagnostics and severity

| Severity | When to use it | Required action affordance |
| --- | --- | --- |
| Information | passive configuration / historical result | Inspect or dismiss; no false completion claim. |
| Warning | stale config, missing optional readiness, approximate location | Explain consequence and next safe action. |
| Blocking | missing accessibility, required permission, system location, required mock-location readiness, invalid route/page | Disable unsafe Start and provide the device-owned or in-app route. |
| Error | source terminal failure | Keep source reason when safe, Retry only if supported, otherwise fresh Start / Stop. |

Diagnostic content is restricted to source projections: service readiness, workflow phase, page-gate outcome, permission/location readiness, RemoteConfig state, install state, and safe error text. It must not log or display unbounded sensitive coordinates, inferred game data, or fabricated runtime success.

## 12. Empty, busy, idempotency, persistence, and form behavior

| Topic | R5 contract |
| --- | --- |
| Empty | State whether no result exists, a scan found no result, data is unavailable, or a list is awaiting first engine publication. These are different states. |
| Busy | Disable only commands unsafe in the projected phase; retain readable status and a permitted Stop where source supports it. |
| Idempotency | One explicit command has one visible in-flight result. Suppress duplicate Start/Rescan/Stop dispatch. Source already rejects duplicate Mushroom Start as already running. |
| Conflicting workflows | Do not imply parallel work when source admission permits one active automation / patrol path. Explain conflict instead of silently replacing it. |
| Saved settings | Preserve source-backed `SettingsStore` configuration values and distinguish Save from Start. |
| Runtime session | `MushroomUiState` is process-local; no process-death or reboot continuation guarantee exists. On reattach, request fresh source projection and never fabricate Resume. |
| Forms | Validate local required fields before emitting a command; engine remains final authority. Keep error text next to the affected input and preserve user input after a rejected command. |

## 13. Responsive, compact, touch, and accessibility requirements

These are `TARGET / R5` presentation requirements; they are not a claim that the current overlay passed a device accessibility audit.

| Area | Contract |
| --- | --- |
| Responsive overlay | Support narrow portrait, landscape, font scaling, and an occluded game surface. Preserve the current compact workflow intent without hiding the active status or terminal controls. |
| Touch hierarchy | Put workflow phase and blocking cause first, primary permitted command second, destructive Stop clearly separated, and secondary configuration below. Avoid adjacent Start/Stop ambiguity. |
| Touch targets | Retain or improve current compact controls toward accessible target sizing; current source values around 40–42 dp are not a conformance result. |
| Compact layout | Use progressive disclosure and scroll rather than clipped fields. Result provenance and critical errors must remain readable. |
| Capture | Capture masking must hide visual overlays without losing focus/state; restoration returns the pre-capture snapshot. |
| Screen reader | Every icon, tab, status, result, map action, and blocked state needs a meaningful label, role, selected/disabled state, and state-change announcement. |
| Visual accessibility | Do not use colour as the sole signal; retain adequate contrast, text alternatives, predictable focus order, and visible keyboard/focus treatment where applicable. |
| Motion | Do not make scan/patrol progress depend on animation; respect platform reduced-motion settings where available. |

## 14. Terminology contract

| Use | Avoid / qualification |
| --- | --- |
| 蘑菇 | Do not use 尋菇 as the primary feature name. |
| 掃描 / 重新掃描 / 停止 | Do not call a terminal current action 暫停. |
| 暫停 / 繼續 | Use only for patrol when its state is truly retained. |
| 等待 Pikmin Bloom | Do not imply the app can foreground or navigate the game. |
| 請先進入蘑菇／地圖畫面 | Do not say 找不到蘑菇 for page-gate failure. |
| 推定座標 / 校準估計 | Must include uncertainty and must not expose player or mock-location movement. |
| 已確認位置 | Only after source confirmation, never immediately after a request. |
| 更新可用 / 需要更新 | Do not call an installer launch 更新成功. |

## 15. Historical screenshot evidence

| Artifact | What it supports | Limitation |
| --- | --- | --- |
| `device-screen.png` | Historical MainActivity home layout and three bottom tabs. | Not a current runtime pass. |
| `pikminx-main-for-start.png` | Historical automation entry cards. | Does not prove workflow dispatch. |
| `settings-on-main.png` | Historical overlay panel/tab presentation over MainActivity. | Does not prove state semantics. |
| `planting-settings.png` | Historical floating icon over Pikmin Bloom. | Does not prove capture masking, position persistence, or gameplay behavior. |
| `device-window.xml`, `overlay-window.xml`, `settings-window.xml` | Historical UI-tree strings / surfaces. | Does not substitute for device, assistive-technology, or gameplay verification. |

## 16. R5 boundary and unresolved decisions

### R5 MUST

- extract only presentation ownership described in [UI_SCREEN_OWNERSHIP_MATRIX.md](UI_SCREEN_OWNERSHIP_MATRIX.md);
- render source-projected engine state and issue explicit, state-gated commands;
- preserve `collapse != pause != stop`, map/UI ownership, page-gate wording, coordinate provenance, and capture snapshot restoration;
- retain source-backed configuration and distinguish saved preferences from an active session;
- make system settings and permission routes visibly user-owned; and
- add validation appropriate to the later implementation without reclassifying source/static evidence as runtime evidence.

### R5 MUST NOT

- start or modify detector/OCR/capture/action/timing logic, coordinate inference/calibration, workflow admission, game interaction, RemoteConfig policy, or update semantics;
- use tab selection, panel closure, map closure, foreground return, or accessibility reattachment as an implicit Start/Resume;
- add automatic mushroom tapping/navigation, a new continuous-scan promise, a configurable patrol-circle radius, or claimed session persistence;
- move workflow-engine authority into `MainActivity`, the overlay shell, or Leaflet/WebView; or
- change production resources, templates, colours, strings, build configuration, assets, or tests as part of this documentation freeze.

### Unresolved, but not R5 blockers

| Item | Safe R5 default |
| --- | --- |
| Configurable circle radius / editor | Preserve fixed 1000 m source behavior; do not expose an editor. |
| Exact visual badge treatment for active/error icon | Use source-backed text/accessibility status; no new semantic colour system required. |
| Diagnostic detail level | Limit to existing source state models and safe messages. |
| Process-death restoration | Re-query source and show no active session until proven; no synthetic Resume. |
| General workflow resumable pause | Offer no Pause/Resume outside patrol until engine contract exists. |

No unresolved product decision requires changing engine behavior merely to extract the presentation boundary. Therefore the handoff is **`R5_UI_READY`**, not `R5_UI_BLOCKED`; that status authorizes planning a later R5 only, not beginning it now.

## 17. Freeze outcome

| Gate | Result |
| --- | --- |
| Requested UI/UX specification, state-command matrix, and ownership matrix documented | PASS |
| Production source changed | 0 files |
| Test source changed | 0 files |
| R4 / R4.5 runtime device and gameplay validation | NOT TESTED; device evidence remains unavailable for this freeze |
| R5 implementation | NOT STARTED |
| Handoff | `R5_UI_READY` |
