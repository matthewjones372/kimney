# 0008 — When the source might not fit

## Problem

Every transformation kimney derives is total: it cannot fail at runtime, so
anything that might — a `String?` into a `String`, a `UserId` whose
constructor `require`s a positive value — is a compile error. That is right
for moving data between layers that already agree, and wrong at the edge,
where a request is exactly the thing that might not fit. There, the
hand-written answer is a validator per DTO that stops at the first problem and
reports it without saying where.

## Not doing

- **No user-written fallible transformers** (`PartialTransformer`, parsing a
  `String` into a `LocalDate`): proposed as spec 0013, on this spec's result
  type and lowering.
- **No catching beyond `IllegalArgumentException`**: that is what `require`
  throws, and the idiom for validating in a constructor. Anything else is a
  bug and propagates.
- **No fail-fast mode.** Every error is collected; a caller who wants the
  first takes it.

## Shape

```kotlin
public sealed interface Partial<out T> {
    public data class Ok<T>(val value: T) : Partial<T>
    public data class Errors(val errors: List<PartialError>) : Partial<Nothing>
}
public data class PartialError(val path: String, val message: String)

fun PlaceOrderRequest.toCommand(): Partial<PlaceOrder> = transformIntoPartial()
// Errors([PartialError("PlaceOrder.customerId", "is null"),
//         PartialError("PlaceOrder.lines[].quantity", "quantity must be positive")])
```

`transformIntoPartial<B>()` and a chain's `.transformPartial()` derive by
every rule, with two differences:

- `S? → T` is allowed: a null records `is null` at its path, and a value goes
  on through `S → T`.
- A constructor or value class that throws `IllegalArgumentException` records
  its message at the path of what it was building.

Nothing is built from a failed part: a class whose arguments recorded errors
is not constructed, so no constructor sees a placeholder. The result is `Ok`
if nothing was recorded, and `Errors` with every error, in source order,
otherwise.

## Why this shape

Its own result type rather than `kotlin.Result`, which carries one
`Throwable`: the point is to report every problem, each with the path the
total rules already name. Catching only `IllegalArgumentException` keeps a
bug from being reported as bad input. Building nothing from a failed part is
what makes the collected errors safe to trust: a constructor never runs on a
value kimney made up.

## Stack

- [x] **`spec-0008-runtime`** — `Partial`, `PartialError`,
      `transformIntoPartial`, `transformPartial`; BCV.
      Done when: the stubs throw `KimneyNotApplied` naming themselves.
- [x] **`spec-0008-engine`** — partial mode: `S? → T` allowed, constructors
      marked with the path they report at.
      Done when: engine tests cover a null, a nested null, and a guarded
      value class, with total mode unchanged.
- [x] **`spec-0008-plugin`** — both adapters and the lowering; box, agreement;
      cookbook and reference.
      Done when: box tests pass for every error collected with its path, an
      `Ok`, a list element failing, and a class not built from a failed part.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers: its own result type, errors accumulated,
  `IllegalArgumentException` only, and fallible user transformers split into
  0013.
