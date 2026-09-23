package io.github.matthewjones372.kimney

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/** This module is compiled without the plugin, which is exactly the case the stub exists for. */
class TransformIntoTest {

    @Test
    fun `a call the plugin did not replace says so, and says how to apply it`() {
        val thrown = shouldThrow<KimneyNotApplied> { 1.transformInto<String>() }

        thrown.message shouldStartWith "transformInto reached runtime: the kimney compiler plugin did not replace"
        thrown.message!!.contains("""id("io.github.matthewjones372.kimney")""") shouldBe true
    }
}
