# Current Status

## Release cleanup — 2026-09-28

This release cleanup preserves the verified product baseline: Flower Planting
`PASS`, Expedition `PASS`, Big Flower Postcard `PASS`, Care
`WAITING_GAME_STATE` with cursor `PETAL_READY → PETAL_COLLECTED → ROUND_ADVANCED`,
and Return Reward `WAITING_GAME_STATE`. It does not reopen gameplay workflows.

`MUSHROOM_IMPLEMENTATION_STUB_FREEZE` is now the canonical Mushroom status:
feature identity remains visible as a disabled, non-interactive overlay tab;
Mushroom scan, periodic scan, analysis, detector, patrol, map, mock-location,
gesture, retry, and scheduling execution is unavailable. Shared capture, OCR,
admission, action, overlay, diagnostics, and active workflow infrastructure is
preserved. Mushroom-only production internals were removed; the retained
future entry points are Chinese TODO-only contracts in
`MushroomFeatureContract.java`.

Runtime recognition asset provenance is recorded in
`ASSET_PROVENANCE.md` and `asset-provenance.json`. Fruit, Seedling, and Nectar
remain active Expedition/Care banks. Mushroom templates are
`REQUIRED_DISABLED_FEATURE` evidence and are excluded from the active APK
package.

Engineering release candidate: `com.pikminx.helper` `3.1.65` / `375`, APK
SHA-256 `8CE106835F205394426D1007EB02C372B755D6CCC3214857E87C13181685C8DE`,
v2 signer SHA-256 `7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`.
Compile and assemble pass. Lint is `FAIL_KNOWN_DEBT` with only unchanged
`OldTargetApi` at `targetSdk 35`. Bounded Xiaomi smoke installed with
`adb install -r`, launched the app, confirmed the accessibility service bound,
showed the overlay, and visibly showed the dimmed `蘑菇（暫停）` tab without
functional Mushroom controls. No safe natural gameplay state was available for
an active non-Mushroom path; no Mushroom run was started.

## Care initial four direct-selection correction — 2026-09-23

The initial White/Yellow/Red/Blue nectar row was incorrectly falling through to the
search workflow. On the Xiaomi frame, the petal-stock and nectar-count OCR rows are
about 6–7% of screen height apart; candidate pairing accepted only 8–20%, so all four
visible cards could be missed. That path opened Search and used keyboard Back even
though the requested base nectar was already visible, matching the reported route
that could leave the feeding view.

Visible-card pairing now accepts the observed same-card spacing and keeps horizontal
card ownership unchanged. `care-initial-four-fix-device.log` records four candidates
for Yellow, two template-confirmed Yellow observations and no Feed search gesture.
The game remained on the feeding view and the visible Yellow nectar count decreased
from 1010 to 1007. A postcard receipt modal appeared after the feeding round and was
manually discarded; production did not auto-accept or auto-discard that unrelated
gameplay choice.

The observed-spacing regression and the existing missing-label Red candidate
regression both pass. The wider historical FeedScreenAnalyzer class still has six
unrelated pre-existing failures in searched-card fixtures; a checkpoint-source replay
reproduces those failures. Release assembly succeeds. Lint still has only unchanged
OldTargetApi and is not PASS. Signed installed artifact SHA256 is
`cb10051fe2c55046b7953f4beb0305b90e354271b3156f82dcdc55a55250cd23`.

## Care petal initial-search and ordered fallback — 2026-09-24

The affected path is Feed / 花瓣生產. A new run now requires an editable search
query for its first target even when matching nectar cards are already visible.
The ordered target list is snapshotted at run start; reaching a petal or nectar
threshold advances by that snapshot's index instead of looking up the current
target name in mutable settings.

`care-two-fixes-real-device.log` records the first White target requiring Search
with four visible candidates, a focused game input with confirmed search-page
evidence, and matching White nectar templates. It then records White → Red at
index 1 of 2; Red also requires Search and its expected template matches. The
advance diagnostic combines nectar-minimum and petal-limit causes, so it proves
ordered progression but does not independently identify which threshold fired.
The current device screen is the idle 花瓣生產 settings page
(`care-petal-settings-scroll-live.png`); a complete visible feed result is not
claimed.

Release assembly succeeded for `3.1.51` / `361`; APK SHA-256 is
`7d59d80782285a70e3dd3b6642bfd998828a3a1e16ef142d4ba6e73f8449a105`. The same
signed APK was installed with `adb install -r`. No tests were run for this
correction. A 2026-09-24 follow-up assembly after a comment-only edit failed at
`:app:processReleaseResources`: AAPT could not open the generated `stableIds.txt`
and returned `(13)`. No executable code or resource changed after the successful
build; the installed artifact remains the hash above.

## DRAG_12 physical correction and gray Seedling guard — 2026-09-23

The original continuous 5+5+2 paths were admitted but Pikmin Bloom selected only
one stroke origin. Stable current-frame geometry was present; center-bin confirmation
was also unstable. Confirmation now uses row/column layout while all taps use the
latest detected centers. Physical evidence showed that explicit item taps select
correctly. Normal capacity now submits three bounded 5, 5 and 2 tap batches through
the same ActionGateway and immutable action context, with no perception between
batches and one fresh verification afterward. A low-capacity fallback reads a
present 0/N counter only when N <= 2 and submits N single-item groups.

`drag12-capacity-device.log` records capacity=2, groups=1,1, successful GO/X/list
return. `drag12-final-device.log` records capacity=12, stable groups=5,5,2,
SELECTION -> WAIT_RESULT, X tap at 153,2501 and verified list return with SCREEN
60% -> 40% reveal. This is physical-device component evidence for the low-capacity
fallback and normal DRAG_12 transaction, not user-confirmed DEVICE_VERIFIED.

