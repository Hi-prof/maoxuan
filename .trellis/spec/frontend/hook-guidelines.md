# Compose Effects And Lifecycle

## Overview

This is a Compose app, so React-style hooks do not apply. Use `remember`, `LaunchedEffect`, lifecycle-aware state collection, and ViewModel coroutines only for clearly scoped effects.

## Effect Rules

- Key an effect with every value that changes its logical identity.
- Keep Room/network/file work in the repository and launch it through `viewModelScope`.
- Use local `remember` only for disposable UI state such as flip/source expansion; durable state belongs in Room or the ViewModel.
- Cancel jobs when the identity or lifecycle condition changes; never leave a timer or download detached from its owner.
- Do not start network work from composition, app startup, tab selection, or background callbacks.

## Three-Second Read Timer

The timer runs only while all conditions remain true:

```text
page is settled
AND reader tab is visible
AND app lifecycle is at least STARTED
AND a normal card (not completion page) is current
```

The effect is keyed by card ID and visibility conditions. Flipping the same card
or opening its interpretation sheet does not reset time; interpretation-sheet
state must not be an effect key. Paging, opening detail, leaving the tab, or
entering background cancels the current continuous interval. After three
seconds, `markRead` is idempotent in Room.

## Focus And Keyboard

- Search may request focus once on initial entry.
- Clearing the query updates state but must not repeatedly request focus or reopen the keyboard.
- Focus requests belong in a one-shot effect, not in every recomposition.

## Common Mistakes

- Using `GlobalScope` or an unowned coroutine.
- Keying the read timer only by index when card identity can change after sync.
- Restarting the timer on front/back flip.
- Restarting or cancelling the timer when a modal interpretation sheet opens.
- Triggering update checks in `init` or `LaunchedEffect(Unit)`.
