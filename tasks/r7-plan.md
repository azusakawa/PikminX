# R7 — Mushroom Map, Location, and Patrol Ownership

Status: `IN_PROGRESS_REQUIREMENT_UPDATE_MAP_VIEWPORT_SEPARATION` (2026-09-16). This supplements the
historical R4 plan; it does not replace it.

## Scope

Extract the existing Mushroom map/patrol coordination and mock-location confirmation callback
families from `PetalAccessibilityService` into the planned
`MushroomPatrolCoordinator` and `MushroomLocationGateway` owners. The Service remains the
Android lifecycle, single-active-workflow, Global Stop, capture, and final action-admission
adapter.

Apply the approved R7 Map UX / Patrol requirement update without discarding the historical
3.1.34–3.1.37 evidence. The new work separates real-GPS map navigation from mock-location patrol
movement and does not start R8 until the updated R7 real-device gate passes.

## Invariants

- Preserve `POINT`, `TWO_POINT`, `AREA`, and `ROUTE` generation, five-second confirmation,
  1.5-second stabilization, tolerance/session/point checks, Pause/Resume/Stop, and normal-scan
  restoration. The historical private `pikminx-mushroom` provider is not an invariant; active
  patrol retains the later system `gps` test-provider correction.
- Keep requested coordinate, confirmed location, scan location, screen coordinate, and Mushroom
  geographic coordinate distinct.
- Do not change detector templates, scoring, thresholds, OCR, capture, gesture geometry, timing
  constants, or other workflow owners. Only the approved Leaflet/UI navigation semantics change.
- Do not use JVM, mocked, fixture, or instrumentation checks as R7 functional acceptance.

## Delivery

1. Add the two focused owners and move map-route state, location-confirmation identity, patrol
   scheduling, stale scan identity, and terminal restoration into them.
2. Rewire the Service to use narrow callbacks while retaining Global Stop and the shared R6 scan
   owner.
3. Review every former Service-family caller, compile, lint, assemble a new signed candidate, and
   verify its package, version, signer, and hash.
4. Install only by `adb install -r`, then perform one bounded real-device scenario at a time:
   visually identify the game screen, require confirmation before scan, and exercise
   Pause/Resume/Stop plus floating-icon Global Stop.

The superseded `USER_MAP_SELECTION_REQUIRED` blocker remains historical evidence only. For the
first low-risk POINT scenario, use a newly confirmed real-GPS fix as the default point, then
press `開始巡航` explicitly. No arbitrary distant coordinate is invented or silently substituted.

## R7 gate record

- `BUILD`: `PASS` — debug and release Java compilation, then `assembleRelease`, produced
  `3.1.34` / `344`.
- `LINT`: `FAIL_KNOWN_DEBT` — only the pre-existing `OldTargetApi` at
  `code/app/build.gradle:27` (`targetSdk 35`).
- `ARTIFACT`: `PASS` — SHA-256, package/version, and the expected v2 signer were verified.
- `INSTALL`: `PASS` — `adb install -r` verified on the authorized Xiaomi 25080RABDG.
- `REAL_DEVICE_UI`: `DEVICE_OBSERVED` — after the user selected the route, the candidate
  visibly showed two selected endpoints and an eleven-point patrol route, still idle.
- `REAL_DEVICE_FUNCTION`: `BLOCKED_MOCK_LOCATION_APP_UNCONFIGURED` — the Start control
  reached the candidate, but Android reported that PikminX is not the selected mock-location
  app (`mock_location_app=null`). The preflight stopped before any location injection, patrol,
  or scan. The user must manually select PikminX in Android Developer options before this
  bounded scenario can resume.
- `REAL_DEVICE_SAFETY`: `PASS_SCOPE_BOUND` — no game gesture, Join, GO, resource spend,
  settings reset, uninstall, or location change occurred; the failed preflight made no location
  change.

## Correction cycle 1 — active patrol controls remain reachable

### Observed failure

After the user configured Android mock-location support, the selected two-endpoint route ran
through eleven bounded capture/analysis cycles and returned to idle. The active floating icon
therefore opened normal presentation rather than Global Stop by the time it was tapped. More
importantly, `startMushroomPatrolFromUi()` closed the Mushroom panel immediately after Start,
making its visible Pause, Resume, and Stop commands unreachable during the run.

### Minimal correction

Keep the already-open Mushroom panel in place when patrol starts. Existing capture handling
already hides the panel only during a capture and restores it while the Mushroom workflow is
active, so no detector, route, timing, location, or Global Stop behavior needs to change.

### Re-verification plan

