# PikminX R4.5 UI Screen Ownership Matrix

**Status:** `R4.5 PASS (documentation / source-evidence only)`  
**Handoff:** `R5_UI_READY`; this file does not authorize implementation.

## 1. Ownership rule

Presentation may render a projection and emit an explicit command. It does not own workflow truth, detector/OCR/capture state, coordinate inference, location confirmation, game interaction, timing, or RemoteConfig policy.

```text
MainActivity / overlay / Leaflet presentation
            │ explicit, state-gated command
            ▼
PetalAccessibilityService and workflow controllers ──► UI projections
            │
            ├─ detector / OCR / capture / action logic
            ├─ location / patrol / coordinates
            └─ settings / config / update state
```

`MushroomUiStore` is a process-local projection channel. A UI selection, spinner, panel state, or map tab never becomes engine state by itself.

### S1 workflow safety boundary

`PetalAccessibilityService` is the common runtime owner for the single-active-workflow gate. It exposes `isAnyWorkflowActive()`, `whichWorkflowIsActive()`, and `requestGlobalStop(reason)` while each workflow/controller remains the local state authority. Start requests are admitted only from idle, reject duplicate and cross-owner requests without side effects, and never auto-stop the current owner to start another. Multi-active recovery is Service-owned and must stop all observed owners, leaving zero active owners. Normal Mushroom scan-to-patrol is one owner transition. `OverlayHost` may report a tap intent but never owns this state. Collapse, tab, and map presentation remain non-Stop.

## 2. Current component and target responsibility inventory

| Component / source target | CURRENT responsibility | R5 target responsibility | Classification |
| --- | --- | --- | --- |
| `MainActivity` | Three app screens, readiness/settings, system-setting and update entry. | Keep as app shell and device-settings boundary; group settings more clearly. | `KEEP` |
| `PetalAccessibilityService` | Floating icon/panel, workflow runtime, capture, action admission, notification/status. | Remain workflow/overlay runtime authority; expose/provide projections, not layout policy. | `KEEP` |
| `SettingsStore` | Persists overlay/workflow configuration. | Remain configuration persistence source; UI reads/writes through existing paths. | `KEEP` |
| `MushroomUiState` / `MushroomUiStore` | Immutable UI projection and in-process publication. | Remain a projection boundary; do not promote to durable engine state. | `KEEP` |
| `MushroomOverlayPanel` | Current 蘑菇 scan/map panel composition. | Presentation extraction candidate only; preserve explicit command boundary. | `MOVE` presentation responsibility |
| `MushroomMapView` | Hosts Leaflet map presentation. | Keep rendering adapter; no patrol/action authority. | `KEEP` |
| `mushroom_map.html` / Leaflet | Renders map geometry, markers, progress supplied by bridge. | Keep map rendering only; do not add engine logic or source-of-truth state. | `KEEP` |
| `MushroomMapBridge` | Validates/map bridge traffic and communicates with Android owner. | Remain boundary/validation point. | `KEEP` |
| `MushroomPatrolController` | Route/patrol lifecycle, cursor, observations, Pause/Resume/Stop semantics. | Remain authority; UI only projects state and sends commands. | `KEEP` |
| `MushroomLocationController` and confirmation policy | Requested vs confirmed location and readiness/confirmation. | Remain authority; UI renders provenance/status. | `KEEP` |
| `MushroomCaptureOverlayPolicy` | Exact overlay visibility snapshot during capture. | Remain authoritative capture masking policy. | `KEEP` |
| `RemoteConfigStateModel` / update models | Source states for config/install flows. | Remain source projection; UI may display safely qualified diagnostics. | `KEEP` |
| Detector/OCR/action/timing classes | Recognition and game-action logic. | Out of R5 UI ownership. | `REMOVE FROM UI SCOPE` |

`MOVE` means move layout/presentation ownership only in a later authorized R5. It does not mean move workflow behavior, state transitions, or data ownership out of the listed source component.

## 3. MainActivity screen ownership

