# Changelog

All notable changes to this project will be documented in this file.

## [3.1.1] - 2023-05-23

### Bug Fixes

- Update Findex 2.0.3 to 2.0.4 to fix the allocation size of the output buffer used in `fetchEntries` callback

### Testing

- Add test with multi entry tables

## [3.1.0] - 2023-05-16

### Features

- Update Findex 2.0.2 -> 2.0.3 to support fetching multiple entry table values

## [3.0.4] - 2023-04-25

### Features

- Add exception handling for Findex callbacks

### Ci

- Test on docker too

## [3.0.3] - 2023-01-16

### Deleted

- All native libraries are downloadable from <https://package.cosmian.com>

## [3.0.2] - 2023-01-13

### Added

- Add `insecureFetchChainsBatchSize` argument to `Findex.search` to reduce the number of `fetchChains` calls during searches
- Add an overload to `Findex.search` with default parameters for `maxResultsPerKeyword` and `maxDepth`

---

## [3.0.1] - 2023-01-08

### Added

- regression test vectors for Findex

---

## [3.0.0] - 2022-12-21

### Added

- support for access policy as boolean expressions to create user decryption keys
- regression test vectors for CoverCrypt

### Changed

- changed `Abe` class to `KmipClient`
- refactored packages to more natural locations
- updated CoverCrypt to 8.0.2
- updated Findex to 1.0.1

### Removed

- support for GPSW

---

## [2.0.0] - 2022-11-15

### Added

### Changed

- updated CoverCrypt to 7 and Findex to 0.10

---

## [1.11.2] - 2022-10-16

### Fixed

- renamed revoke attributes, rotate attributes and fixed documentation

---

## [1.11.1] - 2022-10-14

### Changed

- Update license
- CI: use KMS version from Gitlab variable

---

## [1.11.0] - 2022-10-13

### Added

- Native libraries for Windows and MacOs
- Add Destroy method on Abe class

### Changed

- Update KMS version to 2.3
- Add ABE Demo class to continuous integration

### Fixed

- Make Link[] optional for KMIP Attributes

---

## [0.11.1]

### Changed

- Rename `cosmian_java_lib` to `cloudproof_java`

---

## [0.11.0]

### Changed

- Findex: change search signature for graph compliance

---

## [0.10.1]

### Added

- Add Findex callbacks implementations for indexes compaction usage

### Changed

- Test Findex with Redis callbacks

### Fixed

- FFI: fetch callbacks: send correct allocation size in case of insufficient allocated size in order to retry in Rust code

---

## [0.10.0]

### Added

- Add `compact` function to FFI to compact indexes

### Changed

- Add `label` to `search` and `update` functions

---

## [0.9.0] - 2022-09-15

### Added

- Update `findex` shared library to 0.5.0

---

## [0.8.2] - 2022-09-14

### Fixed

- Findex->search: retry FFI call with adjusted allocated size

### Removed

---

## [0.8.1] - 2022-09-08

### Added

- Publish to Maven Central on tags creation

---

## [0.8.0] - 2022-09-02

### Changed

- Rename Findex native shared library to `cosmian_findex`
- Update `abe_gpsw` shared library to 2.0.1
- Update `cover_crypt` shared library to 6.0.1
- Update Policy according to new Format
  - `maxAttributeValue` becomes `maxAttributesCreations`
  - `store` becomes `axes`
- Update to `kms` 2.2.0

---

## [0.7.8] - 2022-08-22

### Fixed

- Findex Master keys deserialization (K\* deduplicated)

---

## [0.7.7] - 2022-07-28

### Fixed

- KMIP KeyValue structure has changed in KMS from version 2.0.5

---
