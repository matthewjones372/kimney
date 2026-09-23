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

Nothing transforms yet. Every call throws `KimneyNotApplied`, whose message
names the call and how to apply the plugin. Once the plugin derives
transformations (spec 0002), the stub body is only reached in code compiled
without it.
