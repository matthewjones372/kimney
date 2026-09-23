# 0002 — Class to class, or every reason why not

## Problem

After 0001, `transformInto` compiles and throws. The core claim — derive the
constructor call, or refuse to compile and say exactly why — has no code
behind it. Today a user writes the mapping by hand, and when `UserDto` gains a
field the hand-written mapping keeps compiling if the parameter has a default.

## Not doing

- **No overrides.** A missing field is a failure whose hint names the override
  0003 will add; nothing can satisfy it yet.
- **No nullable, value class, container, enum or sealed rules** (0004, 0005).
  `List<A> → List<B>` is a `NoRuleFor` failure, not a pass-through.
- **No recursive types** (0007). A cycle is a `Recursive` failure.

## Shape

```kotlin
data class Address(val street: String, val zip: String)
data class User(val name: String, val address: Address, val admin: Boolean)
data class AddressDto(val street: String, val zip: String, val country: String = "GB")
data class UserDto(val name: String, val address: AddressDto, val email: String)

val dto = user.transformInto<UserDto>()
```

```
e: Main.kt:9:16 Cannot transform User → UserDto:
     UserDto.email: String — User has no property 'email'.
   Fix (from 0003): .withFieldConst(UserDto::email, …) or .withFieldComputed(UserDto::email) { … }
```

`AddressDto.country` takes its default; `User.admin` is ignored; `Address →
AddressDto` recurses. The engine, in `kimney-derive`:

```kotlin
interface TypeModel<T> {
    fun render(type: T): String
    fun isSubtypeOf(sub: T, sup: T): Boolean
    fun constructor(type: T): Constructor<T>?          // public primary, else null
    fun property(owner: T, name: String): T?           // readable, same name
}

fun <T> derive(model: TypeModel<T>, source: T, target: T): Derived<T>
// Derived = Plan(Identity | Construct(args)) | Failures(NonEmpty<Failure>)
```

Failures in this spec: `MissingSource`, `NoPrimaryConstructor`, `NoRuleFor`,
`Recursive`, each with its path.

## Why this shape

The engine is generic over `T` so the FIR adapter passes `ConeKotlinType` and
the IR adapter passes `IrType`, and both run the same rules — the only way the
checker and the lowering cannot drift. The alternative, IR-only reporting, is
simpler but the errors never reach the IDE, since K2 IDE analysis stops at
FIR. Defaults count as a source because a target that declares one has said
what it wants when nothing is given; the rule order makes a same-named source
property win over it.

## Stack

- [ ] **`spec-0002-engine`** — `TypeModel`, `derive`, identity and constructor
      rules, the four failures and their messages, fake-model tests.
      Done when: every failure variant has an engine test naming its text.
- [ ] **`spec-0002-fir`** — FIR adapter and a call checker reporting
      `KIMNEY_CANNOT_TRANSFORM` on `transformInto` calls.
      Done when: diagnostic goldens pass for each failure variant.
- [ ] **`spec-0002-ir`** — IR adapter and lowering replacing the call with the
      planned constructor calls, defaults included.
      Done when: box tests pass, including nested and defaulted fields.
- [ ] **`spec-0002-agreement`** — every box source is also a no-diagnostic
      FIR test; `example` transforms a nested pair.
      Done when: `./gradlew :example:run` prints a transformed value.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Open questions

- **Name matching: exact, or case-insensitive like Chimney's option?**
  Recommend exact only; a flag can come with overrides in 0003.
- **Source properties: `val` only, or any public getter (`fun getX()`)?**
  Recommend Kotlin properties only; Java getters are a separate spec.
- **Non-public target constructors?** Recommend failing with
  `NoPrimaryConstructor` naming the visibility, rather than emitting a call
  that would not compile.
- **Defaults in IR:** call the synthetic `$default` constructor, or inline the
  default expression? Recommend the synthetic constructor — it is what `kotlinc`
  emits for a handwritten call, which is the promise in `AGENTS.md`.
