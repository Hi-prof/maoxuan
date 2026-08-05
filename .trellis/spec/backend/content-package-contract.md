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
    val inspiration: String,
    val explanation: String,
)

ContentPackageReader.read(packageBytes: ByteArray): ParsedContentPackage

AppRepository.importPackage(
    bytes: ByteArray,
    expectedSha256: String? = null,
    expectedContentVersion: String? = null,
    expectedPublishedAt: String? = null,
    requireNewerVersion: Boolean = false,
)

data class ImageAttribution(
    val creator: String,
    val sourceUrl: String,
    val licenseName: String,
    val licenseEvidence: String,
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

The four JSON envelopes inside the ZIP use `schemaVersion: 3`. The remote
`manifest.json` remains `schemaVersion: 1` so an older client can reject the
package by `minimumAppVersionCode` before downloading it. Its fields are:

```text
schemaVersion, contentVersion, publishedAt, minimumAppVersionCode,
packageUrl, packageBytes, packageSha256,
changes.added, changes.updated, changes.withdrawn, releaseNotes
```

Required invariants:

- `contentVersion` is `MAJOR.MINOR.PATCH`; `publishedAt` is UTC ISO-8601.
- `literature.authoredAt` accepts `YYYY`, `YYYY-MM`, `YYYY-MM-DD`, `前N`,
  and `前N世纪` so modern works and ancient thinkers can share the same string
  field without inventing inaccurate positive years.
- `expectedPublishedCards` is a positive integer and, in formal mode, exactly matches the number of published card YAML files.
- A card ID is a stable UUID that is never reassigned. `revision` is a positive integer.
- A published quote is NFC, one paragraph, and at most 90 Unicode code points.
- Published quotes are unique after NFC normalization; an exact duplicate reports
  every source YAML file and blocks validation.
- Every published card contains `interpretation.inspiration` and
  `interpretation.explanation`. Both values are trimmed, non-empty NFC text;
  `inspiration` has a 220-code-point hard limit, `explanation` has a
  420-code-point hard limit, and their combined hard limit is 600 code points.
- Every published card contains a one-paragraph `historicalEvent` of at most
  100 code points plus non-empty NFC `background` and `story` values.
- `contextExcerpt`, `interpretation.coreMeaning`, `interpretation.keyPoint`,
  and `interpretation.contemporaryRelevance` are not part of schema 3 and are
  rejected as unknown fields by both producer and consumer.
- Each published card has at least two distinct HTTP(S) source URLs from two hosts and at least one `original` or `authoritative` source.
- Images are content-hashed, 720 to 8192 pixels per edge, at most 40 million pixels, and explicitly permit share-image redistribution.
- The repository maps each referenced `ImageAssetEntity` to both `QuoteCard.imagePath` and `QuoteCard.imageAttribution`. Missing image rows produce an empty path and null attribution; Compose never reads rights fields from DTOs or Room entities directly.
- An attributed card exposes creator/source/license evidence in the background sheet, includes `图片：<creator> · <license>` in the generated share image, and includes both source and license-evidence URLs in `Intent.EXTRA_TEXT`.
- A published ID and a withdrawal ID cannot coexist in one package.
- Removing a previously active card requires an explicit withdrawal. Snapshot omission alone is invalid.
- Restoring a withdrawn ID requires a revision greater than the recorded withdrawal revision.
- Image IDs are immutable: an existing ID cannot point to different bytes.
- Content `1.5.0` and later schema-3 packages require Android `versionCode >= 7` because ancient-thinker
  cards use `authoredAt` values such as `前4世纪`. App `1.6.0` reads package schema 3 only;
  older package schemas are not silently accepted as partial data.

The builder sorts payloads, serializes compact sorted-key JSON with a trailing
newline, fixes ZIP timestamps to `1980-01-01`, and writes regular-file
permissions. Repeated builds in the same Python/zlib environment must be
byte-identical. Different operating-system zlib builds may produce different
compressed ZIP bytes while every uncompressed entry and its metadata remain
identical; the published manifest's `packageBytes` and `packageSha256` are the
authoritative download contract.

## 4. Validation & Error Matrix

| Condition | Producer behavior | Android behavior |
| --- | --- | --- |
| Unknown YAML/JSON field | Validation error | Strict serialization error |
| Unsupported schema | Validation error | `ContentPackageException` |
| Published count differs from `expectedPublishedCards` | Formal validation error | N/A; producer must not emit the package |
| Quote over 90 code points | Validation error | `ContentPackageException` |
| Missing interpretation object/child or blank child | Field-specific validation error | Strict serialization or `ContentPackageException` |
| Interpretation is non-NFC or exceeds 600 code points | Validation error | `ContentPackageException` |
| Inspiration exceeds 220 or explanation exceeds 420 code points | Validation error | `ContentPackageException` |
| Missing/blank historical event, background, or story | Field-specific validation error | Strict serialization or `ContentPackageException` |
| Removed `contextExcerpt` or old interpretation child appears | Unknown-field validation error | Strict serialization error |
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
- Base: rebuild unchanged `1.4.0`; ZIP SHA-256 and manifest bytes remain identical.
- Good: revise a card, provide both interpretation fields and all required
  background fields; App `1.3.0` imports it.
- Bad: omit `inspiration`, leave a required background field blank, retain
  `contextExcerpt`, or exceed any field/combined limit; both producer and
  Android consumer reject the package.
- Good: withdraw revision 2 of a favorited card; it leaves reading/search but its last trusted snapshot remains in Favorites.
- Good: restore the same UUID at revision 3; current content replaces the snapshot while like/favorite state remains.
- Bad: remove an active card without `withdrawals.json`; Android rejects the full package.
- Bad: edit quote text without increasing revision; Android rejects the package.
- Bad: reuse an image ID for different bytes; Android rejects the package.

## 6. Tests Required

- Python: schema, UUID/revision, source independence, quote and interpretation
  length/NFC, required interpretation/background children, removed-field
  rejection, image bounds/license, and exact declared formal-count validation,
  including both missing and surplus cards. Exact duplicate published quotes are
  rejected with both source filenames.
- Python report: summarize published cards by `workTitle` so editorial checks can
  enforce per-work limits before building a release.
- Python: build twice and assert identical package SHA-256 and identical manifest bytes.
- JVM: valid schema-3 package parse plus blank/oversized interpretation and
  historical event, missing background fields, removed-field rejection,
  traversal, duplicate entry, unknown schema, bad hash, invalid revision, and
  semantic-version cases.
- Instrumentation: fresh schema-5 bootstrap, update/withdraw/restore transaction,
  and normal same-schema preservation of like/favorite/search/round state. The
  one-user `4 -> 5` release intentionally rebuilds the database and does not
  require a data-preserving migration regression.
- Attribution instrumentation: import an image with all rights fields, assert the exact `QuoteCard.imageAttribution` projection, verify both background-sheet links and accessibility descriptions, and verify share-image credit plus complete share text URLs.
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
    inspiration = entity.interpretationInspiration,
    explanation = entity.interpretationExplanation,
)
```

Wrong: let UI or sharing code recover attribution by parsing YAML, DTOs, or a local filename.

```kotlin
val creator = File(card.imagePath).name.substringBefore('-')
```

Correct: project the persisted rights fields once at the repository boundary.

```kotlin
val imageAttribution = image?.let {
    ImageAttribution(it.creator, it.sourceUrl, it.licenseName, it.licenseEvidence)
}
```
