# Mushroom Implementation Stub Freeze

Updated: 2026-09-28

Canonical status: `MUSHROOM_IMPLEMENTATION_STUB_FREEZE`.

The Mushroom feature identity remains visible as a disabled overlay tab. The
tab cannot open a functional Mushroom page and no Mushroom scan, analysis,
patrol, map, mock-location, gesture, retry, or scheduler command is admitted.

## Three-way production classification

- `SHARED_ACTIVE_INFRASTRUCTURE`: `CaptureCoordinator`, `OcrRuntime`,
  `ActionGateway`, `ActionAdmission`, `OverlayHost`, shared screenshot/OCR,
  coordinate/admission checks, diagnostics, and active Postcard map-scene
  detection remain intact.
- `MUSHROOM_DOMAIN_CONTRACT`: `MushroomFeatureContract.startMushroomScan()`
  and `startMushroomPatrol()` retain recognizable names and signatures with
  Traditional-Chinese TODO-only bodies. No production caller invokes them.
- `MUSHROOM_INTERNAL_IMPLEMENTATION`: Mushroom detector/scanner/workflow,
  patrol/location/map/UI owners, Mushroom capture policy, Mushroom-only map
  data types, Nominatim adapter, and mock-location driver were removed from
  production source. Mushroom templates remain provenance evidence but are
  excluded from the active APK asset set.

The active runtime workflows and their safety gates are unchanged. Functional
acceptance remains a signed-APK, physical-Xiaomi, real-game evidence gate;
this source/build record does not claim Mushroom gameplay support.
