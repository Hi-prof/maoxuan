# Type Safety

## Type Boundaries

- `data.content.*Dto`: serialized release contract only.
- `data.local.*Entity`: Room schema only.
- `domain.model.*`: immutable UI-facing values.
- `*UiState` and enums: finite screen/application states.
- `CardInterpretation`: required immutable UI-facing projection of the three
  persisted interpretation fields; Compose never parses serialized content.
- `PersonalNote`: immutable UI-facing projection of one `NoteEntity`; nullable
  `cardId` means standalone, while nullable `title` is presentation-only
  optional data. Body and timestamps are non-null.

Conversion is explicit in the repository. Do not reuse DTOs as database rows or expose entities to composables.

## Runtime Validation

Kotlin serialization uses:

```kotlin
Json {
    ignoreUnknownKeys = false
    explicitNulls = false
}
```

Deserialization does not replace semantic validation. `ContentPackageReader` additionally validates schema, paths, sizes, hashes, UUIDs, dates, quote length/NFC, sources, image rights, and references before returning typed data.

## Common Patterns

- Use nullable types only for genuinely optional reading sections and timestamps.
- Model finite workflow values as enums or sealed types, not strings in UI code.
- Clamp persisted positions at the repository/domain boundary before presenting state.
- Inject clock/random/downloader dependencies where deterministic behavior matters.

## Forbidden Patterns

- Raw JSON maps or casts in screens.
- `!!` for remote/package data.
- `@Suppress` to bypass serialization or Room contract errors. The ViewModel factory's checked generic cast is the narrow existing exception.
- Lenient unknown-field handling for release assets.
- Encoding multiple state meanings into nullable text or unrelated Boolean flags.
