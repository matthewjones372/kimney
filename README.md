# kimney

Compile-time derived transformations between Kotlin types, in the spirit of
Scala's [Chimney](https://github.com/scalalandio/chimney).

```kotlin
val dto = user.transformInto<UserDto>()
```

A K2 compiler plugin derives the constructor calls at compile time. When it
cannot, the call does not compile, and the error names every target field it
could not fill, the path to it, and how to fill it:

```
e: Main.kt:9:16 Cannot transform User → UserDto:
     UserDto.email: String — User has no property 'email'.
```

No reflection and no runtime cost: the generated code is what you would have
written by hand.

**Status: nothing transforms yet.** The design lives in
[docs/roadmap.md](docs/roadmap.md) and [specs/](specs/). How to work here:
[AGENTS.md](AGENTS.md).
