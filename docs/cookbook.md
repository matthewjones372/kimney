# Cookbook

Recipes for the transformations people actually write, one per section. Each
is whole — imports, types and the call — so it can be pasted and edited.

Each recipe wraps its call in an extension function, `fun User.toDto(): UserDto
= transformInto()`, which is the shape worth copying: the return type names the
target, so the call needs no type argument, and callers read `user.toDto()`
wherever the mapping is needed.

Every recipe here is a file in [`example/`](../example/src/main/kotlin/example/cookbook),
quoted verbatim: it compiles through the Gradle plugin on every build, a test
runs it, and another fails the build if this page and the file drift apart.
The [reference manual](reference.md) has the full rules; this page is the
short answer.

The one piece of setup, for every recipe:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney")
}
```

kimney is built for exactly one Kotlin version. On any other, the build stops
at configuration and names both.

- [A first transformation](#a-first-transformation)
- [Nested classes and defaults](#nested-classes-and-defaults)
- [A field with another name, a computed value, a constant](#a-field-with-another-name-a-computed-value-a-constant)
- [A copy with changes](#a-copy-with-changes)
- [Enums across layers](#enums-across-layers)
- [Sealed types](#sealed-types)
- [Generic sealed types](#generic-sealed-types)
- [Optional values](#optional-values)
- [Value class ids and plain columns](#value-class-ids-and-plain-columns)
- [Lists, sets and maps](#lists-sets-and-maps)
- [A `Set` into a `List`](#a-set-into-a-list)
- [Trees and other types that contain themselves](#trees-and-other-types-that-contain-themselves)
- [Your own transformer for a nested pair](#your-own-transformer-for-a-nested-pair)
- [A transformer for everything in scope](#a-transformer-for-everything-in-scope)
- [Validating at the edge](#validating-at-the-edge)
- [Parsing at the edge](#parsing-at-the-edge)
- [Reading the errors](#reading-the-errors)
- [Compiled without the plugin](#compiled-without-the-plugin)

---

## A first transformation

Same-named properties fill the target's constructor. What the target does not
ask for — `admin` — is left behind.

```kotlin
// file: example/src/main/kotlin/example/cookbook/first/FirstTransform.kt
package example.cookbook.first

import io.github.matthewjones372.kimney.transformInto

data class User(val name: String, val email: String, val admin: Boolean)

data class UserDto(val name: String, val email: String)

fun User.toDto(): UserDto = transformInto()
```

The call compiles to `UserDto(name, email)`: no reflection, no
mapping table, nothing at runtime a hand-written mapper would not have.

## Nested classes and defaults

A nested pair is derived by the same rules, as deep as it goes. A target
parameter with a default takes it when the source has nothing of that name.

```kotlin
// file: example/src/main/kotlin/example/cookbook/nested/NestedAndDefaults.kt
package example.cookbook.nested

import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String, val zip: String)

data class Customer(val name: String, val address: Address)

data class AddressDto(val street: String, val zip: String, val country: String = "GB")

data class CustomerDto(val name: String, val address: AddressDto, val tier: String = "standard")

fun Customer.toDto(): CustomerDto = transformInto()
```

`toDto` gives `CustomerDto(name, AddressDto(street, zip, "GB"), "standard")`.

## A field with another name, a computed value, a constant

`into<_, Target>()` starts an override chain: the `_` lets Kotlin infer the
source while you name the target. Each override fills one top-level field.

```kotlin
// file: example/src/main/kotlin/example/cookbook/overrides/Overrides.kt
package example.cookbook.overrides

import io.github.matthewjones372.kimney.into

data class Person(val fullName: String, val born: Int, val email: String)

data class PersonDto(val name: String, val age: Int, val email: String, val source: String)

fun Person.toDto(year: Int): PersonDto = into<_, PersonDto>()
    .withFieldRenamed(Person::fullName, PersonDto::name)
    .withFieldComputed(PersonDto::age) { year - it.born }
    .withFieldConst(PersonDto::source, "import")
    .transform()
