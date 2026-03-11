# CMSF Support Plan

## Scope

Support `docs/draft-ietf-moq-cmsf-00.txt` as an extension on top of the existing MSF and CMAF code in this repository.

The draft is intentionally small in scope. The implementation work here should focus on:

- adding CMSF catalog signaling on top of MSF
- reusing the existing CMAF object model and serializers for CMSF object payloads
- adding validation and timeline helpers for CMSF-specific rules
- covering the new behavior with focused unit tests

## Draft Review Summary

The draft adds four concrete requirements beyond base MSF:

1. A new track packaging type, `cmaf`, for CMAF media tracks.
2. `initData` for CMAF tracks must contain the base64-encoded CMAF initialization header.
3. CMAF-specific track metadata fields:
   - `maxGrpSapStartingType`
   - `maxObjSapStartingType`
4. A CMSF-defined Event Timeline profile:
   - `packaging` must be `eventtimeline`
   - `eventType` must be `org.ietf.moq.cmsf.sap`
   - entries must be location-indexed (`"l"`)
   - `data` must be a two-integer array `[sapType, earliestPresentationTimeMs]`

The draft also defines CMAF object and group packaging constraints that should be enforced where this project exposes CMSF payload validation.

## Current Codebase Assessment

Relevant code already exists:

- `src/main/java/org/red5/io/moq/msf/catalog/PackagingType.java`
  - MSF packaging support exists, but `cmaf` is not yet included.
- `src/main/java/org/red5/io/moq/msf/catalog/MsfTrack.java`
  - MSF builders exist, but there is no CMSF-specific convenience builder and no builder methods for the SAP metadata fields.
- `src/main/java/org/red5/io/moq/msf/catalog/MsfCatalogValidator.java`
  - MSF validation exists, but there are no CMSF-specific catalog rules.
- `src/main/java/org/red5/io/moq/warp/catalog/WarpTrack.java`
  - The CMSF fields `maxGrpSapStartingType` and `maxObjSapStartingType` already exist at the model layer.
- `src/main/java/org/red5/io/moq/msf/timeline/MsfEventTimeline.java`
  - Generic event timeline serialization exists and can be reused for the CMSF SAP timeline profile.
- `src/main/java/org/red5/io/moq/cmaf/`
  - CMAF box models plus serializer/deserializer already exist and should be reused rather than duplicated.

## Gaps To Close

### Phase 1: Catalog and Builder Support

Add first-class CMSF catalog support in the MSF layer.

Tasks:

- Extend `PackagingType` with `CMAF("cmaf")`.
- Add `MsfTrack.cmafVideo(String)` and `MsfTrack.cmafAudio(String)` convenience builders.
- Add builder methods on `MsfTrack.Builder` for:
  - `maxGrpSapStartingType(int)`
  - `maxObjSapStartingType(int)`
- Add a small helper for setting base64-encoded init data from serialized CMAF initialization bytes if the API shape remains clean.

Acceptance criteria:

- CMSF tracks can be created without using raw string packaging values.
- CMSF-specific fields round-trip through JSON serialization.

### Phase 2: CMSF Catalog Validation

Enforce the draft’s catalog rules in `MsfCatalogValidator`.

Tasks:

- For `packaging="cmaf"` tracks:
  - require non-empty `initData`
  - validate optional SAP-starting-type values are within the supported range
  - keep existing MSF live/VOD rules intact
- For `eventtimeline` tracks with `eventType="org.ietf.moq.cmsf.sap"`:
  - require dependency linkage
  - require `mimeType="application/json"`
  - validate that the payload shape is handled by a CMSF-specific timeline helper

Recommended validation range:

- `maxGrpSapStartingType`: allow `1` or `2` if the field is present, because groups must start with SAP type 1 or 2
- `maxObjSapStartingType`: allow `0` to `3` if the field is present, matching the draft’s event timeline semantics

Open point:

- The draft defines these as “maximum” values but does not restate explicit numeric bounds in section 3.5.2. The implementation should document the chosen bounds and keep them easy to revise.

### Phase 3: CMSF SAP Timeline Helper

Create a typed wrapper around the generic MSF event timeline classes for the CMSF SAP timeline profile.

