# Local Recommendation Research

## Scope

Research the minimum recommendation architecture that can personalize the
existing offline reading stream without accounts, remote behavior collection,
or a second feed.

## Repository Evidence

- The app is a single-module Android app using Compose, Room, Kotlin Flow, and a
  repository/ViewModel boundary.
- All 600 published cards already expose `series` and `themes` through YAML,
  package DTOs, Room, domain models, and Compose.
- Published content contains four series: 120 `毛泽东选集`, 30 `毛泽东诗词`,
  300 `名人名言`, and 150 `马原思考` cards.
- There are 137 raw themes. Distribution is highly skewed: `成长` appears on
  197 cards, while many themes appear once. Raw themes are unsuitable as direct
  onboarding choices.
- Explicit positive state already includes likes, favorites, and card-linked
  notes. A three-second settled-page timer marks a card read, but a read is not
  evidence of preference.
- A reading round persists every card's exact position, `readAt`, and current
  position. Backtracking changes current position, so current position alone
  cannot identify the furthest card the user has already seen.
- The action row already contains one text action plus four 48 dp icon actions
  and has a tested 320 dp layout limit. `减少此类` should not be added as a
  fifth action-row icon.
- Room schema version 5 intentionally used destructive fallback from schema 4.
  This feature must add a data-preserving migration from the current schema and
  preserve likes, favorites, notes, searches, rounds, and read timestamps.
- Concurrent app-update work currently modifies `XinghuoApp.kt`,
  `MainViewModel.kt`, `SavedScreens.kt`, related specs, and tests. These are
  integration hotspots; implementation must append to their latest state.

## Confirmed Product Decisions

- Upgrade the existing reading stream; do not add a recommendation tab.
- Show interest onboarding only on a fresh installation and allow skipping.
- Existing users receive no upgrade interruption and can configure interests
  from `我的`.
- Offer 12 curated interests with at most 5 selected:
  `自我成长`, `学习求知`, `人生智慧`, `理想奋斗`, `勇气行动`, `实践求真`,
  `哲学思辨`, `劳动创造`, `人际关系`, `人民社会`, `历史时代`, `诗词文学`.
- Use favorites and linked notes as strong positive signals, likes as a positive
  signal, and `减少此类` as a strong negative signal.
- Reads only manage reading progress. Searches and quick swipes do not affect
  the profile.
- `减少此类` lowers related weights rather than permanently hiding content,
  advances away from the current card, and can be cleared from `我的`.

## Option A: Curated Rules And Weighted Local Ranking

Define stable interest IDs and an exact mapping from existing raw themes/series
to the 12 user-facing interests. Build a bounded profile from selected
interests and explicit feedback. Rank without replacement using injected
randomness, interleaving approximately four personalized candidates with one
exploration candidate.

Advantages:

- Fully offline, deterministic in tests, explainable, and easy to roll back.
- Uses metadata already present in the installed package; no content schema or
  600-file migration is required.
- Every card remains eligible, so the round remains complete and duplicate-free.

Costs:

- The curated mapping must be maintained when editorial themes change.
- It models category affinity, not subtle semantic similarity between quotes.

## Option B: Add Recommendation Categories To The Content Contract

Add canonical interest-category IDs to every authored card and release package,
then rank locally with the same bounded scoring algorithm.

Advantages:

- Editorial ownership is explicit and future content can be validated before
  release.
- Android does not need a raw-theme compatibility map.

Costs:

- Requires a package schema change, synchronized Python and Android changes,
  revision updates, and a bulk edit across 600 cards.
- Older apps need compatibility handling, and the scope becomes a content
  release migration rather than a focused recommendation feature.

## Option C: On-Device Semantic Embeddings Or ML

Ship or generate card vectors and construct a user vector from interacted
cards, optionally mixing it with explicit interests.

Advantages:

- Can find similarity beyond manually curated labels.

Costs:

- Adds model/vector assets, package size, inference or preprocessing cost, and
  much harder explanation and regression testing.
- Six hundred labeled cards and sparse explicit feedback do not justify the
  operational complexity for the first version.

## Recommendation

Use Option A for the first version. Keep taxonomy, profile construction, and
ranking as separate pure Kotlin units so Option B can replace only taxonomy
ownership later without changing UI or persisted feedback contracts.

## Proposed State And Round Boundaries

- Add a singleton recommendation state that records whether fresh-install
  onboarding is complete.
- Add normalized selected-interest rows keyed by stable category ID.
- Add explicit reduce-feedback rows keyed by card ID. Derive their affected
  categories through the central taxonomy; do not retain withdrawn card
  snapshots solely for feedback.
- In the current-to-next Room migration, create the tables and insert
  `onboardingCompleted = true` for existing databases. A fresh database has no
  row until the user saves or skips, which is how the UI distinguishes a fresh
  installation without inspecting install timestamps.
- Persist `furthestPosition` on the reading round. Preserve positions through
  that boundary and rerank only the unseen tail after preference or feedback
  changes. This keeps back-scroll stable while allowing recommendations to
  adapt during a 600-card round.
- On a fresh install, import content but delay first-round creation until the
  user saves or skips onboarding; this prevents an initial random order from
  appearing before preferences are known.

## UI Boundary

- Render a focused interest-selection screen before the app shell only when
  fresh-install onboarding is incomplete.
- Add an `兴趣偏好` destination from `我的`; reuse the same selection
  component and provide a separate destructive-looking, confirmed action to
  clear all `减少此类` records.
- Put `减少此类` in the reader header's `MoreVert` overflow menu, leaving the
  tested card action row unchanged. Advance only after the feedback and unseen
  tail are committed successfully.

## Required Verification

- Pure JVM tests for taxonomy mapping, bounded signal aggregation, cold start,
  exploration ratio, no duplicates, deterministic injected randomness,
  negative weighting, and preservation of the locked prefix.
- Room migration test proving current user/card/round/note/search data survives
  and existing users are marked onboarding-complete.
- Repository instrumentation for fresh onboarding, skip/save, preference
  updates, feedback clear, mid-round unseen-tail reranking, process restart,
  content additions, withdrawals, and completion/new-round behavior.
- Compose instrumentation at 360 x 640, 360 x 800, and 412 x 915 for tag limits,
  onboarding skip/save, `我的` navigation, overflow feedback, and no overlap.

## Relevant Specs

- `.trellis/spec/backend/content-package-contract.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
