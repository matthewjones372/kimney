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
e: Main.kt:15:13 Cannot transform User → UserDto:
    UserDto.email: String — User has no property 'email'. Add it to User, or give UserDto.email a default value.
    UserDto.address.zip: String — Address has no property 'zip'. Add it to Address, or give AddressDto.zip a default value.
```

No reflection and no runtime cost: the generated code is what you would have
written by hand.

**Status:** class-to-class by constructor works, with nested types, defaults
and generics ([docs/reference.md](docs/reference.md)). Collections, enums,
sealed types and overrides are next. The design lives in
[docs/roadmap.md](docs/roadmap.md) and [specs/](specs/). How to work here:
[AGENTS.md](AGENTS.md).
