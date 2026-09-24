# Reference

What kimney does today, and nothing it is planned to do. `docs/roadmap.md`
has the plan, and [the cookbook](cookbook.md) has a runnable recipe for each
rule below.

## Applying

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney")
}
```

The plugin adds `kimney-runtime` to every JVM compilation and loads the
compiler plugin into it. It supports Kotlin 2.4: a patch newer than the
newest tested is applied with a warning, and another minor fails at
configuration, naming the range ([Kotlin versions](../README.md#kotlin-versions)).

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
4. **Containers.** A `List`, `Set`, `Collection`, `Iterable`, `Map` or
   `Array<T>` pair transforms element by element, each element by every rule
   here, into the same kind or a read-only supertype of it (`List` into
   `Iterable`). Order is kept. Crossing kinds (`Set` into `List`) is refused,
   since it would drop duplicates or pick an order. A map's values take every
   rule; its keys only identity or a value class, since anything else could
   turn two keys into one. The loop is written out, so no lambda is created.
   `MutableList`, `MutableSet`, `MutableCollection`, `MutableIterable` and
   `MutableMap` are containers too: a mutable source acts as its read-only
   kind, and a mutable target takes what its read-only kind would. A mutable
   target is always a new collection, even from a source of its own type, so
   neither side's edits reach the other.
5. **Object.** An `object` target is its instance, from an `object` source
   only, so no case can drop the fields of the one it came from.
6. **Enum.** Each source entry becomes the target entry of the same name,
   unless an [enum mapping](#enum-mappings) sends it elsewhere. A source entry
   with no target entry is a compile error; extra target entries are fine.
   Entries are compared by identity, never by ordinal, so an enum compiled
   elsewhere can be reordered safely.
7. **Sealed.** Each direct subclass of the source becomes the target's direct
   subclass of the same simple name, unless a [sealed mapping](#sealed-mappings)
   places it, and each pair is derived by every rule
   here, so a case gets its defaults and a failure inside it has a path
   through it (`ShapeDto.Circle.radius`). A single case into a sealed target —
   `Expr.Add` into `ExprDto` — takes the target's case of the same name the
   same way. A generic hierarchy's cases get their type arguments from where
   their sealed supertype names them directly: `Ok<T> : Result<T>` meeting
   `Result<User>` is `Ok<User>`. A case whose parameter appears only inside
   another type (`Many<T> : Box<List<T>>`) is not solved, and the hierarchy is
   then not modelled.
8. **Constructor.** A final or open Kotlin class outside the standard library
   is built with its public primary constructor. Each parameter takes, in
   order: the source's public property of the same name, transformed by these
   same rules; else the parameter's default value; else it is a failure.
   Source properties the target does not ask for are ignored. A generic target
   is built with its type arguments substituted.

A pair that meets itself below its own derivation — a tree, a linked list,
two types that hold each other — becomes a local function inside the call,
called again where the pair recurs. Nothing is added to the class or the
file, and a value graph with a cycle recurses until the stack runs out, as a
hand-written mapper would.

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
| Null into non-null | `StrictDto.name: String — User.name is String?, and a null has nowhere to go. Make StrictDto.name nullable, or fill it with .withFieldComputed(StrictDto::name) { … }.` |
| Crossing kinds | `StrictOrder.tags: List<TagDto> — a Set is not turned into a List. Fill it with .withFieldComputed(StrictOrder::tags) { … }.` |
| A map key that could collide | `StrictOrder.keyed[key]: LineDto — keys are transformed only as themselves or through a value class, since Line into LineDto could turn two keys into one.` |
| A missing case | `StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto. Map it with .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.…), or send every unmatched entry to one with .withEnumFallback(StatusDto.…).` |

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

## Enum mappings

```kotlin
val dto = account.into<_, AccountDto>()
    .withEnumEntryRenamed(Status.ARCHIVED, StatusDto.INACTIVE)
    .withEnumFallback(StatusDto.UNKNOWN)
    .transform()
```

An enum mapping decides what entries become wherever its two enums meet in
the derivation — at the root, in a field, as an element, inside a sealed
case — rather than naming a field:

| Mapping | Does |
|---|---|
| `withEnumEntryRenamed(from, to)` | `from` becomes `to`, even when the target has an entry named like `from` |
| `withEnumFallback(to)` | every entry of any enum becoming `to`'s enum, with no rename and no entry of the same name, becomes `to` |

For each entry, a rename comes first, then the entry of the same name, then
the fallback. The fallback is also what an entry compiled in after the call
becomes, where without one it throws `NoWhenBranchMatchedException`. An enum
mapped into itself is mapped entry by entry when a mapping names it, rather
than passed through.

The arguments must be the entries as written, `Status.ARCHIVED`, because the
plugin reads them at compile time:

| Failure | Says |
|---|---|
| Not an entry | `Into<Status, StatusDto> — withEnumEntryRenamed takes the entries themselves, like Status.ACTIVE, not a value that holds one.` |
| One entry renamed twice | `StatusDto — Status.ARCHIVED is renamed twice, by withEnumEntryRenamed #1 and #3. Keep one.` |
| Two fallbacks | `StatusDto — StatusDto falls back twice, by withEnumFallback #2 and #4. Keep one.` |

A mapping whose enums never meet is a warning, `KIMNEY_UNUSED_ENUM_MAPPING`:

