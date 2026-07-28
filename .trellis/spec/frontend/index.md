# Android Frontend Guidelines

> Executable conventions for the Jetpack Compose UI in the single-module Android app.

## Overview

The UI is offline-first and renders immutable domain state from `MainViewModel`. Composables send explicit callbacks; they do not access Room, files, or network clients.

## Guidelines Index

| Guide | Description | Status |
| --- | --- | --- |
| [Directory Structure](./directory-structure.md) | Feature packages and ownership boundaries | Active |
| [Component Guidelines](./component-guidelines.md) | Compose component, card layout, accessibility, and styling rules | Active |
| [Effects and Lifecycle](./hook-guidelines.md) | Compose effects, read timing, focus, and lifecycle work | Active |
| [State Management](./state-management.md) | ViewModel, Flow, local state, and update phase model | Active |
| [Quality Guidelines](./quality-guidelines.md) | Visual, instrumentation, API-level, and build checks | Active |
| [Type Safety](./type-safety.md) | DTO/entity/domain/UI boundaries and strict state types | Active |

## Pre-Development Checklist

- Read component and state guidelines before changing a screen or interaction.
- Keep card actions outside the card and preserve vertical pager/back-scroll behavior.
- Treat `360 x 640 dp` as the minimum visual viewport and 90 code points as the maximum quote.
- Preserve the fixed light theme and explicitly light system-bar styles under system dark mode.
- Use the bundled Noto Serif SC family for quote text and licensed Material icons for familiar actions.
- Do not initiate content networking except from the user's explicit update action.

## Quality Check

- Run Debug JVM tests, Android Lint, and Debug assembly.
- Run all instrumentation tests on API 28 and the latest configured API.
- Inspect short, medium, and longest cards at `360 x 640`, `360 x 800`, and `412 x 915`.
- Verify quote, work title, and source do not overlap or clip; verify the full back can scroll without pager movement.
- Verify system light/dark settings both show the same app palette with visible status/navigation icons.
- Verify generated shares are exactly `1080 x 1440`, nonblank, and contain no app controls.

**Language**: Code-spec documentation is written in English.
