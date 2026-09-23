# 0006 — Your own transformer, wherever the pair appears

## Problem

Overrides reach top-level fields only. When a nested pair needs one —
`User → UserDto` renames `fullName`, and `Team` holds a `List<User>` — the
only way through today is a `withFieldComputed` on the whole list that maps
it by hand, which is the mapping kimney was meant to write. There is no way to
say "this is how a `User` becomes a `UserDto`" once and have every nested
`User` use it.

## Not doing

- **No context parameters in this spec.** The roadmap chose them; see the
  first open question for why they are proposed as the next spec instead.
- **No global registry** and no annotation-discovered transformers: a
  transformer is a value passed where it is used.
- **No transformers for fallible pairs** (0008).

## Shape

```kotlin
public fun interface Transformer<in A, out B> {
    public fun transform(source: A): B
}

val userToDto = Transformer<User, UserDto> {
    it.into<_, UserDto>().withFieldRenamed(User::fullName, UserDto::name).transform()
}

val dto = team.into<_, TeamDto>()
    .withTransformer(userToDto)      // every User → UserDto below, in fields, elements and cases
    .transform()
```

A transformer is rule 2: tried on every pair at every depth before any other
rule, including identity, so it also covers what no rule can — a
`Transformer<String?, String>` is how a null gets a value. A pair takes the
transformer whose source is a supertype of its source and whose result is a
subtype of its target, as a function would.

```
TeamDto.members[]: UserDto — two transformers fit User → UserDto: withTransformer #1 and #3. Pass one.
```

A transformer the derivation never uses is a warning, `KIMNEY_UNUSED_TRANSFORMER`,
naming its types: it usually means a type changed underneath it.

The lowering evaluates each transformer once, in chain order with the other
overrides, and calls `transform` where the pair appears.

## Why this shape

A transformer is a value because values are what the rest of kimney already
passes: it can be built with kimney itself, tested on its own, and shared
between chains by an ordinary `val`. Variance-based matching is what a
function type already means, so `Transformer<Animal, Dto>` serves a `Dog`
without a second rule. Making an unused transformer loud is the cheap half of
"the error tells you exactly why": a transformer silently skipped because a
type moved is a mapping that no longer does what its author thinks.

## Stack

- [ ] **`spec-0006-runtime`** — `Transformer`, `withTransformer`; BCV.
      Done when: the stubs throw `KimneyNotApplied` naming themselves.
- [ ] **`spec-0006-engine`** — the transformer rule, `AmbiguousTransformer`,
      and the set of transformers a plan uses.
      Done when: engine tests cover a nested field, an element, a sealed case,
      a null handled by a transformer, variance, and ambiguity.
- [ ] **`spec-0006-fir`** — the chain reader takes `withTransformer`; goldens
      for ambiguity and the unused warning.
      Done when: goldens pass for both.
- [ ] **`spec-0006-ir`** — lowering; box and agreement; example and reference.
      Done when: box tests pass, including evaluation order and a transformer
      compiled in another module.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Decisions

- **Explicit `withTransformer` here; context parameters are spec 0011**,
  built on this spec's rule and lowering.
- **An unused transformer is a warning**, `KIMNEY_UNUSED_TRANSFORMER`.
- **A nested pair's first failure offers a transformer** for that pair.
- **Transformers apply below the root only**: the chain itself is the root.
