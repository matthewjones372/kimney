# 0012 — The build was fast; the daemon was full

## Problem

Full builds had been taking about ten minutes. Measured on 2026-09-24, they
do not: a forced rebuild of everything (`./gradlew build --rerun-tasks`) takes
19 seconds on a fresh daemon, the compiler plugin's tests 14.5 of them, and a
cached build one. The slow builds ran on a Gradle daemon 17 hours old whose
metaspace was 99.3% of its 1 GB cap — every compile and every detekt run with
types loads compiler classes into the daemon — and kimney shares that daemon
with pelican, because the two builds ask for identical JVM settings.

Separately, the README, cookbook and reference quote kimney's error messages
by hand; when a message changes — as 0006's transformer hint changed four —
nothing notices the page is stale.

## Not doing

- **No parallel test forks and no CI split.** The first draft of this spec
  proposed both, on the guess that the tests were slow. They are not.
- **No change to what `./gradlew build` means.**

## Shape

- `gradle.properties` gives kimney's daemon `-Xmx3g -XX:MaxMetaspaceSize=2g`,
  with the measurement above beside it. Different settings from pelican's also
  mean kimney stops sharing a daemon with it.
- `./gradlew quickCheck` runs everything but the compiler plugin's tests, for
  the inner loop on engine and docs work.
- `DocsQuoteGoldensTest`: every kimney message quoted in `README.md` or
  `docs/*.md` — a line of a plain code block, or a table cell in backticks,
  that reads `<path> — <reason>` — must appear verbatim in some
  `testData/**/*.diag.txt`. A quote nothing produces fails the build.

## Stack

- [ ] **`spec-0012-daemon`** — the daemon settings and `quickCheck`.
      Done when: `quickCheck` runs without the compiler tests, and the settings
      carry their reason.
- [ ] **`spec-0012-quotes`** — `DocsQuoteGoldensTest`.
      Done when: it passes, and editing one quoted message fails it.

## Acceptance

```bash
./gradlew build
./gradlew quickCheck
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers; rewritten after measuring, which
  removed the two entries the first draft guessed at.