| Current surface | CURRENT owner | User purpose | TARGET placement | Engine/data owner | R5 treatment |
| --- | --- | --- | --- | --- | --- |
| 首頁 | `MainActivity.buildHomeScreen` | Read service/game readiness; open a workflow entry or game. | 首頁 | Service/system readiness. | `KEEP`; use passive summary, never direct Start. |
| 自動化 | `MainActivity.buildAutomationScreen` | Choose 自動種花, 花瓣生產, 明信片收集, 自動派遣, 回程領取 entry. | 自動化 | Overlay/service controls. | `KEEP`; entry opens overlay/game only. |
| 設定 quick start | `MainActivity.buildSettingsScreen` | Start user-guided setup. | 設定 > 一般與懸浮視窗 | MainActivity + Android routes. | `KEEP`. |
| Overlay visible control | `MainActivity.toggleOverlay` / service static bridge | Show/hide icon. | 設定 > 一般與懸浮視窗 | SettingsStore/service attachment. | `MOVE` grouping only; do not make it run-state. |
| Accessibility status/open settings | Settings screen | Diagnose/enable service. | 設定 > 權限與位置 | Android Settings/service enabled state. | `MOVE` grouping only; device-owned action stays external. |
| Fine/coarse permission | Settings screen | Request/read location permission. | 設定 > 權限與位置 | Android permission API. | `MOVE`; distinguish precise/coarse/none. |
| System location | Settings screen | View/launch location settings. | 設定 > 權限與位置 | Android system setting. | `MOVE`; never toggle internally. |
| Mock-location selection | Developer settings route | Route user to Developer Options. | 設定 > 權限與位置 | Android Developer Options/AppOps. | `MOVE`; no in-app picker or false runtime grant. |
| Restrictions/risk warning | Settings screen | Explain safety limits and acknowledgement. | 設定 > 限制與安全 | UI transient acknowledgement/source policy. | `MOVE`; do not claim persistence unless source stores it. |
| RemoteConfig update notice | Settings screen / `refreshRemoteConfig` | Tell user blocked/update availability. | 設定 > 更新與遠端設定 | RemoteConfig + install state model. | `MOVE`; retain source qualification. |
| Advanced diagnostics | No current dedicated screen. | Inspect safe source-backed status. | 設定 > 診斷資訊 | Source state models. | `TARGET`; no invented telemetry. |

No current MainActivity screen is a workflow engine. `automationEntry()` showing the overlay/game is an entry behavior, not an automation dispatch. `renderInstallState()` not showing every terminal model state means the R5 UI cannot claim an update outcome beyond source projection.

## 4. Floating-overlay surface ownership

| Surface | CURRENT owner | Engine source of truth | Data rendered | Commands emitted | TARGET / R5 boundary |
| --- | --- | --- | --- | --- | --- |
| Floating icon | `PetalAccessibilityService.showOverlay` | Service/settings visibility state; workflow status projection. | Accessible description / compact notice. | Open panel; current tap has non-Mushroom cleanup side effect. | UI shell only; collapse/reopen must not issue Stop/Pause. |
| Floating notice | Service | `OverlayRunStatus` / workflow projection. | Compact status. | None. | Keep a projection only; status is not a full engine graph. |
| Overlay panel shell | Service settings overlay | UI-local expanded/selected tab state. | Tabs and selected workflow panel. | Open/close/select tab. | Separate `overlayPanelExpanded` from `workflowRunning`. |
| 種花 panel | Service overlay page | Planting engine/config. | Flower order, threshold, source status. | Save / explicit Start. | No engine state change on tab selection. |
| 花瓣 panel | Service overlay page | Care engine/config. | Feed/squad/limits config, source status. | Save / explicit Start. | Same. |
| 明信片 panel | Service overlay page | Postcard engine/config. | Collection config, source status. | Save / explicit Start. | Same. |
| 探險 panel | Service reward page | Expedition engine/config. | Dispatch count/mode/method/type. | Save / explicit Start. | Same. |
| 領取 panel | Service return-reward page | Return reward engine/config. | Postcard/nectar/ROI configuration. | Save / explicit ROI and Start. | ROI selection is presentation/configuration, not expedition state. |
| 蘑菇 scan panel | `MushroomOverlayPanel` | `MushroomUiStore` projection / service scan session. | Scan phase, results, provenance. | Start, Rescan, Stop. | Render exact projection; no detector or page-gate ownership. |
| 蘑菇 map panel | `MushroomOverlayPanel` + map view | Map selection/patrol/location owners. | Geometry, markers, requested/confirmed location, patrol phase. | Selection, patrol, jump commands. | Map tab is presentation only; active patrol locks geometry editing. |
| Capture masking | `MushroomCaptureOverlayPolicy` | Policy snapshot. | Hidden/restore state. | No user workflow command. | Preserve exact snapshot restoration; UI cannot guess it. |

