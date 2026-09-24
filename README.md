<div align="center">

# kimney

**Type-safe transformations between Kotlin types, derived at compile time.**
Say what you want to turn into what; the compiler writes the mapping, or tells
you exactly why it cannot.

[![Kotlin 2.4.10](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![K2 compiler plugin](https://img.shields.io/badge/K2-compiler%20plugin-7F52FF)](docs/reference.md)
[![status: pre-release](https://img.shields.io/badge/status-pre--release-orange)](docs/roadmap.md)
[![Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[A first look](#a-first-look) · [When it cannot](#when-it-cannot) ·
[What it covers](#what-it-covers) · [Trying it](#trying-it) · [Why](docs/why.md) ·
[Cookbook](docs/cookbook.md) · [Reference](docs/reference.md) ·
[Roadmap](docs/roadmap.md)

</div>

---

kimney is a K2 compiler plugin for the mapping code every layered Kotlin
service carries: domain to DTO, row to entity, event to message. It is
Scala's [Chimney](https://github.com/scalalandio/chimney), for Kotlin.

You write the call. At compile time kimney works out the constructor calls,
the nested mappings, the enum and sealed `when`s and the collection loops —
and generates exactly that code. There is no reflection, no runtime mapping
table and no annotation processor: what runs is what you would have written
by hand, because it is.

When it cannot derive a mapping, the call does not compile, and the error
names every field it could not fill, the path to it, and what would fix it.

## Why not by hand?

Layered and domain-driven code gives one idea several shapes on purpose — a
request, a command, an aggregate, an event, a row, a view — each saying which
layer it belongs to and what is happening. The shapes are the design; the
mapping between them is the tax, and it is why layers get collapsed. kimney
removes the tax: each crossing is one line, derived again on every build, so
it follows the types as they change and stops the build when it cannot.
[Why not write it by hand?](docs/why.md) walks one order through six shapes
in five one-line mappings, and shows the two bugs a hand-written mapper keeps
compiling through.

## A first look

```kotlin
// file: example/src/main/kotlin/example/cookbook/first/FirstTransform.kt
package example.cookbook.first

import io.github.matthewjones372.kimney.transformInto

data class User(val name: String, val email: String, val admin: Boolean)

data class UserDto(val name: String, val email: String)

fun User.toDto(): UserDto = transformInto()
```

`transformInto` compiles to `UserDto(name, email)`. Nested classes,
defaults, enums, sealed types, optionals, value classes and collections are
derived the same way, as deep as they go.

Where names or values differ, an override chain says so, and every override is
type-checked:

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

## When it cannot

A mapping kimney cannot derive is a compile error on the call. It comes from
the K2 frontend checker, the phase IDE analysis runs too, rather than from code
generation. It lists every problem at once, not the first:

```
e: Main.kt:12:5 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip a default value. Or map Address → AddressDto with .withTransformer(Transformer<Address, AddressDto> { … }).
```

It refuses what would silently lose information, and says what to write
instead:

```
StrictDto.name: String — User.name is String?, and a null has nowhere to go. Make StrictDto.name nullable, or fill it with .withFieldComputed(StrictDto::name) { … }.
StrictOrder.tags: List<TagDto> — a Set is not turned into a List. Fill it with .withFieldComputed(StrictOrder::tags) { … }.
StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.
```

## What it covers

For each pair of types, kimney tries these in order and uses the first that
applies. Each is a recipe in the [cookbook](docs/cookbook.md).

| Pair | Becomes | Recipe |
|---|---|---|
| A subtype of the target | itself | — |
| `S → T?`, `S? → T?` | `S → T`, null kept as null | [Optional values](docs/cookbook.md#optional-values) |
| Value class ↔ what it holds | unwrap and wrap, whatever the property is called | [Value class ids](docs/cookbook.md#value-class-ids-and-plain-columns) |
| `List`, `Set`, `Collection`, `Iterable`, `Map`, `Array` | element by element, order kept | [Lists, sets and maps](docs/cookbook.md#lists-sets-and-maps) |
| `object` → `object` | the target instance | — |
| Enum → enum | entry by name | [Enums](docs/cookbook.md#enums-across-layers) |
| Sealed → sealed | case by name, each case by every rule | [Sealed types](docs/cookbook.md#sealed-types) |
| Class → class | the primary constructor: same-named properties, then defaults | [Nested classes](docs/cookbook.md#nested-classes-and-defaults) |

At the edge, `transformIntoPartial` returns every error with its path instead
of refusing what might not fit
([recipe](docs/cookbook.md#validating-at-the-edge)).

An override — `withFieldConst`, `withFieldComputed` or `withFieldRenamed` —
comes before all of these for the field it names, and your own `Transformer`,
passed with `withTransformer`, before all of them for every nested pair it
fits ([recipe](docs/cookbook.md#your-own-transformer-for-a-nested-pair)).

Not yet: fallible user transformers, and generic sealed hierarchies. The
[roadmap](docs/roadmap.md) has the order.

## Trying it

kimney is not published yet. To use it from another project, include it as a
composite build:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../kimney/kimney-gradle-plugin")
}
includeBuild("../kimney")
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney")
}
```

The Gradle plugin adds `kimney-runtime` to every JVM compilation and loads the
compiler plugin into it. kimney is built for exactly one Kotlin version,
2.4.10; on any other, the build stops at configuration and names both.

Inside this repository, [`example/`](example) applies the plugin the same way
a consumer does, and runs every cookbook recipe on each build.

## How it works

A pure derivation engine, `kimney-derive`, turns a source type and a target
type into either a plan or the full list of reasons it cannot. It sees types
only through an interface, which the plugin implements twice: once over the
K2 frontend, so the checker reports errors on the call before any code is
generated, and once over IR, so the lowering generates the plan. Both run the same engine, so the
checker and the code generator cannot disagree about a rule — and tests that
run every code-generation case back through the checker hold them to it.

## Working on it

Nothing is built without a spec: [`specs/`](specs) holds one per change, and
[AGENTS.md](AGENTS.md) is how work is done here — layering, the testing order,
and the gates `./gradlew build` enforces.

## License

[Apache 2.0](LICENSE).
