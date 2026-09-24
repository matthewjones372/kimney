# 0023 — The configuration cache

## Problem

Gradle's configuration cache skips configuration on a build whose inputs have
not changed, and Gradle is moving builds towards it by default. A plugin that
reads project state at execution time, or holds a `Project`, breaks it: the
build fails, or runs without the cache, with a list of problems naming the
plugin. Nothing has checked kimney's Gradle plugin against it, and the Plugin
Portal asks each plugin to declare whether it supports it.

## Not doing

- **No isolated projects.** Gradle's next step past the configuration cache is
  still incubating; it is its own spec when it is stable.
- **No change to what the plugin does**, unless the test finds a problem.

## Shape

A test in `ic-test`, which already builds a consumer from the published
artifacts, runs that consumer with `--configuration-cache` three times:

- the first build stores an entry with no problems;
- the second reuses it;
- after a change to a type a mapping reads, the build reuses the entry and
  still fails with kimney's message, so the plugin is applied from the cache.

With that passing, the plugin declares
`compatibility { features { configurationCache = true } }` for the Portal.

## Why this shape

The consumer build is the only place the claim can be tested: the plugin's own
functional tests have no compiler plugin to load, and a unit test cannot see
what Gradle serializes. Declaring support only after the test passes keeps the
badge a claim with a test behind it.

## Stack

- [x] **`spec-0023-configuration-cache`** — the test, the declaration, the
      README line.
      Done when: the test passes on each tested Kotlin.

## Acceptance

```bash
./gradlew :ic-test:test
./gradlew :ic-test:test -Pkimney.kotlinUnderTest=2.4.0
./gradlew spotlessApply && ./gradlew build
```

## Decisions

- Drafted and built on the maintainer's instruction to keep going: the
  consumer test first, then the declaration.
- **Found by it:** nothing to change. The plugin passed on 2.4.0, 2.4.10 and
  2.4.20 as it was.
- **The declaration needs an import** in a Kotlin build script,
  `org.gradle.plugin.compatibility.compatibility`, which the Portal's snippet
  leaves out.
