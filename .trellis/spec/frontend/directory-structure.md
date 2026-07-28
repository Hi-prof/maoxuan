# Android UI Directory Structure

## Layout

```text
app/src/main/java/com/xuhuangbin/xinghuozhaidu/
  MainActivity.kt             system bars and Compose root
  XinghuoApp.kt               navigation and screen composition
  ui/MainViewModel.kt         app-level immutable state and events
  ui/components/              reusable card/action/list primitives
  ui/reader/                  vertical reading flow and completion page
  ui/saved/                   favorites and liked summaries
  ui/search/                  local search UI
  ui/detail/                  full card detail
  ui/update/                  update confirmation/progress/error dialog
  ui/share/                   fixed-pixel bitmap renderer
  ui/theme/                   palette and bundled quote font
  domain/model/               UI-facing immutable models
```

## Ownership

- Feature screens compose state and callbacks but do not own persistence.
- `ui/components` contains a component only when two or more screens reuse it or it represents the core card system.
- `MainViewModel` translates user intents to repository calls and owns app/update state.
- `ui/share` is a renderer, not an Activity screenshot; its pixel contract is independent of screen size.
- Theme tokens and the quote font have one owner under `ui/theme`.

## Naming

- Screens end with `Screen`; reusable composables use a domain name such as `FlippableQuoteCard` or `CardActions`.
- UI state types end with `UiState`; finite workflows use an enum such as `UpdatePhase`.
- Event callbacks use `on<Action>` names and are passed down rather than reaching up through globals.

## Extension Rule

Add feature-specific code to its existing `ui/<feature>` package. Keep a single Android module until a real build or ownership boundary appears; do not create generic `utils` or wrapper layers for one call site.
