# Content Package Contract

## 1. Scope / Trigger

This contract applies whenever a change touches `content/`, `content-tool/`, Android content DTOs, `ContentPackageReader`, `ContentUpdateClient`, or `AppRepository.importPackage`.

The release is a complete snapshot, not a delta. The Python tool is the producer and Android is an independent strict consumer. Both sides must reject malformed data instead of silently normalizing incompatible input.

## 2. Signatures

Python entry points:

```python
validate_content(root: Path, *, formal: bool = False) -> ValidatedContent

build_package(
    content_root: Path,
    output_dir: Path,
    *,
    formal: bool = False,
    bootstrap_output: Path | None = None,
    verify_deterministic: bool = False,
) -> dict[str, Any]
```

CLI surface:

```text
python -m xinghuo_content validate <content> [--formal]
python -m xinghuo_content build <content> --output <dir> [--bootstrap-output <zip>] [--formal] [--verify-deterministic]
python -m xinghuo_content report <content> --output <file> [--formal]
```

Android boundary:

```kotlin
data class InterpretationDto(
    val coreMeaning: String,
    val keyPoint: String,
    val contemporaryRelevance: String,
)

ContentPackageReader.read(packageBytes: ByteArray): ParsedContentPackage

AppRepository.importPackage(
    bytes: ByteArray,
    expectedSha256: String? = null,
    expectedContentVersion: String? = null,
    expectedPublishedAt: String? = null,
    requireNewerVersion: Boolean = false,
)
```

## 3. Contracts

Authoring uses `content/project.yaml`, one UTF-8 YAML file per card under `content/cards/`, and one metadata YAML per image under `content/images/`. Project metadata includes a positive integer `expectedPublishedCards`; formal validation requires the actual published-card count to match it exactly. This authoring-only field is not emitted into the release ZIP.

A ZIP contains exactly these JSON files plus declared assets:

```text
package.json
cards.json
images.json
withdrawals.json
assets/<sha256>.<jpg|jpeg|png|webp>
```

The four JSON envelopes inside the ZIP use `schemaVersion: 2`. The remote
`manifest.json` remains `schemaVersion: 1` so an older client can reject the
package by `minimumAppVersionCode` before downloading it. Its fields are:

```text
schemaVersion, contentVersion, publishedAt, minimumAppVersionCode,
packageUrl, packageBytes, packageSha256,
changes.added, changes.updated, changes.withdrawn, releaseNotes
```

Required invariants:

- `contentVersion` is `MAJOR.MINOR.PATCH`; `publishedAt` is UTC ISO-8601.
- `expectedPublishedCards` is a positive integer and, in formal mode, exactly matches the number of published card YAML files.
- A card ID is a stable UUID that is never reassigned. `revision` is a positive integer.
- A published quote is NFC, one paragraph, and at most 90 Unicode code points.
- Every published card contains `interpretation.coreMeaning`,
  `interpretation.keyPoint`, and `interpretation.contemporaryRelevance`.
  Each value is trimmed, non-empty NFC text; their combined hard limit is 600
  Unicode code points, with 200 to 300 code points as the authoring target.
- Each published card has at least two distinct HTTP(S) source URLs from two hosts and at least one `original` or `authoritative` source.
- Images are content-hashed, 720 to 8192 pixels per edge, at most 40 million pixels, and explicitly permit share-image redistribution.
- A published ID and a withdrawal ID cannot coexist in one package.
- Removing a previously active card requires an explicit withdrawal. Snapshot omission alone is invalid.
- Restoring a withdrawn ID requires a revision greater than the recorded withdrawal revision.
- Image IDs are immutable: an existing ID cannot point to different bytes.
- Content `1.1.0` requires Android `versionCode >= 2`. App `1.1.0` reads package
  schema 2 only; package schema 1 is not silently accepted as partial data.

The builder sorts payloads, serializes compact sorted-key JSON with a trailing newline, fixes ZIP timestamps to `1980-01-01`, and writes regular-file permissions. The same sources must therefore produce byte-identical assets.

## 4. Validation & Error Matrix

