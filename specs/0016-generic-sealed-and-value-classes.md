# 0016 — Generic sealed types and value classes

## Problem

`sealed interface Result<out T>` with `Ok<T>(val value: T)` and
`Err(val message: String) : Result<Nothing>` is the most common sealed type
in Kotlin code, and `Result<User> → ResultDto<UserDto>` is `no rule`: kimney
models only non-generic hierarchies, because a case's type arguments have to be
worked out from the sealed type's. Generic value classes (`Id<T>(val raw:
Long)`) are refused for the same reason.

## Not doing

- **No inference through a case's supertype beyond direct parameters.** A case
  `Wrapped<T> : Result<List<T>>`, whose parameter appears only inside another
  type, is not modelled, and a pair needing it stays `no rule`.

## Shape

```kotlin
sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val message: String) : Result<Nothing>
}
sealed interface ResultDto<out T> { /* the same cases */ }

result.transformInto<ResultDto<UserDto>>()   // Result<User>: Ok<User> → Ok<UserDto>, Err → Err
```

A case's type arguments are solved by matching the arguments of its sealed
supertype against the concrete sealed type: `Ok<T> : Result<T>` meeting
`Result<User>` is `Ok<User>`; `Err : Result<Nothing>` has none to solve. Each
case pair then derives by every rule, as for a non-generic hierarchy. A generic
value class holds its property's type with the class's arguments substituted.

The engine needs no change: both adapters hand it concrete case types.

## Stack

- [x] **`spec-0016-generics`** — both adapters solve case type arguments and
      substitute a value class's held type; box, agreement and goldens; docs.
      Done when: box tests pass for `Result<User> → ResultDto<UserDto>`, a
      generic value class, and a hierarchy from another module, and a golden
      shows an unsolvable case staying `no rule`.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to keep going with the
  recommended answers: direct parameters only, adapters only, one entry.
