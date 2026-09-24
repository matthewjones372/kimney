# 0011 — Transformers from context

## Problem

0006 made a transformer a value passed to a chain. A service with a
`Transformer<Money, MoneyDto>` it wants everywhere has to repeat
`.withTransformer(moneyToDto)` on every chain, and a plain `transformInto`
call cannot use one at all without becoming a chain. Kotlin's context
parameters are the language's way to say "this is in scope for everything
below", and they are stable on 2.4.10.

## Not doing

- **No implicit values from anywhere else** — no receivers, no class
  properties, no registry. A context parameter is the one implicit source.
- **No context parameters on classes** (`context(...) class`), which are not
  stable.
- **No change to 0006's rule**: a transformer from context is matched,
  ordered and lowered exactly as one from the chain.

## Shape

```kotlin
context(money: Transformer<Money, MoneyDto>)
fun toDto(invoice: Invoice): InvoiceDto = invoice.transformInto<InvoiceDto>()

val dto = context(moneyToDto) { toDto(invoice) }
```

Every context parameter in scope of a `transformInto` or `transform()` call
whose type is a `Transformer<A, B>` is offered to the engine beside the
chain's own, from the enclosing function or lambda outward. The lowering
reads the parameter; nothing new is evaluated.

Ambiguity names each source by what it is:

```
InvoiceDto.total: MoneyDto — two transformers fit Money → MoneyDto: withTransformer #1 and context parameter 'money'. Pass one.
```

A context transformer that fits nothing is not a warning: it is in scope for
every call below it, and most of those calls will not need it.

## Why this shape

Context parameters are how a Kotlin API asks for a capability without
threading it through every call, which is exactly what a shared transformer
is. Reading them as extra transformers keeps one rule, one matching and one
lowering; the only new code is finding them — in FIR through the checker's
containing declarations, in IR through the enclosing functions' context
parameters — and naming them in a message.

## Stack

- [ ] **`spec-0011-engine`** — `Supplied` carries a label; ambiguity names it.
      Done when: engine tests cover a chain and a context transformer fitting
      one pair.
- [ ] **`spec-0011-fir`** — the checker collects context transformers in scope.
      Done when: goldens pass for a function's and a lambda's context, and for
      ambiguity between chain and context.
- [ ] **`spec-0011-ir`** — the lowering reads them; box and agreement; docs.
      Done when: box tests pass for `transformInto` and a chain inside
      `context(...) { }`, and for a nested function's context.

## Acceptance

```bash
./gradlew build
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers: context parameters only, innermost
  first, and no unused warning for a context transformer.
