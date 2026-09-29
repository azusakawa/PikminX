# Expedition compatibility audit — 2026-09-20

Status: PARTIAL AUDIT / ASSET_IDENTITY_RESOLVED; see latest update below. No production implementation,
asset replacement, build, installation, or gameplay run was performed.
Authority: the user's supplied Expedition Refactor Execution Contract, sections
7, 12, 64 and 65. This is an implementation audit, not a new architecture phase.

## Verified asset gate

SHA-256 comparisons against the manifests on disk:

| Location | Result |
|---|---|
| `../../fruits` | All 17 PNGs match its manifest. |
| `../../seedling` | RED missing; YELLOW matches; BLUE, HUGE, PURPLE, WHITE, PINK, BLACK mismatch. |
| `../code/app/src/main/assets/fruits` | All 17 PNGs differ from its manifest. |
| `../code/app/src/main/assets/seedling` | Same missing RED / matching YELLOW / six mismatches. |

Both Seedling directories also contain `5917b43c-0279-4627-ae07-650d85e873c6.png`
and `c1faa0f8-aa17-432b-9775-608ce47a05b0.png`; the manifest does not map them to
identities. No `seedling_red.png` was found by filename search under `versions`.
Hash disagreement establishes a provenance conflict, not that a particular PNG
is visually wrong. Do not recrop, regenerate, rename by guess, or automatically
replace the manifest. The maintainer must identify the approved eight files and
which source supersedes the manifest. Required production directory names
`fruit_templates` and `seedling_templates` are absent.

No Fruit/Seedling bank calibration was located in the searched production source
and documentation. Nectar thresholds are Care-specific and must not be reused as
invented Expedition thresholds. Xiaomi evidence is needed for calibration.

## Existing responsibility classification

Paths below are relative to `code/app/src/main/java/com/pikminx/helper`.
These are source findings, not functional acceptance.

| Classification | Existing responsibility | Finding / bounded next action |
|---|---|---|
| KEEP | `CaptureCoordinator`, `OcrRuntime` | Existing capture identity, generation/epoch, cancellation and screenshot cleanup paths; reuse, do not add Expedition owners. |
| KEEP | `ActionGateway`, `ActionAdmission` | Existing final frame/generation/foreground checks; retain for all gestures and editable-node actions. |
| KEEP / DEVICE_VERIFY | `PetalAccessibilityService` run ownership | Existing start increments generation; stop invalidates generation. Preserve shared ownership and cleanup; full pause/settings callback trace remains to be completed. |
| KEEP / EXTEND | `OverlayHost`, `platform/settings/SettingsStore` | Existing target, selection and Pikmin-type persistence. Save and Start have distinct listeners. Audit panel-close and active-setting invalidation before altering behavior. |
| KEEP | `NectarTemplateMatcher`, `NectarTemplateMatchCache` | Lazy immutable template loading already exists; nectar evidence cache is short-lived and Care-specific. Do not create parallel decoders for the same bank or copy Care confidence into Expedition. |
| REWRITE | `ExpeditionScreenAnalyzer.findTarget`, Service list path | Currently OCR-first; focused-list fallback reprocesses the current bitmap. Needs candidate-first dual-bank evidence and fresh-frame bounded OCR escalation. |
| REWRITE | `ExpeditionDispatchSession` | Current completion is requested count; list search moves toward earlier items and missing target stops with error. Replace with verified full sweep, skips, quarantine and no-progress semantics. |
| REWRITE | Service `selectDispatchPikminFromGrid` | Calls `PostcardMatcher.findPikminSelectionSlots(width,height)` and taps one slot per iteration. This is not detected live geometry or bounded 5+5+2 drag selection. Preserve Postcard callers. |
| EXTEND / DEVICE_VERIFY | Service search and AUTO paths | Reuse search injection/admission; verify filter establishment, result refresh and selection change. AUTO filtered-pool semantics need physical verification. |
| REWRITE | `ExpeditionScreenAnalyzer.findResultClose` | Samples a bright neutral X, then returns `resultCloseAnchor(width,height)` instead of detected geometry. Must detect the contract's state-specific green X and use fresh bounds. |
| EXTEND | Service GO and return handlers | Existing transition-pending gate avoids immediate GO repeats. Counters currently update on verified return to list; contract requires confirmed post-GO success and exactly-once counting. |
| DELETE after replacement verification | Replaced Expedition OCR/card, fixed-anchor and count-completion branches | Nothing deleted now; no speculative removal or Care/Return Reward refactor. Existing returned-dialog handling must be evaluated against separate-domain handoff rule. |
| EXTEND | `platform/diagnostics/UsageTelemetryClient` | Existing async HTTP transport, session UUID, bounded in-memory diagnostics and retries. Statistics and diagnostics share a session payload; durable acknowledged batches / bounded error outbox are not implemented there. |
| BLOCKED / evidence needed | Server statistics idempotency | No Worker/Wrangler/SQL source found under `versions/3.0`; HTTP success alone cannot prove server deduplication. Locate existing backend before integration. |
| DEVICE_VERIFY | Complete matrix | Fruit/Seedling E2E, filter/AUTO, drag capacity, green X, fresh sweep, pause and restart all remain DEVICE_PENDING for this contract. |

