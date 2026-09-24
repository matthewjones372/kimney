package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.TRANSFORMER
import io.github.matthewjones372.kimney.derive.Supplied
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection

/**
 * Every `Transformer` context parameter of the functions and lambdas around the call, innermost first, numbered
 * from [start] so their indices follow the chain's own.
 */
fun CheckerContext.contextTransformers(start: Int): List<Supplied<ConeKotlinType>> =
    containingDeclarations.asReversed()
        .filterIsInstance<FirCallableSymbol<*>>()
        .flatMap { it.contextParameterSymbols }
        .mapNotNull { parameter ->
            val type = parameter.resolvedReturnType.fullyExpandedType() as? ConeClassLikeType
            val arguments = type?.takeIf { it.lookupTag.classId == TRANSFORMER }?.typeArguments
                ?.map { (it as? ConeKotlinTypeProjection)?.type }
            val (from, to) = arguments ?: return@mapNotNull null
            if (from == null || to == null) null else Triple(from, to, parameter.name.asString())
        }
        .mapIndexed { i, (from, to, name) -> Supplied(from, to, start + i, context = name) }
