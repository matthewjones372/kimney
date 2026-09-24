# 0017 — Mutable collections, on either side

## Problem

A source property typed `MutableList<Line>` is not a container to kimney: only
the read-only interfaces are, so `MutableList<Line> → List<LineDto>` is `no
rule`, and so is any mutable target. Entities and builders often hold mutable
collections, and a DTO that is later appended to wants a mutable one.

## Not doing

- **No concrete targets** (`ArrayList`, `HashMap`): the target stays an
  interface, as `map` returns one.
- **No crossing kinds**, still: `MutableSet` into `MutableList` is refused as
  `Set` into `List` is.

## Shape

`MutableList`, `MutableSet`, `MutableCollection`, `MutableIterable` and
`MutableMap` become containers. As a source, each behaves as its read-only
kind. As a target, each accepts the sources its read-only kind does — a
`List` or a `MutableList` into a `MutableList` — and gets a new collection:
the `ArrayList`, `LinkedHashSet` or `LinkedHashMap` the lowering already
builds, which is mutable, so no lowering changes.

```kotlin
data class Cart(val lines: MutableList<Line>)
data class CartDto(val lines: MutableList<LineDto>)

cart.transformInto<CartDto>()   // a new ArrayList, never the source's own list
```

## Stack

- [ ] **`spec-0017-mutable`** — the kinds in the engine and both adapters; box,
      agreement and a golden; the reference.
      Done when: box tests pass for a mutable source, a mutable target, and a
      target list that is not the source's list; a golden pins a crossing.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to keep going with the
  recommended answers: interfaces only, always a new collection.