The same run exposed a gray Seedling that visually cross-matched Green Apple and
was opened in Fruit mode. Gray/Black Seedling evidence sets the Black override to
.75 score/.20 margin. `large_green_apple.png` is now a title-OCR guard exception:
two matching fresh OCR observations decide Fruit versus Seedling before policy and
action. Fruit mode subsequently skipped the gray Seedling and reached a real Green
Apple (`fruit-ocr-guard-device-2.log`). Seedling/AUTO then completed three dispatches
before reaching the next White Seedling (`grey-seedling-device.log`). No GO was sent
to the incorrectly opened gray target during diagnosis.

Normalizing from a lower list position originally used a short drag and could exhaust
the stage timeout before reaching top. TOP normalization now uses the middle half of
the current content area; the required return-reveal gesture remains SCREEN 60% ->
40%. A large pull can temporarily hide the scrollbar: another immediate long pull
collapsed the sheet, while repeated short probes kept resetting its animation. The
final state machine waits for up to six fresh settle frames before its existing short
fallback probe. `normalization-settle-final-device.log` records a middle position
(`thumbTop=951`), two scrollbar-free WAIT frames, then `thumbTop=530` and SCAN. No
premature middle-list scan or second pull occurred.

The same settle rule now applies after downward list gestures. At the terminal
viewport, Pikmin Bloom can hide its scrollbar while the sheet rebounds; PikminX waits
for stable repeated content instead of immediately dragging again. The pre-fix
`fruit-auto-mixed-full-device.log` loops at the bottom. The corrected
`fruit-auto-mixed-full-final-device.log` reaches `COMPLETE_WITH_SKIPS` without a
repeat-scroll loop.

A subsequent Seedling/AUTO run completed two more dispatches before Pikmin Bloom
restarted. PikminX detected loss of foreground and stopped safely. After restart,
`remaining-after-restart.png` shows only the intentionally unsupported Ice-blue
Seedling plus gifts as unbordered items; supported White/Gray items are no longer
present. The interrupted run is not a full-sweep PASS, while foreground-loss ABORT
behavior is device-observed.

`fruit-auto-red-final-device.log` records a physical Fruit/AUTO/RED transaction. The
game editor accepted `紅色`; after the guarded Back removed the keyboard, the same
live editor value remained valid, the guard completed, AUTO reached WAIT_RESULT, the
green result X was tapped and list return plus SCREEN 60% -> 40% reveal were verified.
`fruit-auto-red-edge-probe-device.log` also records floating-icon Pause during an
active scan: `GLOBAL_WORKFLOW_STOP` completed and five seconds produced no later
gesture or stale workflow advance. Pressing Check & Start afterward created a fresh
Run rather than resuming the stopped one.

The Green Apple guard also exposed an OCR character substitution: real `青蘋果` was
read consistently as `責蘋果`, so the old exact-name semantics skipped a strong real
Fruit. Guarded title evidence now uses only the stable `蘋果`/`苹果` suffix for Fruit
and `花苗` for Seedling; conflicting or absent evidence remains UNKNOWN. In
`green-apple-title-fix-device.log`, two fresh `責蘋果` frames agree on Fruit, then
AUTO reaches WAIT_RESULT, closes the result and performs the required return reveal.
The subsequent uninterrupted Fruit sweep completes with skips and no remaining
safely actionable target (`fruit-auto-mixed-post-apple-full-device.log`).

Final focused verification covers 3 Recognition, 6 SelectionGeometry, 27 Session,
10 Vision, 40 ScreenAnalyzer, 5 SearchKeyboardGuard and 2 TextNormalizer tests (93
total); release assembly succeeds. Lint still has only unchanged OldTargetApi and is
not PASS. Signed installed artifact SHA256 is
`b036ebb3b0b5f21b82766a123aceb40c4d191a7f99d4eda96c2c412a0508c3d4`.

## Post-GO close and Seedling coverage correction — 2026-09-23

The user reported that successful GO remained on the lower-left X result screen.
`postgo-repro-device.log` reproduced SELECTION -> WAIT_RESULT followed by a stage
timeout. The old detector merged its permissive green mask with the flower-field
background. A first local fix still selected animated flower patches, and coordinate
binning could reset two-frame confirmation. Production now verifies a light X inside
a green circular neighborhood in a bottom-anchored, resolution-relative ROI. Two
fresh detections confirm the constant CLOSE state; the latest frame supplies the tap
point. Three real result screenshots replay to 124,2596; unrelated list, settings and
selection screenshots remain null.

`bank-align-v4-result-close-device.log` then recorded nine consecutive real Seedling
GO close taps and verified list returns, with every return reveal using SCREEN 60% to
40%. Matching was reduced from full refinement of every template to quick ranking
plus refinement of the top three Seedling templates; Fruit retains its fast matcher.
This removed the list-stage timeout while preserving held-out positive/negative
scores. Metric is `masked-rgb-ncc-40-bank-align-v4`.

Remaining real-device Seedlings exposed two independent misses. Accepted red pots
could remain above the return viewport, and White/Blue/Huge observations required
evidence-backed template exceptions. After the required return-reveal drag, the run
now normalizes to list top before a fresh downward sweep. White uses score .80 with
the existing .12 margin; Blue keeps score .85 with margin .10; Huge uses .81/.11.
Observed ICE cross-matches remain below the .85 Blue score gate and stay UNKNOWN.
Black and other weak types were not relaxed without positive evidence. Calibration
label is `XIAOMI_HELDOUT_BANK_OVERRIDES_20260923`.

Raw RGB viewport hashing also changed on animated Pikmin at the list bottom. The
current signature hashes quantized dark text, active-card outlines and scrollbar
structure in blocks, ignoring colored sprite motion. Two same-viewport device frames
now produce the same signature while different positions remain distinct.
`seedling-coverage-device.log` completed `COMPLETE_WITH_SKIPS dispatched=6 skipped=23`.
The inspected terminal frame contains open Fruit targets and active Seedling cards;
no unbordered supported Seedling remains in that viewport. Skips retain unresolved
and policy-inapplicable cases, including unsupported ICE. This is tool-observed
physical-device evidence, not user-confirmed DEVICE_VERIFIED.

