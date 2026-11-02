# 0025 — dev.surefold coordinates and packages

## Problem

kimney lives in the `surefold` GitHub organisation now, but it still publishes as `io.github.matthewjones372`
(`kimney-runtime`, `kimney-derive`, `kimney-compiler-plugin`, the Gradle plugin `io.github.matthewjones372.kimney`), and its sources are in
`io.github.matthewjones372.kimney.*`. The `dev.surefold` namespace is verified on Maven Central, and surefold and pelican
are moving to it (surefold 0277, pelican 0107).

## Not doing

- Not renaming modules or changing what the compiler plugin generates, beyond the package of its own runtime types.
- Not deleting or rewriting anything already on Central.
- Not changing specs' text.

## Shape

```kotlin
// build.gradle.kts in a project using kimney
plugins { id("dev.surefold.kimney") version "0.5.0" }

// sources
import dev.surefold.kimney.KimneyNotApplied
```

`io.github.matthewjones372.kimney` becomes `dev.surefold.kimney`, `group` in `gradle.properties` becomes `dev.surefold`,
and the plugin ID becomes `dev.surefold.kimney`. The compiler plugin's `KIMNEY_PLUGIN_ID` (`KimneyRegistrar.kt`), which
keys its compiler options, becomes `dev.surefold.kimney` too, and the `ClassId`s it resolves for its own runtime and
`kimney-derive` types follow the package.

The first `dev.surefold` release goes out alongside a relocation POM per artifact at `io.github.matthewjones372`, and a
last `io.github.matthewjones372.kimney` Portal version whose description names the new ID.

## Why this shape

The same as surefold's and pelican's: coordinates and packages together, in one breaking minor. kimney has no
dependency on the others, so it can move first or in parallel.

## Stack

- [ ] **`dev-surefold-coordinates`** — `group`, plugin IDs, POM `url`/`scm`, and the package move with the compiler
      plugin's ID and `ClassId`s. About 70 source files, mechanical, by script.
      Done when: `./gradlew build` is green, including the plugin's tests against the runtime, with no
      `io.github.matthewjones372` outside specs.
- [ ] **`dev-surefold-relocation`** — relocation POMs and the Portal pointer; README and docs.
      Done when: a project depending on `io.github.matthewjones372:kimney-runtime:<new>` resolves `dev.surefold:kimney-runtime`.

## Acceptance

```bash
./gradlew build
grep -rn "io.github.matthewjones372" --include=*.kt --include=*.kts . | grep -v '^./specs/'   # empty
```

## Open questions

1. Version. **Answered (owner, 2 Nov 2026):** 0.5.0, the next minor.
2. The compiler plugin keys its options by plugin ID string. A user who upgrades must update the plugin ID in their build
   in the same change. **Answered (owner, 2 Nov 2026):** accept that, and say it in the release notes.
