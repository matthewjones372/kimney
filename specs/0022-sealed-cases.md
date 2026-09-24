# 0022 — A sealed case the target does not have

## Problem

Sealed hierarchies map case by name, as enums map entry by name, and have the
same dead end:

```
ShapeDto — Shape.Hexagon has no subclass of the same name in ShapeDto.
```

The shapes are the same as 0021's: a case the other layer calls something
else (`Shape.Hexagon` as `ShapeDto.Polygon`), and a target with a catch-all
(`data object Unsupported`) for everything it does not model. A third is
particular to sealed types: a case that needs building differently, from its
own fields, into whichever target case fits. Today every one of them means a
`withTransformer` for the whole hierarchy, every arm written by hand.

## Not doing

- **No fallback that is a class.** A fallback is an object: a class case needs
  arguments, and building one from the source case is the third tool below.
- **No enum ↔ sealed renames.** An enum entry into a sealed object case (0015's
  territory) keeps matching by name only.
- **No nested case paths** (`Shape.Polygon.Hexagon` into `ShapeDto.Hexagon` by
  skipping a level): cases are the direct subclasses, as 0015 reads them.

## Shape

The two calls of 0021, for sealed types, and one change to how a case's
transformer is matched:

```kotlin
public fun <S : Any, T : Any> withSealedCaseRenamed(from: KClass<S>, to: KClass<T>): Into<A, B>
public fun <T : Any> withSealedFallback(to: T): Into<A, B>

// Hexagon → Polygon, derived by every rule; the other cases by name.
fun Shape.toDto(): ShapeDto = into<_, ShapeDto>()
    .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.Polygon::class)
    .transform()

// Every unmatched case becomes one object.
fun Event.toMessage(): Message = into<_, Message>()
    .withSealedFallback(Message.Unsupported)
    .transform()

// A case built by hand into whichever target case fits.
val hexagon = Transformer<Shape.Hexagon, ShapeDto> { ShapeDto.Polygon(sides = 6, side = it.side) }
shape.into<_, ShapeDto>().withTransformer(hexagon).transform()
```

- **A renamed case derives like any other arm**: `Hexagon → Polygon` by every
  rule, its fields reported with their paths if they do not fit.
- **A fallback is an object**, one of the target's cases, and the argument must
  name it: `Message.Unsupported`, not a variable. A class or a value from
  elsewhere fails with the reason.
- **A transformer from a source case into the target parent handles that
  case.** Today such a transformer is never tried, because the arm fails as a
  missing case first; now a case with no same-named target takes a
  transformer whose source fits the case and whose result fits the target
  parent, before the fallback.
- **Precedence per case:** a rename, then a same-named case, then a
  transformer into the parent, then the fallback, then the missing-case error.
- **Mistakes are named** as in 0021: two renames of one case or two fallbacks
  for one target type is `DuplicateOverride`; one whose types never meet is the
  unused warning.
- **The missing-case error says all three ways out:**

  ```
  ShapeDto — Shape.Hexagon has no subclass of the same name in ShapeDto. Map it with .withSealedCaseRenamed(Shape.Hexagon::class, ShapeDto.….class), build it with .withTransformer(Transformer<Shape.Hexagon, ShapeDto> { … }), or send every unmatched case to one object with .withSealedFallback(ShapeDto.…).
  ```

The fallback is also the `else` of the lowered `when`, as 0021's is: a case
compiled in after the call becomes it rather than throwing. Without one, a
case added to the source is a compile error at the next build.

## Why this shape

Class literals for cases, as entries for enums, keep the call checked and
renamable. The same names as 0021, with `Sealed` for `Enum`, so one reading of
the reference covers both. A fallback is limited to objects because an object
is the only case kimney can produce without the source's data; anything with
fields has a better answer in the transformer, which gains no new API, only
the match it should always have had.

## Stack

- [x] **`spec-0022-runtime`** — `withSealedCaseRenamed`, `withSealedFallback`;
      BCV.
      Done when: the stubs throw `KimneyNotApplied` naming themselves.
- [x] **`spec-0022-engine`** — renames, the transformer into the parent and the
      fallback in the sealed rule, in that order; the failures and the hint.
      Done when: engine tests cover each, the precedence between them, a
      nested pair, duplicates and unused ones.
- [x] **`spec-0022-plugin`** — both chain readers; the lowering; goldens, box,
      agreement; cookbook and reference.
      Done when: box tests pass for a renamed case with fields, a fallback,
      a case transformer, and all three in one hierarchy.

## Acceptance

```bash
./gradlew spotlessApply && ./gradlew build
./gradlew :kimney-compiler-plugin:test -Pkimney.kotlinUnderTest=2.4.20
```

## Decisions

Drafted and committed on the maintainer's go-ahead with the recommended
answers:

- **Built after 0021**, on its plan change and chain reading.
- **A fallback is an object only**; a defaulted `data class` is left to a
  transformer.
- **A case transformer into the parent is tried only when no same-named case
  exists**, so no existing derivation changes.
- **The fallback is also the `else`**, as in 0021 (decided while building,
  for the same reason).
- **Whether a fallback is an object is the engine's check.** The checker only
  requires it written as a qualifier; one that is not an object case of the
  target fits nothing and is the unused warning. Reading the class kind in FIR
  meant `FirResolvedQualifier.symbol`, whose return type changed in Kotlin
  2.4.20 (`NoSuchMethodError` there, found by the Kotlin matrix).
- **Its own warning**, `KIMNEY_UNUSED_SEALED_MAPPING`, beside 0021's.
- **A case into its sealed parent (0015) keeps its message** without the
  three offers: the mappings serve sealed-to-sealed pairs.
