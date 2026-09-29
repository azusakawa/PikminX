# R3 — OCR Runtime Extraction and Shared Evidence Support

## Status and scope

R3 is an ownership extraction from `PetalAccessibilityService` into concrete
`OcrRuntime`. This document records the implemented source boundary and the
evidence required to call the phase complete; it does not itself claim that
the builds, tests, or device runtime gate have passed.

R3 preserves the R2 `CaptureCoordinator` and `ActionGateway` contracts. It is
not an OCR redesign, feature migration, accuracy change, or gameplay change.

## Ownership boundary

Before R3, the Service is the Android adapter and also contains the OCR
transaction wiring, including request/callback coordination and capture
resource handoff. After R3, `OcrRuntime` is the sole owner of the common OCR
transaction lifecycle. The Service connects Android lifecycle/platform hooks,
`CaptureCoordinator`, `OcrRuntime`, `ActionGateway`, and the existing workflow
methods; it must not retain a second competing OCR transaction owner.

```text
Workflow
   ↓
CaptureCoordinator ── captured Bitmap + immutable capture context ──→ OcrRuntime
                                                                    ↓
                                                        immutable OcrScan.Frame
                                                                    ↓
                                                                 Workflow
                                                                    ↓
                                                               ActionGateway
```

`CaptureCoordinator` remains the sole capture transaction owner. `OcrRuntime`
does not dispatch screenshots, create a second screenshot queue, own overlay
visibility, retry Android screenshots, or change capture geometry.
`ActionGateway` remains independent: `OcrRuntime` returns evidence only and
never performs actions, dispatches gestures, invokes global Back, or performs
mock-location operations. Workflow state machines remain outside the runtime.

## Runtime responsibilities

The concrete runtime owner should contain the existing common responsibilities
where applicable:

- OCR request creation and profile-selection handoff;
- OCR transaction identity, recognizer invocation, in-flight ownership, and
  callback routing;
- timeout, cancellation/invalidation, stale/duplicate callback rejection, and
  existing queue/backpressure behavior;
- diagnostic timing and immutable frame construction;
- propagation of capture identity and retain/release coordination.

`OcrScanner`, `OcrScan`, `OcrRuntimeDiagnostics`, and `GeometryValidation`
remain behavior contracts to audit and preserve. A type is not moved merely
because its name contains “OCR”. Shared evidence helpers may move only where
the existing plan requires ownership cleanup; they must not become a generic
global state framework. `PetalMatcher` remains cross-feature and is not
semantically split by R3 unless the extraction proves it necessary.

## Identity and evidence contract

The request-time identity chain remains intact:

```text
runGeneration
captureSequence
admissionEpoch
captureTimestamp
package/window identity
CaptureGeometry
OCR transaction identity
```

These values flow from capture through the OCR transaction and immutable
`OcrScan.Frame` to workflow evidence. Callback-time state must not regenerate
request identity. In particular, a frame captured at admission epoch E1 stays
E1 when the live epoch becomes E2; downstream workflow/action admission must
not treat it as E2.

`OcrScan.Frame` contains OCR/capture facts only. It must not gain workflow
state such as Feed, Planting, Expedition, Postcard, Reward, Mushroom, patrol,
overlay, or requested-location state.

## Resource and terminal behavior

The existing Bitmap lifecycle is preserved. `CaptureCoordinator` owns the
captured resource, retains it for OCR, and `OcrRuntime` releases its ownership
on callback, timeout, cancellation, failure, or other terminal completion.
When OCR and another analysis share a Bitmap, each retain is completed
independently and final cleanup occurs exactly once. No use-after-recycle,
double recycle, or terminal leak is acceptable.

Synchronous OCR setup failure must release OCR ownership and the outer capture
ownership correctly. A timeout or invalidation makes the transaction terminal;
late callbacks are ignored and cannot deliver workflow evidence or resurrect
resources. Duplicate callbacks are accepted at most once.

## Compatibility and threading

Use the narrowest compatibility callback or adapter required by current
callers (conceptually, a request carrying Bitmap, capture context, profile,
and callback). Do not rewrite every workflow API or introduce a universal
asynchronous framework.

Preserve the established caller, recognizer-callback, runtime-routing, and
workflow-delivery threading behavior unless a mechanically equivalent handoff
is required. Expensive OCR work must not move to the Android main thread, and
feature decisions must remain in their workflows.

## Profiles and focused OCR

Inventory and preserve current consumers independently: Care/Feed, Flower
Planting, Expedition, Postcard, Reward where applicable, Mushroom page gating,
and focused OCR paths. Full-frame, focused, and Mushroom OCR may have
different outer Bitmap ownership details; extraction must characterize those
differences rather than normalize them speculatively. Mushroom remains
feature-incomplete; R3 preserves only its current OCR/page-gate handoff.

## Required evidence

Record baseline and post-extraction results for the existing OCR, geometry,
queue/backpressure, capture, admission, and gateway tests. Add focused
coverage for successful delivery, identity/profile preservation, setup failure,
timeout, cancellation, stale and duplicate callbacks, Stop/generation
replacement, queue behavior, resource release, and exactly-once terminal
delivery. Add capture-to-frame and stale-OCR-to-action integration coverage.

Required source/build gates are the repository's R3 test, lint, debug,
android-test, and release checks. Do not change feature expectations to make a
gate pass. Report source, unit, integration, build, and runtime results
separately.

Xiaomi runtime evidence is carried forward from R2.1 as deferred technical
debt. If the device is unavailable, report `R3 runtime gate = NOT TESTED —
DEVICE UNAVAILABLE`; this does not by itself fail source migration. If it is
available, the bounded smoke is at most one real capture, one OCR transaction,
one terminal result, and Stop. Do not require positive Mushroom behavior or
indefinite scanning.

## Exit and next phase

R3 source PASS requires unambiguous OCR transaction ownership, preserved
CaptureCoordinator and ActionGateway boundaries, preserved identity/geometry,
unchanged OCR/profile/timeout/queue/threading behavior, safe Bitmap cleanup,
rejected stale/duplicate callbacks, unchanged workflow state, and passing
source/build gates. Runtime evidence is reported separately.

Return `R4_READY` only when the OCR boundaries and resource/callback behavior
are evidenced. Otherwise return `R4_BLOCKED`. R4 must not start automatically.