Focused verification: 3 Recognition, 25 Session, 10 Vision and 39 ScreenAnalyzer
regressions pass; release assembly succeeds. Lint has only the unchanged OldTargetApi
finding and is not PASS. Signed installed artifact SHA256 is
superseded by the artifact recorded in the current section above.

## Alignment v3 implemented — 2026-09-20

The existing matcher now retains three alignment starts and refines at .05/.025/.0125;
ownership/cache/assets are unchanged. Metric is `masked-rgb-ncc-40-align-v3` with
calibration label `XIAOMI_HELDOUT_ALIGN3_20260920`. Seedling score minimum is raised
from .81 to .85, separating observed ICE negatives (up to .82533) from independent
supported positives (at least .85989). Margin remains .12; Fruit gates remain
.85/.04. Huge/White/other weak cases remain UNKNOWN; this is limited calibration.
Current-source replay matches 23 independently recorded bank/candidate reference
scores and margins to 1e-12. Release assembly and 13 recognition/vision regressions
pass (`alignment-v3-checks.log`). Signed artifact is `alignment-v3.apk`; gameplay
and on-device latency remain DEVICE_PENDING. Historical diagnostic sections below
describe earlier source states, not a parallel production path.

## Independent frames reveal ICE cross-match risk — 2026-09-20

Three-level refinement was replayed on inspected, previously unused-for-tuning
`device-seed-before.png` and `selection-final-complete.png`. Red/Blue/Purple scores
on the former are 0.92085/0.87486/0.85989 with margins above .13; two Apple scores
remain 0.86241/0.86396 with margins above .10. Mushroom/UI negatives remain below
0.612 Fruit and 0.567 Seedling. Complete active cards are excluded by current geometry.

However, visible ICE-blue seedlings (not canonical BLUE) score 0.81158 on the latter
and up to 0.82533 in the controlled perturbation set. The old .81 Seedling threshold
would accept an unsupported identity. Therefore do NOT deploy refinement with old
calibration. A stricter bank score gate must separate these observed ICE negatives
from supported positives (lowest independent supported positive here .85989).
These are limited samples, not universal coverage; Huge remains uncertain. Both
bank results are in `heldout-before-*_templates.log`; second-frame Seedling results
are in `heldout-complete-seedling.log`. No production algorithm/threshold changed.

## Bounded refinement comparison — 2026-09-20

Host-only top-three multi-start refinement (steps .05, .025, .0125) narrows the
same-image +/-3px Pink score range to 0.87789–0.90512 and Red to
0.88096–0.91561, versus current production's 0.75581–0.90483 and
0.73410–0.83747. Original-ROI bank costs are 103–112 ms on this host. Both gift
negatives remain below 0.463 across perturbations. Huge and Blue remain weaker;
do not force their acceptance. Evidence: `bounded-alignment-fine.log` and the local
`CropSensitivityProbe.java`. Earlier two-level variants are recorded separately;
the three-level diagnostic is the current candidate. This remains outside production.
Next compare both banks on independent fruit/seedling and active/reward/gift frames
before changing the metric or its calibration. Single-image tuning is insufficient.

## Controlled alignment diagnostic — 2026-09-20

`CropSensitivityProbe.java` replays the same real pixels with +/-3 pixel vertical
ROI edges. Pink score spans 0.75581–0.90483; Huge 0.76039–0.82194; Red
0.73410–0.83747. Thus animation is not required to reproduce the instability.
An offline dense alignment search improves the two Blue-best scores from
0.59201/0.56360 to 0.79568/0.80985 and Red from 0.78164 to 0.90891. Huge and
Pink do not improve on their original unperturbed rectangles. Two gift negatives
remain weak (0.41983/0.45439). See `crop-sensitivity.log` and
`dense-alignment-diagnostic.log`. Dense search costs 542–642 ms per Seedling-only
candidate on this host, so it is not a production solution or calibrated acceptance.
Next evaluate a bounded multi-start refinement against this reference plus negative
cases; do not deploy dense search or retain old calibration labels after metric changes.
No production source or threshold changed during this diagnostic.

## Seedling recognition diagnosis — 2026-09-20

The prior run's Huge Seedling frame 144 scored 0.81987/margin 0.12263; frame 145
changed bounds from 186x219 to 186x216 and scored 0.80552/margin 0.10408. Two-frame
target confirmation therefore lacked sustained accepted recognition. No policy
mapping defect has been established. Do not bypass confirmation or lower thresholds.

With the helper idle, a current list scroll exposed complete Huge/Blue/Pink/Red
seedlings. Current-source host replay on `seedling-visible-calibration.png` reports
Huge 0.80834/0.10920, Pink 0.84784/0.16651, Red 0.78164/0.10641; Blue-best scores
are 0.59201 and 0.56360. Prior runtime Pink maximum was 0.80573. Measurements are
in `seedling-visible-host-scores.log`; they establish candidate/score sensitivity,
not accepted calibration or gameplay PASS. Next investigate alignment/crop stability
against these real pixels and negative gift/card examples. No production code,
thresholds or canonical assets changed in this diagnostic step. Phone remains on
the list, helper idle; no GO was sent.

## Seedling attempt exposed premature top detection — 2026-09-20

