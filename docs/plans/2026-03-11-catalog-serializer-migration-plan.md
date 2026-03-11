# Catalog Serializer Migration Plan

## Goal

Migrate the existing catalog implementations so the `org.red5.io.moq.catalog.CatalogSerializer` stack becomes the primary foundation for MoQ catalog serialization in this repository, replacing the current pattern where WARP/MSF maintain separate serializer code paths.

This should reduce duplicated JSON handling while preserving existing WARP/MSF behavior and validation rules.

## Current State

There are currently two parallel catalog systems:

- Generic catalog-format model:
  - `src/main/java/org/red5/io/moq/catalog/Catalog.java`
  - `src/main/java/org/red5/io/moq/catalog/CatalogTrack.java`
  - `src/main/java/org/red5/io/moq/catalog/CatalogSerializer.java`
- WARP/MSF model:
  - `src/main/java/org/red5/io/moq/warp/catalog/WarpCatalog.java`
  - `src/main/java/org/red5/io/moq/warp/catalog/WarpTrack.java`
  - `src/main/java/org/red5/io/moq/warp/catalog/WarpCatalogSerializer.java`
  - `src/main/java/org/red5/io/moq/msf/catalog/MsfCatalogSerializer.java`

The important constraint is that these are not just different serializers. They currently represent different JSON schemas:

- `Catalog` uses:
  - `streamingFormat`
  - `streamingFormatVersion`
  - `commonTrackFields`
  - nested `selectionParams`
  - optional `catalogs`
- `WarpCatalog` / `WarpTrack` use:
  - `version`
  - `deltaUpdate`
  - `generatedAt`
  - `isComplete`
  - flat per-track fields like `codec`, `width`, `height`, `targetLatency`, `eventType`
  - delta operations `addTracks`, `removeTracks`, `cloneTracks`

Because of that, the migration cannot start by simply subclassing `CatalogSerializer` from `WarpCatalogSerializer` or swapping delegates. A mapping layer is required first.

## Migration Principles

- Keep WARP/MSF validation logic intact until the model migration is complete.
- Separate transport/JSON concerns from spec validation concerns.
- Migrate incrementally with compatibility adapters before deleting any existing serializer.
- Preserve current JSON output for WARP/MSF tests until an explicit format change is intended.

## Recommended Direction

Use `CatalogSerializer` as the base serialization engine, but evolve the catalog model so WARP/MSF catalog classes become specialized views over a shared base model instead of independent POJOs.

That implies two layers:

1. Shared base catalog model and serializer infrastructure in `org.red5.io.moq.catalog`
2. Format-specific adapters/validators/builders for WARP, MSF, CMSF, CARP, etc.

## Phase 1: Define The Target Abstraction

Before changing code, explicitly decide what “base class” means here.

Recommended target:

- `CatalogSerializer` becomes the shared JSON engine and factory surface.
- `Catalog` / `CatalogTrack` become the shared base data model.
- `WarpCatalog` and `WarpTrack` either:
  - extend the shared base classes, or
  - become format-specific facade/builders that convert to/from the base classes.

Recommendation:

- Do not force inheritance first.
- Start with conversion adapters, then decide whether inheritance is still useful once field alignment is clear.

Reason:

- The current field mismatch is large enough that premature inheritance will likely create an awkward base class with many nullable format-specific fields.

## Phase 2: Build Explicit Mapping Adapters

Create explicit conversion utilities between the two models.

Suggested additions:

- `src/main/java/org/red5/io/moq/catalog/WarpCatalogAdapter.java`
- `src/main/java/org/red5/io/moq/catalog/MsfCatalogAdapter.java`

Responsibilities:

- `WarpCatalog -> Catalog`
- `Catalog -> WarpCatalog`
- `MsfCatalog -> Catalog`
- `Catalog -> MsfCatalog`

Mapping decisions to make explicit:

- flat WARP track fields to `selectionParams`
  - `codec`, `mimeType`, `framerate`, `bitrate`, `width`, `height`, `samplerate`, `channelConfig`, `displayWidth`, `displayHeight`, `lang`
- common repeated track values to `commonTrackFields`
  - `namespace`, `packaging`, `renderGroup`
- unsupported generic catalog fields for WARP/MSF
  - `streamingFormat`
  - `streamingFormatVersion`
  - nested `catalogs`
- unsupported WARP/MSF fields in generic catalog format
  - `deltaUpdate`
  - `addTracks`
  - `removeTracks`
  - `cloneTracks`
  - `generatedAt`
  - `isComplete`
  - `role`
  - `targetLatency`
  - `eventType`
  - `maxGrpSapStartingType`
  - `maxObjSapStartingType`

Expected result:

- We have a precise inventory of which fields round-trip cleanly and which require extensions.

## Phase 3: Gap Analysis And Base Model Extension

Once adapters exist, identify the minimum set of changes needed for `Catalog` / `CatalogTrack` to support WARP/MSF as a real base instead of a lossy interchange model.

Likely required extensions to the generic base model:

- Root-level:
  - `generatedAt`
  - `deltaUpdate`
  - `isComplete`
  - `addTracks`
  - `removeTracks`
  - `cloneTracks`
- Track-level:
  - `role`
  - `targetLatency`
  - `eventType`
  - `type`
  - `trackDuration`
  - `isLive`
  - `parentName`
  - `maxGrpSapStartingType`
  - `maxObjSapStartingType`

Potential generic-model cleanups:

- move current media properties under `selectionParams` only
- keep compatibility accessors on WARP/MSF types during migration
- consider `initData` and `initTrack` support alignment with CMSF/MSF expectations

Key decision:

- If the catalog-format schema is intended to remain distinct from WARP/MSF, stop here and keep adapters permanently.
- If the intent is for `CatalogSerializer` to become the actual shared foundation for all catalog variants, then extend the base model to carry WARP/MSF semantics directly.