```

- `withFieldRenamed` reads another source property, transformed by the
  usual rules.
- `withFieldComputed` runs a lambda on the source. It compiles to a direct
  call: no function object is created.
- `withFieldConst` gives a value.

The chain must be written as one expression from `into()` to `.transform()`,
because the plugin reads it at compile time. A builder stored in a `val` is a
compile error that says so.

## A copy with changes

With an override, a source of the target's own type is rebuilt rather than
passed through. Here that is `copy(suspended = true)`; it works the same for a
class that has no `copy`.

```kotlin
// file: example/src/main/kotlin/example/cookbook/copy/CopyWithChanges.kt
package example.cookbook.copy

import io.github.matthewjones372.kimney.into

data class Account(val id: Long, val owner: String, val suspended: Boolean)

fun Account.suspend(): Account = into<_, Account>()
    .withFieldConst(Account::suspended, true)
    .transform()
```

## Enums across layers

Each source entry becomes the target entry of the same name. Extra target
entries are fine; a source entry the target lacks is a compile error.

```kotlin
// file: example/src/main/kotlin/example/cookbook/enums/Enums.kt
package example.cookbook.enums

import io.github.matthewjones372.kimney.transformInto

enum class Status { ACTIVE, SUSPENDED }

enum class StatusDto { ACTIVE, SUSPENDED, UNKNOWN }

fun Status.toDto(): StatusDto = transformInto()
```

Entries are compared by identity, never by ordinal, so an enum from another
module can be reordered without breaking the mapping.

## Sealed types

Each case becomes the target case of the same simple name, and each pair goes
through every rule — so `Card` picks up its default `network`.

```kotlin
// file: example/src/main/kotlin/example/cookbook/sealed/Sealed.kt
package example.cookbook.sealed

import io.github.matthewjones372.kimney.transformInto

sealed interface Payment {
    data class Card(val last4: String) : Payment

    data class Transfer(val iban: String) : Payment

    data object Cash : Payment
}

sealed interface PaymentDto {
    data class Card(val last4: String, val network: String = "unknown") : PaymentDto

    data class Transfer(val iban: String) : PaymentDto

    data object Cash : PaymentDto
}

fun Payment.toDto(): PaymentDto = transformInto()
```

A case added to `Payment` without one in `PaymentDto` stops the build at every
call that meets it: the exhaustiveness a hand-written `when` loses the day
someone adds an `else`.

## Generic sealed types

A result type — `Lookup<T>` with a `Found<T>` and a `Missing` — maps like any
sealed type, each case's type arguments worked out from the sealed type's:
`Found<T> : Lookup<T>` meeting `Lookup<Product>` is `Found<Product>`, and
`Missing : Lookup<Nothing>` has none.

```kotlin
// file: example/src/main/kotlin/example/cookbook/results/Results.kt
package example.cookbook.results

import io.github.matthewjones372.kimney.transformInto

sealed interface Lookup<out T> {
    data class Found<T>(val value: T) : Lookup<T>

    data class Missing(val key: String) : Lookup<Nothing>
}

sealed interface LookupView<out T> {
    data class Found<T>(val value: T) : LookupView<T>

    data class Missing(val key: String) : LookupView<Nothing>
}

data class Product(val sku: String, val pence: Long)

data class ProductView(val sku: String, val pence: Long)

fun Lookup<Product>.toView(): LookupView<ProductView> = transformInto()
```

A case whose type parameter appears only inside another type —
`Many<T> : Box<List<T>>` — is not worked out, and a pair that needs it is
`no rule`.

## Optional values

`S? → T?` derives `S → T` behind a null check, and `S → T?` just derives
`S → T`. So `Address? → AddressDto?` is the constructor rule with null kept as
null.

```kotlin
// file: example/src/main/kotlin/example/cookbook/optionals/Optionals.kt
package example.cookbook.optionals