Suggested additions:

- `src/main/java/org/red5/io/moq/msf/timeline/CmsfSapTimelineEntry.java`
- `src/main/java/org/red5/io/moq/msf/timeline/CmsfSapTimeline.java`

Tasks:

- Model a location-indexed entry with:
  - `groupId`
  - `objectId`
  - `sapType`
  - `earliestPresentationTimeMs`
- Serialize to the exact CMSF JSON shape:
  - `{ "l": [groupId, objectId], "data": [sapType, earliestPresentationTimeMs] }`
- Validate:
  - index type is location-only
  - `sapType` is `0..3`
  - if `objectId == 0`, `sapType` must be `1` or `2`
  - presentation time is non-negative

Acceptance criteria:

- CMSF SAP timeline payloads can be created and parsed without manual JSON handling.
- Invalid CMSF SAP timeline payloads fail clearly.

### Phase 4: CMSF Payload Validation on Top of Existing CMAF Models

Add CMSF-oriented validation utilities that reuse `org.red5.io.moq.cmaf`.

Suggested addition:

- `src/main/java/org/red5/io/moq/cmaf/validate/CmsfPayloadValidator.java`

Tasks:

- Validate a CMAF initialization payload intended for catalog `initData`:
  - must deserialize as a CMAF header / initialization segment
  - should reject obvious media-fragment payloads in place of init data
- Validate a CMSF object payload:
  - must contain at least `moof` + `mdat`
  - must represent exactly one track
  - must be decode-order content
- Validate group-level assumptions where enough information is available:
  - first object in a group must start with SAP type 1 or 2
  - objects and fragments must not span groups

Notes:

- Some group-level rules depend on publisher-side sequencing context rather than a single object. Those checks should be exposed as sequence validators instead of forcing them into single-object validation.
- The current `CmafFragment` model optionally includes `styp`; the CMSF draft only requires `moof` followed by `mdat`, so validation should not require `styp`.

### Phase 5: Publisher-Facing Helpers

Only add this if the repository intends to package CMSF, not just parse and validate it.

Tasks:

- Add helper methods to convert `InitializationSegment` bytes to base64 `initData`.
- Add helper utilities to package aligned CMAF fragments into CMSF objects and track group/object numbering.
- Add SAP extraction helpers from CMAF fragments if needed by the CMSF SAP timeline writer.

This phase should stay small unless a concrete publisher integration is being added.

### Phase 6: Tests

Add focused unit tests before broader integration work.

Tests to add:

- catalog serialization/deserialization for `packaging="cmaf"`
- round-trip coverage for `maxGrpSapStartingType` and `maxObjSapStartingType`
- validator failures for missing `initData` on CMAF tracks
- validator failures for invalid SAP starting type values
- CMSF SAP timeline serialize/parse/validation tests
- CMAF init-data helper tests using `InitializationSegment`
- CMSF object validation tests using valid and invalid `CmafFragment` payloads

Likely files:

- `src/test/java/org/red5/io/moq/msf/CmsfCatalogTest.java`
- `src/test/java/org/red5/io/moq/msf/CmsfSapTimelineTest.java`
- `src/test/java/org/red5/io/moq/cmaf/CmsfPayloadValidatorTest.java`

## Recommended Implementation Order

1. Catalog enum and builder support.
2. CMSF catalog validation.
3. CMSF SAP timeline typed helper.
4. CMSF payload validation utilities.
5. Optional publisher-facing packaging helpers.

This order gives early value with low risk and keeps test coverage close to the feature work.

## Risks and Ambiguities

- The draft is `-00`, so field semantics may still move.
- The repository currently mixes WARP and MSF base types in several places; CMSF support should stay layered on MSF behavior and avoid introducing another parallel catalog stack.
- Some CMSF requirements are publisher workflow constraints, not simple static model validation rules. Those need sequence-aware helpers, not just POJO validation.
- The draft says the `moof` must contain a “Track Box (trak)”; for fragmented BMFF that likely intends the fragment track context carried by `traf`/`tfhd`, not a literal `trak` box inside `moof`. Implementation should follow the repository’s existing CMAF fragment model and note this spec wording ambiguity in code comments or docs.