## 5. 蘑菇 scan result ownership

| Field / behavior | Authoritative owner | UI may do | UI MUST NOT do |
| --- | --- | --- | --- |
| Page gate | Service / `MushroomPageGate` | Explain waiting/invalid-page state. | Treat invalid page as no result; tap/navigate the game. |
| Foreground state | Service/game-frame admission | Say return to Pikmin Bloom. | Bring game forward or resume automatically. |
| Detector output | Detector/service scan session | Render type, size, confidence, screen bounds. | Rewrite confidence or infer a result. |
| Scan location | Source projection | Render coordinate/accuracy/timestamp or unavailable. | Pretend unavailable location exists. |
| Mushroom coordinate | Source/provenance model | Render provenance and uncertainty. | Convert screen coordinate to geographic coordinate. |
| Map-view navigation eligibility | `MushroomCoordinateSource` | Offer map-view navigation only for `USER_SELECTED` / `AUTHORITATIVE`; never inject mock location. | Offer player or mock-location movement for any result source. |
| Scan retry | Service session/epoch | Display retry/busy status. | Start parallel analysis or timer-driven speculative retries. |
| Normal 3-second scan behavior | Service scan scheduling | Label current behavior `FEATURE NOT COMPLETE`. | Promise continuous/background scanning UX. |

## 6. Map, location, and patrol ownership

| Concern | Authoritative owner | Presentation owner | R5 rule |
| --- | --- | --- | --- |
| Leaflet rendering | `MushroomMapView` / HTML bridge data | Leaflet/WebView | Render only; no route/patrol decisions in JavaScript. |
| Mode selection | Android map selection owner | Map panel | Display single point, two points, area, route; commands are validated by Android. |
| Area circle | Source selection behavior | Map panel | Preserve fixed 1000 m circle; editor/radius is unresolved and excluded. |
| Current vs requested vs confirmed location | Location controller / confirmation policy | Map panel | Render distinct labels; never label request as confirmed. |
| Initial/current map viewport | Real device location owner | Leaflet/map panel | Open and 目前位置 recenter from confirmed real GPS when usable; unavailable GPS is explicit, never invented. |
| Map navigation | Map navigation boundary | Leaflet/map panel | Search, 定位座標 / 前往座標, pan, and zoom move only the viewport; validate coordinates and never inject mock location. |
| Place/address Search provider | Dedicated provider boundary outside MushroomMapView | Map panel | Preserve candidate selection, attribution, limits, failure, privacy, and network policy; until approved, expose SEARCH_PROVIDER_DECISION_REQUIRED. |
| Viewport versus game/location facts | Map presentation plus location/patrol owners | Map panel | Keep viewport, requested/confirmed mock location, patrol point, and Mushroom geographic coordinate distinct. |
| Location readiness | Service/location owner | MainActivity settings + map/patrol status | Route user to permissions/system/developer settings; no fake grant. |
| Patrol cursor and observations | Patrol controller | Map panel | Render only; Pause retains source session, Stop is terminal. |
| Active patrol geometry lock | Patrol controller / selection owner | Map panel | Disable/reject editing while active; do not apply delayed edits. |
| Patrol foreground/capture behavior | Patrol/service | Map panel | Project source state; no hidden resume or game interaction. |

## 7. System settings, update, and diagnostics ownership