## Evidence and verification limits

- Inspected historical `../../../validation/device-dispatch-7-task.png`: the
  visible expedition list includes available, active and completed cards. It is
  not a current frame, a post-GO acceptance run, or a source of action coordinates.
- Existing regression files include `ExpeditionDispatchSessionTest`,
  `ExpeditionScreenAnalyzerTest`, `ActionAdmissionTest`, `OcrRuntimeTest`,
  `CaptureCoordinatorTest`, `UsageTelemetryClientTest`. Some session tests encode
  old fixed-grid / bottom-first / requested-count behavior. None was run here.
- Git exists at `versions/3.0`, but `git rev-parse --verify HEAD` fails: there is
  no initial commit. The index already contains extensive staged additions and
  worktree modifications. Nothing was staged, reset, restored, cleaned or committed.
  A rollback checkpoint must not silently absorb unrelated staged work.
 - Referenced `<CODEX_CONFIG_ROOT>/RTK.md` is absent.
- Existing Care cursor documents remain unchanged; this contract authorizes
  Expedition but does not authorize resuming Care or Mushroom.

## Resume condition

Resolve the approved Seedling asset mapping/provenance first, then finish the
remaining compatibility trace and continue the contract's implementation order.
Do not declare CODE_READY, BUILD_VERIFIED, REGRESSION_VERIFIED or DEVICE_VERIFIED
from this audit. No replaced production path is ready for deletion yet.

## Continuation recheck — 2026-09-20

The current filesystem now exposes `assets/fruits_templates` (plural fruits)
and `assets/seedling_templates`, rather than the names recorded during the
initial audit. This task did not rename these directories. The required
`fruit_templates` singular name is still absent. Earlier path observations
above describe the initial snapshot, not the current directory inventory.

Rechecked the current `seedling_templates` files against the supplied Seedling
manifest: RED is still missing, YELLOW matches, and the other six named types
still differ. Also hashed both PNG files currently in the Codex attachments
directory: neither matches the manifest's RED SHA-256. Filename search under
`versions` and attachments found no named RED PNG. These checks did not resolve
canonical identity; the same section 64 stop gate remains. No asset, source,
Git index, or gameplay state was changed by this continuation.

## User-confirmed asset update — 2026-09-20

The user confirmed the main APK assets directory as authoritative and corrected
the Seedling filenames there. `seedling_templates` now contains exactly the eight
named types, including RED and YELLOW, with no UUID PNG filenames. The prior
template-identity stop gate is resolved by this confirmation; do not request it
again based on the stale manifest.

`python scripts/expedition_assets.py --inspect` passed for exactly 17 Fruit and
8 Seedling PNGs (signature, IHDR dimensions, per-chunk CRC, terminal IEND, SHA-256).
All Seedlings and 16 Fruits are 1254x1254; `giant_grapefruit.png` is 80x80.
This is file-integrity evidence, not recognition accuracy or gameplay acceptance.
The original PNG bytes were not changed. A separate in-memory check accepted a
valid PNG and rejected CRC damage, a truncated header, a missing end, and an
invalid signature.

Added `scripts/expedition_assets.py` as the repeatable metadata generator/checker.
Its default mode checks committed manifests; `--write` regenerates metadata only
after approval, and `--inspect` validates current PNGs without trusting manifests.
Existing metadata remains stale: Windows returned Access Denied on manifest write,
on both native attempts to rename `fruits_templates` to contract name
`fruit_templates`, and on saving the inspection output under `validation`.
No permissions were changed; the script currently targets the actual plural
directory. Manifest synchronization and the required directory rename remain
unfinished. No production build, matcher integration or device workflow was run.
ADB lists one connected device, but that alone proves no gameplay acceptance.