SEEDLING/AUTO/MIXED settings were visually confirmed on the installed confirmed-count
candidate. At frame 40, normalization ended although the detected scrollbar thumb
was at 671 with track top 529 (`seedling-auto-device.log`). Source accepted broad
header words as an alternative to scrollbar-top evidence. That override is removed;
top detection now uses the current scrollbar or the existing repeated-viewport
normalization fallback. Release and 35 session/vision regressions pass in
`top-evidence-checks.log`. Signed candidate SHA256
`d5d61a16b04078029f324bb19899aa76d7c4945fc5694fe7fdbd2cf5d838e081`
was subsequently installed with `-r` and its installed hash matched. The retry log
`top-evidence-device.log` shows normalization continuing past thumbTop 679 to 531
(trackTop 529) before SCAN at frame 19. This is component evidence, not full
user-confirmed DEVICE_VERIFIED. Source checkpoint: `495ac2fb374c70cd32edc18c28d639b8b15d4f8f`.
The retry ended at 22:56:18 with `COMPLETE_WITH_SKIPS dispatched=0 skipped=51`.
No Detail transition occurred. Huge Seedling was classified positively on frames
144 and 186 but did not reach target selection; investigate fresh-frame candidate
confirmation/score stability before claiming Seedling E2E. Thresholds were not lowered.
The attempted run was stopped through the floating icon at 22:47:22; the log confirms
`GLOBAL_WORKFLOW_STOP_COMPLETE`. No Detail/GO transition occurred. Seedling E2E and
the modified statistics counter remain DEVICE_PENDING.

## Confirmed-dispatch statistics — 2026-09-20

Fruit/Seedling usage increments now occur only when `observePostGo` accepts a
completed GO gesture followed by fresh green-close evidence. That transition is
one-shot; repeated frames cannot count again. Returning to the list no longer
increments usage, so a later close/return failure does not erase a confirmed
dispatch. Existing return-completed workflow progress remains separate. Release
assembly and 25 session regressions pass (`confirmed-count-checks.log`). Backend
acknowledgement/idempotency and this counter's physical-device acceptance remain
unverified; no server aggregate result is claimed.

## GO freshness and return gesture observation — 2026-09-20

The installed signed candidate SHA256 is
`c81e81f0d3a83f1ec05ea33f65028d3bfea18cd00ff343f66e13d6e84c400f06`.
GO success now requires a completed GO gesture and a newer capture containing the
green close control; generic result OCR cannot advance SELECTION. The panel handle
detector rejects sparse map lettering, fixing the reproduced ambiguous-handle stop.
25 session and 10 vision regressions pass; release assembly succeeded. Lint retains
the sole known OldTargetApi finding and is not PASS.

Physical-device runtime `handle-fill-device.log` recorded two Fruit/AUTO/MIXED
transactions reaching WAIT_RESULT and verified list return at 22:34:09 and 22:34:54.
Both returns issued the user-specified SCREEN-height 60% to 40% upward drag before
fresh scanning. The empty-query keyboard guard completed with two absent-keyboard
observations. An inspected selection screenshot shows 2/12 and visible GO. These
are component observations, not user-confirmed DEVICE_VERIFIED; the complete matrix,
Seedling E2E, DRAG_12, held-out calibration and Cloudflare contracts remain pending.
At 22:39:45 the run ended `COMPLETE_WITH_SKIPS dispatched=2 skipped=46` after
268 frames. The terminal screenshot was inspected: list bottom is visible and the
helper is idle. No user confirmation has yet upgraded these observations to
DEVICE_VERIFIED. Source checkpoint: `889efc8cdfd856109ba7b2e62a514b57bda6a311`.

## Manual AUTO/search evidence and MIXED correction — 2026-09-20

With PikminX stopped, manual game navigation observed Apple AUTO changing `0/12`
to `2/12`, selected-card backgrounds and visible GO. Native Cancel restored `0/12`.
Search exposed an enabled focused game EditText with empty text; earlier claims
that no editor existed were not established. Back on the empty query closed both
keyboard and search field. The phone was returned to Expedition List without GO.

Source now distinguishes an existing empty editor (including null text) from a
missing editor, and removes the unverified MIXED/no-focus bypass. Only a query
already verified empty in this run may complete after keyboard/search collapse.
Manual evidence is not PikminX E2E or user-confirmed DEVICE_VERIFIED. The modified
filter path still needs device verification; nonempty filtered AUTO is untested.

## Selection cell boundaries — 2026-09-20

Visual bounds above long OCR names could include the neighboring Pikmin. The
search interval is now clipped by current neighboring-label midpoints. More than
five labels in a row is rejected instead of silently truncated. Missing visual
items retain their column indexes, and a drag group with an unconfirmed intervening
column is rejected. This prevents crossing an unknown item merely to connect two
known centers. These source guards remain DEVICE_PENDING; they do not establish
OCR coverage or actual drag selection correctness.

## DRAG_12 batch correction — 2026-09-20

The previous per-row capture loop conflicted with the contract. The current source
plans up to 5, 5 and 2 actual items within observed rows, then submits nonoverlapping
strokes together through the existing gateway. Only the full gesture callback
requests fresh perception. Partial results are verified without repeating selection
gestures; the existing state timeout prevents endless polling. Capacity reading is
performed on the post-transaction frame rather than shortening the batch beforehand.
18 focused regressions pass; release assembled, lint only OldTargetApi. Actual drag
selection, low-capacity behavior and full E2E remain DEVICE_PENDING. Detection still
depends on OCR labels and needs stronger device coverage before deployment.

## Active-card exclusion — 2026-09-20

Current-frame pale-pink rectangular card outlines now exclude already-dispatched
items before template matching. On `selection-final-complete.png`, the same Java
pixel path retains all five available seedling proposals and excludes all eight
proposals inside the four active cards, including the high-scoring Apple. Nine
geometry regressions pass; this is host analysis of device pixels, not a new device
or E2E PASS. Clipped/changed card presentations and runtime cost still need live
validation. Further automatic dispatch requires the remaining safety review.

## Statistics retention correction — 2026-09-20

