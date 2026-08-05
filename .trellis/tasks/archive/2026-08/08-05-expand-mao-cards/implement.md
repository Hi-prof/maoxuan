# Expand Mao Cards Implementation Plan

> **For agentic workers:** Follow this plan task-by-task. Read `prd.md`, `design.md`, registered Trellis context, and `research/*.md` before editing. Track progress with the checkboxes below.

**Goal:** Ship a strict schema-4 content pipeline and Android consumer containing 100 additional Mao Zedong Selected Works cards.

**Architecture:** Python remains the authoritative producer and Android remains an independent strict consumer. Schema 4 changes only the source-count invariant; the new app/code and content/minimum-code pair prevents schema-3 consumers from downloading schema-4 snapshots. The existing Room representation already supports one source and needs no migration.

**Tech Stack:** Python 3.11+, PyYAML, pytest, Ruff, Kotlin, kotlinx.serialization, JUnit, Gradle, Android Room.

## Global Constraints

- App version is `1.10.0`, version code is `13`.
- Content version is `1.6.0`, schema is `4`, and `minimumAppVersionCode` is `13`.
- Exactly 700 cards are published: 220 Mao Selected Works, 30 Mao poems, 300 popular quotations, and 150 Marxist-principles cards.
- New volume counts are 23 / 18 / 19 / 40 for Volumes 1 / 2 / 3 / 4.
- Every new quote is continuous source text, unique, NFC, one paragraph, and at most 90 code points.
- Every new card has exactly one `original` or `authoritative` HTTP(S) source.
- Existing UUIDs, revisions, source lists, images, Room data, and user state remain unchanged.

---

### Task 1: Upgrade The Producer Contract To Schema 4

**Files:**
- Modify: `content-tool/src/xinghuo_content/validator.py`
- Modify: `content-tool/tests/test_content_tool.py`
- Modify: `content/templates/card.yaml`

**Interfaces:**
- Consumes: `validate_content(root: Path, *, formal: bool = False) -> ValidatedContent`
- Produces: schema-4 `ValidatedContent` where published cards require one or more sources and at least one strong evidence type.

- [ ] Replace the fixture's project schema with 4 and add a test that deletes the second source while retaining one `original` source; `validate_content` must succeed.
- [ ] Add failure tests for an empty `sources` list and a one-item `contextual` list; assert field-specific validation messages.
- [ ] Run `python -m pytest content-tool/tests/test_content_tool.py -q` and confirm the new schema/source tests fail under the current implementation.
- [ ] Change the project schema requirement and returned `ContentProject.schema_version` from 3 to 4. Replace the two-source and two-host checks with:

```python
if status == "published" and not sources:
    issues.append(f"{path}: published cards require at least one source")
if status == "published" and not any(
    source["type"] in {"original", "authoritative"} for source in sources
):
    issues.append(f"{path}: at least one source must be original or authoritative")
```

- [ ] Keep duplicate-URL validation for multi-source cards and remove only the distinct-host requirement and now-unused host helper/import logic.
- [ ] Reduce `content/templates/card.yaml` to one example authoritative/original source.
- [ ] Run the focused pytest file and confirm schema 4 is emitted into all four JSON envelopes.

### Task 2: Upgrade The Android Consumer And App Version

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentPackageReader.kt`
- Modify: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentPackageReaderTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: schema-4 ZIP bytes from the Python builder.
- Produces: `ParsedContentPackage` for schema 4 with at least one valid strong source per card.

- [ ] Change test fixtures to schema 4 and one original source; add explicit tests that an empty source list fails and a schema-3 envelope fails.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests "*ContentPackageReaderTest" --no-daemon` and confirm the new tests fail.
- [ ] Set `SUPPORTED_SCHEMA = 4`; replace `card.sources.size < 2` with `card.sources.isEmpty()` and report a missing-source error while keeping per-source URL/date/type and strong-source checks.
- [ ] Set `versionName = "1.10.0"` and `versionCode = 13`.
- [ ] Run the focused JVM tests and confirm schema 4 with one source succeeds while schema 3 and zero sources fail.

### Task 3: Author And Audit 100 Mao Selected Works Cards

**Files:**
- Create: `content/cards/601-*.yaml` through `content/cards/700-*.yaml`
- Read: `.trellis/tasks/08-05-expand-mao-cards/research/volumes-1-2-candidates.md`
- Read: `.trellis/tasks/08-05-expand-mao-cards/research/volumes-3-4-candidates.md`

