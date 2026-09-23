# 0009 — The paths nothing takes

## Problem

Total coverage is 90.5% against a floor of 90, and what is uncovered is the
code that exists for when kimney itself is wrong: the internal-error guard in
both adapters, the IDE-cancellation check, and the lowering's report when it
cannot build a call the checker accepted. None of it is tested, and the last
is also wrong for a user: it goes to `MessageCollector` with no file or line,
and the test framework cannot see it at all. Two real behaviours are also
untested: a type-parameter target (`fun <X> f(a: A): X = a.transformInto()`)
and a user's own extension on `Into` inside a chain.

Built before 0004, which adds more adapter code of the same shape.

## Not doing

- **No floor change** and no Kover exclusions for "unreachable" code.
- **No new rules** and no change to any message a user already sees.

## Shape

One guard, shared by both adapters, with the control-flow rethrow inside it:

```kotlin
fun <R> guarded(fallback: () -> R, report: (String) -> Unit, block: () -> R): R
```

The lowering reports through `pluginContext.diagnosticReporter` at the call,
as `KIMNEY_INTERNAL_ERROR`, so a user gets `e: Main.kt:12:5 kimney's checker
accepted a call its lowering cannot build…` rather than a bare line.

A lowering-only runner registers the IR extension without the checker, so the
disagreement path is reachable on purpose, and its goldens pin the text:

```
testData/loweringOnly/noRule.kt          // Int → String reaches the lowering
testData/loweringOnly/noRule.ir.diag.txt
```

## Why this shape

The alternative is excluding these lines from coverage as unreachable, which
AGENTS.md rules out and which would leave the disagreement message — the one a
user sees when kimney has a bug — never read by anyone. Turning the checker off
is the honest way to reach it: it is exactly the condition the path exists for.

## Stack

- [ ] **`spec-0009-guard`** — `guarded` with unit tests for the report, the
      fallback and the cancellation rethrow; both adapters use it.
      Done when: the unit tests pass and neither adapter has its own `catch`.
- [ ] **`spec-0009-ir-diagnostics`** — the lowering reports positioned
      diagnostics; the lowering-only runner and its goldens; the two untested
      behaviours get diagnostic cases.
      Done when: goldens pin the disagreement text with a position, and total
      coverage is above 92%.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to go with the
  recommendation in spec 0004 that this come first.