import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String)

data class Profile(val nickname: String, val billing: Address?)

data class AddressDto(val street: String)

data class ProfileDto(val nickname: String?, val billing: AddressDto?)

fun Profile.toDto(): ProfileDto = transformInto()
```

The other direction, `String? → String`, is refused: see
[Reading the errors](#reading-the-errors).

## Value class ids and plain columns

A value class source unwraps and a value class target wraps, whatever the
held property is called, so a domain `UserId` meets a `Long` column — and
another value class — through the value inside.

```kotlin
// file: example/src/main/kotlin/example/cookbook/ids/ValueClasses.kt
package example.cookbook.ids

import io.github.matthewjones372.kimney.transformInto

@JvmInline
value class UserId(val raw: Long)

@JvmInline
value class OwnerId(val value: Long)

data class User(val id: UserId, val name: String)

data class UserRow(val id: Long, val name: String)

data class Document(val owner: OwnerId)

fun User.toRow(): UserRow = transformInto()

fun UserRow.toUser(): User = transformInto()

fun UserId.toOwnerId(): OwnerId = transformInto()
```

## Lists, sets and maps

A container pair transforms element by element with every rule, keeping the
source's order. A map's values take every rule; its keys only identity or a
value class, as here, since anything else could turn two keys into one.

```kotlin
// file: example/src/main/kotlin/example/cookbook/collections/Collections.kt
package example.cookbook.collections

import io.github.matthewjones372.kimney.transformInto

@JvmInline
value class Sku(val code: String)

enum class Tag { GIFT, FRAGILE }

enum class TagDto { GIFT, FRAGILE }

data class Line(val sku: Sku, val quantity: Int)

data class LineDto(val sku: String, val quantity: Int)

data class Order(val lines: List<Line>, val tags: Set<Tag>, val stock: Map<Sku, Int>)

data class OrderDto(val lines: List<LineDto>, val tags: Set<TagDto>, val stock: Map<String, Int>)

fun Order.toDto(): OrderDto = transformInto()
```

The generated code is the loop `map` compiles to — one new collection, sized
to the source where its size is known — and no lambda is created.

## A `Set` into a `List`

Crossing kinds is refused, because `Set → List` has to pick an order and
`List → Set` would drop duplicates. The error names the fix, which is to write
the decision down:

```kotlin
// file: example/src/main/kotlin/example/cookbook/crossing/CrossingKinds.kt
package example.cookbook.crossing

import io.github.matthewjones372.kimney.into

data class Article(val title: String, val tags: Set<String>)

data class ArticleDto(val title: String, val tags: List<String>)

fun Article.toDto(): ArticleDto = into<_, ArticleDto>()
    .withFieldComputed(ArticleDto::tags) { it.tags.sorted() }
    .transform()
```

## Trees and other types that contain themselves

A comment thread, a category tree, an org chart: a type that holds more of
itself derives like any other, to whatever depth the value has.

```kotlin
// file: example/src/main/kotlin/example/cookbook/trees/Trees.kt
package example.cookbook.trees

import io.github.matthewjones372.kimney.transformInto

data class Comment(val author: String, val text: String, val replies: List<Comment>)

data class CommentView(val author: String, val text: String, val replies: List<CommentView>)

fun Comment.toView(): CommentView = transformInto()
```

When a pair meets itself below its own derivation, kimney writes that part as
a local function inside the call and calls it again where the pair recurs —
the recursive function you would have written. Mutually recursive pairs
(`Folder` holding `File` holding `Folder`) work the same way. A value graph
with a cycle recurses until the stack runs out, as a hand-written mapper
would.

## Your own transformer for a nested pair

Overrides reach top-level fields only. When a nested pair needs one — every
`User` inside a `Team` renames `fullName` — write how that pair maps once, as a
`Transformer`, and pass it to the chain. It serves every pair it fits below the
root: fields, list elements, map values and sealed cases.

```kotlin
// file: example/src/main/kotlin/example/cookbook/transformers/Transformers.kt
package example.cookbook.transformers