Build and install one new signed candidate, then use the same user-selected route only. Verify
that the live panel permits Pause, Resume, and Stop; after a fresh Start, close only the panel
with its own close button, then use the visibly identified floating icon for Global Stop and
observe ten seconds without a new patrol capture. Stop immediately for an uncertain game screen,
unexpected game interaction, multiple workflow, or failed/revived Global Stop.

### Current correction-cycle gate

`3.1.35 / 345` compiled, reproduced only the known lint debt, assembled, passed artifact signer
verification, and installed by `adb install -r`. After the upgrade restarted the service, the
candidate visibly reported that no map position was selected. The former user-selected route was
not reconstructed or substituted. R7 re-verification is therefore blocked until the user selects
the intended map geometry again on this installed candidate.

## Correction cycle 2 — GPS provider mismatch

### Observed failure

With two manually selected points, the candidate advanced its internal patrol and produced scan
summaries, but the real Pikmin Bloom character remained at its original game location. The
post-scenario, read-only `dumpsys location` record is preserved as
`releases/r7-3.1.35-345-20260916T045518Z/device-evidence/26-r7-345-location-provider-dumpsys.txt`.
It shows Pikmin Bloom subscribed to system `gps` and `fused`, while PikminX had injected only its
separate `pikminx-mushroom` provider.

### Root cause

`AndroidMockLocationDriver` creates, writes, listens to, and immediately reads back the private
`pikminx-mushroom` test provider. That callback satisfies the local confirmation gate, but it is
not evidence that the `gps`/`fused` providers used by the game received the same location. The
route state and local map projection can therefore advance while the visible game character does
not move. This is `ROOT_CAUSE_CONFIRMED_PROVIDER_MISMATCH`, not a route-generation, scan, timing,
or panel-control defect.

### Minimal design and re-verification boundary

- Change only the existing mock driver to replace the system `gps` test-provider boundary during
  an active, user-selected patrol; retain its existing initialization, confirmation, Stop, and
  provider-removal lifecycle.
- Do not alter map geometry, route generation, detector configuration, capture, scan cadence,
  timing, UI, Global Stop, or game gestures.
- Build one new signed candidate and install only with `adb install -r`.
- Before any scan is accepted, establish on the physical device that the game character moves to
  each selected point and that `dumpsys location` attributes the active test location to `gps`.
  The old self-confirmation alone is insufficient.
- Re-run only a user-selected `POINT` followed by a user-selected `TWO_POINT`; a `ROUTE` requires
  at least three manually selected points. Do not invent or log replacement map coordinates.
- If the platform rejects the `gps` test-provider replacement or the game still does not move,
  preserve the evidence and stop R7 after this third correction cycle rather than patching around
  the symptom.

## Correction cycle 3 — icon-only patrol and in-session result list

### User-directed behavior

Once patrol Start succeeds, the persistent UI must be only the floating icon. The settings/Mushroom
panel must not reopen around each capture. Each completed point should briefly state which Mushroom
types and sizes were detected, then retain the accumulated current-patrol results for review after
the workflow stops and the user reopens the icon.

### Minimal design

- Close the already-open settings/Mushroom panel once, immediately after successful patrol Start.
  With no settings panel, the existing capture hide/restore calls are no-ops, so capture never
  flips the panel open and closed.
- Reuse the existing floating notice generated by the Mushroom run status; change its patrol text
  from a count alone to the detected type/size labels for that point.
- Retain each patrol scan's existing `MushroomUiState.Result` entries in the workflow owner's
  current patrol list. This is an in-process list that is visible after Stop when the user reopens
  the panel; it intentionally does not add a database, file, or cross-service-restart history.
- While icon-only patrol is active, icon tap remains the existing Global Stop. The existing
  Pause/Resume methods are preserved but have no visible control; no hidden or long-press gesture
  will be invented without a separate user instruction.
- Do not change mock-location behavior, route generation, detector configuration, capture timing,
  scan cadence, game gestures, or Global Stop.

### Re-verification boundary

Build and install one final R7 candidate, then use only manually selected geometry. Verify: the
panel closes once after Start and remains closed through at least one capture; the icon stays
visible; a result notice names the current detected Mushroom labels; the accumulated in-session
list remains visible after Global Stop; and the system `gps` provider plus the real game character
move for the selected point. A three-point `ROUTE` is still separately required for full route
acceptance.

### Engineering and deployment gate

`3.1.37 / 347` compiled and assembled, retained only the known `OldTargetApi` lint debt, passed
package/version/v2-signer/SHA-256 verification, and installed with `adb install -r`. Accessibility
remains enabled and the Android mock-location app-op remains allowed. No user geometry was
reconstructed and no location was injected after this final candidate was installed; all new
icon-only, result-list, and real-game GPS behavior remains `NOT_TESTED` until manual selection.

## 2026-09-16 approved Map UX / Patrol requirement update

### Product contract

