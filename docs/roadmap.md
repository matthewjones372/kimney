# Roadmap

Planned specs, in build order. Each is drafted only when the one before it has
shipped. The decisions under each were taken in the design discussion that
started the project (2026-09-23) and are the starting point for that spec's
draft, not a substitute for it.

| Spec | Scope |
|---|---|
| 0001 | Workspace, build, gates, runtime stubs — no derivation |
| 0002 | Class → class by constructor, end to end: engine, FIR checker, IR lowering |
| 0003 | Override DSL: `withFieldConst`, `withFieldComputed`, `withFieldRenamed` |
| 0004 | Enum → enum and sealed → sealed by name |
| 0005 | `T → T?`, `T? → T?`, value class wrap and unwrap |
| 0006 | User-supplied transformers for nested pairs, passed with `withTransformer` |
| 0007 | Recursive types |
| 0008 | Partial transformers |
| 0009 | Tests for the internal-error and disagreement paths — built before 0004 |
| 0010 | `List`/`Set`/`Collection`/`Map`/`Array`, element by element |
| 0011 | Transformers from context parameters, on 0006's rule |
| 0012 | Faster builds and docs that quote only real messages |
| 0013 | Fallible user transformers, on 0008's result type |
| 0014 | Publishing to Maven Central and the Gradle Plugin Portal |
| 0015 | A sealed case into its target's sealed parent |

## Rule order

The engine tries these for each `(Source, Target)` pair, first match wins.
Specs add rules to this list; none reorders it without saying why.

1. Override for this target path (0003)
2. User-supplied transformer in scope (0006)
3. Identity — `Source` is a subtype of `Target` (0002)
4. Nullable — `T → T?` and `T? → T?` through the non-null type; `T? → T` is a failure until 0008 (0005)
5. Value class — wrap the target, unwrap the source (0005)
6. Containers — element-wise (0010)
7. Object → object, the target instance (0004)
8. Enum → enum by case name (0004)
9. Sealed → sealed by subclass simple name, exhaustive `when` (0004)
10. Class → class by primary constructor: same-named source property, then the
   parameter's default, then a failure (0002)

## Decisions already taken

- **Mechanism.** A K2 compiler plugin, not KSP: errors land on the call
  expression and show in the IDE.
- **Architecture.** A pure engine in `kimney-derive` behind a `TypeModel`
  interface; FIR and IR are both adapters running the same engine.
- **Total only until 0008.** Anything that could fail at runtime is a compile
  error naming the field.
- **Overrides must be one expression.** `into<B>()…transform()` is read from
  the syntax tree; a builder stored in a `val` is a failure (0003).
- **Nested transformers** are passed with `.withTransformer(t)` (0006), and
  from context parameters (0011), the nearest Kotlin has to Chimney's
  implicits, once 0006's rule and lowering exist to build them on.
- **Recursive types** get a private generated helper per recursive pair rather
  than a failure (0007).
- **Test harness.** JetBrains' compiler test framework, as in
  `Kotlin/compiler-plugin-template`.
- **Kotlin version.** One pinned version for the whole project until 1.0.
