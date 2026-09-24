# What it costs, measured

kimney's claim is that a derived mapping costs what the hand-written one does,
because it compiles to the same code. [Why](why.md) argues it; this page
times it.

## What was measured

[`benchmarks/`](../benchmarks/src/main/kotlin/kimney/benchmarks) holds four
mappings, each written twice: once as a kimney call and once by hand, as a
careful person would write it — `map` for a list, an exhaustive `when` for an
enum, a validator that collects every error with its path. A test checks that
both sides of every pair give equal results, so each pair compares like with
like.

| Pair | What it exercises |
|---|---|
| `orderView` | `Order → OrderView`: ten lines, value classes unwrapped, an enum by name |
| `placeOrder` | `PlaceOrderRequest → PlaceOrder`: ten lines, value classes wrapped |
| `customer` | `Customer → CustomerDto`: two nested classes, one of them nullable, and a default |
| `signupValid` | `transformIntoPartial` on a form that passes: ten emails checked |
| `signupInvalid` | the same form failing: a null and five bad emails, every error collected |

## One run

JMH 1.37, average time, 5 × 1 s warmup and 5 × 1 s measurement per fork,
2 forks; `-prof gc` for allocation. Apple M3, 24 GB, Temurin 21.0.9, kimney at
spec 0018. The numbers are copied from that run's `jmh-result.json`.

| Pair | By hand (ns/op) | kimney (ns/op) | By hand (B/op) | kimney (B/op) |
|---|---:|---:|---:|---:|
| `orderView` | 45.6 ± 0.5 | 43.7 ± 0.4 | 352 | 352 |
| `placeOrder` | 45.9 ± 16.8 | 46.0 ± 5.9 | 344 | 344 |
| `customer` | 8.9 ± 3.5 | 8.0 ± 0.4 | 96 | 96 |
| `signupValid` | 45.4 ± 0.8 | 43.7 ± 0.9 | 280 | 280 |
| `signupInvalid` | 2846.9 ± 32.5 | 2852.5 ± 26.4 | 4080 | 4136 |

## What it shows

- **Total mappings are the same code.** Time agrees within the error on every
  pair, and allocation agrees to the byte: the same objects are built, and
  nothing else is.
- **A passing validation costs what a hand-written one does.** The partial
  lowering's bookkeeping — an error list, a check per step — is the same
  bookkeeping a validator has.
- **A failing validation is dominated by the exceptions.** Both sides build an
  `IllegalArgumentException` per bad email, and filling its stack trace is
  nearly all of the 2.8 µs. kimney allocates 56 bytes more per failing call
  here, about 1.4%; the hand-written validator was written knowing its shape,
  and the lowering's intermediate list is the likely difference.

## What it cannot show

These are microbenchmarks on one machine and one JDK. They say the derived
code and the hand-written code run alike; they say nothing about how much of
a service's time mapping takes, which is usually very little. The ± columns
are JMH's 99.9% confidence intervals; `placeOrder` and `customer`'s wide ones
on the hand-written side are run-to-run noise, not a difference between the
two.

## Running it

```bash
./gradlew :benchmarks:jmh
```

About three and a half minutes; nothing else in the build depends on it.
`-PbenchmarkArgs="orderView -f 1"` narrows it, and any JMH option passes
through the same way.
