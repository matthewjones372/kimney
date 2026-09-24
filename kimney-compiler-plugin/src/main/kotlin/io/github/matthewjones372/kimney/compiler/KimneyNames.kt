package io.github.matthewjones372.kimney.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val KIMNEY = FqName("io.github.matthewjones372.kimney")
private val INTO_CLASS = ClassId(KIMNEY, Name.identifier("Into"))

val TRANSFORM_INTO = CallableId(KIMNEY, Name.identifier("transformInto"))
val TRANSFORM = CallableId(INTO_CLASS, Name.identifier("transform"))
val TRANSFORMER = ClassId(KIMNEY, Name.identifier("Transformer"))
val TRANSFORM_INTO_PARTIAL = CallableId(KIMNEY, Name.identifier("transformIntoPartial"))
val TRANSFORM_PARTIAL = CallableId(INTO_CLASS, Name.identifier("transformPartial"))
val PARTIAL_ERROR = ClassId(KIMNEY, Name.identifier("PartialError"))
val PARTIAL_OK: ClassId = ClassId.fromString("io/github/matthewjones372/kimney/Partial.Ok")
val PARTIAL_ERRORS: ClassId = ClassId.fromString("io/github/matthewjones372/kimney/Partial.Errors")

/** The calls that end a chain: each reads the links behind it back to into(). */
val TERMINALS = setOf(TRANSFORM, TRANSFORM_PARTIAL)
val INTO = CallableId(KIMNEY, Name.identifier("into"))
val WITH_FIELD_CONST = CallableId(INTO_CLASS, Name.identifier("withFieldConst"))
val WITH_FIELD_COMPUTED = CallableId(INTO_CLASS, Name.identifier("withFieldComputed"))
val WITH_FIELD_RENAMED = CallableId(INTO_CLASS, Name.identifier("withFieldRenamed"))
val WITH_TRANSFORMER = CallableId(INTO_CLASS, Name.identifier("withTransformer"))

/** Every link a chain may have between `into()` and `transform()`. */
val OVERRIDES = setOf(WITH_FIELD_CONST, WITH_FIELD_COMPUTED, WITH_FIELD_RENAMED, WITH_TRANSFORMER)