Unacknowledged usage batches are no longer evicted at 16 entries. Upload runs
serially off the gameplay thread and starts only after the batch has been committed
to local storage. A bare HTTP 409 is retained as a failed send; no duplicate-acceptance
meaning is inferred without the backend contract. Existing 2xx acceptance is retained.
Server deduplication, precise acknowledgement semantics, statistics/error separation
and confirmed flower-result counting remain unverified/incomplete. Client regression
results do not establish server idempotency or gameplay acceptance.

## Return gesture correction — 2026-09-20

The user's clarified return gesture uses **screen height 60% to 40%**, not the
list-content rectangle. Source now derives those endpoints from the current
full-screen capture. After confirmed list return, this gesture runs before candidate
perception, including when the returned panel is only partly expanded. Its callback
clears the one-shot flag and requires a fresh scan; it cannot arm end-of-list
confirmation. An unchanged bottom frame therefore still gets scanned first.
This correction remains pending device verification; the earlier full-sweep result
does not verify the post-dispatch return path.

## Expedition contract continuation — 2026-09-20

Full E2E remains **INCOMPLETE / DEVICE_PENDING**. The active list path now detects
pixel candidates before dual-bank scoring and targeted header/card OCR, through
the existing capture/OCR/action owners. Whole-screen candidate discovery and the
same-bitmap focused rescan are removed. Metric is `masked-rgb-ncc-40-align-v2`;
bank acceptance remains disabled pending calibration. Five-frame uncertainty
budget, visual-identity confirmation and dimmed-list rejection are implemented.

Release assembly and focused regressions pass; lint remains the sole unchanged
OldTargetApi error. A signer-compatible candidate was installed with `adb install -r`.
Two bounded physical observations produced runtime scores/ROI OCR without a target
selection or GO. Floating-icon Stop was observed with about 89 seconds of no later
recognition events. These observations are not user-verified E2E acceptance.

**Sweep correction implemented:** current-frame handle expansion, top normalization,
downward scan, fresh sweep after return, per-run candidate quarantine and distinct
COMPLETE / COMPLETE_WITH_SKIPS / ABORTED outcomes replace the legacy top-stop and
requested-count completion. Exact-pixel bounded score memoization addresses the
reproduced capture-age failure; detected scrollbar evidence addresses an animated
bottom that never repeated its pixel signature. The physical run normalized
and reached the bottom, then safely aborted after eight missing tab-OCR
confirmations; full-sweep completion remains unverified.

**Root cause now reproduced:** the correct selected-tab OCR had a leading `|`
glyph (`|探險`) and failed exact matching. The local tab-label normalizer now strips
edge punctuation/symbols while retaining exact name/current bounds checks.
**Continuation:** tab correction traversed the prior OCR failure. Bottom completion
still needs verification; a bounded extra observation handles an initial missing
bottom after an end scroll. A missed Pink card title now tolerates one interrupted
sampled text row, with unchanged score admission. Regressions/build pass; lint
remains OldTargetApi only. The latest physical run had additional touch events and changing panel geometry,
then timed out; a floating map/window was visible afterward. Device automation is stopped pending user
restoration/readiness. The combined card/timing build is not installed yet.

**Next:** after user readiness, install the combined correction and finish the
sweep observation, then selection/filter and GO/green-X/return.
Calibrated admission and backend batch acknowledgement remain incomplete. Source,
APK identities, checks and observation limits:
[sweep results](../validation/expedition-sweep/RESULTS.md),
[earlier perception results](../validation/expedition-vision/RESULTS.md).

## Selection / result / telemetry continuation — 2026-09-20

Code-ready changes now derive selection item bounds from current OCR labels and
pixels, dispatch DRAG_12 as bounded 5+5+2 gestures with fresh count verification,
clear MIXED filters through the existing search guard, and require AUTO's selected
count plus GO evidence. The result close detector recognizes the current green
circular X bounds in the lower-left ROI; fixed selection slots and the old neutral-X
anchor are removed from the Expedition path. Usage snapshots carry immutable batch
IDs and a bounded persistent retry outbox. Focused tests and release assembly pass;
lint remains the existing OldTargetApi finding.

Selection, AUTO/DRAG_12 and post-GO changes remain unverified on device. The
installed APK used for the full sweep kept calibration closed; the source candidate
now has an evidence-labeled conservative gate, and no Fruit/Seedling target, GO, green-X return, or server
acknowledgement has been observed. The current candidate and exact evidence are in
[sweep results](../validation/expedition-sweep/RESULTS.md). A read-only GET/OPTIONS
check of the configured usage endpoint returned 404; backend source and the separate
error-report contract are still unavailable in this workspace.

## Physical full-sweep evidence — 2026-09-20

The final candidate was installed and byte-verified on Xiaomi after the list was
fully expanded. The real-device run normalized from the bottom with 37 gestures,
then completed 7 downward list gestures, and ended with
`COMPLETE_WITH_SKIPS dispatched=0 skipped=57`. It recorded 293 screenshots,
538 OCR transactions and 582/582 admitted action checks with no stale/rejected
admissions or Capture-Age failure. This advances the list-sweep component to
`DEVICE_OBSERVED_COMPLETE_WITH_SKIPS`; calibrated Fruit/Seedling admission, target
selection, AUTO/DRAG_12, GO, green-X return and backend acknowledgement remain
pending. No target was tapped and no resource was consumed.

The terminal frame provides shadow calibration evidence: Fruit minima `0.85/0.04`
from Apple/Lemon and Seedling minima `0.81/0.12` from Huge, with ICE and active-card
negatives. The source gate is labeled `XIAOMI_FRAME294_CONSERVATIVE_20260920`; lower
scoring canonical types remain UNKNOWN. Calibrated selection reached the real
selection screen, but calibrated E2E remains pending.