| Condition | Producer behavior | Android behavior |
| --- | --- | --- |
| Unknown YAML/JSON field | Validation error | Strict serialization error |
| Unsupported schema | Validation error | `ContentPackageException` |
| Published count differs from `expectedPublishedCards` | Formal validation error | N/A; producer must not emit the package |
| Quote over 90 code points | Validation error | `ContentPackageException` |
| Missing interpretation object/child or blank child | Field-specific validation error | Strict serialization or `ContentPackageException` |
| Interpretation is non-NFC or exceeds 600 code points | Validation error | `ContentPackageException` |
| Missing/damaged asset or wrong hash | Validation/build error | Reject before Room transaction |
| ZIP traversal, duplicate, undeclared, or oversized entry | Not emitted | Reject during package read |
| Manifest hash/version/date mismatch | N/A | Reject before import |
| Version downgrade or same version when newer is required | N/A | Reject before writes |
| Missing explicit withdrawal | N/A | Reject inside import transaction |
| Same revision with changed content | N/A | Reject inside import transaction |
| Download cancellation | N/A | Reset UI update state; keep installed content |
| Python source validation failure | CLI exit code `2` | N/A |
| Deterministic build failure | CLI exit code `3` | N/A |

Assets are decoded and written to a content-addressed internal directory before the Room transaction. Database references switch only after all checks pass. A failed transaction may leave an unreferenced file, which startup cleanup removes; it must never leave Room pointing at a missing file.

## 5. Good / Base / Bad Cases

- Good: publish a new UUID at revision 1 with two sources and a licensed image; it joins the current unread tail after import.
- Base: rebuild unchanged `1.2.0`; ZIP SHA-256 and manifest bytes remain identical.
- Good: revise a card from revision 1 to 2 and add all three interpretation
  fields; App `1.1.0` imports it and preserves local user/round state.
- Bad: add only `coreMeaning`, leave a child blank, or exceed 600 combined code
  points; both producer and Android consumer reject the package.
- Good: withdraw revision 2 of a favorited card; it leaves reading/search but its last trusted snapshot remains in Favorites.
- Good: restore the same UUID at revision 3; current content replaces the snapshot while like/favorite state remains.
- Bad: remove an active card without `withdrawals.json`; Android rejects the full package.
- Bad: edit quote text without increasing revision; Android rejects the package.
- Bad: reuse an image ID for different bytes; Android rejects the package.

## 6. Tests Required

- Python: schema, UUID/revision, source independence, quote and interpretation
  length/NFC, required interpretation children, image bounds/license, and
  exact declared formal-count validation, including both missing and surplus cards.
- Python: build twice and assert identical package SHA-256 and identical manifest bytes.
- JVM: valid schema-2 package parse plus blank/oversized interpretation,
  traversal, duplicate entry, unknown schema, bad hash, invalid revision, and
  semantic-version cases.
- Instrumentation: bootstrap upgrade, update/withdraw/restore transaction,
  Room `2 -> 3`, and preservation of like/favorite/search/round state.
- Cross-layer: build a package with the Python tool and parse/import that exact artifact on Android.
- Release workflow: `content-vX.Y.Z` must equal `project.yaml` version; publish only after all assets upload.

## 7. Wrong vs Correct

Wrong: treat a snapshot omission as deletion or update content without a revision change.

```kotlin
// Wrong: absence is ambiguous and can erase cached/user-visible state.
deleteEveryCardNotIn(newIds)
```

Correct: require an explicit tombstone and enforce monotonic revisions inside the same transaction that applies the snapshot.

```kotlin
val silentlyMissing = existingActive - newIds - withdrawalIds
require(silentlyMissing.isEmpty())
require(previous == null || incoming.revision >= previous.revision)
```

Wrong: let screens infer or parse interpretation from optional background text.

```kotlin
val interpretation = card.background.orEmpty()
```

Correct: validate the structured payload once, persist explicit columns, and
expose the immutable domain value to Compose.

```kotlin
val interpretation = CardInterpretation(
    coreMeaning = entity.interpretationCoreMeaning,
    keyPoint = entity.interpretationKeyPoint,
    contemporaryRelevance = entity.interpretationContemporaryRelevance,
)
```
