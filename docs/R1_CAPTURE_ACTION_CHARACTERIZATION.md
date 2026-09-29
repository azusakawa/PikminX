# R1 Capture / Action Characterization

**Scope:** `versions/3.0` current production source. This is an observation
record, not an ownership move or a behavior specification.

**R1 status:** `PARTIAL`  
**Production source changed:** no  
**R2 decision:** `R2_BLOCKED`

R1 found two source facts that prevent the requested universal contract from
being proven:

1. `ACTION_SET_TEXT` and its focus fallback bypass the shared final
   `ActionAdmission` recheck.
2. `ScreenshotRequest.admissionEpoch` is written at enqueue but not read when
   OCR/action context is built; an in-flight request cannot be proven to retain
   its request-time epoch across a rescan.

No source change is made here. `SOURCE` below means current production control
flow, `TEST` means JVM coverage, `RUNTIME` means a device/API observation in
this R1 run (none), `USER_REQUIREMENT` means the authorized R1 boundary, and
`OFFICIAL_GAME_BEHAVIOR` is product context only.

## 1. Capture lifecycle

`PetalAccessibilityService.requestScan()` is the shared scheduled entry. It
rejects non-running/busy workflows and feature-specific ineligible states before
`takeGameScreenshot()`, the only source enqueue path.

| Stage | Input / owner | Output | Invalidation or terminal path |
|---|---|---|---|
| Schedule | Service `Handler`, run/mode/busy state | `requestScan()` | stopped, busy, foreground/bounds, Reward, or Mushroom guards return before capture. |
| Request | Service snapshots generation, mode, source/window bounds, display/window IDs, sequence, regions, epoch | queued `ScreenshotRequest` | pipeline clear drops it; later callback has no matching active ID. |
| Active capture | Queue head; Service installs six-second timeout and calls Android window/display capture | callback carrying entry ID | dispatch exception, timeout, callback failure, clear, or late callback. |
| Screenshot result | Callback owns copied ARGB bitmap; it builds `CaptureGeometry` and masks fallback regions | synchronous action, OCR, or Mushroom page preflight | copy failure, stale run, mismatched callback, or branch terminal recycles/drops it. |
| Analysis | OCR transaction and optional bounded H10a/Mushroom analysis jobs | immutable frame/evidence | OCR terminal state, registry, frame safety, generation, and admission gate delivery. |
| Action | workflow handler runs with frame geometry/context | node, gesture, Back, input, or next scan | section 10 records the input-action exception. |

Primary source anchors: `requestScan`, `takeGameScreenshot`,
`dispatchNextScreenshot`, `screenshotCallback`, `startOcrTransaction`, and
`completeOcrSuccess` in `PetalAccessibilityService`.

## 2. Screenshot serialization contract

`ScreenshotRequestQueue` is FIFO and has exactly one active **queue entry**.
`enqueue()` makes an entry queued. `startNext()` polls the oldest pending entry
only when no entry is active; repeated calls while active return that same
entry. `finish(id)` completes only the matching active ID. A mismatched/late ID
returns false and increments the bounded late-callback count.

| Question | Current source answer |
|---|---|
| When queued? | At `takeGameScreenshot()` -> `enqueue()`. Depth includes pending plus active. |
| When active? | `dispatchNextScreenshot()` calls `startNext()` and receives the head. |
| When complete? | Matching success/failure callback, copy failure, dispatch exception, or timeout calls `finish(activeId)`. |
| Ordering? | `ArrayDeque` FIFO; later enqueue cannot replace the active entry. |
| Pause/stop? | `pause()` -> `clearScreenshotPipeline()` removes timeout, clears entries, restores capture presentation. |
| Generation change? | Callback checks `isActiveRun(request.generation())`; start/pause advance run generation. |
| Repeated request while active? | Normal `busy` gating prevents it. The queue returns the same active entry, but `dispatchNextScreenshot()` has no independent guard against another Android dispatch of it. A forced rescan that clears busy without clearing the pipeline is therefore not proven single-shot at the platform boundary. |

`finish()` does not automatically dispatch the next pending entry; a later
`dispatchNextScreenshot()` call does. `clear()` changes queue state only;
Service lifecycle code owns overlay restoration and bitmap cleanup.

`ScreenshotRequestQueueTest` and `OcrQueueBackpressureTest` prove FIFO entry
serialization, active idempotence, late-ID rejection, clear behavior and queue
metrics (`TEST`). Android callback ordering is not proven (`RUNTIME` absent).

