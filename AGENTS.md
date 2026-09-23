# Working in this repo

kimney is a Kotlin compiler plugin that derives total transformations between
types — `user.transformInto<UserDto>()` — at compile time, and refuses to
compile when it cannot, naming every field it could not fill and how to fill
it. The error message is the product. Treat it the way an HTTP library treats
its wire format.

## Specs come first

Nothing is implemented without a spec file in `specs/`. An agent drafts it, a
human edits it, and only the edited, committed version gets built.

The draft is a proposal, not a plan. Its job is to be **cheap to disagree
with**, so it stays short enough to read in one sitting:

- **One page.** Roughly 80 lines. A draft that runs longer is proposing too
  much at once — split it into two specs rather than writing more.
- **No prose padding.** Fill the template's headings and stop. Where a heading
  has nothing real under it, write "nothing" and move on.
- **Uncertainty goes under Open questions**, not into a hedged paragraph under
  Shape. Three or four questions in a first draft is healthy.
- **Options, not verdicts.** Where a design could go two ways, give each a
  sentence and recommend one. Do not silently pick.

Then stop and hand it over. Do not implement a spec nobody has edited and
committed.

Before a spec exists: ask questions, read code, answer in chat. No code.

`specs/README.md` gives the layout and the lifecycle. `docs/roadmap.md` lists
the specs that are planned but not yet drafted, and the decisions already
taken for them.

## One spec section per pull request

Pull requests are stacked. Each branch sits on the one before it and is
reviewable on its own.

- **Soft cap: 200 changed lines**, excluding golden fixtures and `testData/`.
  Past that, split before writing code rather than after.
- **One spec section per PR.** A spec with four stack entries is four branches.
- **Announce the split first.** Post the intended stack — branch name and one
  line each — and wait for a yes before the first commit.

### Working a stack

```bash
git config rebase.updateRefs true
```

Branch from `origin/main` and build bottom-up with each PR based on its parent:

```bash
git switch -c spec-0002-engine origin/main
gh pr create --base main --fill

git switch -c spec-0002-fir        # branches off spec-0002-engine
gh pr create --base spec-0002-engine --fill
```

After review changes land on a lower branch, restack from the top and push the
chain:

```bash
git switch spec-0002-fir
git rebase origin/main
git push --force-with-lease origin spec-0002-engine spec-0002-fir
```

## Comments

Comments record what the code cannot: the reason a thing is done the way it is.
They do not restate the code, and they are not essays.

- **KDoc: one line by default.** Two or three only where the reason genuinely
  takes them.
- **No worked examples in KDoc** unless the call is hard to get right from the
  signature. `docs/` carries the tutorial.
- **No restating the code.** `/** The path. */ val path: Path` earns nothing.
- **No history.** That belongs in the commit message. Exception: naming a bug
  the comment exists to stop coming back.
- **No rhetorical framing.** State the fact.
- **Inline `//` notes are for the line below them.** Past three lines it is
  KDoc or it is too long.

Compiler-API code is the exception that proves the rule: where a FIR or IR call
is there because of an undocumented compiler behaviour, say so in one line and
name the Kotlin version it was observed on. That is the comment the next Kotlin
upgrade needs.

Same rules in test sources. A test name carries the claim.

## Imports

**No wildcard imports, anywhere, and no unused ones.** detekt fails the build
on either (`WildcardImport`, `UnusedImport`), and `.editorconfig` tells ktlint
and the IDE the same thing. Every complete example in `docs/` carries its
imports and its `plugins { }` block written out.

## Layout

```
kimney-runtime          the DSL a user calls; stubs the plugin replaces
kimney-derive           type model + rules → Plan or failures; the whole brain
kimney-compiler-plugin  FIR checker and IR lowering, adapters onto kimney-derive
kimney-gradle-plugin    included build; applies the compiler plugin
example/                applies the Gradle plugin by id, as a consumer does
```

`kimney-runtime` and `kimney-derive` depend on the Kotlin standard library and
nothing else. `NoThirdPartyDependenciesTest` asserts both runtime classpaths.
A dependency added to either is a build failure until a spec says otherwise.

