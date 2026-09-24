# Why not write it by hand?

## The classes are the design; the mapping is the tax

Domain-driven design gives one idea several shapes, on purpose. An order is a
`PlaceOrderRequest` at the edge, a `PlaceOrder` command, an `Order` aggregate
with its own identity and invariants, an `OrderPlaced` event, an `OrderRow` in
storage and an `OrderView` going back out. They look alike field for field —
and that is the point: each one says which layer you are in and what is
happening, and each can change for its own reasons without dragging the
others along.

The cost is the mapping between them. Every pair of shapes is a function that
restates every field, and every field added to the domain is one more line in
each of them. Under that weight, teams stop paying: the entity doubles as the
DTO with `@JsonIgnore` on the parts the API must not see, the event reuses the
row, the command is the request with a comment. The layers collapse because
the boilerplate made them expensive, not because they were wrong.

kimney takes the cost away and leaves the design. Here is one order through
all six shapes — value-class identities in the domain, plain columns in the
row, a view with an extra enum entry — with each crossing written once:

```kotlin
// file: example/src/main/kotlin/example/cookbook/layers/OrderFlow.kt
package example.cookbook.layers

import io.github.matthewjones372.kimney.into
import io.github.matthewjones372.kimney.transformInto

// The domain: identities and quantities are types, not Longs and Ints.
@JvmInline value class OrderId(val value: Long)

@JvmInline value class CustomerId(val value: Long)

@JvmInline value class Sku(val code: String)

@JvmInline value class Quantity(val units: Int)

enum class OrderStatus { PLACED, SHIPPED }

data class OrderLine(val sku: Sku, val quantity: Quantity)

data class Order(val id: OrderId, val customerId: CustomerId, val lines: List<OrderLine>, val status: OrderStatus)

// The way in: what the API accepts, and the command it becomes.
data class LineRequest(val sku: String, val quantity: Int)

data class PlaceOrderRequest(val customerId: Long, val lines: List<LineRequest>)

data class PlaceOrder(val customerId: CustomerId, val lines: List<OrderLine>)

// The way out: what happened, what is stored, and what the API answers.
data class OrderPlaced(val orderId: OrderId, val customerId: CustomerId, val lines: List<OrderLine>)

enum class StatusColumn { PLACED, SHIPPED }

data class OrderRow(val id: Long, val customerId: Long, val status: StatusColumn)

enum class StatusView { PLACED, SHIPPED, UNKNOWN }

data class LineView(val sku: String, val quantity: Int)

data class OrderView(val id: Long, val lines: List<LineView>, val status: StatusView)

// Five crossings between six shapes, one line each.
fun PlaceOrderRequest.toCommand(): PlaceOrder = transformInto()

fun PlaceOrder.toOrder(id: OrderId): Order = into<_, Order>()
    .withFieldConst(Order::id, id)
    .withFieldConst(Order::status, OrderStatus.PLACED)
    .transform()

fun Order.placed(): OrderPlaced = into<_, OrderPlaced>().withFieldRenamed(Order::id, OrderPlaced::orderId).transform()

fun Order.toRow(): OrderRow = transformInto()

fun Order.toView(): OrderView = transformInto()
```

Five crossings, one line each, and a test that runs every one. What each line
does not say, kimney derives: `Long` into `CustomerId` and back, `String` into
`Sku`, a `List<LineRequest>` into a `List<OrderLine>`, `OrderStatus` into
`StatusColumn` and `StatusView`. What it cannot derive — a field the next
shape needs and this one lacks — stops the build at that line, naming the
field.

So the shapes stay separate for as long as they are separate, and cost nothing
while they are alike.

## And the mapping you write by hand goes stale

A mapper is easy to write and only right on the day it is written; nothing in
Kotlin tells you when it stops being right. kimney derives the mapping again
from the two types on every build, so a change to either type changes the
mapping — or stops the build and says why. Two bugs the compiler will not find
for you, both of which compile. Both are tested — the test asserts the bug.

```kotlin
// file: example/src/main/kotlin/example/cookbook/why/Drift.kt
package example.cookbook.why

import io.github.matthewjones372.kimney.transformInto

data class Signup(val email: String, val marketingConsent: Boolean)

// `marketingConsent` was added to both classes after the hand-written mapper below. Its default keeps old
// callers compiling, which is exactly why nothing tells the mapper it is now wrong.
data class SignupDto(val email: String, val marketingConsent: Boolean = false)

fun Signup.toDtoByHand(): SignupDto = SignupDto(email = email)

fun Signup.toDto(): SignupDto = transformInto()

enum class Plan { FREE, PRO, ENTERPRISE }

enum class PlanDto { FREE, PRO, UNKNOWN }

// Written when there were two plans. ENTERPRISE arrived later and fell into the `else` without a word.
fun Plan.toDtoByHand(): PlanDto = when (this) {
    Plan.FREE -> PlanDto.FREE
    Plan.PRO -> PlanDto.PRO
    else -> PlanDto.UNKNOWN
}
```

**The consent that disappears.** `marketingConsent` was added to `Signup`
and to `SignupDto` after `toDtoByHand` was written. The DTO field has a default so
that existing callers keep compiling — and `toDtoByHand` is one of them. It still
compiles, and it records every signup as not consenting. The kimney `toDto` needs no
edit: it was derived again, found the new property, and carries it.

**The plan that becomes `UNKNOWN`.** `Plan.toDtoByHand` was written for two plans.
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