## 3. Capture identity contract

| Fact | Creation / carrier | Current protection |
|---|---|---|
| Run generation | Incremented on workflow start and pause; copied into request and OCR transaction ID | callback requires `isActiveRun(request.generation())`. |
| Capture sequence | Incremented before enqueue; copied into request, geometry, OCR transaction, frame context | final admission requires current sequence; geometry requires matching transform sequence. |
| OCR transaction | Registry begins with run generation and geometry sequence | one active transaction; terminal/registry checks reject old or duplicate callbacks. |
| Capture timestamp | Android screenshot timestamp -> `CaptureGeometry` | admission rejects future or older-than-3000-ms frame. |
| Package/window/bounds | Current root/window snapshot -> geometry/frame context | final admission compares current package, bounds, and captured non-negative window ID. |
| Admission epoch | Snapshot is put in request; current epoch is put in active OCR transaction/context | admission compares context/current epochs, but the request snapshot is not consumed after enqueue. |

An OCR frame must be complete, belong to the expected transaction, have matching
geometry/transform, and pass current admission before workflow delivery.
`CaptureGeometryTest`, `OcrScanTest`, `OcrScannerTest`,
`OcrQueueBackpressureTest`, and `ActionAdmissionTest` cover the pure-value
portions (`TEST`).

The unconsumed request epoch means the stronger statement that a pre-rescan
capture cannot silently become evidence for a later epoch is not established.
This is a `SOURCE` finding, not a reproduced device failure.

## 4. Bitmap ownership and cleanup contract

`copyBitmap()` closes the Android hardware buffer after creating a mutable ARGB
copy. The callback owns that copy until it recycles it directly or transfers
ownership to `ActiveOcrTransaction`.

| Path | Owner / release |
|---|---|
| Late callback, old generation, mismatched pending capture-only action | callback recycles immediately. |
| Capture-only or keyboard-close action | callback recycles in `finally`. |
| Normal OCR or Mushroom page preflight | `ActiveOcrTransaction` owns source cleanup. |
| OCR success/failure | `finishOcrPostProcessing()` requests cleanup exactly once. |
| OCR timeout/cancel | transaction becomes terminal; cleanup is requested, with a defensive delayed request after cancellation. |
| H10a/Mushroom analysis | each job retains worker and main-result holds, then releases them idempotently on completion/cancel/rejection. |
| Stop/destroy | `pause()` cancels jobs/OCR before pipeline clear; `onDestroy()` closes scanner/executor after pause. |

`DeferredCleanup` refuses post-cleanup retention, marks cleaned before invoking
cleanup, and invokes it at most once after all holds release.
`DeferredCleanupTest`, `OcrScannerTest`, and `OcrQueueBackpressureTest` cover
the generic and OCR ownership models (`TEST`). No R1 device run proves every
real recycle/hardware-buffer/destroy race branch (`RUNTIME` absent).

## 5. Overlay suppression and restoration contract

| Presentation state | Capture behavior | Restoration |
|---|---|---|
| Ordinary feature icon/settings/notice | Non-Mushroom requests carry `List.of()` overlay regions; no explicit ordinary-feature hide/mask occurs. | No capture-specific restore path exists; this does not prove a vendor excludes overlays. |
| Mushroom collapsed icon, settings card, notice | `hideMushroomCaptureOverlays()` snapshots all three values then sets them `INVISIBLE`. | Callback, exception, timeout, pipeline clear, and destroy restore saved values. |
| Mushroom expanded panel / Leaflet map | `MushroomOverlayPanel` and `MushroomMapView` are children of `settingsOverlay`; hiding the root suppresses the expanded presentation. | Restoring the root restores its existing child presentation. |
| Mushroom automation card suppression | Separate helper sets visible settings card to `GONE` while retaining the icon. | Restores only during active Mushroom run; distinct from per-capture snapshot. |
| Return Reward ROI | ROI becomes `INVISIBLE`; capture waits 50 ms. | Callback, exception, timeout, pipeline clear, and stop restore it only during active Return Reward. |
| Display fallback mask | Mushroom snapshots visible icon/notice rectangles and masks only copied OCR bitmap with padding. | On-screen views remain untouched. |

