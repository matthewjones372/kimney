# Reference

What kimney does today, and nothing it is planned to do. `docs/roadmap.md`
has the plan.

## Applying

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney")
}
```

The plugin adds `kimney-runtime` to every JVM compilation and loads the
compiler plugin into it. kimney is built for exactly one Kotlin version; on
any other, the build fails at configuration naming both versions.

## `transformInto`

```kotlin
import io.github.matthewjones372.kimney.transformInto

val dto: UserDto = user.transformInto<UserDto>()
```

The plugin replaces the call with the constructor calls you would have
written. The source is evaluated once. For each target type, in order:

1. **Identity.** A source that is already a subtype of the target is used as
   it is, unless the call has overrides, in which case the target is rebuilt.
2. **Nullable target.** `S → T?` is `S → T`. `S? → T?` is `S → T` on the
   non-null value, with null kept as null; the source is read once. So every
   rule below also works inside an optional: `Address? → AddressDto?`.
3. **Value class.** A value class target wraps: `Long → UserId` derives
   `Long` into what `UserId` holds. A value class source unwraps, whatever its
   property is called, so `UserId → OrderId` meets through the `Long` inside.
   A value class source into a data class unwraps too, and does not match
   property names.
4. **Object.** An `object` target is its instance, from an `object` source
   only, so no case can drop the fields of the one it came from.
5. **Enum.** Each source entry becomes the target entry of the same name. A
   source entry with no target entry is a compile error; extra target entries
   are fine. Entries are compared by identity, never by ordinal, so an enum
   compiled elsewhere can be reordered safely.
6. **Sealed.** Each direct subclass of the source becomes the target's direct
   subclass of the same simple name, and each pair is derived by every rule
   here, so a case gets its defaults and a failure inside it has a path
   through it (`ShapeDto.Circle.radius`). Generic sealed hierarchies are not
   supported yet.
7. **Constructor.** A final or open Kotlin class outside the standard library
   is built with its public primary constructor. Each parameter takes, in
   order: the source's public property of the same name, transformed by these
   same rules; else the parameter's default value; else it is a failure.
   Source properties the target does not ask for are ignored. A generic target
   is built with its type arguments substituted.

The generated `when` ends in the throwing `else` an exhaustive hand-written
`when` compiles to, so a case added to a source compiled elsewhere fails the
same way it would by hand.

Anything else is a compile error on the call, naming every field that could
not be filled and the path to it:

```
e: Main.kt:15:13 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip a default value.
```

The other failures:

| Failure | Says |
|---|---|
| No public primary constructor | `Hidden — Hidden has no public primary constructor: it is private.` |
| No rule for the pair | `OrderDto.count: Long — no rule transforms Int into Long.` |
| Null into non-null | `UserDto.name: String — User.name is String?, and a null has nowhere to go. Make UserDto.name nullable, or fill it with .withFieldComputed(UserDto::name) { … }.` |
| A missing case | `StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.` |
| A type containing itself | `TreeDto.child: TreeDto — Tree → TreeDto contains itself, and recursive types are not supported yet.` |

## Overrides

```kotlin
import io.github.matthewjones372.kimney.into

val dto = user.into<_, UserDto>()
    .withFieldRenamed(User::fullName, UserDto::name)
    .withFieldConst(UserDto::source, "import")
    .withFieldComputed(UserDto::age) { 2026 - it.born }
    .transform()
```

`into<_, UserDto>()` infers the source and names the target. An override
fills one top-level constructor parameter of the target and wins over every
other way of filling it:

| Override | Fills the field with |
|---|---|
| `withFieldConst(field, value)` | `value` |
| `withFieldComputed(field) { source -> … }` | the lambda's result, applied to the source |
| `withFieldRenamed(from, to)` | the source property `from`, transformed by the rules above |

The source is evaluated once, then each override's expression in the order
written, then the constructor. A computed lambda compiles to a direct call of
its body: no function object is created.

The whole chain must be one expression from `into()` to `.transform()`, with
property references written out and a lambda for `withFieldComputed`, because
the plugin reads it at compile time. Anything else is a compile error:

| Failure | Says |
|---|---|
| Not a constructor parameter | `UserDto.nickname — withFieldConst names 'nickname', which is not a constructor parameter of UserDto.` |
| Overridden twice | `UserDto.name — overridden twice, by withFieldRenamed and withFieldConst. Keep one.` |
| Wrong value type | `UserDto.age: Long — withFieldConst gives String, which is not a Long.` |
| Unreadable renamed source | `UserDto.name: String — withFieldRenamed reads User.inherited, which kimney cannot read: it is inherited, an extension or not public.` |
| The chain escapes | `Into<User, UserDto> — the overrides must be one chain from into() to .transform(), …` |

The wrong-value-type check is kimney's, not the compiler's: `KProperty1` is
covariant in its value, so `withFieldConst(UserDto::age, "forty")` type-checks
with `T` widened to `Any`.

A Java platform type (`String!`) counts as non-null, as Kotlin lets it be used.

Not yet: nested overrides, nullable to non-null, collections, generic sealed
hierarchies, generic value classes and recursive types. `docs/roadmap.md` has the order.

Compiled without the plugin, the call throws `KimneyNotApplied`, whose message
says how to apply it.
