package io.github.matthewjones372.kimney.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GuardTest {

    /** Named as the IDE's is: the guard knows it by name, since Gradle's compiler relocates the real one. */
    private class ProcessCanceledException : RuntimeException()

    private interface ControlFlowException

    private class Cancelled : IllegalStateException(), ControlFlowException

    private val reports = mutableListOf<String>()

    @Test
    fun `work that succeeds answers for itself and reports nothing`() {
        guarded(fallback = { "fallback" }, report = { reports += it }) { "done" } shouldBe "done"

        reports.shouldBeEmpty()
    }

    @Test
    fun `work that throws is reported as a bug in kimney, and the fallback answers`() {
        val answer = guarded(fallback = { "fallback" }, report = { reports += it }) { error("no constructor") }

        answer shouldBe "fallback"
        reports shouldBe listOf(
            "kimney failed on this call (IllegalStateException: no constructor). This is a bug in kimney; " +
                "please report it with the call and the types involved.",
        )
    }

    @Test
    fun `the IDE's cancellation is not swallowed, by class or by interface`() {
        shouldThrow<ProcessCanceledException> {
            guarded(fallback = {}, report = { reports += it }) { throw ProcessCanceledException() }
        }
        shouldThrow<Cancelled> { guarded(fallback = {}, report = { reports += it }) { throw Cancelled() } }

        reports.shouldBeEmpty()
    }
}