## WSL write-gate continuation — 2026-09-20

`WRITE_GATE=PASS`: `WSL_DISTRO_NAME=Ubuntu`, Linux cwd
 `<PROJECT_ROOT>`, and kernel
`6.6.87.2-microsoft-standard-WSL2`. Temporary files were created, read,
modified, deleted, and checked absent under `versions/3.0`, both template
banks, and `src`, `src/main`, `src/main/java`, `src/main/res`, and
`src/main/assets`. Both existing manifests opened in read/write mode without
content changes during the gate. No ACL changes were made.

Resumed the approved Expedition checkpoint:
- Regenerated Fruit then Seedling metadata with the existing
  `python3 scripts/expedition_assets.py --write` generator.
- Renamed `fruits_templates` to contract-required `fruit_templates` and
  updated the generator bank path. All 17 Fruit PNG hashes were checked
  unchanged across the rename; the generator does not modify PNGs.
- `python3 scripts/expedition_assets.py`: PASS for 17 Fruit and 8 Seedling
  PNGs, including exact file inventory, dimensions, chunk CRC and SHA-256.
- Scoped `git diff --check`: PASS. No staging or Git history changes.

The earlier permission and template identity blockers are resolved. Historical
entries above remain as evidence of their original snapshots.

Compatibility recheck remains PARTIAL: production Expedition is still OCR-first;
`findResultClose` still returns `resultCloseAnchor`, and grid selection still
calls `PostcardMatcher.findPikminSelectionSlots`. Settings still persist the
requested dispatch count; telemetry still carries session IDs rather than a
verified immutable batch acknowledgement contract. No backend Wrangler/SQL
source was found within `versions/3.0`.

NEXT REQUIRED INPUT: Xiaomi Fruit/Seedling bank calibration evidence and its
accepted scores/margins. No such calibration was found in the searched production
source, validation metadata or documentation. Contract section 12 prohibits
inventing thresholds or copying Care thresholds. Automatic dual-bank recognition
must not be activated using guessed calibration. The source inspection does not
establish live card/grid/green-X geometry or gameplay success.

NEXT ACTION: load the existing calibration evidence when located, finish the
compatibility trace, then continue the authorized Expedition vertical slice.
Care and Mushroom remain outside this continuation. No production Java behavior
was changed, no legacy path was removed, and no APK build/regression or device
run was performed in this continuation. Overall implementation remains PARTIAL;
real-device Fruit and Seedling status remains DEVICE_PENDING.

## Calibration-independent continuation — 2026-09-20

The user's latest instruction supersedes the preceding `NEXT REQUIRED INPUT`:
missing numeric calibration does not block independent implementation.

```text
CALIBRATION_DATA=NOT_FOUND
DEVICE_CALIBRATION_REQUIRED
FRUIT=DEVICE_PENDING
SEEDLING=DEVICE_PENDING
```

Searched repository source, documentation, manifests, logs and validation text
for `calibration`, `threshold`, `margin`, `fruitScore`, `seedlingScore`,
`USER_DEVICE_OBSERVED`, `Xiaomi`, and `Expedition recognition diagnostics`.
Build/cache outputs and bulk decompiler output were excluded from the text pass;
asset/evidence filenames were also inspected. No matching Xiaomi Fruit/Seedling
score calibration artifact was found. The apparent calibration hits are Android
touch-input settings (`validation/d1.1-20260916-controlled-overlay-input/04-pre-input.txt`
and `08-post-input.txt`), Mushroom coordinate calibration in the separate FreeWay
reconstruction, and this audit's earlier missing-evidence notes. None has current
bank score semantics. Historical `../../../validation/device-dispatch-7-task.png`
was inspected again: a real screenshot is not labeled score/margin calibration.
No historical numeric confidence was adopted.

Current bank manifests (paths relative to this document):
- `../code/app/src/main/assets/fruit_templates/manifest.json`, SHA-256
  `3cb4613a86b0a510af3ee82dd977c467c0fca9fae8abe6d3653f892bbfe1d92c`.