import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

data class User(val fullName: String, val email: String)

data class UserDto(val name: String, val email: String)

data class Team(val lead: User, val members: List<User>, val motto: String?)

data class TeamDto(val lead: UserDto, val members: List<UserDto>, val motto: String)

/** How a User becomes a UserDto, written once, with kimney itself. */
val userToDto = Transformer<User, UserDto> {
    it.into<_, UserDto>().withFieldRenamed(User::fullName, UserDto::name).transform()
}

/** A null gets a value only where someone says which. */
val noMotto = Transformer<String?, String> { it ?: "(none)" }

fun Team.toDto(): TeamDto = into<_, TeamDto>()
    .withTransformer(userToDto)
    .withTransformer(noMotto)
    .transform()
```

A transformer is tried before any other rule, so it also does what no rule
will: `noMotto` is how a `String?` becomes a `String`, with the choice written
down. It fits as a function would, so a `Transformer<Person, UserDto>` serves a
`User` that extends `Person`. Two that fit the same pair are an error naming
both, and one that fits nothing is a warning — usually a type that changed
underneath it.

The chain evaluates each transformer once, in the order written, and calls
`transform` where its pair appears.

## A transformer for everything in scope

A transformer every mapping in a layer should use — how money is shown, how an
id is encoded — does not need passing to each chain. Put it in a context
parameter, and every `transformInto` and chain beneath it uses it for the
pairs it fits.

```kotlin
// file: example/src/main/kotlin/example/cookbook/context/ContextTransformers.kt
package example.cookbook.context

import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.transformInto

data class Money(val pence: Long, val currency: String)

data class MoneyDto(val display: String)

data class Invoice(val total: Money, val tax: Money)

data class InvoiceDto(val total: MoneyDto, val tax: MoneyDto)

/** How money is shown, decided once for the whole API layer. */
val showMoney = Transformer<Money, MoneyDto> {
    MoneyDto("${it.currency} ${it.pence / 100}.${(it.pence % 100).toString().padStart(2, '0')}")
}

// `this.` is required: a context parameter is also an `Any?`, so a bare transformInto() could mean either.
context(money: Transformer<Money, MoneyDto>)
fun Invoice.toDto(): InvoiceDto = this.transformInto()

fun present(invoice: Invoice): InvoiceDto = context(showMoney) { invoice.toDto() }
```

Inside a function with a context parameter, write `this.transformInto()`:
`transformInto` extends `Any?`, so the context parameter is a receiver it could
mean too, and Kotlin asks you to say which. A context transformer is matched
exactly like one passed with `withTransformer`. One that fits nothing is not a warning, since it is there
for every call below it, most of which will not need it. Two that fit the same
pair — one passed, one in context — is an error naming both.

## Validating at the edge

Where data might not fit — a request, a form, a message from outside —
`transformIntoPartial` returns a `Partial`: every error with its path, or the
value. A null into a non-null field is an error there instead of a compile
failure, and a constructor's `require` is an error at what it was building.

```kotlin
// file: example/src/main/kotlin/example/cookbook/partial/Validation.kt
package example.cookbook.partial

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.transformIntoPartial

@JvmInline
value class Email(val address: String) {
    init {
        require("@" in address) { "is not an email address" }
    }
}

// What arrives over the wire: anything may be missing.
data class SignupForm(val email: String?, val name: String?, val referrals: List<String?>)

// What the domain accepts: nothing is.
data class Signup(val email: Email, val name: String, val referrals: List<Email>)

fun SignupForm.validate(): Partial<Signup> = transformIntoPartial()
```

`SignupForm("nope", null, listOf("a@b.c", null)).validate()` gives

```
Errors([PartialError("Signup.email", "is not an email address"),
        PartialError("Signup.name", "is null"),
        PartialError("Signup.referrals[]", "is null")])
