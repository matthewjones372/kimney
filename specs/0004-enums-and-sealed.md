# 0004 — Every case, or the one that is missing

## Problem

A status enum or a sealed result type cannot cross a layer today: the target
enum has no primary constructor and a sealed class is abstract, so both are
`no rule`. The hand-written replacement is a `when` per pair, repeated for
every enum and every sealed type, and it catches a newly added source case
only for as long as nobody has written `else`.

## Not doing

- **No case overrides** (Chimney's `withCoproductInstance`). A missing case
  is fixed by adding it; an override for it is a later spec.
- **No generic sealed hierarchies** (`sealed interface Result<out T>`). Their
  cases need type arguments inferred per subclass; they stay `no rule`.
- **No enum ↔ sealed crossings**, no mapping by ordinal, no renaming rules.
- **No nullable enums** (`Status? → StatusDto?`): that is 0005.

## Shape

```kotlin
enum class Status { ACTIVE, SUSPENDED }
enum class StatusDto { ACTIVE, SUSPENDED, UNKNOWN }

sealed interface Shape {
    data class Circle(val radius: Double) : Shape
    data class Square(val side: Double) : Shape
    data object Empty : Shape
}
sealed interface ShapeDto {
    data class Circle(val radius: Double) : ShapeDto
    data class Square(val side: Double, val unit: String = "cm") : ShapeDto
    data object Empty : ShapeDto
}

status.transformInto<StatusDto>()   // when (status) { ACTIVE -> StatusDto.ACTIVE; … }
shape.transformInto<ShapeDto>()     // when (shape) { is Circle -> ShapeDto.Circle(shape.radius); … }
```

Every source case must have a target case of the same simple name; extra
target cases are fine. Each sealed case pair is derived by all the rules, so
`Square` gets its default and a nested failure has a path through the case:

```
StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.
ShapeDto — Shape.Hexagon has no subclass of the same name in ShapeDto.
ShapeDto.Circle.radius: Double — no rule transforms Int into Double.
```

`object` → `object` becomes a rule of its own, in any position: the target
instance, with nothing read from the source.

## Why this shape

Matching by name at compile time is the only way the source's exhaustiveness
reaches the target: a case added to either side changes kimney's answer, and
an added source case is a compile error at every transformation that meets it.
Direct subclasses only, with each pair going back through the rules, handles
nested sealed hierarchies without a second matching scheme. The lowering is a
`when` with a throwing `else`, the same code an exhaustive hand-written `when`
compiles to, so a case added to a source compiled in another module fails the
same way it would by hand.

## Stack

- [x] **`spec-0004-engine`** — `TypeModel` learns enum entries, sealed cases
      and objects; the enum, sealed and object rules; `MissingCase`.
      Done when: engine tests cover each rule and a nested case failure's path.
- [x] **`spec-0004-fir`** — FIR adapter for the three; goldens.
      Done when: goldens pass for missing entries, missing subclasses and a
      nested failure inside a case.
- [x] **`spec-0004-ir-enum`** — enum and object lowering; box and agreement.
      Done when: box tests pass, including an enum from another module.
- [x] **`spec-0004-ir-sealed`** — sealed lowering; example and reference.
      Done when: box tests pass, including a nested sealed hierarchy.

## Acceptance

```bash
./gradlew build
./gradlew :example:run
```

## Decisions

- **Enum lowering** compares entries by identity; no ordinal switch.
- **Coverage debt goes first**, as spec 0009, built before this one.
- **`object` targets** come from `object` sources only.
