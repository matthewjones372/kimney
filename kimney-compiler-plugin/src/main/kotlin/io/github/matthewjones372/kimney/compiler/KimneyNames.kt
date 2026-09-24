package io.github.matthewjones372.kimney.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val KIMNEY = FqName("io.github.matthewjones372.kimney")
private val INTO_CLASS = ClassId(KIMNEY, Name.identifier("Into"))

val TRANSFORM_INTO = CallableId(KIMNEY, Name.identifier("transformInto"))
val TRANSFORMER = ClassId(KIMNEY, Name.identifier("Transformer"))
val INTO = CallableId(KIMNEY, Name.identifier("into"))
val TRANSFORM = CallableId(INTO_CLASS, Name.identifier("transform"))
val WITH_FIELD_CONST = CallableId(INTO_CLASS, Name.identifier("withFieldConst"))
val WITH_FIELD_COMPUTED = CallableId(INTO_CLASS, Name.identifier("withFieldComputed"))
val WITH_FIELD_RENAMED = CallableId(INTO_CLASS, Name.identifier("withFieldRenamed"))
val WITH_TRANSFORMER = CallableId(INTO_CLASS, Name.identifier("withTransformer"))

/** Every link a chain may have between `into()` and `transform()`. */
val OVERRIDES = setOf(WITH_FIELD_CONST, WITH_FIELD_COMPUTED, WITH_FIELD_RENAMED, WITH_TRANSFORMER)
