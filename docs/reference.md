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
   it is.
2. **Constructor.** A final or open Kotlin class outside the standard library
   is built with its public primary constructor. Each parameter takes, in
   order: the source's public property of the same name, transformed by these
   same rules; else the parameter's default value; else it is a failure.
   Source properties the target does not ask for are ignored. A generic target
   is built with its type arguments substituted.

Anything else is a compile error on the call, naming every field that could
not be filled and the path to it:

```
e: Main.kt:15:13 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, or give UserDto.email a default value.
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip a default value.
```

The other failures:

| Failure | Says |
|---|---|
| No public primary constructor | `Hidden — Hidden has no public primary constructor: it is private.` |
| No rule for the pair | `OrderDto.count: Long — no rule transforms Int into Long.` |
| A type containing itself | `TreeDto.child: TreeDto — Tree → TreeDto contains itself, and recursive types are not supported yet.` |

Not yet: nullable to non-null, value classes, collections, enums, sealed
types, overrides and recursive types. `docs/roadmap.md` has the order.

Compiled without the plugin, the call throws `KimneyNotApplied`, whose message
says how to apply it.
