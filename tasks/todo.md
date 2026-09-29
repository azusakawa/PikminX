# R4 checklist

> Historical R4 checklist only. Its checked test/build entries are retained as
> historical engineering evidence, not active B0 functional acceptance. See
> `../docs/B0_FORWARD_TRACEABLE_BASELINE.md` for active B0 criteria.

- [x] Record pre-change unit-test baseline (616 tests, 0 failures, 0 errors).
- [x] Map current ownership, consumers, adapter boundaries, and package-move dependencies.
- [x] Move the focused platform/update/diagnostics owners and required typed contracts.
- [x] Add compatibility and passive-side-effect regression tests.
- [x] Review the final source boundary and dependency direction.
- [x] Update the four authorized R4 documents.
- [x] Run the required unit, lint, debug, Android-test-build, and release-build checks (622 tests, 0 failures, 0 errors).
- [x] Verify release package/version/signing/hash evidence; device smoke not run because `adb devices` reported no connected device.
