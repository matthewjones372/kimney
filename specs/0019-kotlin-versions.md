# 0019 — Every Kotlin 2.4

## Problem

0.1.0 applies to one Kotlin, 2.4.10, and stops any other build at
configuration. A project on 2.4.0 or 2.4.20 cannot use it until kimney
releases for that exact version, and nothing says whether the jar would work
there: no test has run it on any compiler but its own.

## Not doing

- **No Kotlin 2.3.** The compiler test framework is a third API there, and a
  plugin compiled against 2.4's compiler is not known to load into 2.3's.
- **No 2.5**, which is at Beta1: the next minor is a kimney release of its own,
  as the compiler plugin API promises nothing across minors.
- **No jar per Kotlin.** One jar, compiled against the catalog's Kotlin, is
  what is published and what is tested on each compiler.

## Shape

`-Pkimney.kotlinUnderTest=2.4.20` runs every compiler test on that Kotlin's
compiler and test framework, with the plugin still compiled against the
catalog's. Two things differ between framework versions, and each has one
answer:

- Its builders were renamed in 2.4.20 (`NonGrouping*Phase*` to `*Stage*`): a
  shim per side, `src/testFixturesPhase` and `src/testFixturesStage`, holding
  two names.
- Its goldens changed form in 2.4.20: `x.diag.txt` for `x.fir.diag.txt`, and
  `line:col` for an offset range. The goldens stay in one form, and a task
  converts a copy for the newer framework, so nothing checked in has a twin.

CI runs the suite on each tested Kotlin: `kimney.kotlinTested` in
`gradle.properties`, which a test holds the workflow's matrix to.

The Gradle plugin accepts any Kotlin with the catalog's major and minor. On a
patch newer than the newest tested it warns and goes on; on another minor it
stops, naming the range:

```
kimney 0.2.0 supports Kotlin 2.4.0 to 2.4.20; this build uses 2.5.0. Use one of those, or a kimney release built for 2.5.
```

## Why this shape

Patch releases keep the compiler plugin API, mostly: running the suite on
2.4.20 found the one place they did not (below). Warning past the newest
tested patch rather than failing keeps a Kotlin patch release from breaking
every kimney user the day it ships, while saying plainly that it is untested.

## Stack

- [x] **`spec-0019-kotlin-range`** — the switch, the shims, the golden
      conversion, the lowering fix, the gate, CI and docs.
      Done when: the suite passes on 2.4.0, 2.4.10 and 2.4.20, and a
      consumer on 2.4.0 and on 2.4.20 compiles and runs a mapping.

## Acceptance

```bash
./gradlew build
./gradlew :kimney-compiler-plugin:test -Pkimney.kotlinUnderTest=2.4.0
./gradlew :kimney-compiler-plugin:test -Pkimney.kotlinUnderTest=2.4.20
```

## Decisions

- Drafted on the maintainer's instruction to fix the Kotlin versions, with the
  recommended answers: 2.4.x only, one jar, a warning past the newest tested
  patch.
- **Found by it:** on 2.4.20, 16 box tests failed IR validation. Constructor
  calls were typed `Box<T>`, the constructor's declared type, not `Box<Int>`;
  `Map.put` and `Partial.Ok.value` likewise. 2.4.10 accepts that IR and 2.4.20
  rejects it as referencing a type parameter out of scope. The lowering now
  types every such call with the concrete type. Forcing `-Xverify-ir=error` on
  2.4.10 does not catch it (its validator lacks the check), so the 2.4.20 run
  is the guard.
