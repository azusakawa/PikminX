# R2 — Capture and Action Transaction Extraction

## Status and scope

R2 is the first production ownership refactor after R1.1. It extracts the
shared screenshot transaction and final action-safety seams while preserving
workflow behavior. Runtime/device evidence is pending; this document does not
claim device or gameplay PASS.

The extraction is deliberately package-light. `CaptureCoordinator` and
`ActionGateway` may remain in the current Java package until a later package
move is justified.

## Ownership boundary

Before R2, `PetalAccessibilityService` owns the Android endpoints, screenshot
queue/timeout/retry/cleanup orchestration, capture identity, overlay capture
preparation, final admission, and primitive action dispatch, alongside every
workflow and OCR integration.

After R2, the boundary is:

```text
feature workflow ── request capture ──> CaptureCoordinator
                                          │
                                          └─ Service CapturePlatform adapter

feature workflow ── request action ────> ActionGateway
                                          │
                                          └─ Service ActionGateway.Platform adapter
```

`PetalAccessibilityService` remains responsible for AccessibilityService
lifecycle, current root/window lookup, Android screenshot API calls, actual
node/gesture/global-action dispatch, and WindowManager attachment where still
required. Workflow state machines, OCR runtime ownership, feature detectors,
and `OverlayHost` are not migrated in R2.

## CaptureCoordinator contract

`CaptureCoordinator<R,F>` owns request admission/enqueue, FIFO serialization,
capture-sequence allocation when a request leaves it unset, request identity,
timeout and retry lifecycle, callback validation, capture
geometry association, overlay preparation/restoration token handling,
completion/cancellation, and stale/late callback rejection. It delegates
platform work through `Platform.prepareOverlay`, `restoreOverlay`, `dispatch`,
`schedule`, and `cancel`.

`Request` carries request-time generation, capture sequence, admission epoch,
mode, expected/target bounds, display/window identity, presentation kind, and
overlay regions. Callback success must match the request's sequence, generation,
and epoch. `Outcome` carries the request, optional frame/geometry, failure kind,
and retry indication to the existing OCR/analysis compatibility listener.

The existing `ScreenshotRequestQueue`, `ScreenshotRetryPolicy`,
`DeferredCleanup`, `CaptureGeometry`, `ScreenshotOverlayMask`, and
`FrameAnalysisExecutor` remain the supporting contracts; they are not
rewritten as part of R2.

The R1.1 invariant remains mandatory:

```text
one logical request → one enqueue → one active transition
→ at most one platform dispatch → one terminal completion
```

Late A callbacks cannot complete or restore presentation for newer active B.
Bitmap ownership remains capture → direct recycle, or deferred retain/release
until OCR and analysis users finish, with exactly-once final cleanup.

## ActionGateway contract

`ActionGateway` owns the final `ActionAdmission` decision and platform
primitive coordination for game-mutating actions: node click, editable focus,
editable set-text, tap, path, continued gesture, and game Back. It obtains
fresh `CurrentState` from its platform delegate and returns a `Result` carrying
the action kind, admission decision, and dispatch result. Rejected actions do
not invoke the platform delegate.

The Service remains the Android adapter through `Platform.currentState`,
`performNodeAction`, `dispatchGesture`, and `performGlobalAction`. The gateway
does not become an Android service and does not own read-only node queries.
Every action retains the sequence:

```text
old frame evidence → workflow decision → live state lookup
→ final ActionAdmission → one platform primitive
```

This includes the R1.1 editable-node safety path
(`ACTION_FOCUS`/`ACTION_SET_TEXT`) and the existing `FeedHoldLifecycle`:
initial, continuation, terminal, and abort paths still require fresh admission;
an invalid terminal gesture is never synthesized.

## Compatibility and non-goals

The current Service/OCR integration continues to consume equivalent captured
frame and identity information; `OcrScanner`, `OcrScan`, OCR transaction
ownership, feature retry policies, and detector APIs are unchanged. Compatibility
listeners/adapters are preferred over broad workflow handler redesign.

R2 does not move Planting, Care/Feed, Expedition, Postcard, Reward, Mushroom
scan/patrol, map or mock-location state, `OverlayHost`, or
`GameStateSnapshot`. It does not change coordinate math or introduce any
screen-to-geographic mapping (`screenX/screenY != latitude/longitude`).

## Verification gate

The focused JVM tests are `CaptureCoordinatorTest`, `ActionGatewayTest`, and
`CaptureActionGatewayIntegrationTest`.
They must cover FIFO and single-dispatch capture,
retry/timeout/cancel/generation/epoch/stale and late callbacks, exact overlay
restore, cleanup ownership, valid and stale actions, node keyboard admission,
continued gesture and Back, dispatch counts, and the capture-to-stale-action
integration path. Existing workflow/OCR/detector expectations remain the
oracle.

Required build checks are `testDebugUnitTest`, `lintDebug`,
`assembleDebug`, `assembleDebugAndroidTest`, and `assembleRelease`. Report
their source/build evidence separately from the signed-device gate. The Xiaomi
smoke gate is explicitly `RUNTIME_EVIDENCE_PENDING`; no device PASS is
asserted here.

On 2026-09-14, the required JVM, lint, debug, androidTest APK, and signed
release checks passed. No `adb install -r` or Xiaomi smoke was run without
explicit approval, so runtime/device status remains `NOT_TESTED`.

R2 exit is `R2_READY` only after capture/action ownership, invariants, tests,
lint, and builds are evidenced. R3 (OCR extraction) must not start
automatically.