- `../code/app/src/main/assets/seedling_templates/manifest.json`, SHA-256
  `5c454b54005e2587f062b08bb7100e3920da2266b0cf23afebf2a18a7ac2defd`.

Implemented in the active `../code` project:
- One lazy Fruit/Seedling matcher, retaining the two best distinct templates
  per bank and their score difference. Metric `rgb-ncc-80-v1`: Pearson
  correlation over RGB channels at 80x80. Flat/missing banks produce unavailable
  scores, serialized as JSON null. Scores are measurements, not probabilities.
- No score or margin acceptance threshold, universal threshold, confidence
  relaxation, or Care threshold reuse. Uncalibrated visual results remain
  UNKNOWN; exact cross-bank ties remain AMBIGUOUS and cannot dispatch.
- Both ordinary and focused Expedition list OCR enter this matcher. Existing
  OCR/card/gift/pot-color guards remain the fallback. Agreement requires two
  distinct increasing capture sequences, matching candidate identity, run and
  admission context; same-frame focused OCR cannot supply a second vote. OCR
  escalation is bounded to three captures before the existing safe miss path.
  Existing action admission, transition confirmation and Global Stop remain.
- Production Logcat `PikminX` / `EXPEDITION_RECOGNITION` and the existing bounded
  usage diagnostic payload now record bank, bestTemplate, bestScore,
  secondTemplate, secondScore, margin, candidate origin and width/height,
  frameId, visualClassification, finalClassification, metric and calibration
  status. `finalClassification` is recognition evidence, not action completion.
  No new network transport or endpoint was added.

The diagnostic candidate ROI reuses the current OCR-anchored icon region; it is
not a newly verified visual card detector. Independent visual-only candidate
geometry, recognition accuracy, live burst timing and dispatch E2E remain
DEVICE_PENDING. For Xiaomi calibration, preserve the exact APK/source identity,
these manifests, screenshots keyed to capture sequence, recognition logs,
manual ground-truth labels and negative/ambiguous examples. Evaluate thresholds
against that evidence and the exact metric/crop semantics before enabling visual
auto-dispatch. Do not ask the user to supply arbitrary numbers.

Verification results are recorded in
`../validation/expedition-recognition/RESULTS.md`. This is a bounded integration
slice, not acceptance of the entire earlier Expedition refactor contract.
Care and Mushroom were not resumed; the gameplay cursor was not advanced.

## Execution-contract recheck and domain boundary correction — 2026-09-20

The full Expedition E2E contract remains **INCOMPLETE**. This continuation does
not establish CODE_READY, BUILD_VERIFIED, or DEVICE_VERIFIED for that contract.
The existing calibration-independent recognition integration remains partial.

Rechecked capture/OCR/action admission ownership, service start/stop and gesture
callbacks, template banks/cache, settings, telemetry transport, source regressions,
and Expedition geometry. The classification table above remains applicable with
these concrete updates:

- KEEP: CaptureCoordinator, OcrRuntime, ActionGateway/ActionAdmission and immutable
  template caches. No new owner, runtime, dependency, endpoint or threshold added.
- DELETE: Expedition's embedded returned-reward collect handler and its now-unused
  collect-target helper. A recognized blocking returned-reward dialog now stops
  through the existing error/pause path and tells the user to handle Return Reward
  before starting a new Run. No reliable cross-owner handoff has been established;
  no Return Reward implementation was refactored.
- EXTEND: Expedition start explicitly resets recognition consensus, burst/frame
  tracking, pending recognition and current item kind. Immutable template banks
  survive a new Run.
- EXTEND, still outstanding: settings persistence has no active-run change listener;
  Save and Start listeners are distinct, and panel X only changes presentation.
  Shared pause performs hold cleanup before generation invalidation; do not reorder
  that shared lifecycle blindly or break required hold release.
- REWRITE, still outstanding: OCR-anchored candidate discovery, fixed proportional
  detail fallback, predicted Postcard grid slots, neutral-X/fixed-anchor result
  close, requested-count termination and upward list scan. None is certified as
  the contract's candidate-first / 5+5+2 / green-X / full-sweep implementation.
- BLOCKED for server integration: no /v1/usage backend source was found in 3.0 or
  the bounded root Worker/Wrangler/SQL filename search. Root .wrangler runtime cache
  exists but is not server source. Requested the backend location. HTTP 2xx alone
  does not establish immutable-batch acknowledgement or server deduplication.
