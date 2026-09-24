# Why not write it by hand?

A mapper is easy to write. That is not the problem. The problem is that a
hand-written mapper is only right on the day it is written, and nothing in
Kotlin tells you when it stops being right.

kimney's answer is to not have a mapper to keep right: the mapping is derived
again from the two types on every build, so a change to either type changes
the mapping — or stops the build and says why.

## Two bugs the compiler will not find for you

Both of these compile. Both are tested — the test asserts the bug.

```kotlin
// file: example/src/main/kotlin/example/cookbook/why/Drift.kt
package example.cookbook.why

import io.github.matthewjones372.kimney.transformInto

data class Signup(val email: String, val marketingConsent: Boolean)

// `marketingConsent` was added to both classes after the hand-written mapper below. Its default keeps old
// callers compiling, which is exactly why nothing tells the mapper it is now wrong.
data class SignupDto(val email: String, val marketingConsent: Boolean = false)

fun byHand(signup: Signup): SignupDto = SignupDto(email = signup.email)

fun byKimney(signup: Signup): SignupDto = signup.transformInto<SignupDto>()

enum class Plan { FREE, PRO, ENTERPRISE }

enum class PlanDto { FREE, PRO, UNKNOWN }

// Written when there were two plans. ENTERPRISE arrived later and fell into the `else` without a word.
fun planByHand(plan: Plan): PlanDto = when (plan) {
    Plan.FREE -> PlanDto.FREE
    Plan.PRO -> PlanDto.PRO
    else -> PlanDto.UNKNOWN
}
```

**The consent that disappears.** `marketingConsent` was added to `Signup`
and to `SignupDto` after `byHand` was written. The DTO field has a default so
that existing callers keep compiling — and `byHand` is one of them. It still
compiles, and it records every signup as not consenting. `byKimney` needs no
edit: it was derived again, found the new property, and carries it.

**The plan that becomes `UNKNOWN`.** `planByHand` was written for two plans.
When `ENTERPRISE` was added, it went into the `else`, and every enterprise
customer is now `UNKNOWN` downstream. kimney has no `else` to fall into: an
entry the target lacks is a compile error on every call that meets it. Here
is the message from kimney's own tests, for the same mistake in another enum —

```
StatusDto — Status.ARCHIVED has no entry of the same name in StatusDto.
```

— and adding `ENTERPRISE` to `PlanDto` is the whole fix.

Neither bug is exotic. Defaults exist to keep old code compiling, and `else`
exists to stop a `when` from breaking; in a mapper, both turn "this changed"
into "this is quietly wrong".

## What each change costs you

| When this happens | By hand | With kimney |
|---|---|---|
| A field is added to both types, with a default on the target | Compiles, and silently uses the default | Mapped |
| A field is added to both types, no default | Compile error in the mapper, which you edit | Mapped |
| A field is added to the target only, no default | Compile error in the mapper | Compile error naming the field and how to fill it |
| A field is renamed on one side, no default | Compile error in the mapper | Compile error naming the field; one `withFieldRenamed` fixes it |
| A target field with a default has no source of that name | The default, silently | The default, silently — the same, and on purpose: see below |
| A new enum entry or sealed case | Caught only if the `when` has no `else` | Compile error at every call that meets it |
| An optional nested object | `?.let { … }` by hand, at each one | Derived, null kept as null |
| A `List<Line>` of nested types | `.map { … }` around another hand-written mapper | Derived, element by element |
| A `String?` flowing into a `String` | `!!`, `?: ""` or a bug | Compile error that says null is the reason |

And what it costs at runtime: nothing. `transformInto` compiles to the
constructor calls, null checks, `when`s and loops you would have written. No
reflection, no mapping registry, no generated mapper classes, and the tests
check that no lambda object is created where a hand-written mapping would
not create one.

The row about a target field with a default is the one place kimney is no better than
by hand. A default is
how a target says what it wants when nothing is given, so kimney uses it; a
target field whose default should never be used should not have one.

## Compared with other ways out

| | Errors arrive | You declare | Runtime cost |
|---|---|---|---|
| Hand-written mappers | When a customer finds them, for the silent cases | Every field, every time | None |
| Reflection-based mappers | At runtime, on the first call | Nothing, until it guesses wrong | Reflection on every call |
| Annotation processors (kapt, KSP) | At compile time, on the mapper declaration | An annotated mapper interface per pair | Generated classes |
| kimney | At compile time, on the call, all of them at once | Nothing, or an override where names differ | None |

## When not to use it

Be honest with yourself about these:

- **The mapping is mostly logic.** If most fields are computed from several
  others, a function says that better than a chain of `withFieldComputed`.
- **You have three small mappers.** The drift above is a cost that grows with
  the number of pairs and fields. For a handful, write them by hand.
- **You cannot pin Kotlin.** A compiler plugin is built for one compiler;
  kimney is built for 2.4.10, and a build on any other stops at configuration.
- **You need it published today.** kimney is pre-release and not on Maven
  Central yet; [the README](../README.md#trying-it) shows the composite build.
- **You need what is not there yet**: recursive types and fallible
  transformations. The
  [roadmap](roadmap.md) has them in order.
