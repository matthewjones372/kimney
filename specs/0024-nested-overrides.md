# 0024 — Overrides that reach nested fields

## Problem

An override names a top-level field of the target and nothing deeper (0003).
Changing one field inside a nested pair, such as `PersonDto.address.zip` or
`OrderDto.customer.tier`, means writing a `Transformer` for the whole nested
pair (0006) and restating everything kimney derived there. This is the request
people make of a mapper most often, and the README lists it under "Not yet".

The engine is most of the way there. Rule 1 already reads "override for this
target path", and `Failure` paths already carry every field from the root. But
`derive` hands overrides to the root pair only, and every nested pair is built
with none.

## Not doing

- **No paths through containers or sealed cases**: `{ it.lines[0].sku }` or
  "every element's `sku`". A path that crosses a `List`, a `Map` or a sealed
  type is a failure that points to `withTransformer`. These need syntax of
  their own, which is a later spec.
- **No nested source paths for `withFieldRenamed`.** A nested target can be
  filled from a nested source value with `withFieldComputed`.
- **No change to the top-level overloads.** `withFieldConst(UserDto::email, …)`
  keeps compiling and means what it does now.

## Shape

A selector is a lambda over the target. It is never run: the plugin reads it as
a chain of properties, and Kotlin type-checks it.

```kotlin
fun Person.toDto(): PersonDto = into<_, PersonDto>()
    .withFieldConst({ it.address.zip }, "N1 9GU")
    .withFieldComputed({ it.address.country }) { it.region.countryCode }   // it: Person, the root source
    .transform()
```

New stubs on `Into<A, B>`, next to the `KProperty1` ones:

```kotlin
public fun <T> withFieldConst(field: (B) -> T, value: T): Into<A, B>
public fun <T> withFieldComputed(field: (B) -> T, compute: (A) -> T): Into<A, B>
```

A selector may take a safe call, `{ it.billing?.zip }`; the override then
applies only when `billing` is not null.

The engine's `Override` carries a path, `List<String>`, in place of `field`.
When the derivation reaches a constructor parameter, it hands the overrides
that start with that parameter's name to the nested pair, with the first step
removed.

A pair that has overrides is always built by its constructor, even when its
source has the target's type, just as the root is now. It is also never shared
with a recursive local function. Everything around the overridden field is
still derived.

New failures, on the call:

```
PersonDto.address.zip — withFieldConst names 'zip', which is not a constructor parameter of AddressDto.
PersonDto.address — the selector must be a chain of properties, like { it.address.zip }.
OrderDto.lines — the selector crosses List<LineDto>; an override cannot reach inside elements. Map LineDto with .withTransformer(…).
PersonDto.address.zip — withFieldConst reaches inside Address → AddressDto, which the transformer addressToDto also maps. Keep one.
```

The existing failure for overriding the same field twice applies to paths too.

## Why this shape

Kotlin has no nested property reference, so the choice is how to spell one.
**A lambda selector**, `{ it.address.zip }`, is the shortest. Kotlin checks it
completely, the plugin already reads lambdas out of the tree for
`withFieldComputed`, and it is how Chimney writes the same thing
(`_.address.zip`). Its cost is a lambda that looks as if it runs and never
does. **A path of references**, `PersonDto::address / AddressDto::zip`, would
need a `div` operator per depth and a separate overload for every nullable step,
and it repeats each class name. Recommended: the lambda.

## Stack

- [x] **`spec-0024-runtime`** ([#5](https://github.com/matthewjones372/kimney/pull/5)): the two selector stubs and the BCV `.api`.
      Done when: each throws `KimneyNotApplied` naming itself.
- [x] **`spec-0024-engine`** ([#6](https://github.com/matthewjones372/kimney/pull/6)): `Override.path`, handing overrides down to nested
      pairs, building a pair that has overrides from its constructor, and the
      container, stray-path and transformer-conflict failures.
      Done when: engine tests cover depths one, two and three, a pair of the
      same type, a nullable step, the stray path, the container crossing and
      the transformer conflict.
- [x] **`spec-0024-fir`** ([#7](https://github.com/matthewjones372/kimney/pull/7)): reading a selector into a path, and the
      not-a-chain failure.
      Done when: diagnostic goldens pass for all three new failures.
- [x] **`spec-0024-ir`** ([#8](https://github.com/matthewjones372/kimney/pull/8)): lowering the overridden field into the nested
      constructor call, keeping 0003's evaluation order; agreement tests; a
      cookbook recipe; the README's "Not yet" line removed.
      Done when: box tests pass at each depth, including evaluation order.

## Acceptance

```bash
./gradlew build
./gradlew :example:test
```

## Decisions

The four questions the draft left open, each settled on its recommended answer:

- **Selector syntax:** a lambda over the target, `{ it.address.zip }`.
- **What `withFieldComputed` receives at depth:** the root source `A`, as
  Chimney does. The matching source path may not exist, which is often the
  reason for the override in the first place.
- **An override inside a pair a transformer also fits:** a failure naming
  both, because silently skipping a transformer the user passed is the
  surprise kimney refuses elsewhere.
- **A nullable step**, such as `{ it.billing?.zip }`: allowed, and the
  override applies only when the value is not null, through the null check
  the derivation already makes.
