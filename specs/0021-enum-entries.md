# 0021 — An enum entry the target does not have

## Problem

Enums map entry by name, and a source entry the target lacks is a compile
error:

```
StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.
```

That is right when the entry was added by mistake, and a dead end when it was
not. Two shapes come up in every layered service: a domain entry the API
shows under another name (`ARCHIVED` as `INACTIVE`), and an API or wire enum
with a catch-all (`UNKNOWN`, `UNRECOGNIZED`) that everything unmatched should
land in. Today the only way through is `withTransformer` for the enum pair,
which means writing the whole `when` by hand, matches included. It does not
apply at the root either, so `status.transformInto<StatusDto>()` has no way
through at all. And the error suggests nothing.

## Not doing

- **No sealed cases.** The same two calls for sealed hierarchies are 0022, on
  this spec's rule.
- **No `String` into an enum**, with or without a fallback. That is parsing,
  and parsing is a `PartialTransformer` (0013).
- **No mapping by ordinal**, and no case-insensitive or `snake_case` name
  matching. A name kimney matches is the name as written.
- **No fallback computed from the source entry.** A lambda from entry to entry
  is a `Transformer`, and 0006 already has one.

## Shape

Two chain calls, each naming entries as values:

```kotlin
public fun <S : Enum<S>, T : Enum<T>> withEnumEntryRenamed(from: S, to: T): Into<A, B>
public fun <T : Enum<T>> withEnumFallback(to: T): Into<A, B>

// One entry sent somewhere else; the rest still match by name.
fun Status.toDto(): StatusDto = into<_, StatusDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .transform()

// Everything with no same-named entry goes to one.
fun WireStatus.toDomain(): Status = into<_, Status>()
    .withEnumFallback(Status.UNKNOWN)
    .transform()
```

Both apply to their enum pair wherever it occurs — at the root, in a field, as
a list element, inside a sealed case — as a transformer does, but at the root
too: they name entries, not the pair's whole mapping.

- **A rename wins over a name match.** `withEnumEntryRenamed(Status.PENDING,
  StatusDto.ACTIVE)` sends `PENDING` to `ACTIVE` even though `StatusDto` has a
  `PENDING`: what is written at the call is what happens.
- **A fallback covers its target enum from every source enum** that meets it
  in the derivation, and only the entries nothing else matched.
- **Entries are read at compile time.** An argument must be the entry itself,
  `Status.ARCHIVED`, not a variable holding one, or the call fails as other
  overrides do:

  ```
  Into<Status, StatusDto> — withEnumEntryRenamed takes the entries themselves, like Status.ARCHIVED, not a value that holds one.
  ```

- **Mistakes are named.** Two renames of one entry, or two fallbacks for one
  target enum, fail as `DuplicateOverride` does. A rename or fallback whose
  enums never meet in the derivation is a warning, as an unused transformer
  is:

  ```
  withEnumEntryRenamed(Status.ARCHIVED → StatusDto.INACTIVE) is not used: no Status → StatusDto pair occurs. A type it names may have changed.
  ```

- **The missing-entry error says both ways out:**

  ```
  StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto. Map it with .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.…), or send every unmatched entry to one with .withEnumFallback(StatusDto.…).
  ```

The lowering is unchanged in kind: the same `when` over the source's entries,
compared by identity, with renamed and fallen-back arms pointing at their
chosen target entry. Its `else`, which today throws for an entry compiled in
after this call was, returns the fallback when there is one: an enum from a
library that gains an entry without this code being recompiled still lands in
the catch-all it was given. Without a fallback, an entry added to the source
is a compile error at the next build, as now.

## Why this shape

Entries as values, not names as strings, so a typo or a removed entry is a
compile error in the call itself and "rename" in the IDE follows them. Two
calls rather than one general `withEnumMapping(Map<S, T>)` because the two
intents differ in what they promise about the future: a rename covers one
entry and leaves every later one to be checked, and a fallback absorbs every
later one on purpose. That is exactly the drift kimney exists to catch, so it
is opt-in, named at the call, and the docs send people to the rename first.
Scoping both by enum pair rather than by field matches how transformers
already work and keeps the chain short when the same enum appears in many
places.

## Stack

- [x] **`spec-0021-runtime`** — `withEnumEntryRenamed`, `withEnumFallback`; BCV.
      Done when: the stubs throw `KimneyNotApplied` naming themselves.
- [x] **`spec-0021-engine`** — `Plan.EnumByName` carries each source entry's
      target entry; the renames and fallbacks in the enum rule; the failures
      and the hint.
      Done when: engine tests cover a rename, a rename over a name match, a
      fallback, both together, a nested pair, duplicates and unused ones.
- [x] **`spec-0021-plugin`** — both chain readers take the calls and read the
      entries; the lowering; goldens, box, agreement; cookbook and reference.
      Done when: box tests pass at the root, in a field, in a list and in a
      partial transformation, and goldens hold every message above.

## Acceptance

```bash
./gradlew spotlessApply && ./gradlew build
./gradlew :kimney-compiler-plugin:test -Pkimney.kotlinUnderTest=2.4.20
```

## Decisions

Drafted and committed on the maintainer's go-ahead with the recommended
answers:

- **Names:** `withEnumEntryRenamed` and `withEnumFallback`, Kotlin's word
  rather than Chimney's.
- **A fallback with nothing to catch is not a warning**; only one whose target
  enum never appears is unused.
- **A rename over a name match is allowed** and wins.
- **A fallback covers its target enum from every source enum.**
- **The fallback is also the `else`** of the lowered `when`, so an entry
  compiled in later falls back at runtime rather than throwing (decided while
  committing, from reading the lowering).
- **The unused warning is its own diagnostic**, `KIMNEY_UNUSED_ENUM_MAPPING`,
  so it can be suppressed apart from `KIMNEY_UNUSED_TRANSFORMER` (decided while
  building).
- **The not-an-entry message names a real entry** of the enum the argument
  holds, `like Status.ACTIVE`, rather than the one the author meant, which the
  plugin cannot know.
