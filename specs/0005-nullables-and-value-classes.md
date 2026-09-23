# 0005 — Null goes where null can go

## Problem

`Address? → AddressDto?` is `no rule` today, because a nullable type is not a
class to construct, so any optional nested object sends the whole mapping back
to hand-written code. `String? → String` is refused with the same generic
message as `Int → Long`, which does not say that null is the reason. And a
value class (`@JvmInline value class UserId(val raw: Long)`) cannot cross to a
`Long` or to another value class, which is exactly where one usually sits:
the domain uses `UserId`, the DTO uses `Long`.

## Not doing

- **No collections.** The roadmap had them here; they need loops or lambdas in
  the lowering, and they are proposed as their own spec, 0010.
- **No null into a default** (`name ?: <the parameter's default>`): a null
  that silently becomes a default is a decision the user should write.
- **No generic value classes**, and nothing for `Result` or `kotlin.Nothing`.
- **Nullable to non-null stays a failure** until partial transformers (0008).

## Shape

```kotlin
data class User(val id: UserId, val address: Address?, val nickname: String)
data class UserDto(val id: Long, val address: AddressDto?, val nickname: String?)

@JvmInline value class UserId(val raw: Long)

user.transformInto<UserDto>()
// UserDto(id = user.id.raw, address = user.address?.let { AddressDto(…) }, nickname = user.nickname)
```

New rules, tried after identity and before objects:

- **Nullable target.** `S → T?` derives `S → T`. `S? → T?` derives `S → T`
  on the non-null value and keeps null as null, evaluating the source once.
- **Value class target.** `S → V(inner: I)` derives `S → I` and wraps it.
- **Value class source.** `V(inner: I) → T` reads `inner` and derives `I → T`,
  so `UserId → OrderId` works by unwrapping and wrapping, whatever the two
  properties are called.

One new failure replaces the generic one for null:

```
UserDto.name: String — User.name is String?, and a null has nowhere to go. Make UserDto.name nullable, or fill it with .withFieldComputed(UserDto::name) { … }.
```

The override hint appears only on top-level fields, as in 0003.

## Why this shape

Deriving through the non-null type means every existing rule works inside an
optional without a nullable copy of each: `Address? → AddressDto?` is the
constructor rule behind a null check, and `Status? → StatusDto?` is the enum
rule. Unwrap-and-wrap for value classes, rather than matching their property
names, treats a value class as what it is — a type around one value — so a
`raw` and a `value` still meet. The lowering is the null check and the
`.raw` read a person would write; a value class wrap is its constructor call,
which the backend erases.

## Stack

- [x] **`spec-0005-engine`** — `TypeModel` learns nullability and value
      classes; the three rules; `NullableToNonNull`.
      Done when: engine tests cover each rule, a nullable inside a sealed
      case, and the new failure with and without its hint.
- [x] **`spec-0005-fir`** — FIR adapter; goldens; the one existing golden
      whose `String? into String` message moves.
      Done when: goldens pass and the moved golden reads better.
- [x] **`spec-0005-ir`** — null-safe, wrap and unwrap lowering; box and
      agreement; example and reference.
      Done when: box tests pass, including a nullable read evaluated once and
      a value class from another module.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Decisions

- **Collections are spec 0010**, not part of this one.
- **A value class source always unwraps first**, even into a data class whose
  property names would have matched.
- **A nullable source read is held in a temporary**, so it is read once.