- Existing planting telemetry derives a petal-counter decrease, not the actual
  flowers-planted result. This is not TOTAL_FLOWERS_PLANTED acceptance; changing
  that separate workflow is outside this Expedition correction.

Read-only ADB screenshot showed the Android launcher, not Expedition. No app was
launched, game control tapped, APK installed or workflow started. The temporary
screenshot was deleted after that determination. Historical device-dispatch-7
was inspected for context only; it is not current geometry or score calibration.
Fruit/Seedling remain DEVICE_PENDING; live required state was not available in
this observation. Section 12 still forbids fabricated acceptance thresholds.

Validation: asset inventory/CRC/SHA-256 PASS (17 Fruit, 8 Seedling); standalone
recognition algorithm regression PASS; changed analyzer freshly compiled and its
37 source regressions PASS. Production assembly and Gradle regressions did not
complete: AAPT could not open generated stableIds.txt (error 13), reproduced with
normal, isolated desktop and user-temp build output directories. No ACL/security
setting was changed. See ../validation/expedition-contract/RESULTS.md.

No initial Git HEAD exists; extensive staged/untracked pre-existing work remains.
No index/history mutation was made. The turn-only patch isolates this correction.
Next: resolve the AAPT environment failure; continue candidate-first geometry and
Xiaomi calibration collection before visual admission; obtain existing backend
source for the statistics contract. No Care or Mushroom gameplay cursor advanced.

### Build recovery and settings ownership update

Supersedes the preceding AAPT blocker and outstanding saved-settings listener item:
installed SDK AAPT2 successfully links the same minimal probe that fails under the
cached Maven executable. Offline command-line override restores release assembly;
no ACL, security policy, Gradle project setting or dependency changed. Final source
has 80 passing selected regressions and only the pre-existing OldTargetApi lint error.
APK signature matches the existing production signer; no install was performed.

SettingsStore remains the sole settings owner. It atomically saves an Expedition
configuration revision; repeated saves and gameplay counters do not change it.
Service listener, pending start check, callback guard and final admission reject
obsolete revisions. A late notification cannot invalidate an already-current Run.
Expedition Pause now invalidates generation/running/gateway before cleanup; this
branch does not change normal Care hold cleanup. Observer teardown is explicit.
No settings UI redesign or automatic launch was added. Physical Pause/settings
acceptance remains DEVICE_PENDING.

Manual navigation on the connected device encountered an existing Mushroom result
animation after the Expedition-tab tap. Stopped without continuing/collecting;
requested user preparation of a usable Expedition List. No new Mushroom workflow
was started. Geometry/calibration collection is GAME_STATE_UNAVAILABLE at this
interruption, and backend source location remains unanswered. Continue independent
Expedition work; do not infer calibration or E2E acceptance from the restored build.
See validation/expedition-contract/RESULTS.md for current artifacts and checks.

### Current-device observations and control geometry

The user cleared the interruption and confirmed Expedition List. Manually
captured the available Fruit/Seedling/gift examples and Fruit Detail/selection
screens without Auto, selecting Pikmin or GO. Returned the phone to the list.
Calibration evidence is now COLLECTED_UNVALIDATED, not NOT_FOUND; no production
acceptance threshold is enabled. Decoration backgrounds and a Pink/Huge near-tie
are real reasons to preserve uncertainty. Host exploratory metric differs from
rgb-ncc-80-v1 and must not be used as production calibration.

DELETE/REWRITE completed for the fixed detail-action fallback and fixed search X.
Detail action now uses OCR bounds or a unique observed rounded teal outline.
The actual expanded-detail variant moves that control into the middle of the
screen; both heights are covered by the same detector and tests. Search X/Y now
come from current pixels. Full candidate-first recognition, grid selection,
post-GO green X, full sweep and backend batch acknowledgement remain outstanding.
Release assembly and 38 changed Analyzer regressions pass; only unchanged
OldTargetApi lint debt remains. Signed APK verified, not installed. Raw/local
observations and measurement limits: ../validation/expedition-calibration/README.md.
No functional PASS or workflow cursor advancement is claimed.

### Candidate-first path integrated; remaining sweep failure reproduced