`MushroomCaptureOverlayPolicyTest` proves visibility snapshot values and
`ScreenshotOverlayMaskTest` proves clipping, padding, merging, and edge fill
(`TEST`). Vendor composition, failure restoration, and Leaflet/WebView capture
have no R1 device evidence (`RUNTIME` absent).

## 6. Screenshot failure and retry contract

`ScreenshotRetryPolicy` retries only transient failures at **500, 1000, 2000,
and 4000 ms**. The fifth consecutive transient failure stops; a non-transient
failure stops immediately. A successful copied bitmap and pause reset
`consecutiveScreenshotFailures`. OCR failures and admission rejections use
their own recovery paths and do not consume this budget.

The Service marks internal error, interval-too-short, invalid display, and
(API 34+) invalid window as transient. Timeout and synchronous dispatch
exception use the same failure route. `ScreenshotRetryPolicyTest` and
`ScreenshotAdmissionRetryIntegrationTest` establish schedule, reset, stale
rescan, and pause cancellation (`TEST`); Android error delivery was not
observed (`RUNTIME` absent).

## 7. ActionAdmission contract

`ActionAdmission.evaluate()` takes immutable `FrameContext` plus fresh
`CurrentState`, rejecting in this order:

1. null/invalid values: `INVALID_CONTEXT`;
2. stopped service: `NOT_RUNNING`;
3. generation mismatch: `GENERATION`;
4. newest capture mismatch: `CAPTURE_SEQUENCE`;
5. epoch mismatch: `ADMISSION_EPOCH`;
6. nonzero OCR transaction mismatch: `OCR_SEQUENCE`;
7. invalid/over-age timestamp: `CAPTURE_AGE`;
8. package mismatch: `PACKAGE`;
9. absent package/bounds: `WINDOW_UNAVAILABLE`;
10. bounds mismatch: `WINDOW_BOUNDS`;
11. captured non-negative window-ID mismatch: `WINDOW_ID`.

`FRAME_NOT_ACTION_SAFE` is assigned by the Service before policy evaluation
when an OCR frame is incomplete, mismatched, or has unsafe geometry/ROI mapping.
OCR sequence zero is allowed for capture-only handoffs, while all other
capture/window/age/epoch checks remain active.

R1 extends `ActionAdmissionTest` to freeze each named policy rejection reason.
`CaptureGeometryTest` and `OcrScanTest` cover geometry/frame safety (`TEST`).

## 8. Final-action recheck

The normal sequence is:

```text
captured evidence -> workflow decision -> state/time may change
-> ActionAdmission.evaluate(current root/window) -> Android dispatch
```

Every gesture reaches `dispatchGestureSafely()` immediately before
`dispatchGesture()`. Global Back reaches `performGameGlobalAction()` immediately
before `performGlobalAction()`. `clickGameNode()` admits before lookup and again
immediately before `ACTION_CLICK`. They reject changed package, bounds, window,
run, capture, epoch, OCR sequence, root/window availability, or frame age.

`MultiStageGestureWorkflowTest` and `MultiStageFreshnessTest` establish the
source model for changed generation/capture, expiry, and fresh-stage handoff
(`TEST`); they are not Android dispatch tests.

**Known exception:** `setEditableText()` invokes `ACTION_SET_TEXT`, with
`ACTION_FOCUS` then a second set-text attempt as fallback, without a shared
final `ActionAdmission` check. Feed search, Expedition filter, Planting search,
and Postcard search call this helper. Workflow focus/keyboard checks do not
replace current package/window/generation/capture/epoch checks. This violates
the requested universal final-action invariant.

## 9. Coordinate-space contract

| Space | Current representation / conversion | Preserved boundary |
|---|---|---|
| Bitmap/capture | OCR tokens/detector points are local to copied screenshot; `OcrScan.Transform` maps focused OCR back to source coordinates. | Never use source pixels as screen points without geometry transform. |
| Window | `CaptureGeometry.targetWindowBoundsOnScreen` is the captured game-window rectangle and target ROI basis. | Target-window fallback or mismatched bounds is analysis-only. |
| Display/screen | `ScreenCoordinateTransform.toScreen()` scales/clamps bitmap points; `dispatchScreenTap()` accepts physical screen points. | Do not reuse mapping with differing window/capture geometry. |
| Game-relative ROI | `OcrScan.Profile` uses screenshot or target-window basis. | Target-window action requires resolved action-safe basis. |
| Map/geographic | `MapCoordinate` carries latitude/longitude for Mushroom map/location/patrol. | `screenX/screenY != latitude/longitude`; R1 adds no conversion. |

