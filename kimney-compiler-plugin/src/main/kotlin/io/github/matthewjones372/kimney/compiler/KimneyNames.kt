package io.github.matthewjones372.kimney.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val TRANSFORM_INTO = CallableId(FqName("io.github.matthewjones372.kimney"), Name.identifier("transformInto"))

/** The IDE cancels analysis by throwing; swallowing that as an internal error would wedge it. Matched by name, since
 * the class is relocated in the embeddable compiler Gradle loads. */
fun Throwable.isControlFlow(): Boolean =
    generateSequence<Class<*>>(javaClass) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
        .any { it.simpleName == "ProcessCanceledException" || it.simpleName == "ControlFlowException" }

fun internalError(e: Exception): String =
    "kimney failed on this call (${e::class.simpleName}: ${e.message}). This is a bug in kimney; " +
        "please report it with the call and the types involved."