```

Every error is collected, not the first. Nothing is built from a part that
failed, so no constructor ever runs on a value kimney made up, and only
`IllegalArgumentException` is caught — anything else is a bug and is
thrown. A chain ends in `.transformPartial()` to do the same.

## Parsing at the edge

What kimney cannot check itself — a date, a currency code, an enum by a label
— is a `PartialTransformer`: a function to a `Partial`, written once and
passed with `withPartialTransformer`, or put in a context parameter.

```kotlin
// file: example/src/main/kotlin/example/cookbook/parsing/Parsing.kt
package example.cookbook.parsing

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.PartialError
import io.github.matthewjones372.kimney.PartialTransformer
import io.github.matthewjones372.kimney.into
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A parse that can fail, written once: its error has no path, so it lands wherever the date sits. */
val isoDate = PartialTransformer<String, LocalDate> { text ->
    try {
        Partial.Ok(LocalDate.parse(text))
    } catch (e: DateTimeParseException) {
        Partial.Errors(listOf(PartialError("", "is not a date: ${e.parsedString}")))
    }
}

data class StayForm(val checkIn: String, val checkOut: String)

data class Stay(val checkIn: LocalDate, val checkOut: LocalDate)

fun StayForm.toStay(): Partial<Stay> = into<_, Stay>().withPartialTransformer(isoDate).transformPartial()
```

`StayForm("2026-09-24", "soon").toStay()` gives
`Errors([PartialError("Stay.checkOut", "is not a date: soon")])`. A
transformer's errors are re-rooted where its value sits: one with no path
lands at the field, and one from a nested `transformIntoPartial` keeps its
own path below it. A partial transformer is used only by a partial call; in a
total one that it would fit, kimney refuses to compile and says to end the
chain with `.transformPartial()`.

## Reading the errors

When a transformation cannot be derived, the call does not compile. The error
sits on the call and lists every field it could not fill, each with its path
from the target and what would fix it:

```
e: Main.kt:12:5 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip a default value. Or map Address → AddressDto with .withTransformer(Transformer<Address, AddressDto> { … }).
```

The ones you will meet, each quoted from the plugin's tests:

| You wrote | kimney says |
|---|---|
| A field nothing fills | `UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).` |
| `String?` into `String` | `StrictDto.name: String — User.name is String?, and a null has nowhere to go. Make StrictDto.name nullable, or fill it with .withFieldComputed(StrictDto::name) { … }.` |
| An enum entry the target lacks | `StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.` |
| Two types no rule connects | `ShapeDto.Circle.radius: Double — no rule transforms Int into Double. Or map Shape.Circle → ShapeDto.Circle with .withTransformer(Transformer<Shape.Circle, ShapeDto.Circle> { … }).` |
| A `Set` into a `List` | `StrictOrder.tags: List<TagDto> — a Set is not turned into a List. Fill it with .withFieldComputed(StrictOrder::tags) { … }.` |
| A map key that could collide | `StrictOrder.keyed[key]: LineDto — keys are transformed only as themselves or through a value class, since Line into LineDto could turn two keys into one.` |
| A private constructor | `Hidden — Hidden has no public primary constructor: it is private.` |
| One field overridden twice | `UserDto.name — overridden twice, by withFieldRenamed and withFieldConst. Keep one.` |
| A constant of the wrong type | `UserDto.age: Long — withFieldConst gives String, which is not a Long.` |

The last one is kimney's check, not the compiler's: `KProperty1` is covariant
in its value, so `withFieldConst(UserDto::age, "forty")` type-checks with the
value widened to `Any`.

## Compiled without the plugin

`transformInto` and the override chain are stubs the plugin replaces. In code
compiled without it, a call throws `KimneyNotApplied`, whose message says how
to apply the plugin — a sentence, not a stack trace from somewhere else.