`CaptureGeometryTest`, `ScreenCoordinateTransformTest`, `OcrScanTest`, and
`MushroomCoordinateIntegrityTest` cover pure separation/mapping (`TEST`).
Physical device coordinate dispatch remains unverified (`RUNTIME` absent).

## 10. Node and gesture action paths

| Action family | Final path | Shared final admission |
|---|---|---|
| Bitmap tap | `dispatchTap()` -> bitmap-to-screen -> `dispatchGestureSafely()` | Yes. |
| Physical-screen tap | `dispatchScreenTap()` -> `dispatchGestureSafely()` | Yes. |
| Swipe/path | `dispatchPath()` -> `dispatchGestureSafely()` | Yes. |
| Hold/continued gesture | Feed start/slice/release -> `dispatchGestureSafely()` | Yes; same-window cleanup applies. |
| Global Back | `performGameGlobalAction()` -> `performGlobalAction()` | Yes. |
| Accessibility node click | `clickGameNode()` -> `ACTION_CLICK` | Yes, twice. |
| Editable text / focus | `setEditableText()` -> `ACTION_SET_TEXT` / `ACTION_FOCUS` | **No.** |

No detector directly dispatches an action. `MushroomDetector` is action-free;
map/mock-location activity is a distinct location path, not accessibility
gesture dispatch.

## 11. Multi-stage gesture behavior

| Flow | Context / freshness | Terminal cleanup |
|---|---|---|
| Feed zoom | Current frame begins pinch; completion waits settle and requests fresh capture. | Generation-guarded callback. |
| Feed hold | Start uses current context; each continuation comes from fresh OCR/capture-only context. Each slice/release rechecks admission and same-window identity. | Abort obtains fresh cleanup context; window mismatch ends local hold state without terminal gesture. |
| Feed spiral/Back | Current evidence creates path/Back; later verification comes from new scan. | Callback enters feature recovery or terminal error. |
| Planting scroll/reveal | Current frame action then scheduled fresh capture. | Bounded workflow retry/error. |
| Expedition reveal/scroll | Current evidence gesture then delayed scan/focused OCR. | Bounded session retry/terminal state. |

`MultiStageGestureWorkflowTest` covers Planting scroll, Feed zoom/hold,
replacement/expiry, generation invalidation, and deterministic cleanup (`TEST`).

## 12. Workflow coverage

All required workflows use the shared scheduled capture path in current source:

| Workflow | Source use of shared seam |
|---|---|
| Care (`FEED`) | scan/OCR, search, zoom, hold, collection, action helpers. |
| Flower Planting | scan/OCR/H10a, search, node/tap start/stop, scroll. |
| Expedition (`DISPATCH`) | scan/OCR/focused list recovery, selection gestures, Back. |
| Return Reward | ROI hide/capture, OCR evidence, screen taps, rescans. |
| Postcard | scan/OCR/focused OCR, search/input, taps, Back. |
| Mushroom normal/continuous | capture, hide/mask, page gate, OCR, worker detector, result publication. |
| Mushroom patrol | same scan path after patrol/location confirmation; location action remains separate. |

This is `SOURCE` coverage only; it does not claim a device run for every
workflow. The domain interpretation of Care/Planting/Expedition/Postcard is
retained from R0/R0.5 documentation (`OFFICIAL_GAME_BEHAVIOR`).

## 13. Missing runtime evidence and characterization gaps

1. No R1 device/API run proves callback ordering, Android error delivery,
   overlay composition, hardware-buffer release, or restoration after
   timeout/cancel/destroy.
2. No device test proves physical screen coordinates against a live game window
   or foldable/vendor display/window capture behavior.
3. No Service-level test exercises queue-active reentry, request-time epoch
   propagation, or every bitmap terminal branch.
4. Editable text/focus actions have no common final admission.
5. Request-time admission epoch is unconsumed after enqueue.

These are explicit gaps; no expected output was changed to manufacture a pass.

## 14. Invariants R2 must preserve or explicitly change under new authorization

1. One FIFO active **queue entry**, late-callback rejection, metrics, and no
   second queue.
2. Generation, capture sequence, timestamp, window/package/bounds, and OCR
   identity from capture through final action admission.
3. Exactly-once bitmap cleanup after all asynchronous holds release.
4. Screenshot retry classification, 500/1000/2000/4000-ms sequence,
   fifth-failure stop, and reset rules.
