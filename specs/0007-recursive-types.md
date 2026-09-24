# 0007 — Types that contain themselves

## Problem

`Tree(val label: String, val children: List<Tree>) → TreeDto` is a compile
error today: the engine meets `Tree → TreeDto` again inside itself and stops,
because a plan that expands a pair inside itself never ends. Comment threads,
category trees, org charts and ASTs all have this shape, and each one sends
its mapping back to a hand-written recursive function.

## Not doing

- **No cycle detection at runtime.** A value graph that contains itself
  recurses until the stack runs out, exactly as a hand-written mapper would.
- **No mutual recursion across separate `transformInto` calls**: one call's
  plan is closed over its own pairs.

## Shape

```kotlin
data class Tree(val label: String, val children: List<Tree>)
data class TreeDto(val label: String, val children: List<TreeDto>)

tree.transformInto<TreeDto>()
// fun tree$kimney(s: Tree): TreeDto = TreeDto(s.label, s.children.map { tree$kimney(it) })
```

When a pair meets itself below its own derivation, the inner occurrence
becomes a reference to the outer one, and the outer one becomes a named plan.
The lowering emits a named plan once, as a local function inside the call's
block, and each reference as a call to it. Mutually recursive pairs
(`Folder → FolderDto` containing `File → FileDto` containing `Folder`) get one
function each, calling each other.

A self-reference that could never terminate — a non-null field of the pair's
own type, with no list, map or `?` between — still derives, as the class
itself does; its values cannot be built without recursion either.

`Recursive` is removed from the failures: nothing is left that reports it.

## Why this shape

A local function is what a person writes for this, and it keeps the
generated code inside the call it belongs to — nothing is added to the
class or the file. Naming plans only where recursion occurs keeps every other
transformation exactly as it is now: inline constructor calls, no function
per pair.

## Stack

- [ ] **`spec-0007-engine`** — `Plan.Named` and `Plan.Reference`; the
      recursion rule; `Recursive` removed.
      Done when: engine tests cover a tree, a list of itself, an optional
      self, and two mutually recursive pairs.
- [ ] **`spec-0007-ir`** — local functions for named plans; goldens move;
      box and agreement; docs.
      Done when: box tests pass for a tree, mutual recursion, and a
      recursive pair inside a sealed case.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers: local functions, named only where
  recursion occurs, no runtime cycle detection.
