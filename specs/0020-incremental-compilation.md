# 0020 — Mappings recompile when the types they read change

## Problem

Under Kotlin incremental compilation a mapping that no longer derives can
compile green. In `matthewjones372/petshop` (PR #7), `api/Dtos.kt` holds
`fun Pet.toDto(): PetDto = transformInto()`, with `Pet.species: Species` from
module `domain` mapped to `SpeciesDto`. Adding `Rabbit` to `Species` and running
`./gradlew build` passes: `:api:compileKotlin` runs but leaves `Dtos.kt` alone,
because the file never names `Species` and `Pet`'s ABI did not change. Only
`--rerun` or a clean build reports `Species.Rabbit has no entry of the same name
in SpeciesDto`.

Kotlin IC recompiles a file only when a symbol that file looked up changes. The
FIR checker walks from the root pair into nested types through `FirTypeModel`
and records none of what it reads, so a user's build is wrong until the next
clean one. Every nested rule is exposed: enum entries, sealed cases, constructor
parameters, property types.

## Not doing

- **No change to the engine.** `kimney-derive` stays compiler-free; recording is
  the FIR adapter's job, as it is the only side that sees a lookup tracker.
- **No recording in the IR adapter.** IC dirties whole files, and the checker
  reads every type the lowering does, on the same file.
- **No IC for the JS, Native or Wasm backends** beyond what the same FIR
  recording gives them for free; only JVM is tested.
- **No new diagnostic.** Nothing a user sees changes except that the error
  above appears on the incremental build.

## Shape

`FirTypeModel` takes a recorder along with the session. Every class it resolves
to read something is recorded against the checked call's file:

- the class itself — `recordClassLikeLookup(classId, source, fileSource)`,
  which covers signature changes: supertypes, modality, sealed subclass list;
- each member it reads by name — `recordClassMemberLookup(name, classId, …)`:
  properties (`property`), constructor parameters (`<init>`, from
  `construction` and `valueClass`), enum entries (`enumEntries`), sealed
  inheritors (`sealedCases`, recorded on the parent and on each case).

```kotlin
val model = FirTypeModel(context.session, Lookups(context.session.lookupTracker, call.source, context.containingFileSymbol.source))
```

`session.lookupTracker` is null outside an incremental build, so the recorder
is a no-op there and plain compiles pay nothing.

**Kotlin 2.4 API used:** `org.jetbrains.kotlin.fir.lookupTracker`
(`FirSession.lookupTracker: FirLookupTrackerComponent?`) and its extensions
`recordClassLikeLookup(ClassId, KtSourceElement?, KtSourceElement?)` and
`recordClassMemberLookup(String, ClassId, KtSourceElement?, KtSourceElement?)`
in `FirLookupTrackerComponentKt`. Checked present with the same JVM signatures
in 2.4.0, 2.4.10 and 2.4.20.

## Why this shape

Recording in the adapter, at the point each class is resolved, means a rule
added to the engine later is covered without anyone remembering to record for
it: the engine can only see a class through `TypeModel`. The alternative —
walking the finished `Plan` after derivation and recording what it names —
misses failed derivations (whose fix must also trigger a recompile) and every
class that was read but not used, such as a sealed case the target lacked.
Recommended: record in the adapter.

## Stack

- [ ] **`spec-0020-ic-test`** — a Gradle TestKit test in a new `ic-test`
      project: publishes runtime, compiler plugin and Gradle plugin to a
      build-local repository, writes the petshop shape (`domain` + `api`),
      builds, adds `Rabbit`, builds again, expects the error; plus the same
      for a sealed case, a constructor parameter and a property type change.
      Done when: enabled on `main`, the enum case fails for the reason in Problem.
- [ ] **`spec-0020-lookups`** — the recorder in `FirTypeModel` and the checker.
      Done when: every `ic-test` case passes, on each tested Kotlin.

## Acceptance

```bash
./gradlew :ic-test:test
./gradlew :ic-test:test -Pkimney.kotlinUnderTest=2.4.0
./gradlew spotlessApply && ./gradlew build
```

## Decisions

Drafted from the petshop report; the maintainer said to continue on the
recommended answers:

- **Enum entries:** record the class and each member read; the enum case in
  `ic-test` settles whether that is enough.
- **`ic-test` runs under `check`**, so in `./gradlew build`, and in the CI Kotlin
  matrix with the nested build on `-Pkimney.kotlinUnderTest`.
- **Two PRs:** the harness first, with the failing cases `@Disabled` naming the
  second.
- **Typealiases:** the alias's `ClassId` is recorded too.
