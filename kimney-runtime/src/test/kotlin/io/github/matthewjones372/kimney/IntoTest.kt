package io.github.matthewjones372.kimney

import io.kotest.assertions.throwables.shouldThrow
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
    }

    @Test
    fun `transform says it was not replaced`() {
        shouldThrow<KimneyNotApplied> { chain.transform() }.message shouldStartWith "transform reached runtime"
    }
}