| Surface / action | Current initiating surface | External / engine owner | UI responsibility | Prohibited ownership transfer |
| --- | --- | --- | --- | --- |
| Accessibility settings | MainActivity settings | Android Settings | Explain state, launch user-owned system screen, refresh returned state. | App cannot silently enable service. |
| Overlay permission / attachment | MainActivity/service | Android overlay policy | Explain availability and service projection. | UI cannot guarantee attachment over every surface. |
| Fine/coarse permission | MainActivity settings | Android permission framework | Request/read and distinguish outcome. | Do not call approximate location precise. |
| System location setting | MainActivity settings | Android system setting | Show status and launch route. | Do not switch provider state itself. |
| Mock-location app selection | MainActivity developer-settings route | Android Developer Options/AppOps | Explain manual selection and refresh readiness. | Do not present `ACCESS_MOCK_LOCATION` as a normal runtime permission or create an in-app picker. |
| Unknown-sources / installer | MainActivity update flow | Android settings/installer | Show source install phase and launch route. | Do not claim install/update success on launch. |
| RemoteConfig | MainActivity / service source state | RemoteConfig model/policy | Render valid/stale/error/block/update distinction. | Do not decide policy or hide stale/error qualification. |
| Diagnostics | Target Settings section | Existing safe source models | Render current source statuses and bounded error text. | Do not add telemetry, coordinate history, or runtime claims not supplied by source. |

## 8. R5 extraction map

| R5 presentation slice | May move / compose | Must remain external or engine-owned | Acceptance boundary |
| --- | --- | --- | --- |
| App IA and settings grouping | MainActivity layout and labels using existing source routes. | Android settings, permission, installer, Developer Options. | Every button still directs user to the correct owner. |
| Overlay shell | Panel expansion, tab selection, compact status hierarchy. | Service lifecycle and workflow run-state. | Collapsing/switching emits no Pause/Stop/Start. |
| Workflow pages | Layout around existing configuration and explicit commands. | Action admission, workflow logic, configuration semantics. | Save is distinct from Start; no tab-driven dispatch. |
| 蘑菇 scan | State/result renderer and command affordances. | Page gate, detector/OCR/capture, scan interval/epoch. | Correct phase/provenance with no auto-tap/navigation. |
| 蘑菇 map | Map controls and state renderer. | Location confirmation, selection validation, patrol route/cursor. | Requested/confirmed locations remain distinct; active patrol locks edits. |
| Update/diagnostics | Source-state renderer. | RemoteConfig policy, install process, platform result. | Valid/stale/error/install phase is qualified, never fabricated. |
| Accessibility/responsiveness | Focus, labels, touch ordering, compact layout. | Runtime service readiness and Android system controls. | No colour-only or icon-only state communication. |

## 9. Explicit non-ownership / R5 MUST NOT

R5 MUST NOT:

- modify `PetalAccessibilityService` detector/OCR/capture/action/timing behavior to fit a layout;
- move the state machine, action admission, location confirmation, coordinate provenance, patrol cursor, or RemoteConfig policy into `MainActivity`, an overlay widget, or Leaflet;
- turn panel Collapse, tab selection, map close, foreground return, or service reattach into an implicit Start, Pause, Resume, or Stop;
- call broad current `pause()` for a presentation-only change;
- add automatic mushroom tapping, game navigation, a new continuous-scan guarantee, configurable circle radius, or session-persistence promise;
- change production resource files, WebView assets/templates, colours, strings, build configuration, detector tuning, or tests as part of this R4.5 documentation freeze.

## 10. Ownership acceptance checklist

| Check | Required result |
| --- | --- |
| MainActivity | App navigation/settings boundary only; no workflow engine migration. |
| Overlay | `overlayPanelExpanded`, `workflowEnabled`, and `workflowRunning` remain distinct. |
| Tabs | All workflow and scan/map tabs are presentation-only until explicit command. |
| Map navigation | Open/recenter/Search/coordinate navigation/pan/zoom and geometry editing never begin patrol or mock movement. |
| Patrol start | Only explicit 開始巡航 may transition a selected route/point into patrol. |
| Pause semantics | Only patrol receives Pause/Resume; other current cleanup remains terminal Stop semantics. |
| Scan | Page gate, result provenance, `FEATURE NOT COMPLETE` continuous behavior qualification remain visible. |
| Map/patrol | Leaflet renders; Android/service owns validation, location confirmation, and patrol lifecycle. |
| Device settings | Accessibility, permissions, system location, mock selection, installer remain user/platform-owned. |
| Diagnostics | Source-backed, privacy-bounded, and never presented as device-runtime proof. |
| Scope | R4.5 changes documentation only; R5 remains not started. |

## 11. Freeze result

No unresolved product decision makes a presentation extraction unsafe: the safe default is to retain source ownership and exclude unimplemented semantics. The result is `R5_UI_READY`, not `R5_UI_BLOCKED`. It is a planning handoff, not authorization to start R5.