REWRITE completed for list object discovery: CaptureCoordinator capture -> existing
bounded analysis executor -> current pixel proposals -> one cached Fruit/Seedling
matcher -> same-frame targeted OCR through OcrRuntime. Dynamic ROI identity/window
bounds and cleanup are guarded. The old full-screen candidate/focused-rescan path
and OCR-anchored icon ROI are deleted. Banks remain uncalibrated/fail-closed; raw
scores are measurements, not probabilities or dispatch permissions. The two decoded
banks share one Expedition matcher/cache; Care's nectar path is untouched.

Final-source safeguards bound OCR uncertainty to five fresh observations even when
rank/label identity changes, include visual identity in target confirmation, and
reject a dimmed navigation backdrop. Strong classifications are policy-filtered
only after both banks are evaluated. No canonical PNG was changed.

Signed Xiaomi observations exercised ROI OCR, fresh captures and floating-icon Stop
without target taps or GO. The second run reproduced the old at-top/no-target stop:
normalization/downward sweep, quarantine and completion semantics are still REWRITE,
not a functional PASS. Bank calibration, selection, post-GO control and Cloudflare
backend acknowledgement remain pending. Current evidence/limitations are in
../validation/expedition-vision/RESULTS.md. No Care/Mushroom cursor advancement.


### Full-sweep implementation and reproduced list defects

REWRITE implemented for list normalization, downward sweep, run completion and
candidate-specific no-progress quarantine. The obsolete remaining-count helper
and count UI were removed because they prematurely terminate the required sweep;
the old stored count remains untouched/ignored. Target, method and Pikmin type
settings retain the existing interaction model.

EXTEND: current pixel surface now includes a detected panel handle and scrollbar.
A fresh bottom viewport must be scanned before a later admitted scroll and two
fresh end observations can finish. A current actionable candidate prevents end
confirmation. A bounded exact-pixel score cache sits inside the existing matcher
lock/lifecycle and never supplies action bounds or relaxes capture-age admission.

Physical observations reproduced and locally corrected repeated bank-computation
cost, sheet-collapse during normalization, and animated-bottom nontermination.
Latest run reached the bottom but safely aborted on eight missing tab-OCR
confirmations. Added tab-row failure diagnostics; end completion remains unverified.
These are component observations, not
Fruit/Seedling E2E or DEVICE_VERIFIED. The calibration gate remains closed;
selection, green-X dispatch/return and backend acknowledgement remain REWRITE /
DEVICE_VERIFY. See ../validation/expedition-sweep/RESULTS.md.

The tab diagnostic reproduced `|探險` inside the exact current selected-tab bounds.
A local label normalizer strips only edge punctuation/symbols, preserving exact
name and bounds requirements; extra words/interior noise stay rejected. The
corrected candidate is build/regression checked, pending installation and device
sweep observation. This does not enable Fruit/Seedling score admission.

Tab correction traversed the former failure in a signed-device scan. The animated
bottom still rescanned; a bounded fresh-frame settling guard and scrollbar logging
were added. Subsequent device observation had additional touch events and panel changes;
a floating map was visible afterward. Completion remains pending user readiness. Same-image analysis also exposed
one-row glyph interruption rejecting the visible Pink seedling card; the local
text-row detector now handles that gap. Neither correction enables score admission
or changes capture/OCR/action ownership. See sweep RESULTS for exact evidence.

### Selection, post-GO control and statistics continuation

REWRITE completed for Expedition selection geometry: `ExpeditionSelectionGeometry`
uses current-frame OCR card labels and nearby item pixels, while the postcard
matcher remains unchanged for its own workflow. DRAG_12 sends bounded groups of
up to 5+5+2 current item centers and waits for a fresh selected-count increase
between groups. The current `0/N` capacity limits the desired count when present.
MIXED clears the existing game search field through the shared keyboard guard and
requires a refreshed visible item list. AUTO now requires selected count and GO
evidence together.

REWRITE completed for post-GO close evidence: the old neutral-X detector and fixed
anchor were removed. The current lower-left ROI must contain a green circular
component with a contrasting X; the returned point is that component's current
center. Unit geometry evidence is synthetic only.

EXTEND completed for the existing usage transport: immutable session IDs are emitted
as `batchId`, and context-backed sessions persist bounded JSON batches before retrying
the same payload. Success or HTTP 409 removes the local batch. No screenshots or
account identity are added. The configured usage URL returned 404 to read-only
GET/OPTIONS checks, and no Worker source is present here; server deduplication and
the separate error-report endpoint remain pending. No calibration or device PASS is
claimed.