`kimney-derive` knows nothing about the compiler. It sees types only through
its `TypeModel` interface. The FIR adapter and the IR adapter each implement
that interface and run the same engine, so the checker that reports an error
and the lowering that generates code cannot disagree about a rule. A rule
written in an adapter instead of the engine is the one bug this layout exists
to prevent.

Only `kimney-compiler-plugin` imports `org.jetbrains.kotlin.*`.

## Values, errors and effects

The engine is a pure function: `(source, target, overrides) → Plan` or a
non-empty list of `Failure`. No mutable state survives a call, no rule depends
on the order pairs were seen in, and nothing is cached across compilations.

**Report every failure, not the first.** A user fixing one field per compile is
the experience this library exists to replace.

`Failure` is sealed. Each variant owns its message and its fix hint, rendered
in one place. Never add an `else` to a `when` over a sealed type — the missing
branch is the compiler naming a failure that has no message yet.

Every failure carries its full path from the root target
(`UserDto.address.zip`) and the source type it was looking at.

**Generated code does no reflection and allocates nothing the handwritten
version would not.** A transformation compiles to the constructor calls a
person would have written. A `KClass` or a `KProperty` reaching runtime from
generated code is a bug.

**The plugin never crashes the compiler.** A type the engine cannot model is a
`Failure` with a message, not an exception. Both adapters run each call
through `guarded` in `Guard.kt`, which turns anything thrown into an
internal-error diagnostic naming the call and asking for an issue. It is the
one generic `catch` in the codebase; detekt permits it nowhere else.

Public API returns read-only types. `FunctionalStyleTest` lists every file
permitted a mutable accumulator, each with its reason.

## Testing

Write the failing test first. Work out which of these a change can break:

- **Engine tests** in `kimney-derive`, against a fake `TypeModel`. Every rule
  and every `Failure` variant is proven here first. Most coverage lives here.
- **Diagnostic goldens.** `testData/diagnostics/*.kt` with markers on the
  failing expression, and the expected rendered messages beside them. A moved
  golden is the test working: read the diff and decide whether the new message
  is better. Do not regenerate for green.
- **Box tests.** `testData/box/*.kt` compile, run, and return `"OK"`. They
  prove the IR lowering produces what the engine planned.
- **Agreement.** For every box test there is a matching statement that the FIR
  checker reports nothing on the same source. A checker that rejects what the
  lowering can build — or accepts what it cannot — is caught here.
- **Lowering only.** `testData/loweringOnly/*.kt` run the lowering with the
  checker switched off, so the report for a call the checker should have
  refused is reachable, and its goldens pin that text and its position.
- **The consumer path.** `example/` applies the Gradle plugin by id and
  compiles. If `example` needs a trick a user would not know, the plugin is
  wrong.

Harness: JetBrains' compiler test framework, as used by
`Kotlin/compiler-plugin-template`. Test names are sentences in backticks,
Kotest matchers, JUnit 5.

Kover is aggregated across modules with a floor of 90% on `check`.

## Kotlin version

The compiler plugin API is not stable. The whole project pins one Kotlin
version (`gradle.properties`, `kotlinVersion`) until 1.0, and the Gradle plugin
refuses to apply to a build on a different Kotlin version with a message
naming both. Bumping Kotlin is its own spec.

## Verifying

`./gradlew build` runs tests, detekt and spotless. Run it before saying
anything is done, and quote the result rather than predicting it. Finish with:

```bash
./gradlew spotlessApply && ./gradlew build
```

| Gate | Fails when | Not the fix |
|---|---|---|
| detekt | any finding | a suppression with no reason |
| `NoThirdPartyDependenciesTest` | runtime or derive gains a dependency | widening the allowlist without a spec |
| `FunctionalStyleTest` | a new file allocates a mutable collection | an unexplained entry |
| Kover | aggregate line coverage under 90% | lowering the floor |
| binary-compatibility-validator | `kimney-runtime`'s API moved without its `.api` file | regenerating without reading it |
| Diagnostic goldens | a rendered message changed | regenerating for green |

Before saying it is done:

- The failing test came first, and fails without the change.
- `./gradlew build` is green, gates included.
- A new rule lives in `kimney-derive`, not in an adapter.
- A new `Failure` has a golden pinning its exact message.
- `docs/reference.md` reflects the change, or the change is invisible from
  outside.
