# Backend Development Guidelines

> Contracts for the local data layer, the Python content tool, and static release assets.

## Overview

The MVP has no runtime API server. Its backend boundary is the deterministic content pipeline:

```text
content/*.yaml -> content-tool -> versioned ZIP + manifest -> Android parser -> Room
```

Personal reading state never leaves the device.

## Guidelines Index

| Guide | Description | Status |
| --- | --- | --- |
| [Directory Structure](./directory-structure.md) | Ownership of authoring, packaging, network, and persistence code | Active |
| [Content Package Contract](./content-package-contract.md) | Executable YAML, ZIP, manifest, revision, and import contract | Active |
| [App Update Contract](./app-update-contract.md) | GitHub App Release discovery, verified APK download, and installer contract | Active |
| [Database Guidelines](./database-guidelines.md) | Room tables, transactions, withdrawals, and asset lifecycle | Active |
| [Error Handling](./error-handling.md) | CLI exit codes and Android update failure behavior | Active |
| [Quality Guidelines](./quality-guidelines.md) | Required content and Android checks | Active |
| [Logging Guidelines](./logging-guidelines.md) | Privacy-preserving diagnostic policy | Active |

## Pre-Development Checklist

- Read `content-package-contract.md` before changing YAML fields, JSON DTOs, ZIP contents, or update behavior.
- Read `database-guidelines.md` before changing Room entities, DAO queries, import ordering, or reading-round state.
- Read `error-handling.md` before adding an update phase or a content-tool command.
- Read `app-update-contract.md` before changing Android versions, App Release assets, APK download, install permissions, or App update UI state.
- Keep the Python producer and Android consumer strict and compatible in the same change.
- Preserve stable card UUIDs, monotonic revisions, explicit withdrawals, and local user state.

## Quality Check

- Run the commands in `quality-guidelines.md`.
- Confirm formal validation reports the exact `expectedPublishedCards` count
  declared by the project (600 for content `1.6.0`) and all referenced images.
- Confirm two deterministic builds produce identical ZIP and manifest bytes.
- Run parser, repository, and instrumentation tests when the package or database contract changes.
- Verify a failed or cancelled update leaves the prior `content_state` and readable cards intact.

**Language**: Code-spec documentation is written in English.
