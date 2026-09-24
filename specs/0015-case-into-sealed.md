# 0015 — A case into the sealed type that holds its twin

## Problem

`val e = Expr.Add(…); e.transformInto<ExprDto>()` is `no rule`: the source is
the case `Expr.Add`, not the sealed `Expr`, so the sealed rule does not apply,
and `ExprDto` is abstract, so the constructor rule does not either. The fix
today is to widen the source's type by hand, which is noise a reader has to
explain.

## Not doing

- **No case into a case of another name**, and no generic hierarchies.

## Shape

A sealed target meets a source that is not itself sealed: kimney takes the
target's case with the source's simple name and derives the pair by every
rule, as it would one arm of a sealed pair. No case of that name is
`MissingCase`, as for a sealed source:

```
ExprDto — Expr.Mul has no subclass of the same name in ExprDto.
```

## Stack

- [x] **`spec-0015-case`** — `TypeModel.caseName`; the rule; both adapters;
      box, agreement and a golden; the reference.
      Done when: a box test maps a case into its target's sealed parent, and a
      golden pins the missing name.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to continue with the
  recommended answers: by simple name only, one stack entry.