The calibrated candidate was subsequently installed and physically reached the
Apple Pikmin-selection page (`0/12`) after a current Apple target tap. It stopped
before GO because the fresh MIXED picker exposed no writable empty Unity editor;
the keyboard remained visible. No resource was used. The source correction now
uses the game's fresh unfiltered picker when no prior filter is focused, and adds
the observed post-return 60%→40% finger-up reveal before scanning. A combined
device rerun remains pending. Final source candidate
`sweep-final-calibrated.apk` is release-built and signer-verified but not installed.

## Earlier calibration-independent integration — 2026-09-20

`CALIBRATION_DATA=NOT_FOUND`; `DEVICE_CALIBRATION_REQUIRED`.
Fruit and Seedling remain `DEVICE_PENDING`. Dual-bank raw scoring/diagnostics
and fresh-frame OCR consensus are integrated; no visual score/margin acceptance
threshold is configured or production verified. See
[the current audit continuation](EXPEDITION_COMPATIBILITY_AUDIT.md#calibration-independent-continuation--2026-09-20)
and [validation results](../validation/expedition-recognition/RESULTS.md).
This user-authorized engineering slice does not advance the Care gameplay cursor
or resume Mushroom.

## Five Main Functions Stabilization Loop — 2026-09-16

The active product order is Flower Planting, Care / Feed, Expedition,
Postcard, then Return Reward. Process one workflow at a time; do not begin the
next workflow before the current real-device exit gate passes or reaches a hard
blocker. R7 work and R8–R14 ownership migration are paused.

`MUSHROOM_IMPLEMENTATION_STUB_FREEZE`: the user-facing 蘑菇 identity remains
visible but disabled. No Mushroom runtime owner is initialized, and no scan,
map, patrol, mock-location, or stale-callback path remains reachable. Historical
Mushroom records below remain evidence/reference only.

P1 Flower Planting is `PASS`. The initial signed-device attempt established
the earliest failure as `ACTION_REJECTED / CAPTURE_AGE` at `WAITING_START`.
Correction 3.1.43 uses the existing Chinese-only OCR profile for
`WAITING_START` and `VERIFYING_START`, without relaxing the 3-second action
gate. Its bounded attempt reached actual active planting and the real game
reported `30` flowers over `2 分 16 秒`; Yellow changed from `1,108` to
`1,107`. Floating-icon Global Stop stopped the PikminX owner; the visible
game-native Stop was then used to limit further authorized petal use, as the
existing S1 contract does not add a game-native Stop action.

P2 Care / Feed is `P2_BLOCKED_AFTER_AUTHORIZED_R4`. The authorized fourth
candidate, `3.1.50` / `360`, correctly recognizes the visible nectar-list
presentation without treating its magnifier as an active search input, but
live OCR did not associate the target card's petal-count token. The guarded
positive-list candidate derivation therefore fell back to legacy search and
stopped before selection. `BUILD` and `ARTIFACT`: `PASS`; `LINT`:
`FAIL_KNOWN_DEBT` for the same sole `OldTargetApi` finding at
`code/app/build.gradle:27`; `INSTALL`: `PASS` by normal `adb install -r` and
installed identity `3.1.50` / `360`. `REAL_DEVICE_FUNCTION`: `FAIL` — Red was
not selected, fed, bloomed, collected, or advanced. `REAL_DEVICE_SAFETY`:
`PARTIAL` — Red remained nectar `366`, petals `1,149`; Yellow remained nectar
`829`, petals `456`; no resource was consumed. `GLOBAL_STOP`:
`NOT_TESTED_AUTO_STOPPED_BEFORE_TAP`; the second attempt stopped for the same
candidate failure before the intended icon tap. Yellow and P3 remain
`NOT_RUN`; do not begin another P2 correction without new authorization. The
sealed record is
[`../releases/p2-3.1.50-360-20260916T150538Z/release-manifest.json`](../releases/p2-3.1.50-360-20260916T150538Z/release-manifest.json).

`COMPILE`, `ASSEMBLE_RELEASE`, `ARTIFACT`, `NO_NEW_LINT_REGRESSION`,
`INSTALL`, `REAL_DEVICE_FUNCTION`, `REAL_DEVICE_SAFETY`, `GLOBAL_STOP`, and
`NO_STALE_RESURRECTION` remain separate gates. Existing `OldTargetApi`
(`targetSdk 35`) remains known lint debt; any new lint issue blocks deployment.

### M1/P1 suspension candidate — 3.1.42 / 352

`BUILD`: `PASS` — `:app:compileReleaseJavaWithJavac` and
`:app:assembleRelease` passed. `LINT`: `FAIL_KNOWN_DEBT` — exactly one
existing `OldTargetApi` at `code/app/build.gradle:27`; no new finding.
`ARTIFACT`: `PASS` — `com.pikminx.helper` `3.1.42` / `352`, SHA-256
`2d362c6801f4cd10d963cb3348e403b02c401b640e7e14d70e97d92518d01e1f`,
verified with the existing v2 signer SHA-256
`7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`.
`INSTALL`: `PASS` — normal in-place `adb install -r` reported `3.1.42` / `352`.
`MUSHROOM_DISABLED_UI`: `PASS` — the dimmed 蘑菇 tab did not leave Planting or
open Mushroom map/scan/patrol UI. `PLANTING_ENTRY` and authorized Yellow
selection: `PASS`. `REAL_DEVICE_FUNCTION`: `FAIL_ACTION_REJECTED_CAPTURE_AGE`
— the real Start control remained visible. `REAL_DEVICE_SAFETY` and
`GLOBAL_STOP`: `PASS` — no planting became active and Yellow remained `1,108`.
`NO_STALE_RESURRECTION`: `PASS_OBSERVED_10_SECONDS` for the post-stop observation. Raw evidence is
[`17-p1-attempt-1-logcat-final.txt`](../releases/m1-p1-3.1.42-352-20260916T081326Z/device-evidence/17-p1-attempt-1-logcat-final.txt)
and [`18-p1-attempt-1-post-stop-10s.png`](../releases/m1-p1-3.1.42-352-20260916T081326Z/device-evidence/18-p1-attempt-1-post-stop-10s.png).

### P1 correction candidate — 3.1.43 / 353

`BUILD`: `PASS` — `:app:compileReleaseJavaWithJavac` and
`:app:assembleRelease`. `LINT`: `FAIL_KNOWN_DEBT` — the same sole
`OldTargetApi` at `code/app/build.gradle:27`; no new finding. `ARTIFACT`:
`PASS` — `com.pikminx.helper` `3.1.43` / `353`, SHA-256
`e7b69e27333d6bae08d0c692d47235ea557e3de225620afa1f27256312c637c9`,
with the existing v2 signer SHA-256
`7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`.
This is correction build 1 of at most 3. `INSTALL`, `REAL_DEVICE_FUNCTION`,
`REAL_DEVICE_SAFETY`, `GLOBAL_STOP`, and `NO_STALE_RESURRECTION` are `PASS`.
The real game recorded `30` flowers over `2 分 16 秒`; authorized Yellow
changed from `1,108` to `1,107`. The floating icon logged
`GLOBAL_WORKFLOW_STOP_COMPLETE` and no later PikminX workflow callback was
captured. The game-native Stop was used afterward to cap resource use, because
existing S1 stops the PikminX workflow rather than adding a native-game Stop
semantic. The sealed record is
[`../releases/p1-3.1.43-353-20260916T083410Z/release-manifest.json`](../releases/p1-3.1.43-353-20260916T083410Z/release-manifest.json).

The authoritative current B0 baseline record is
[`../releases/b0-3.1.31-341-20260915T125230Z/release-manifest.json`](../releases/b0-3.1.31-341-20260915T125230Z/release-manifest.json).
Do not substitute a mutable `code/app/build` output for that sealed manifest.

- Current baseline identity: `com.pikminx.helper` `3.1.31` / `341`.
- Historical R5 provenance: `UNRECOVERED_FROM_AVAILABLE_EVIDENCE`.
- Existing `3.1.30` source-to-APK linkage: `UNVERIFIED`.
- `BUILD`: `FAIL` — the sealed B0 `lintDebug` record reports `OldTargetApi` at
  `code/app/build.gradle:27` (`targetSdk 35`); its sealed release assembly is
  `PASS`. B0 does not change that policy.
- `ARTIFACT`: `PASS` — the sealed source/APK/signer bundle verified again on
  2026-09-15.
- `REAL_DEVICE_FUNCTION`: `NOT TESTED` — B0 has no deployment authorization or
  authorized gameplay scenario.
- `REAL_DEVICE_SAFETY`: `NOT TESTED` — no bounded B0 physical-device session
  was run.

The manifest retains historical build and test records, plus the immutable
source, APK, signer, and verification evidence. Test records are not active
functional acceptance gates. Historical reports remain unchanged. See
[B0_FORWARD_TRACEABLE_BASELINE.md](B0_FORWARD_TRACEABLE_BASELINE.md) for the
bounded scope, exclusions, and limitations.

## O1 conditional real-device validation — 2026-09-15

The sealed candidate was reverified read-only: source tree
`d4227e628f457fab4a4effe55beeb8c546a817872f6d7bac6cd15ff31e2c9265` and APK
`a46d398764399f1794922b616b3dfc7cadbee84e144cbc9150ac6a83207b7106` both
match `b0-3.1.31-341-20260915T125230Z`. `COMPILE` and `APK_ASSEMBLY` remain
sealed `PASS`; `LINT` remains sealed `FAIL` solely for `OldTargetApi`; and
`ARTIFACT_INTEGRITY` is `PASS`.

The connected Xiaomi Android 16 device has an older `com.pikminx.helper`
`3.1.27` / `337` installation. Its full signer compatibility with the exact
B0 certificate could not be established from the read-only Package Manager
record, and historical APK recovery is out of scope. Therefore the conditional
diagnostic-deployment gate is `BLOCKED — INSTALLED SIGNER COMPATIBILITY
UNVERIFIED`; no `adb install -r`, app launch, workflow, game action, or device
session occurred. `REAL_DEVICE_FUNCTION` and `REAL_DEVICE_SAFETY` remain
`NOT TESTED`. See
[O1_CONDITIONAL_REAL_DEVICE_VALIDATION_2026-09-15.md](../validation/O1_CONDITIONAL_REAL_DEVICE_VALIDATION_2026-09-15.md)
for the complete evidence record.

## S1 global workflow safety gate — 2026-09-16

S1 source implementation and signed release assembly are complete. The
Service owns the single-active-workflow admission gate and routes an active
floating-icon tap to global Stop; `OverlayHost` remains presentation-only.
Duplicate same-workflow and cross-workflow starts are rejected without side
effects, stale callbacks cannot restart a stopped owner, and normal Mushroom
scan-to-patrol remains an internal same-owner transition. Collapse, tab, and
map presentation are not Stop.

`BUILD`: `PASS` — compile and `assembleRelease`; `LINT`: `FAIL_KNOWN_DEBT`
solely for the unchanged `OldTargetApi` targetSdk 35 finding. `ARTIFACT`:
`PASS` — signed `3.1.32` / `342` candidate, package, signer, and SHA-256 are
recorded in its release manifest; it was installed with `adb install -r` on
the authorized Xiaomi and its installed identity was verified.

`REAL_DEVICE_FUNCTION`: `BLOCKED — SAFE REAL-DEVICE WORKFLOW STATE UNAVAILABLE`.
The observed Pikmin Bloom home screen and idle PikminX panel showed a
ready-to-start status; no workflow was started and no multi-active state was
manufactured. `REAL_DEVICE_SAFETY`: `NOT TESTED` — test conduct avoided
resource- or game-state-changing workflows, but no running workflow exists as
device evidence for the Stop contract. The historical B0 and O1 records above
remain unchanged; this status does not promote source/build evidence to device
evidence.

## R7 integrated map/patrol requirement update — 2026-09-16

The approved R7 documentation now requires initial/recenter current real GPS,
viewport-only Search and coordinate navigation, explicit 開始巡航, and
separation of viewport, requested/confirmed mock location, patrol point,
scan location, and Mushroom geographic coordinate. The safe default first
POINT scenario may use current confirmed GPS; the former
BLOCKED_USER_MAP_SELECTION_REQUIRED condition is not a blocker for that
low-risk scenario.

Place/address Search is approved for explicit-submit OpenStreetMap Nominatim
use through the R4 RemoteConfig provider/endpoint/enabled configuration and
built-in fallback. It is native/provider-owned rather than Leaflet-owned,
shows attribution, sends only the entered query, and has no autocomplete,
periodic, bulk, or polygon behavior. The source implementation remains subject
to its new compile/lint/artifact and real-device gates.

OFFICIAL_DOCUMENTED: current Pikmin Bloom Help Center guidance documents
Android location services/permissions as required for play; this is platform
guidance, not proof that PikminX patrol or gameplay works. DEVICE_OBSERVED and
UNRESOLVED evidence remain separate.

R7 historical evidence, including signed 3.1.34 / 344 build/install/device
records, is retained unchanged and remains historical evidence only. It is
labelled separately from current source/build/artifact status and does not
establish the updated real-device acceptance gates. R7 documentation status is
`PASS`; R7 completion remains `IN_PROGRESS`.

### 3.1.41 / 351 implementation candidate — 2026-09-16

`IMPLEMENTATION_SCOPE`: `PARTIAL` — the fresh-real-GPS map refresh/recenter,
viewport-only map navigation, validated coordinate navigation, explicit
`開始巡航` copy, and Nominatim-backed explicit location Search are present.
Search has one in-process request lane, one-second start spacing, repeated-query
cache, locale-aware request headers, a provider-owned result model, privacy
disclosure, attribution, and viewport-only candidate selection.

`BUILD`: `PASS` — `:app:compileReleaseJavaWithJavac` and
`:app:assembleRelease` passed. `LINT`: `FAIL_KNOWN_DEBT` — `:app:lintDebug`
reports exactly the pre-existing `OldTargetApi` at `code/app/build.gradle:27`
(`targetSdk 35`) and no new finding. No JUnit, AndroidTest, or instrumentation
task was used as functional evidence.

`ARTIFACT`: `PASS` —
[r7-3.1.41-351-20260916T072903Z](../releases/r7-3.1.41-351-20260916T072903Z/release-manifest.json)
records package `com.pikminx.helper`, version `3.1.41` / `351`, APK SHA-256
`97a4087809fe79eff21c7533ec44e0adfc169eb1868ef95f4baf3906cf7fabdc`, and
the existing v2 signer SHA-256
`7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`.

The prior `3.1.40 / 350` candidate remains historical `BUILD`/`ARTIFACT`
evidence only: candidate
`r7-3.1.40-350-20260916T064809Z` has package `com.pikminx.helper`, version
`3.1.40` / `350`, SHA-256
`925a0d34e1e64ddff06810c4e2aab473c65f9949699d73682af356ff6b272717`, and the
existing v2 signer SHA-256
`7cbdcf023887a46cfd840419433806ae69803326587a17a30d18e6139fbf3a3f`. Its
[release manifest](../releases/r7-3.1.40-350-20260916T064809Z/release-manifest.json)
records the independent checks.

The earlier local 3.1.38 / 348 and 3.1.39 / 349 artifacts are retained as
`REJECTED_STATIC_REVIEW`: the former retained a marker after an unavailable
refresh; the latter could re-render a prior marker while a fresh request was
pending. Neither was installed or used as R7 evidence.

`INSTALL`: `PASS` — the authorized Xiaomi accepted `adb install -r` and then
reported `com.pikminx.helper` version `3.1.41` / `351`.

`MAP_INITIAL_CURRENT_GPS`: `BLOCKED` — the bounded device session opened
Pikmin Bloom and the idle Mushroom overlay, but two visible 地圖-tab taps left
the panel on 掃描. The preserved evidence is
[11-map-initial-current-gps.png](../releases/r7-3.1.41-351-20260916T072903Z/device-evidence/11-map-initial-current-gps.png)
and
[12-map-open-attempt-02.png](../releases/r7-3.1.41-351-20260916T072903Z/device-evidence/12-map-open-attempt-02.png).
`MAP_VIEW_DOES_NOT_INJECT_MOCK_LOCATION`: `PARTIAL` — no patrol or mock
location action was invoked, but Map could not be reached.

`REAL_DEVICE_FUNCTION`: `BLOCKED`; `REAL_DEVICE_SAFETY`: `PARTIAL` — the
session ended after about six minutes (within the 15-minute authorization),
with no Join, `GO`, ticket spend, purchase, uninstall, data clear, downgrade,
or permission/settings reset. Map-dependent coordinate Search, patrol,
confirmation, Pause/Resume, Global Stop, TWO_POINT, and ROUTE gates remain
`NOT_TESTED`. `ROOT_CAUSE_UNRESOLVED`: source click wiring exists, but the
visible evidence cannot establish why 地圖 did not activate.

`LOCATION_SEARCH`: `SOURCE_IMPLEMENTED_DEVICE_NOT_TESTED` because its Map
entry is blocked. `R7_EXIT_GATE`: `BLOCKED_MAP_TAB_NOT_ACTIVATING`; all
remaining physical-device gates are required, and R8 must not begin.