```
withEnumEntryRenamed(Other.A → OtherDto.B) is not used: no Other → OtherDto pair occurs. A type it names may have changed.
withEnumFallback(OtherDto.B) is not used: nothing becomes OtherDto. A type it names may have changed.
```

A fallback that has nothing to catch today is not a warning: written ahead
of need is what it is for.

## Sealed mappings

```kotlin
val message = event.into<_, Message>()
    .withSealedCaseRenamed(Event.Escalated::class, Message.Raised::class)
    .withTransformer(Transformer<Event.Merged, Message> { Message.Closed(it.ticket, "merged") })
    .withSealedFallback(Message.Ignored)
    .transform()
```

The same two calls as enums have, for sealed types, wherever the two
hierarchies meet:

| Mapping | Does |
|---|---|
| `withSealedCaseRenamed(From::class, To::class)` | the case `From` becomes the case `To`, derived by every rule, even when the target has a case named like `From` |
| `withSealedFallback(Object)` | every case with nothing else to become is the object case `Object` |

For each source case: a rename, then the case of the same name, then a
[transformer](#transformers) from the case into the target's sealed parent,
then the fallback. The transformer is tried only for a case with no
same-named target, so none that already derives changes. The fallback is
also what a case compiled in after the call becomes.

A rename takes class literals and a fallback the object itself:

| Failure | Says |
|---|---|
| Not a class literal | `Into<Shape, ShapeDto> — withSealedCaseRenamed takes class literals, like Shape.Hexagon::class, not a value that holds one.` |
| Not an object | `Into<Shape, ShapeDto> — withSealedFallback takes the object itself, like ShapeDto.Unsupported, not a value that holds one.` |
| One case renamed twice | `ShapeDto — Shape.Hexagon is renamed twice, by withSealedCaseRenamed #1 and #2. Keep one.` |

A mapping whose types never meet is a warning, `KIMNEY_UNUSED_SEALED_MAPPING`:

```
withSealedCaseRenamed(ShapeDto.Circle → Shape.Circle) is not used: no sealed type with ShapeDto.Circle becomes one with Shape.Circle. A type it names may have changed.
withSealedFallback(Other.Unknown) is not used: no sealed type becomes one with Other.Unknown. A type it names may have changed.
```

A fallback that is not one of the target's object cases fits nothing, so it
is that warning too.

## Transformers

```kotlin
import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

val userToDto = Transformer<User, UserDto> { UserDto(it.fullName) }

val dto = team.into<_, TeamDto>().withTransformer(userToDto).transform()
```

A `Transformer<A, B>` passed with `withTransformer` is tried first on every
pair below the root, before identity and every other rule — in fields, list
elements, map values and sealed cases. It fits a pair `S → T` when `S` is a
subtype of `A` and `B` a subtype of `T`. It is how a pair no rule covers gets
through, `String? → String` included.

Each transformer is evaluated once, in chain order with the other overrides.
The root pair is the chain's own and never goes to a transformer.

| Failure | Says |
|---|---|
| Two transformers fit one pair | `TeamDto.lead: UserDto — two transformers fit User → UserDto: withTransformer #1 and #3. Pass one.` |
| A transformer fits nothing (warning) | `withTransformer(Int → Long) is not used: no pair below the root fits it. A type it names may have changed.` |

A `Transformer` context parameter of any function or lambda around the call
is offered the same way, innermost first: `context(showMoney) {
invoice.transformInto<InvoiceDto>() }`. It is never reported unused. When a
passed and a context transformer both fit, the error names both:
`withTransformer #1 and context parameter 'money'`.

The first failure inside a nested class offers one: `… Or map Address →
AddressDto with .withTransformer(Transformer<Address, AddressDto> { … }).`

## Partial transformations

```kotlin
import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.transformIntoPartial

val result: Partial<Signup> = form.transformIntoPartial()
```

`transformIntoPartial<B>()`, and a chain's `.transformPartial()`, derive by
every rule above with two differences:

- `S? → T` is allowed. A null records `PartialError(path, "is null")`; a value
  goes on through `S → T`.
- A constructor or value class that throws `IllegalArgumentException` records
  its message at the path of what it was building.

Arguments are built before their constructor, and a constructor runs only if
none of its arguments recorded an error. The result is `Partial.Ok` with the
value if nothing was recorded, and `Partial.Errors` with every error, in the
order met, otherwise. Other exceptions propagate. A pair no rule connects is
still a compile error: partial mode relaxes what may fail at runtime, not what
can be derived.

A `PartialTransformer<A, B>`, passed with `withPartialTransformer` or in a
context parameter, fits pairs as a `Transformer` does, in partial calls only.
Its errors are re-rooted where its value sits, with
`Partial.Errors.relocatedTo`: an empty path becomes the pair's path, and a
path written from its own root has that root replaced. In a total call a
partial transformer that fits is a compile error:
`Booking.checkIn: Int — the transformer that fits String → Int can fail. End the chain with .transformPartial().`

A Java platform type (`String!`) counts as non-null, as Kotlin lets it be used.

Not yet: nested field overrides (a transformer covers the pair), concrete
collection targets (`ArrayList`, `HashMap`), and primitive arrays other than
as themselves. `docs/roadmap.md` has the order.

Compiled without the plugin, the call throws `KimneyNotApplied`, whose message
says how to apply it.
