# 0010 — Every element, the same way

## Problem

`List<Address> → List<AddressDto>` is `no rule`: a `List` is an interface,
so nothing constructs it, and the one field holding a list of nested objects
sends the whole mapping back to hand-written code — usually a `.map { }` with
a hand-written mapping inside it, which is the thing kimney removes everywhere
else. The same goes for `Set`, `Map` and arrays.

## Not doing

- **No crossing kinds.** `List → Set` deduplicates and `Set → List` picks an
  order; both are decisions the user writes with `withFieldComputed`.
- **No mutable targets** (`MutableList`, `ArrayList`): the target is the
  read-only interface, as `map` returns one.
- **No primitive arrays** beyond identity (`IntArray → LongArray` is `no rule`).
- **No `Sequence`, `Flow` or Java collections** other than through identity.

## Shape

```kotlin
data class Order(val lines: List<Line>, val tags: Set<Tag>, val prices: Map<UserId, Money>)
data class OrderDto(val lines: List<LineDto>, val tags: Set<TagDto>, val prices: Map<Long, MoneyDto>)

order.transformInto<OrderDto>()
```

A container pair derives its element pair by every rule, so elements can be
optional, enums, sealed or value classes. The target kind is the source's or a
read-only supertype of it:

| Source | Target |
|---|---|
| `List` | `List`, `Collection`, `Iterable` |
| `Set` | `Set`, `Collection`, `Iterable` |
| `Collection` | `Collection`, `Iterable` |
| `Iterable` | `Iterable` |
| `Map` | `Map` |
| `Array<A>` | `Array<B>` |

A failure inside an element has `[]` in its path, and a crossing says why:

```
OrderDto.lines[].sku: String — Line has no property 'sku'. Add it to Line, or give LineDto.sku a default value.
OrderDto.tags: List<TagDto> — a Set is not turned into a List. Fill it with .withFieldComputed(OrderDto::tags) { … }.
```

The lowering is the loop `map` compiles to: one collection sized to the
source where the size is known, filled in the source's order — an `ArrayList`
for lists, `LinkedHashSet` and `LinkedHashMap` for sets and maps, an array for
an array.

## Why this shape

Deriving the element through every rule means containers need no rules of
their own beyond "one element at a time". Refusing crossings keeps the one
thing a user can get wrong — a silent dedupe, an arbitrary order — as an error
with a named fix instead. A loop rather than a call to `map`, because `map`
is an inline function taking a lambda, and building a lambda in IR for the
inliner to take apart again is more machinery than the loop it becomes.

## Stack

- [ ] **`spec-0010-engine`** — `TypeModel` learns container kinds and element
      types; the container rule; `ContainerMismatch`.
      Done when: engine tests cover each allowed kind, a crossing, a map, and
      an element failure's path.
- [ ] **`spec-0010-fir`** — FIR adapter; goldens.
      Done when: goldens pass for an element failure, a crossing and a key.
- [ ] **`spec-0010-ir-iterables`** — lists, sets, collections, iterables and
      arrays; box and agreement.
      Done when: box tests pass, including order preserved in a set and an
      element that is itself a list.
- [ ] **`spec-0010-ir-maps`** — maps; example and reference.
      Done when: box tests pass, including insertion order preserved.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Decisions

- **Map keys** transform only by identity or value class wrap and unwrap;
  any other key pair is a failure naming the collision it could cause.
- **The lowering is a loop**, with a bytecode check that no lambda is created.
- **`Iterable` is included**, growing its list as it fills.
