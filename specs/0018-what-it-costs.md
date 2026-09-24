# 0018 — What it costs, measured

## Problem

The README says a kimney transformation costs what the hand-written mapping
costs, because it compiles to the same code. Bytecode checks pin that no
lambda is created, but nothing has timed it: the claim that matters most to
someone choosing kimney is the one with no number behind it.

## Not doing

- **No benchmark in `build`.** It runs when asked for, as pelican's do.
- **No comparison with other mapping libraries**: kimney against the code it
  replaces is the claim; others are theirs to measure.

## Shape

A `benchmarks` module, run by JMH the way pelican's is — the bytecode stub
generator, the JDK pinned, `./gradlew :benchmarks:jmh` — with each pair
measured both ways: an order with a list of lines, value-class ids and an enum
into its view; a nested class with a default; and a sealed type. `-prof gc`
reports allocation beside time. `docs/what-it-costs.md` records a run with the
machine and JDK it ran on, and says what the numbers can and cannot show.

## Stack

- [x] **`spec-0018-benchmarks`** — the module, the benchmarks, one recorded
      run and the page.
      Done when: the run's numbers are on the page, taken from its JSON.

## Acceptance

```bash
./gradlew :benchmarks:jmh
```

## Decisions

- Drafted and committed on the maintainer's instruction to keep going with the
  recommended answers: JMH as pelican runs it, kimney against hand-written code
  only, the run recorded with its machine.
