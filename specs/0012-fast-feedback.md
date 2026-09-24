# 0012 — Faster answers, and docs that cannot quote a stale error

## Problem

A full `./gradlew build` now takes about ten minutes locally, almost all of it
the compiler plugin's generated test classes running one after another in a
single JVM. CI runs that same build once per JDK, so a one-line engine change
waits behind every box test. Separately, the README, cookbook and reference
quote kimney's error messages by hand; when a message changes — as 0006's
transformer hint changed four — nothing notices the page is stale.

## Not doing

- **No test deletion or sampling.** Every test still runs on every build.
- **No change to what `./gradlew build` means**: it still runs every gate.
- **No new CI provider** and nothing that needs a secret.

## Shape

- The compiler plugin's test task forks one JVM per generated class, up to
  half the machine's cores. The framework's test classes share nothing
  between them, so they parallelise without changes.
- CI splits into two jobs that run side by side: `engine` (runtime, derive,
  the Gradle plugin, the example) and `compiler` (the compiler plugin's tests),
  each on JDK 21 and 25. A Kover job merges their reports and applies the floor.
- `./gradlew quickCheck` runs everything but the compiler plugin's tests, for
  the inner loop on engine and docs work.
- `DocsQuoteGoldensTest`: every kimney message quoted in `README.md`,
  `docs/*.md` — a line in a plain code block, or a table cell in backticks,
  that reads `<path>… — <reason>` — must appear verbatim in some
  `testData/**/*.diag.txt`. A quote nothing produces fails the build.

## Why this shape

Forking per class is the cheapest real speed-up: the framework already
isolates each test, so the only cost is JVM start-up, which the box tests
dwarf. Splitting CI by module rather than by test keeps each job's failure
readable. Checking quotes against goldens rather than regenerating the docs
keeps the docs hand-written — they choose which messages to show — while
making each one a claim a test holds.

## Stack

- [ ] **`spec-0012-parallel`** — forks for the compiler tests; `quickCheck`.
      Done when: a full build is measurably faster, quoted before and after.
- [ ] **`spec-0012-ci`** — the split workflow and the merged coverage job.
      Done when: the workflow validates, and each job runs the tasks it names.
- [ ] **`spec-0012-quotes`** — `DocsQuoteGoldensTest`.
      Done when: it passes, and editing one quoted message fails it.

## Acceptance

```bash
./gradlew build
./gradlew quickCheck
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers; forks at half the cores, `quickCheck` as
  the name, and quotes matched verbatim.
