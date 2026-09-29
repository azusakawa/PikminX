# B0 Forward-Traceable Baseline

## Scope and authority

This record establishes the forward baseline for `versions/3.0`; it does not
reconstruct any earlier release history. The authoritative B0 release record is
the sealed manifest at
[`../releases/b0-3.1.31-341-20260915T125230Z/release-manifest.json`](../releases/b0-3.1.31-341-20260915T125230Z/release-manifest.json).
That manifest, rather than a mutable `code/app/build` output, records the
sealed build, artifact, signer, and historical verification records. It does
not establish runtime or gameplay behavior.

The preserved as-found state is
[`../archives/b0-as-found-3.1.30-340-20260915T190403/as-found-manifest.json`](../archives/b0-as-found-3.1.30-340-20260915T190403/as-found-manifest.json).
It is labeled **"Current-state baseline; earlier history unavailable."**

## Provenance limitations

- `HISTORICAL_R5_PROVENANCE`: `UNRECOVERED_FROM_AVAILABLE_EVIDENCE`
- `EXISTING_3_1_30_SOURCE_LINK`: `UNVERIFIED`
- No VCS history predated this B0 work. A local Git repository was initialized,
  but no commit is made without an existing author identity.
- The B0 APK is a new baseline build. It is neither a recovered historical R5
  nor a historical R6 artifact, and this record is not a reproducible-build or
  SLSA certification.

## Preserved input boundary

The source inventory uses an explicit allowlist:

- Android source, JVM tests, instrumentation tests, resources, and assets under
  `code/app/src`;
- `code` Gradle configuration and `app/build.gradle`;
- project documentation, task notes, source-tracking files, and B0 scripts;
- the shared `gradlew` scripts and `gradle/wrapper` files required to invoke the
  isolated Gradle project.

It excludes generated Gradle/build output and caches, historical APKs and
runtime captures, archive/release directories, the machine-specific
`local.properties` SDK locator, and the protected signing directory. The
signing configuration reference remains in `code/app/build.gradle`; no key,
password, token, or credential property is archived, tracked, or reported.

## B0-only source change

The only Android production configuration change is this release identity
change in `code/app/build.gradle`:

```diff
- versionCode 340
- versionName "3.1.30"
+ versionCode 341
+ versionName "3.1.31"
```

No gameplay, detector, OCR, UI, timing, workflow, dependency, toolchain,
application-id, or signer behavior is changed by B0.

## Bounded current-state review

Existing source retains the following ownership boundaries:

- `CaptureCoordinator` is the capture transaction owner.
- `OcrRuntime` retains capture/request identity and OCR resource ownership.
- `ActionGateway` remains the final action-admission boundary.
- `OverlayHost` is presentation-only.
- R6 keeps normal Mushroom coordination in `MushroomWorkflowCoordinator` and
  bounded analysis in `MushroomScanner`; patrol uses the shared session path
  rather than a competing scan loop.

No additional B0 source-boundary issue was identified by this bounded review.
The sealed historical build record has one lint blocker: `OldTargetApi` at
`code/app/build.gradle:27` (`targetSdk 35`). Scope forbids correcting that
policy failure. Its `NEW_BASELINE_TEST_BUILD` field is retained unchanged in
the sealed manifest as historical evidence, but is not an active B0 functional
gate. Mushroom accuracy and all device, accessibility, gameplay, self-update,
and installation behavior remain outside this work package.

## Active B0 acceptance criteria — 2026-09-15

This addendum replaces `@Test`, unit-test, and Android-test execution as
functional acceptance gates for the active B0 work. Existing test files,
fixture assets, historical reports, and sealed manifest entries are preserved;
they remain engineering history only.

| Category | Status | Evidence and boundary |
|---|---|---|
| `BUILD` | `FAIL` | The sealed `lintDebug` record reports `OldTargetApi`; the sealed `assembleRelease` record is `PASS`. No fresh build was run for this documentation-only addendum. |
| `ARTIFACT` | `PASS` | On 2026-09-15, `Verify-ReleaseBundle.ps1` verified the sealed source tree (`d4227e628f457fab4a4effe55beeb8c546a817872f6d7bac6cd15ff31e2c9265`) and signed APK (`a46d398764399f1794922b616b3dfc7cadbee84e144cbc9150ac6a83207b7106`). It does not build, install, or establish gameplay behavior. |
| `REAL_DEVICE_FUNCTION` | `NOT TESTED` | No signed B0 APK deployment or authorized PikminX-to-game production-path scenario occurred. |
| `REAL_DEVICE_SAFETY` | `NOT TESTED` | No bounded physical-device scenario was authorized or run. |

`OFFICIAL_DOCUMENTED` gameplay meaning is maintained in
[`OFFICIAL_GAME_MODEL.md`](OFFICIAL_GAME_MODEL.md), refreshed on 2026-09-15.
`DEVICE_OBSERVED` is empty for B0. `UNRESOLVED` includes the installed game and
account versions, task-specific prerequisites/costs, fresh screen identifiers,
and all observable post-action and recovery evidence.

The documentation changes in this addendum are post-seal and do not modify the
preserved files inside the B0 release bundle or change its source-to-APK claim.
No installation, real-device scenario, resource use, location change, or next
work package is authorized. The next authorized action is therefore **none**;
an explicit later authorization must name the signed APK, device, gameplay
scenario, attempt/duration bound, resource budget, and Stop/cleanup action.

## Verification tool

`../scripts/Verify-ReleaseBundle.ps1` reads an explicit release manifest,
verifies required entries, source inventory/tree and APK hashes, and validates
the package/version/signer/signature schemes with local Android build tools. It
never builds, installs, repairs, or rewrites a manifest.
