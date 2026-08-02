# Backend Directory Structure

## Overview

The project has no runtime server. Backend responsibilities are split between the Python authoring tool and the Android local data/update layer.

## Directory Layout

```text
content/
  project.yaml             release metadata
  cards/*.yaml             one source file per card
  images/*.yaml            image rights and file metadata
  images/source/*          distributable source images
content-tool/
  src/xinghuo_content/     validation, reporting, deterministic build
  tests/                   producer contract tests
app/src/main/java/.../
  data/content/            release DTOs, versioning, strict ZIP reader
  data/network/            explicit user-triggered manifest/package download
  data/update/             Android APK cache, permission, and installer boundary
  data/local/              Room entities, relations, DAO, database
  data/AppRepository.kt    import transaction and domain projection
  data/ReadingRoundPlanner.kt
```

## Module Ownership

- `validator.py` owns authoring validation and normalization.
- `builder.py` owns release JSON/ZIP bytes. No other code should hand-build release assets.
- `ContentPackageReader` owns decoding untrusted package bytes into validated DTOs.
- `AppRepository` owns file-to-database import ordering, withdrawal semantics, and domain projections.
- `AppUpdateClient` owns GitHub App Release parsing and verified streaming downloads; `AppUpdateManager` owns only Android cache and installer integration.
- DAO methods remain small persistence operations; cross-table behavior belongs in a Room transaction in the repository.
- UI code must not access DTOs, DAOs, or content files directly.

## Naming Conventions

- Python source uses `snake_case`; release JSON uses the existing `camelCase` contract.
- Android persistence types end in `Entity`; serialized types end in `Dto`; UI-facing types live under `domain.model`.
- Stable content IDs are data, not filenames. YAML filenames should be descriptive but may change without changing an ID.

## Extension Rule

Add a field in this order: YAML validator/model -> deterministic payload -> Android DTO/parser validation -> Room/domain mapping if persisted -> UI -> producer and consumer tests. Do not introduce a second parser or duplicate release serializer.
