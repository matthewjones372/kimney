# 0003 — Filling the fields the engine cannot

## Problem

After 0002, a target field with no same-named source and no default is a
compile error, and the only fix is to change one of the two classes. A
renamed field (`fullName` → `name`), a constant (`source = "import"`) or a
derived value (`age` from `born`) forces the whole mapping back to hand-written
code, which is the thing kimney exists to replace.

## Not doing

- **No nested selectors.** Overrides name top-level target fields only;
  Kotlin has no `UserDto::address::zip`. A nested pair gets its own
  transformer in 0006.
- **No `withFieldConstPartial` or fallible overrides** (0008).
- **No renaming by rule** (case-insensitive, prefix stripping).

## Shape

```kotlin
val dto = user.into<_, UserDto>()
    .withFieldConst(UserDto::source, "import")
    .withFieldComputed(UserDto::age) { ageOf(it.born) }   // it: User
    .withFieldRenamed(User::fullName, UserDto::name)
    .transform()
```

The stubs, in `kimney-runtime`, carry the typing:

```kotlin
public class Into<A, B> internal constructor() {
    public fun <T> withFieldConst(field: KProperty1<B, T>, value: T): Into<A, B>
    public fun <T> withFieldComputed(field: KProperty1<B, T>, compute: (A) -> T): Into<A, B>
    public fun <S, T> withFieldRenamed(from: KProperty1<A, S>, to: KProperty1<B, T>): Into<A, B>
    public fun transform(): B
}
public fun <A, B> A.into(): Into<A, B>
```

An override wins over every other source for its field (rule 1 in the
roadmap). New failures, on the `transform()` call:

```
UserDto.nickname — withFieldConst names 'nickname', which is not a constructor parameter of UserDto.
UserDto.name — overridden twice, by withFieldRenamed and withFieldConst. Keep one.
Into<User, UserDto> — the overrides must be one chain ending in .transform(); this one is stored in 'builder'.
```

`MissingSource`'s hint grows the override: `…or give UserDto.email a default
value, or add .withFieldConst(UserDto::email, …)`.

## Why this shape

Kotlin cannot infer one type argument and take the other explicitly, so
`user.into<UserDto>()` is not expressible with the source typed. The options:
`into<_, UserDto>()` keeps both types in the type system with standard syntax;
`from(user).into<UserDto>()` reads better but adds an entry word; a lambda on
`transformInto<UserDto> { }` reads best but leaves the source untyped inside,
so `withFieldComputed` could only be checked by the plugin, not the compiler.
Recommend `into<_, UserDto>()`: every override is type-checked before kimney
runs. The chain must be one expression because the plugin reads it from the
tree; a builder that escapes is a failure, not a runtime lookup.

## Stack

- [ ] **`spec-0003-runtime`** — `Into`, `into`, the four stubs; BCV `.api`.
      Done when: each stub throws `KimneyNotApplied` naming itself.
- [ ] **`spec-0003-engine`** — `derive` takes overrides; rule 1; the two new
      failures; the longer `MissingSource` hint.
      Done when: engine tests cover each override and each failure's text.
- [ ] **`spec-0003-fir`** — the checker reads the chain from `transform()`
      back to `into`, and reports `OverrideNotStatic` for any `Into` that
      escapes it.
      Done when: diagnostic goldens pass for all three new failures.
- [ ] **`spec-0003-ir`** — lowering of the chain; agreement tests; example
      and reference updated.
      Done when: box tests pass for each override, including evaluation order.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Decisions

- **Entry syntax:** `into<_, UserDto>()`.
- **`withFieldComputed` lowers** to a direct call of the lambda's body with the
  source as its argument; no function object.
- **Evaluation order:** source once, then override expressions in the order
  written, then the constructor.
- **A const for a derivable field** is silent.