## Phase 4: Introduce A Shared Serializer Base Class

Once the base model can represent WARP/MSF without loss, add a shared abstract serializer layer.

Suggested additions:

- `src/main/java/org/red5/io/moq/catalog/AbstractCatalogSerializer.java`
- or evolve `CatalogSerializer` itself to support typed subclasses

Possible API shape:

```java
public abstract class AbstractCatalogSerializer<TCatalog, TTrack> {
    public String toJson(TCatalog catalog) { ... }
    public TCatalog fromJson(String json) throws IOException { ... }
}
```

Then:

- `CatalogSerializer` handles `Catalog`
- `WarpCatalogSerializer` extends the shared base and uses adapter conversion
- `MsfCatalogSerializer` extends the shared base and uses adapter conversion plus MSF validation

At this stage, `WarpCatalogSerializer` and `MsfCatalogSerializer` should stop owning raw Gson parsing logic.

## Phase 5: Migrate WARP Serializer To The Shared Base

Refactor `WarpCatalogSerializer` first, because it is the direct current base for MSF.

Target behavior:

- `WarpCatalogSerializer` delegates JSON parsing/rendering to the shared serializer infrastructure
- `WarpCatalogValidator` remains the authoritative WARP validation layer
- existing WARP JSON round-trip tests continue to pass

Recommended steps:

1. Replace direct `Gson` usage in `WarpCatalogSerializer`.
2. Deserialize via shared catalog model plus adapter back to `WarpCatalog`.
3. Add round-trip tests that compare WARP-specific fields before and after conversion.

Do not remove `WarpCatalog` or `WarpTrack` in this phase.

## Phase 6: Migrate MSF Serializer To The Shared Base

After WARP is stable, migrate `MsfCatalogSerializer`.

Target behavior:

- `MsfCatalogSerializer` should no longer wrap `WarpCatalogSerializer` as a JSON parser.
- It should use the shared catalog serializer infrastructure directly, then apply:
  - conversion to `MsfCatalog`
  - `MsfCatalogValidator`

Recommended steps:

1. Introduce an `MsfCatalogAdapter` if the WARP adapter is insufficient.
2. Move MSF/CMSF-specific field mapping into the adapter layer.
3. Keep `MsfCatalog`, `MsfTrack`, and validators as the user-facing API.

## Phase 7: Consolidate Builders And Helpers

Once serializers are shared, clean up the construction APIs so the codebase has one clear direction for new catalog work.

Tasks:

- keep `MsfCatalog.builder()` and `MsfTrack.builder()` as convenience APIs
- optionally add generic builder helpers on the base `Catalog` model
- document which layer callers should use:
  - generic catalog format API
  - WARP API
  - MSF/CMSF API

This avoids future duplication where new features land in WARP/MSF models but not in the shared catalog base.

## Phase 8: Compatibility And Deprecation

After both format-specific serializers sit on the shared base:

- deprecate direct Gson constructors/usages in `WarpCatalogSerializer`
- deprecate any duplicate helper methods that only exist because of the old serializer split
- consider deprecating or narrowing `CatalogSerializer.createSimpleCatalog()` and `createExampleCatalog()` if they encourage the old generic-only model instead of the shared foundation

Do not delete the old classes until:

- WARP tests pass
- MSF/CMSF tests pass
- no production code depends on the old serialization entry points behaving differently

## Testing Strategy

Add tests before and during migration.

Required test categories:

- adapter unit tests
  - `WarpCatalog <-> Catalog`
  - `MsfCatalog <-> Catalog`
- serializer compatibility tests
  - old WARP JSON fixtures still deserialize correctly
  - old MSF/CMSF JSON fixtures still deserialize correctly
- round-trip tests
  - WARP -> JSON -> WARP
  - MSF -> JSON -> MSF
  - CMSF-specific field preservation
- lossiness tests
  - fail loudly if unsupported fields would be dropped by conversion

Suggested files:

- `src/test/java/org/red5/io/moq/catalog/WarpCatalogAdapterTest.java`
- `src/test/java/org/red5/io/moq/catalog/MsfCatalogAdapterTest.java`
- `src/test/java/org/red5/io/moq/warp/WarpCatalogSerializerCompatibilityTest.java`
- `src/test/java/org/red5/io/moq/msf/MsfCatalogSerializerCompatibilityTest.java`

## Risks

- The generic catalog model currently matches catalog-format semantics, not WARP/MSF semantics. Treating it as the base class without extension will silently drop fields.
- `commonTrackFields` plus nested `selectionParams` is structurally different from the flat WARP/MSF track model, so careless migration can change emitted JSON.
- WARP delta updates have no direct equivalent in the current generic catalog model.
- MSF/CMSF-specific fields such as `targetLatency`, `eventType`, and SAP metadata are not represented in the current generic model.

## Recommended Implementation Order

1. Add `WarpCatalogAdapter` and `MsfCatalogAdapter` with explicit tests.
2. Document which fields are lossless vs lossy.
3. Extend `Catalog` / `CatalogTrack` only as needed to eliminate lossiness for WARP/MSF.
4. Refactor `WarpCatalogSerializer` onto the shared serializer base.
5. Refactor `MsfCatalogSerializer` onto the shared serializer base.
6. Deprecate duplicate serializer logic.

## Practical Recommendation

Do not start by making `WarpCatalog` extend `Catalog`.

Start with adapters and tests. If the shared model becomes complete enough after extension, inheritance may become reasonable. If not, keep the shared serializer infrastructure plus adapters as the stable architecture. That path is lower-risk and still achieves the actual goal: one primary catalog serialization foundation instead of multiple JSON stacks.