The final candidate then produced the first real Xiaomi complete-sweep observation:
after list expansion it normalized with 37 gestures and admitted 7 downward gestures,
and ended `COMPLETE_WITH_SKIPS` with 57 safe skips and zero dispatches. The run had
582/582 admitted action checks, no stale/rejected admission and no Capture-Age
failure. This verifies the list-sweep and safe-skip component only; no template
admission, target selection, GO, green-X return or backend acknowledgement was
observed. See validation/expedition-sweep/selection-final-device-logcat-2.txt.

The terminal frame also supplies conservative shadow calibration evidence for the
current production metric. Fruit thresholds `0.85` / `0.04` are supported by the
observed Apple/Lemon positives; Seedling thresholds `0.81` / `0.12` are supported
by Huge, while ICE and active-card negatives stay below the score gate. The source
candidate labels this calibration `XIAOMI_FRAME294_CONSERVATIVE_20260920` and keeps
other lower-scoring canonical types UNKNOWN. The calibrated candidate reached the
real Apple `0/12` selection screen and stopped before GO because MIXED had no
writable empty Unity editor; no resource was used. This does not establish E2E
acceptance.

The follow-up source correction uses the fresh unfiltered picker when no prior
filter is focused. User-observed return behavior is encoded as one current
content-geometry finger-up reveal from approximately 60% to 40% before the next
scan (`RETURN_REVEAL`). Combined device rerun remains pending.

Correction to the preceding geometry description: user clarification specifies
screen-height 60% to 40%, not content-height percentages. The current source applies
that gesture once before inspecting returned candidates, without a separate initial
handle expansion. Reveal completion does not establish an exhausted viewport; a
fresh candidate scan is mandatory even if the bottom frame is unchanged. The
post-dispatch device path remains unverified.

The usage outbox audit found that the 16-entry trim deleted unacknowledged batches
and that HTTP 409 was assumed to mean already accepted. Both paths were removed.
One background executor serializes upload; successful durable batch storage is
required before transmission. Only existing 2xx acceptance removes a batch, and
failed removal commits stop the current flush. Backend deduplication and the actual
acknowledgement contract still need source/evidence; this correction does not prove
exactly-once aggregation. The separate statistics and error schemas remain incomplete.

Current-frame active-card exclusion fixes a reproduced admission hazard: the terminal
device screenshot contains high-scoring Fruit inside already-dispatched cards.
The pixel proposer now recognizes complete pale-pink card outlines (four supported
edges) and excludes their contents before bank scoring. Same-image production Java
analysis retains the five outside seedlings and removes all eight inside proposals.
Synthetic regressions cover translated columns at three scales. This remains
DEVICE_PENDING; incomplete/clipped outlines are not established by this check.

DRAG_12 correction replaces the earlier per-segment screenshot loop: one current
frame produces up to three row-bounded strokes (5/5/2), submitted sequentially in
one Android gesture description through existing admission/cancellation. The callback
requests fresh verification only after all strokes. No absent slots are invented,
and a partial count does not trigger a blind re-drag. Post-batch capacity/count
verification remains; actual selection geometry and low-capacity safety are still
DEVICE_PENDING. Shared single-path callers use the unchanged callback guards.

Selection geometry review reproduced neighboring-item contamination when a long
label widened the pixel search. Current same-row label midpoints now bound that
search. Rows with more than five labels fail closed; columns whose visual bounds
are missing retain their index so a drag cannot bridge an unconfirmed gap. Sparse
ambiguous detection is rejected, not treated as a valid compact row. Device-level
selection and low-capacity acceptance remain unverified.

New manual device observations establish game-native AUTO `0/12 -> 2/12` with GO,
Cancel returning to `0/12`, and an enabled focused empty game EditText while search
is open. Back collapses the empty search field with its keyboard. The raw hierarchy
does not distinguish Java null text from an empty string, so the prior failure's
precise cause remains unproven; the source's unconditional null-text rejection was
nonetheless incorrect for a present empty editor. That case is now handled without
treating a missing editor or punctuation-only query as empty. The MIXED/no-focus
bypass was removed. The corrected path is pending device verification.