**Interfaces:**
- Consumes: verified candidate quote, work metadata, source URL, theme and context notes.
- Produces: 100 schema-4 authoring records accepted by `validate_content`.

- [ ] Select exactly 23 / 18 / 19 / 40 verified candidates from Volumes 1 / 2 / 3 / 4, preferring uncovered and low-coverage works; reject any candidate whose source page cannot confirm the exact continuous quote.
- [ ] Before authoring, compare normalized candidate quotes with every existing published quote and reject exact duplicates.
- [ ] Create sequentially numbered files 601 through 700 with fresh random UUIDs, `revision: 1`, `status: published`, accurate literature fields, one or more focused themes, and balanced reuse of the eight existing image IDs.
- [ ] Write unique inspiration, explanation, historical event, background and story fields per the design quality rules; set exactly one source of type `original` or `authoritative` and `review.status: verified` with `checkedAt: '2026-08-05'`.
- [ ] Run `python -m xinghuo_content validate content` during authoring and fix every field, length, UUID, duplicate, source and image error.
- [ ] Generate a temporary report and verify exactly 220 `毛泽东选集` cards, 55 cards in each volume, 700 total published cards after metadata is updated, no work above seven total cards, and balanced image use.

### Task 4: Version The Snapshot And Synchronize Bundled Content

**Files:**
- Modify: `content/project.yaml`
- Modify: `app/src/main/assets/bootstrap.zip`
- Modify: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentPackageReaderTest.kt`
- Modify: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/InterestTaxonomyTest.kt`
- Modify: `README.md`
- Modify: `资料/README.md`

**Interfaces:**
- Consumes: validated 700-card schema-4 authoring tree.
- Produces: deterministic `content-v1.6.0` snapshot and matching APK bootstrap.

- [ ] Set `content/project.yaml` to schema 4, content `1.6.0`, publication time `2026-08-05T00:00:00Z`, minimum app code 13, expected count 700 and accurate release notes.
- [ ] Update bundled-content and recommendation tests from `1.5.0` / 600 to `1.6.0` / 700 where those assertions describe current bundled content; do not alter historical migration fixtures.
- [ ] Update README current source/App versions, totals, series counts, single-source authoring rule and required release order. Keep the most recently published content release documented as `content-v1.5.0` until a human publishes a new release.
- [ ] Update the material-library README to say formal schema-4 cards require at least one strong source.
- [ ] Run:

```powershell
python -m xinghuo_content build content `
  --output dist `
  --bootstrap-output app/src/main/assets/bootstrap.zip `
  --formal `
  --verify-deterministic
```

- [ ] Parse the generated bootstrap in the Android JVM test and assert `1.6.0`, schema 4 and 700 cards.

### Task 5: Update Executable Documentation And Run The Full Quality Gate

**Files:**
- Modify: `.trellis/spec/backend/content-package-contract.md`
- Modify: `.trellis/spec/backend/quality-guidelines.md`
- Modify: `.trellis/spec/backend/index.md`
- Modify if its schema wording is affected: `.trellis/spec/backend/database-guidelines.md`

**Interfaces:**
- Consumes: final producer, consumer and content behavior.
- Produces: repository specs that state the same schema, source, version and count contracts as code.

- [ ] Update all schema-3/two-source/current-count wording to schema 4, one strong source, content 1.6.0 and 700 cards; preserve historical migration facts that still describe old fixtures.
- [ ] Search the repository for stale current-contract phrases: `schema 3`, `schema-3`, `two sources`, `two hosts`, `双源`, `600`, and `1.5.0`; classify every remaining match as historical fixture or defect.
- [ ] Run the Python and formal-content gate:

```powershell
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content report content --output content-report.json --formal
python -m xinghuo_content build content --output dist --formal --verify-deterministic
```

- [ ] Run the Android gate:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon` when API 28/latest emulators are available; otherwise record the unrun instrumentation gate explicitly.
- [ ] Review `git diff --check`, the generated report, all 100 new source URLs and the final volume/work/image distribution. Do not commit `dist/`, `content-report.json`, APKs or caches.

## Rollback Points

- After Task 2, producer/consumer contract tests establish the schema boundary before bulk content is added.
- Before replacing bootstrap, keep the source tree formally valid; a failed build must not leave a partially generated asset staged.
- The entire release is additive and can be reverted as one worktree change before publishing. After a real content release, rollback requires a higher content patch version built from the last trusted snapshot.
