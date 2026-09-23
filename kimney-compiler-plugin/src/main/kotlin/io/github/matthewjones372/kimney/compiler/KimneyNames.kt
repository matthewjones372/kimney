package io.github.matthewjones372.kimney.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val KIMNEY = FqName("io.github.matthewjones372.kimney")
private val INTO_CLASS = ClassId(KIMNEY, Name.identifier("Into"))

val TRANSFORM_INTO = CallableId(KIMNEY, Name.identifier("transformInto"))
val INTO = CallableId(KIMNEY, Name.identifier("into"))
val TRANSFORM = CallableId(INTO_CLASS, Name.identifier("transform"))
val WITH_FIELD_CONST = CallableId(INTO_CLASS, Name.identifier("withFieldConst"))
val WITH_FIELD_COMPUTED = CallableId(INTO_CLASS, Name.identifier("withFieldComputed"))
val WITH_FIELD_RENAMED = CallableId(INTO_CLASS, Name.identifier("withFieldRenamed"))
val OVERRIDES = setOf(WITH_FIELD_CONST, WITH_FIELD_COMPUTED, WITH_FIELD_RENAMED)

/** The IDE cancels analysis by throwing; swallowing that as an internal error would wedge it. Matched by name, since
 * the class is relocated in the embeddable compiler Gradle loads. */
fun Throwable.isControlFlow(): Boolean =
    generateSequence<Class<*>>(javaClass) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
        .any { it.simpleName == "ProcessCanceledException" || it.simpleName == "ControlFlowException" }

fun internalError(e: Exception): String =
    "kimney failed on this call (${e::class.simpleName}: ${e.message}). This is a bug in kimney; " +
        "please report it with the call and the types involved."
