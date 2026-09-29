# R4 Platform Configuration, Update and Diagnostics Ownership

> Historical R4 plan only. It is not the active B0 acceptance plan; its test
> requirements/results remain historical engineering evidence. Active B0
> criteria are in `../docs/B0_FORWARD_TRACEABLE_BASELINE.md`.

## Authorized scope

Move the existing typed platform support owners into explicit Java packages while preserving every persisted value, state transition, Android adapter contract, workflow behavior, and R2/R3 ownership boundary.  The user-provided R4 specification is the approved plan.

## Invariants

- Keep `CaptureCoordinator`, `OcrRuntime`, `ActionGateway`, workflow state machines, detector behavior, and `OFFICIAL_GAME_MODEL.md` unchanged.
- Preserve settings preference file, keys, defaults, parsing, serialization, and synchronous confirmed-receipt/dispatch commits.
- Preserve remote-config state names, cache/single-flight behavior, feature gates, update verification/session behavior, receiver identity/actions/extras, and telemetry payload/event behavior.
- Do not initiate a real download, PackageInstaller update, or destructive preference operation.

## Delivery slices

1. Move settings, config, update, and diagnostic/telemetry owners into four focused packages; change only the visibility/import contracts required by existing adapters.
2. Add targeted source tests for settings compatibility, feature admission, diagnostics passivity, receiver callback mapping, and telemetry's local upload double.
3. Update R4 architecture/traceability/release documentation, build all required artifacts, verify release metadata, and report any bounded runtime evidence separately.