- Opening `蘑菇 → 地圖` requests a fresh, usable real GPS fix and centers Leaflet on that fix with
  a current-position marker. It never initializes to the former hard-coded Taipei coordinate,
  a patrol point, a mock-location confirmation, or a stale last-known fix.
- If no real fix is available, the map remains a neutral, usable viewport with an explicit
  loading/unavailable state. It does not synthesize a coordinate and still permits map-only
  coordinate navigation (and place search after a provider is approved).
- `目前位置`, selection viewing, result viewing, coordinate navigation, search-result selection,
  pan, and zoom are Leaflet viewport operations only. A viewport coordinate is never assigned to
  a patrol point, requested mock location, confirmed mock location, scan/player location, or
  Mushroom geographic coordinate merely by viewing it.
- Existing UI actions previously labelled `轉跳` are converted to map viewing commands. The
  only mock-location request path is `開始巡航` → existing patrol controller → location
  confirmation → stabilization → scan. POINT, TWO_POINT, AREA, and ROUTE edits remain harmless
  until that explicit Start command.
- `開始巡航找菇` changes to `開始巡航`; its patrol semantics do not change.
- Coordinate navigation parses `latitude, longitude` in native code, validates the existing
  `MapCoordinate` ranges, reports an error for invalid input, and never falls back to `0,0`.
- The map's current-position marker projects only the fresh real GPS fix. Requested/confirmed
  mock locations remain patrol/location facts and never overwrite that marker or recenter the
  viewport.

### Place-search provider decision — approved 2026-09-16

`Nominatim` is the current default for explicit place/address/landmark Search. The provider is
outside `MushroomMapView`: native UI emits a command, `LocationSearchProvider` resolves the
configured endpoint, and result selection only centres the existing Leaflet viewport. A result is
never a patrol point, mock location, current GPS, confirmed location, or Mushroom coordinate.

The optional R4 `RemoteConfig` `locationSearch` object carries `provider`, `endpoint`, and
`enabled`; an absent/unavailable config uses the built-in enabled Nominatim default. This preserves
the existing config/fallback ownership and lets the public endpoint be disabled or replaced with a
compatible endpoint without a new APK. RemoteConfig does not own any Mushroom workflow state.

Nominatim requests occur only after the user presses 搜尋. The client uses one in-process request
lane with at least one second between request starts, an in-memory repeated-query cache, a
PikminX-identifying User-Agent, locale-aware `Accept-Language`, `q`, `format=jsonv2`, `limit=5`,
and `addressdetails=1`; it omits polygon data. Search text is the only user content sent to the
provider—never current GPS, device identifiers, patrol history, scan results, or unrelated data.
The UI discloses this network use and shows OpenStreetMap/Nominatim attribution. A central proxy
must replace the public endpoint before multi-user traffic needs an application-wide rate limit.

No-results and provider failure leave the map unchanged and show a bounded status. There is no
autocomplete, periodic search, or bulk/systematic geocoding.

### Updated real-device gate

Historical 3.1.34/344 through 3.1.40/350 records remain evidence, not proof of this update.
The active source-changing candidate is `3.1.41 / 351` and must record the following separately:

| Gate | Required status before R7 can be sealed |
| --- | --- |
| `BUILD` / `ARTIFACT` | New candidate compiled and independently package/version/signer/hash verified. |
| `MAP_INITIAL_CURRENT_GPS` | `PASS` on a physical device. |
| `CURRENT_LOCATION_RECENTER` | `PASS` on a physical device. |
| `MAP_VIEW_DOES_NOT_INJECT_MOCK_LOCATION` | `PASS` with visible evidence before patrol Start. |
| `COORDINATE_NAVIGATION` | `PASS` with valid and invalid input evidence. |
| `LOCATION_SEARCH` | `PASS` with the approved Nominatim contract and real-device evidence. |
| `POINT_PATROL` / `LOCATION_CONFIRM_BEFORE_SCAN` | `PASS` using the newly confirmed real-GPS POINT by default where safely possible. |
| `PAUSE` / `RESUME` / `GLOBAL_STOP` / `NO_STALE_RESURRECTION` | `PASS` in a bounded real-game scenario. |
| `TWO_POINT` / `ROUTE` | `PASS` where safely available; ROUTE has at least three points. |

Device execution still requires an explicit bounded attempt/duration/Stop plan and preserved
visible evidence. No install, mock-location injection, game interaction, resource spend, or
location movement is implied by source/build/artifact success.

The 2026-09-16 authorization permits one R7 session of at most 15 minutes and
at most two attempts per scenario after the signed `3.1.41 / 351` candidate is
installed with `adb install -r`. It permits mock location only through the
existing patrol flow. Do not uninstall, clear data, downgrade, reset settings,
Join or `GO` a Mushroom, spend a ticket, or purchase anything. Preserve
before/after visible evidence and use Global Stop when the bounded scenario
ends or becomes unsafe/ambiguous.

