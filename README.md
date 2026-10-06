<div align="center">

# kimney

**Compile-time, type-safe transformations for Kotlin, inspired by Scala [Chimney](https://github.com/scalalandio/chimney).**
You say which type to turn into which, and the compiler writes the mapping or
tells you why it cannot.

[![Kotlin 2.4](https://img.shields.io/badge/Kotlin-2.4.0–2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![K2 compiler plugin](https://img.shields.io/badge/K2-compiler%20plugin-7F52FF)](docs/reference.md)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.matthewjones372/kimney-runtime)](https://central.sonatype.com/artifact/io.github.matthewjones372/kimney-runtime)
[![status: 0.x](https://img.shields.io/badge/status-0.x-orange)](docs/roadmap.md)
[![Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

[A first look](#a-first-look) · [When it cannot](#when-it-cannot) ·
[What it covers](#what-it-covers) · [Trying it](#trying-it) · [Why](docs/why.md) ·
[Cookbook](docs/cookbook.md) · [Reference](docs/reference.md) ·
[What it costs](docs/what-it-costs.md) · [Roadmap](docs/roadmap.md)

</div>

---

kimney is a K2 compiler plugin for the mapping code that layered Kotlin
services tend to carry: domain to DTO, row to entity, event to message.

You write the call. At compile time kimney works out the constructor calls,
nested mappings, enum and sealed `when`s and collection loops, and generates
that code. There is no reflection, no runtime mapping table and no annotation
processor. The generated code is the same as what you would write by hand,
and [what it costs](docs/what-it-costs.md) has the measurements.

When it cannot derive a mapping, the call does not compile. The error names
each field it could not fill, the path to it, and what would fix it.

## Why

Layered and domain-driven code often gives one idea several shapes on purpose:
a request, a command, an aggregate, an event, a row, a view. Each shape says
which layer it belongs to. Writing the mappings between them is tedious, and
that cost is often why layers end up merged. With kimney each mapping is one
line, derived again on every build, so it follows the types as they change and
fails the build when it cannot. [docs/why.md](docs/why.md) takes one order
through six shapes with five one-line mappings, and shows two bugs that a
hand-written mapper keeps compiling through.

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
derived the same way, at any depth.

Where names or values differ, you add overrides to the call. Each override is
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

If kimney cannot derive a mapping, the call is a compile error. The error comes
from the K2 frontend checker (the same phase the IDE runs for analysis), not
from code generation, and it lists all the problems at once:

```
e: Main.kt:12:5 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, give UserDto.email a default value, or add .withFieldConst(UserDto::email, …).
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, give AddressDto.zip a default value, or add .withFieldConst({ it.address.zip }, …). Or map Address → AddressDto with .withTransformer(Transformer<Address, AddressDto> { … }).
```

It also refuses conversions that would silently lose information, and says
what to write instead:

```
StrictDto.name: String — User.name is String?, and a null has nowhere to go. Make StrictDto.name nullable, or fill it with .withFieldComputed(StrictDto::name) { … }.
StrictOrder.tags: List<TagDto> — a Set is not turned into a List. Fill it with .withFieldComputed(StrictOrder::tags) { … }.
StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.
```

## What it covers

For each pair of types, kimney tries these rules in order and uses the first
that applies. Each has a recipe in the [cookbook](docs/cookbook.md).

| Pair | Becomes | Recipe |
|---|---|---|
| A subtype of the target | itself | |
| `S → T?`, `S? → T?` | `S → T`, null kept as null | [Optional values](docs/cookbook.md#optional-values) |
| Value class ↔ what it holds | unwrap and wrap, whatever the property is called | [Value class ids](docs/cookbook.md#value-class-ids-and-plain-columns) |
| `List`, `Set`, `Collection`, `Iterable`, `Map`, `Array`, and their mutable kinds | element by element, order kept, always a new mutable collection for a mutable target | [Lists, sets and maps](docs/cookbook.md#lists-sets-and-maps) |
| `object` → `object` | the target instance | |
| Enum → enum | entry by name | [Enums](docs/cookbook.md#enums-across-layers) |
| Sealed → sealed | case by name, each case by every rule | [Sealed types](docs/cookbook.md#sealed-types) |
| Class → class | the primary constructor: same-named properties, then defaults | [Nested classes](docs/cookbook.md#nested-classes-and-defaults) |

At the edge of a system, `transformIntoPartial` returns every error with its
path instead of refusing at compile time what might not fit
([recipe](docs/cookbook.md#validating-at-the-edge)).

An override (`withFieldConst`, `withFieldComputed` or `withFieldRenamed`) takes
priority over all of these rules for the field it names, at any depth
([recipe](docs/cookbook.md#a-field-inside-a-nested-class)). Your own
`Transformer`, passed with `withTransformer`, takes priority over all of them
for every nested pair it fits
([recipe](docs/cookbook.md#your-own-transformer-for-a-nested-pair)).

## Trying it

kimney is on Maven Central, including the Gradle plugin and its marker. The
plugin is not on the Gradle Plugin Portal, so `pluginManagement` needs to list
Central:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney") version "0.3.0"
}
```

**From Maven Local**, to try a change before it is released: run
`./gradlew publishToMavenLocal` in this repository and put `mavenLocal()` first
in both repository lists above, with the `-SNAPSHOT` version from
`gradle.properties`.

**As a composite build**, to work on kimney and another project together:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../kimney/kimney-gradle-plugin")
}
includeBuild("../kimney")
```

In each case the Gradle plugin adds `kimney-runtime` to every JVM compilation
and loads the compiler plugin into it. Both setups have been checked from a
separate project. The next release, 0.4.0, adds support for Gradle's
configuration cache: a consumer build is stored, reused, and still derives
mappings on every tested Kotlin version. Until it is on Central, use a
`-SNAPSHOT` from Maven Local to try it.

In this repository, [`example/`](example) applies the plugin the same way a
consumer would, and runs every cookbook recipe on each build.

### In the editor

IntelliJ's K2 mode only loads bundled compiler plugins by default. Go to
Help → Find Action → Registry, uncheck
`kotlin.k2.only.bundled.compiler.plugins.enabled`, and restart. kimney's errors
then show on the call as you type. The setting is per IDE, so each developer
needs to set it once.

### Kotlin versions

A compiler plugin runs inside the compiler, and the compiler's plugin API can
change between minor versions, so each kimney release lists the Kotlin versions
it supports. From 0.2.0 that is any Kotlin 2.4: CI runs every compiler test on
2.4.0, 2.4.10 and 2.4.20, and a newer 2.4 patch release is applied with a
warning that it is untested. Any other minor version stops the build at
configuration time with a message naming the supported range. 0.1.0 supports
2.4.10 only.

### Known limitations
- **JVM only.** The Gradle plugin applies to JVM compilations.
- **No overrides inside collection elements or sealed cases.** A transformer for the pair covers them.

### Releasing

The build publishes to the Central Portal with the vanniktech plugin and leaves
the upload staged for a manual release. With `mavenCentralUsername`,
`mavenCentralPassword` and a signing key in `~/.gradle/gradle.properties`, set
a release `version` in `gradle.properties` and run both commands below. The
Gradle plugin is a separate build, so it needs its own:

```bash
./gradlew publishToMavenCentral
```

```bash
./gradlew -p kimney-gradle-plugin publishToMavenCentral
```

The plugin marker is published to Central as well, so a consumer resolves the
plugin by id with `mavenCentral()` in `pluginManagement.repositories`.
Publishing to the Gradle Plugin Portal is not set up.

## How it works

The derivation engine, `kimney-derive`, is a pure function: it takes a source
type and a target type and returns either a plan or the full list of reasons
it cannot make one. It sees types only through an interface, which the
plugin implements twice: once over the K2 frontend, so the checker reports
errors on the call before any code is generated, and once over IR, so the
lowering can generate the plan. Because both use the same engine, the checker
and the code generator agree on the rules, and tests run every code-generation
case back through the checker to confirm it.

## Working on it

Each change starts with a spec: [`specs/`](specs) holds one per change, and
[AGENTS.md](AGENTS.md) describes how work is done here, including the layering,
the testing order, and the checks `./gradlew build` enforces.

## License

[Apache 2.0](LICENSE).
