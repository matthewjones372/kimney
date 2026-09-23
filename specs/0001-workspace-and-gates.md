# 0001 — A workspace the gates can already fail

## Problem

There is no build. Every later spec needs somewhere for the engine, the plugin
and its tests to live, and needs the gates in `AGENTS.md` to be real before the
first rule is written — a gate added after the code is a gate the code was
never held to.

## Not doing

- **No derivation.** No rules, no `TypeModel`, no diagnostics. That is 0002.
- **No publishing.** No Maven Central, no signing, no Dokka. A later spec.
- **No docs site.** `README.md` and `docs/reference.md` exist and say what is
  true today: nothing transforms yet.

## Shape

The five modules from `AGENTS.md`, Kotlin pinned in `gradle.properties`, and a
compiler plugin that loads and does nothing:

```kotlin
// kimney-runtime — the stub every later spec's lowering replaces
public inline fun <reified B> Any?.transformInto(): B =
    throw KimneyNotApplied("transformInto")
```

```kotlin
// example/build.gradle.kts — the consumer path, by id
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.matthewjones372.kimney")
}
```

A build on another Kotlin version fails at configuration with:

```
kimney 0.1.0 is built for Kotlin 2.4.10; this build uses 2.4.20.
```

## Why this shape

`kimney-gradle-plugin` is an included build, as `pelican-gradle-plugin` is,
because a plugin has to be built before the build applying it is configured and
`example` should take the path a user takes. The version refusal is here rather
than later because a mismatched compiler plugin fails with a `NoSuchMethodError`
deep inside the compiler, and the first user to hit it files a bug that is not
ours. A stub that throws, rather than one that returns `TODO()`, gives a user
who forgot the plugin a sentence instead of a stack trace.

## Stack

- [ ] **`spec-0001-build`** — root build, version catalog, `.editorconfig`,
      detekt config, spotless, Kover (90% floor), CI workflow, empty modules.
      Done when: `./gradlew build` is green and a wildcard import fails it.
- [ ] **`spec-0001-runtime`** — `transformInto` stub, `KimneyNotApplied`, BCV
      `.api` file, `NoThirdPartyDependenciesTest` for runtime and derive.
      Done when: adding any dependency to either module fails `check`.
- [ ] **`spec-0001-plugin`** — `CompilerPluginRegistrar` registering empty FIR
      and IR extensions; test harness wired with one box test that passes.
      Done when: a box test compiles through the plugin and returns `"OK"`.
- [ ] **`spec-0001-gradle`** — Gradle plugin applying the compiler plugin,
      Kotlin version refusal, `example` applying it by id.
      Done when: `example` builds, and a functional test on 2.4.20 fails with
      the message above.

## Acceptance

```bash
./gradlew spotlessApply && ./gradlew build
./gradlew :example:run    # prints the KimneyNotApplied message: nothing lowers yet
```

## Open questions

- **Plugin id and group?** Recommend `io.github.matthewjones372.kimney`, which
  Central accepts without owning a domain.
- **Minimum JVM target?** Recommend 17 for the plugin (the compiler's own
  floor) and 11 for `kimney-runtime`, which user code links against.
- **`FunctionalStyleTest` from day one?** Recommend yes, empty allowlist — IR
  transformers are visitor-shaped and will want mutable state, so each one
  should have to argue for it.
- **Does the test harness artifact follow the pinned Kotlin exactly?** It is
  published per compiler version; recommend pinning it to `kotlinVersion` in
  the catalog so a bump moves both.