### 3.1.41 / 351 engineering gate record

`BUILD`: `PASS` — `:app:compileReleaseJavaWithJavac` and
`:app:assembleRelease` passed after the Nominatim implementation and redirect
hardening review. No JUnit, AndroidTest, or instrumentation task was used as
functional evidence.

`LINT`: `FAIL_KNOWN_DEBT` — `:app:lintDebug` reports exactly one finding, the
pre-existing `OldTargetApi` at `code/app/build.gradle:27` (`targetSdk 35`),
and no new finding.

`ARTIFACT`: `PASS` —
[r7-3.1.41-351-20260916T072903Z](../releases/r7-3.1.41-351-20260916T072903Z/release-manifest.json)
records package `com.pikminx.helper`, version `3.1.41` / `351`, APK SHA-256
`97a4087809fe79eff21c7533ec44e0adfc169eb1868ef95f4baf3906cf7fabdc`, and
the unchanged v2 signer SHA-256
`7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`.

`INSTALL`, `REAL_DEVICE_FUNCTION`, and `REAL_DEVICE_SAFETY` remain pending the
authorized bounded session. The prior `3.1.40 / 350` record below is retained
as historical evidence.

### 3.1.41 / 351 bounded device attempt — 2026-09-16

`INSTALL`: `PASS` — the authorized Xiaomi accepted
`adb install -r`; `dumpsys package` reports `com.pikminx.helper` version
`3.1.41` / `351`.

`MAP_INITIAL_CURRENT_GPS`: `BLOCKED` — after opening Pikmin Bloom and the
idle Mushroom overlay, two visible taps on 地圖 left the panel on 掃描. The
before/after evidence is
[11-map-initial-current-gps.png](../releases/r7-3.1.41-351-20260916T072903Z/device-evidence/11-map-initial-current-gps.png)
and
[12-map-open-attempt-02.png](../releases/r7-3.1.41-351-20260916T072903Z/device-evidence/12-map-open-attempt-02.png).
No fresh-GPS request, map viewport, search, coordinate, patrol, or scan
scenario can be accepted from that state.

`MAP_VIEW_DOES_NOT_INJECT_MOCK_LOCATION`: `PARTIAL` — no patrol was started
and no mock-location action was invoked, but the Map surface was unreachable.
`COORDINATE_NAVIGATION`, `LOCATION_SEARCH`, `POINT_PATROL`, confirmation,
Pause/Resume, Global Stop, TWO_POINT, and ROUTE are `NOT_TESTED` because they
depend on Map. `REAL_DEVICE_FUNCTION` is `BLOCKED`; `REAL_DEVICE_SAFETY` is
`PARTIAL`: no Join, `GO`, ticket spend, purchase, uninstall, data clear,
downgrade, or permission/settings reset occurred, but patrol safety is not
established.

The bounded session ended after about six minutes, below the 15-minute limit.
`ROOT_CAUSE_UNRESOLVED`: source has a Map-tab listener, but the two screenshots
alone do not establish why it did not activate. Do not patch or retry without
a concrete UI/event diagnosis and a new bounded authorization. R7 remains
`IN_PROGRESS`; R8 remains prohibited.

### 3.1.40 / 350 engineering gate record

`BUILD`: `PASS` — `assembleRelease` and `assembleDebugAndroidTest` completed on
2026-09-16. The Android-test APK was compiled for interface compatibility only;
no test execution is functional evidence.

`ARTIFACT`: `PASS` —
[r7-3.1.40-350-20260916T064809Z](../releases/r7-3.1.40-350-20260916T064809Z/release-manifest.json)
records package `com.pikminx.helper`, version `3.1.40` / `350`, APK SHA-256
`925a0d34e1e64ddff06810c4e2aab473c65f9949699d73682af356ff6b272717`, and the
unchanged v2 signer. `LINT`: `FAIL_KNOWN_DEBT` solely for the pre-existing
`OldTargetApi` at `code/app/build.gradle:27`; no new lint finding remains.

The local `3.1.38 / 348` and `3.1.39 / 349` artifacts are retained as
`REJECTED_STATIC_REVIEW`: static review found stale-marker cases before the
fresh request or unavailable result was fully rendered. Neither was installed
or promoted as R7 evidence.

`INSTALL`, `REAL_DEVICE_FUNCTION`, and `REAL_DEVICE_SAFETY` are `NOT_TESTED`.
`LOCATION_SEARCH` was `BLOCKED_PROVIDER_DECISION` for this historical
candidate. The updated R7 exit gate is still `IN_PROGRESS`, so R8 remains
prohibited.
