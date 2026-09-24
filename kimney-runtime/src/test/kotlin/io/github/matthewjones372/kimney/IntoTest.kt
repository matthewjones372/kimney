package io.github.matthewjones372.kimney

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/** Compiled without the plugin: every stub in the chain says which one was reached. */
class IntoTest {

    private data class User(val name: String)

    private data class UserDto(val name: String)

    private val chain = Into<User, UserDto>()

    @Test
    fun `into says it was not replaced`() {
        shouldThrow<KimneyNotApplied> { User("Ada").into<_, UserDto>() }.message shouldStartWith "into reached runtime"
    }

    @Test
    fun `each override says it was not replaced`() {
        shouldThrow<KimneyNotApplied> { chain.withFieldConst(UserDto::name, "Ada") }
            .message shouldStartWith "withFieldConst reached runtime"
        shouldThrow<KimneyNotApplied> { chain.withFieldComputed(UserDto::name) { it.name } }
            .message shouldStartWith "withFieldComputed reached runtime"
        shouldThrow<KimneyNotApplied> { chain.withFieldRenamed(User::name, UserDto::name) }
            .message shouldStartWith "withFieldRenamed reached runtime"
        shouldThrow<KimneyNotApplied> { chain.withTransformer(Transformer<User, UserDto> { UserDto(it.name) }) }
            .message shouldStartWith "withTransformer reached runtime"
        shouldThrow<KimneyNotApplied> { chain.withEnumEntryRenamed(Level.LOW, Level.HIGH) }
            .message shouldStartWith "withEnumEntryRenamed reached runtime"
        shouldThrow<KimneyNotApplied> { chain.withEnumFallback(Level.LOW) }
            .message shouldStartWith "withEnumFallback reached runtime"
    }

    @Test
    fun `transform says it was not replaced`() {
        shouldThrow<KimneyNotApplied> { chain.transform() }.message shouldStartWith "transform reached runtime"
    }

    @Test
    fun `a transformer is an ordinary value, usable without the plugin`() {
        Transformer<User, UserDto> { UserDto(it.name.uppercase()) }.transform(User("ada")) shouldBe UserDto("ADA")
    }

    @Test
    fun `the partial calls say they were not replaced`() {
        shouldThrow<KimneyNotApplied> { User("Ada").transformIntoPartial<UserDto>() }
            .message shouldStartWith "transformIntoPartial reached runtime"
        shouldThrow<KimneyNotApplied> { chain.transformPartial() }.message shouldStartWith
            "transformPartial reached runtime"
    }

    @Test
    fun `a partial result gives its value or nothing`() {
        Partial.Ok(1).valueOrNull() shouldBe 1
        Partial.Errors(listOf(PartialError("A.b", "is null"))).valueOrNull() shouldBe null
    }
}

private enum class Level { LOW, HIGH }