5. Mushroom snapshot/mask/restore and Return Reward ROI ordering; no map-to-
   screen coordinate conflation.
6. Bitmap/window/display/ROI/geographic coordinate separation, especially
   `screenX/screenY != latitude/longitude`.
7. Fresh-context checks for every gesture, node click, global Back, and
   multi-stage continuation.
8. Before action ownership moves, resolve or explicitly retain and test the
   editable-input admission bypass and request-time epoch propagation.

## R1 exit assessment

| Exit item | Status | Basis |
|---|---|---|
| Screenshot serialization | `PARTIAL` | Queue-entry contract characterized; platform duplicate-dispatch reentry not excluded. |
| Capture identity | `PARTIAL` | Core fields characterized; request-time epoch is not propagated. |
| Bitmap ownership | `CHARACTERIZED` | Source ownership and pure cleanup tests identified. |
| Overlay capture | `PARTIAL` | Source policy characterized; runtime composition/restoration absent. |
| Retry behavior | `CHARACTERIZED` | Policy/integration tests cover schedule and reset. |
| ActionAdmission | `CHARACTERIZED` | Facts/reasons source-mapped and tests pin policy reasons. |
| Final action recheck | `FAIL` | Editable text/focus bypasses it. |
| Coordinate spaces | `CHARACTERIZED` | Source/test transform and separation evidence. |
| Multi-stage gesture safety | `CHARACTERIZED` | Source/JVM fresh-context and cleanup evidence. |
| Production workflow behavior changed | `NO` | R1 changes only docs/tests. |

**R1 result:** `PARTIAL`  
**R2 decision:** `R2_BLOCKED`

The exact blockers are a common final `ActionAdmission` for editable text/focus
dispatch and request-time admission-epoch propagation for in-flight screenshots.
R1 documents them and does not repair either.

## R1.1 Safety Seam Closure (2026-09-14)

**Scope:** only the authorized final editable-node admission, request-time
`admissionEpoch` propagation, and screenshot platform-dispatch idempotency
seams.  No workflow ownership moved, `PetalAccessibilityService` was not split,
and detector/OCR/gesture/timing behavior was not changed.

**R1.1 result:** `PASS`  
**R2 decision:** `R2_READY`

### Keyboard action inventory and closure

| Workflow | Method / node source | Evidence context | Final node mutation path |
|---|---|---|---|
| Care / Feed | `enterFeedNectarSearch()` -> editable/enabled `findGameNode()` | Full OCR `ActiveOcrTransaction` -> `runWithActionContext()` | `focusGameEditableText()` / `setEditableText()` -> `performGameEditableNodeAction()` -> `ACTION_FOCUS` / `ACTION_SET_TEXT` |
| Flower Planting | `enterPlantingFlowerSearch()` -> `setGameEditableText()` -> editable/enabled `findGameNode()` | Full or focused OCR action context | Same common final path |
| Expedition | `handleDispatchPikminFilter()` -> `setFocusedGameEditableText()` -> focused editable `findGameNode()` | Full or focused OCR action context | Same common final path |
| Postcard | `enterPostcardPetalSearch()` -> `setGameEditableText()` -> editable/enabled `findGameNode()` | Full or focused OCR action context | Same common final path |

`setEditableText()` was the shared bypass root cause: it directly called
`performAction(ACTION_SET_TEXT)`, then a focus fallback and retry.  Feed also
called `ACTION_FOCUS` directly before entering that helper.  All production
`ACTION_SET_TEXT` and `ACTION_FOCUS` calls now route through
`performGameEditableNodeAction()`; Feed uses `focusGameEditableText()`.

After the node is resolved, the helper verifies a live enabled/editable game
node and its captured window ID when present, then evaluates the current
`ActionAdmission` immediately before dispatch.  That final admission rechecks
running state, generation, capture/OCR sequence, request epoch, frame age,
current game package, and current window availability/bounds/ID.  A rejected
context invokes stale-action recovery and executes no node mutation.  The text
value remains the caller-provided `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`; no
search string, focus order, retry timing, or keyboard close behavior changed.

`NodeActionAdmissionTest` proves valid focus and set-text dispatch with the
unchanged payload, and zero dispatches for changed generation/capture/epoch,
package, missing/changed bounds, changed window ID, and stale frames.

### Request/frame identity closure

