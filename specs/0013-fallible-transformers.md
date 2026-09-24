# 0013 — Your own transformer, when it can fail

## Problem

0008 made a transformation that may fail collect its errors, but only for the
failures kimney itself can see: a null, a constructor's `require`. Parsing is
the rest of the edge — a `String` into a `LocalDate`, an ISO code into a
`Currency` — and today it needs a `withFieldComputed` that either throws or
hides the failure, on every field that needs it.

## Not doing

- **No partial overrides** (`withFieldComputedPartial`): a partial transformer
  for the pair covers the same ground once.
- **No partial transformer in a total transformation.** One that would fit is
  a compile error saying to use the partial call, never a silent throw.

## Shape

```kotlin
public fun interface PartialTransformer<in A, out B> {
    public fun transform(source: A): Partial<B>
}

val isoDate = PartialTransformer<String, LocalDate> { text ->
    runCatching { LocalDate.parse(text) }
        .fold({ Partial.Ok(it) }, { Partial.Errors(listOf(PartialError("", "is not a date"))) })
}

form.into<_, Booking>().withPartialTransformer(isoDate).transformPartial()
// Errors([PartialError("Booking.checkIn", "is not a date")])
```

A partial transformer is matched as 0006's are — every pair below the root,
before any other rule, by variance — and from a context parameter as 0011's
are, but only in a partial transformation. Its errors are re-rooted where its
value sits: an error at `""` lands at the pair's path, and one at
`"Booking.checkIn"` from a nested kimney call lands at the pair's path with
its own root replaced — `Trip.outbound.checkIn`. `Partial.Errors.relocatedTo`
does that, and is public for anyone composing validations by hand.

In a total transformation a partial transformer that fits is a failure:

```
Booking.checkIn: LocalDate — the transformer that fits String → LocalDate can fail. End the chain with .transformPartial().
```

## Why this shape

A separate interface, not a `Transformer<A, Partial<B>>`, because the two mean
different things to the engine: one fills a pair, the other may leave it
failed. Re-rooting keeps every path in one result written from the same root,
however many transformers built it.

## Stack

- [ ] **`spec-0013-runtime`** — `PartialTransformer`, `withPartialTransformer`,
      `relocatedTo`; BCV.
      Done when: `relocatedTo` has tests for an empty, a rooted and a bare path.
- [ ] **`spec-0013-engine`** — partial transformers in the rule; the failure in
      total mode.
      Done when: engine tests cover both modes and a mix of both kinds.
- [ ] **`spec-0013-plugin`** — both adapters, context parameters included;
      box, agreement; cookbook and reference.
      Done when: box tests pass for a parse failure's path, a nested kimney
      call's paths, and an `Ok`.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to continue with the
  recommended answers: its own interface, partial calls only, errors
  re-rooted, and no partial overrides.
