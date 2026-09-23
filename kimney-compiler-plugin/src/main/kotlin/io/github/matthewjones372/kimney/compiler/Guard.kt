package io.github.matthewjones372.kimney.compiler

/**
 * Runs one adapter's work on one call. Anything it throws is reported as an internal error and [fallback] answers
 * instead, so the compiler never crashes on kimney's account — except the IDE's cancellation, which must propagate.
 */
fun <R> guarded(fallback: () -> R, report: (String) -> Unit, block: () -> R): R =
    try {
        block()
    } catch (e: Exception) {
        if (e.isControlFlow()) throw e
        report(internalError(e))
        fallback()
    }

/** Matched by name: the embeddable compiler Gradle loads relocates these classes. */
internal fun Throwable.isControlFlow(): Boolean =
    generateSequence<Class<*>>(javaClass) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
        .any { it.simpleName == "ProcessCanceledException" || it.simpleName == "ControlFlowException" }

internal fun internalError(e: Exception): String =
    "kimney failed on this call (${e::class.simpleName}: ${e.message}). This is a bug in kimney; " +
        "please report it with the call and the types involved."