The request-time epoch is copied once at `takeGameScreenshot()` and is never
recomputed for that request's action-safe context:

```text
ScreenshotRequest.admissionEpoch
-> screenshot callback / CaptureGeometry
-> startOcrTransaction(..., admissionEpoch)
-> ActiveOcrTransaction + OcrScanner
-> OcrScan.Frame.admissionEpoch
-> ActionAdmission.FrameContext
-> completeOcrSuccess() final admission
-> workflow handler/action
```

Capture-only and keyboard-close callback paths build their `FrameContext` from
the same request epoch.  Focused Dispatch, Planting, and Postcard OCR derives
the epoch from the originating immutable action context; focused Dispatch
recovery uses that same epoch too.  `completeOcrSuccess()` requires the frame
epoch to match its active transaction before the final admission check.

| Identity field | Immutable request/frame fact | Live rechecked state |
|---|---|---|
| Generation | Request generation and OCR transaction ID | Current running generation |
| Capture sequence | Request sequence, `CaptureGeometry`, OCR transaction ID | Newest capture sequence |
| Transaction ID | `OcrScan.TransactionId` | Latest actionable OCR request sequence |
| Admission epoch | `ScreenshotRequest`, active transaction, and `OcrScan.Frame` | Current `actionAdmissionEpoch` |
| Capture timestamp | `CaptureGeometry` from screenshot result | Current uptime / maximum frame age |
| Package | Immutable expected `GAME_PACKAGE` action target | Current root/window package |
| Window ID / bounds | Request snapshot and `CaptureGeometry` | Current game window ID / bounds |

`PetalAccessibilityServiceWorkflowTest.requestEpochIsRetainedThroughOcrAndRejectsChangedEpochBeforeWorkflowHandling`
uses request epoch `E1`, advances only the current epoch to `E2`, and proves
that the `E1` callback is rejected with `ADMISSION_EPOCH` before workflow
handling or an action.  `OcrScanTest.frameRetainsSuppliedAdmissionEpoch` verifies the frame
carrier itself.

### Screenshot forced-reentry closure

`ScreenshotRequestQueue.claimNext()` claims an active logical entry once for
platform dispatch.  `dispatchNextScreenshot()` dispatches only a claimed entry;
matching completion and pipeline clear release the claim.  Per-request terminal
paths call `completeScreenshotRequest()` before restoring capture presentation,
so a late callback cannot restore the ROI/overlay state of a newer active
request.  Existing
`startNext()` FIFO/active-entry behavior is unchanged.

`ScreenshotRequestQueueTest.dispatchClaimAllowsOnePlatformDispatchPerLogicalRequest`
forces a second claim while request A is active and records:

```text
logical request id = A
enqueue count      = 1
active transitions = 1
platform dispatch  = 1
completion count   = 1
duplicate callback = not completed / no second delivery
```

`ScreenshotRequestQueueTest.lateCallbackCannotRestoreCapturePresentationForNewActiveRequest`
then finishes A, activates B, and proves A's late terminal attempt leaves B's
capture presentation hidden until B itself completes.

The former forced-reentry duplicate platform dispatch is therefore fixed; this
is not merely a queue-entry serialization claim, and a late callback cannot
alter a newer request's capture presentation.

### Final action-path coverage

| Action family | Final boundary | R1.1 status |
|---|---|---|
| Gesture tap/path | `dispatchGestureSafely()` | `PASS` |
| Continued Feed hold | fresh context + `dispatchGestureSafely()` | `PASS` |
| Accessibility node click | `clickGameNode()` | `PASS` |
| Global Back | `performGameGlobalAction()` | `PASS` |
| Keyboard focus/set text | `performGameEditableNodeAction()` | `PASS` |

There is no known game-mutating Accessibility action exception in current
source.  Overlay composition remains `RUNTIME_EVIDENCE_PENDING`; source/test
coverage retains the existing presentation snapshot -> hide/mask -> capture ->
restore-exact-presentation contract and does not make a device-composition
claim.

### Verification

`ActionAdmissionTest`, `NodeActionAdmissionTest`, `SearchKeyboardGuardTest`,
`ScreenshotRequestQueueTest`, `ScreenshotRetryPolicyTest`, `CaptureGeometryTest`,
OCR transaction/stale-callback tests, multi-stage freshness tests, and the full
`testDebugUnitTest` suite passed.  `lintDebug` passed.  These are JVM/static
results only; no new device or gameplay E2E assertion is made.
