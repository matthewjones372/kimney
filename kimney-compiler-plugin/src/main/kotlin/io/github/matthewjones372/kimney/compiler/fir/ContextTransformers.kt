package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.PARTIAL_TRANSFORMER
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
            val kind = type?.lookupTag?.classId?.takeIf { it == TRANSFORMER || it == PARTIAL_TRANSFORMER }
            val arguments = type?.takeIf {
                kind != null
            }?.typeArguments?.map { (it as? ConeKotlinTypeProjection)?.type }
            val (from, to) = arguments ?: return@mapNotNull null
            if (from == null ||
                to == null
            ) null else Found(from, to, parameter.name.asString(), kind == PARTIAL_TRANSFORMER)
        }
        .mapIndexed { i, found ->
            Supplied(found.from, found.to, start + i, context = found.name, canFail = found.canFail)
        }

private data class Found(val from: ConeKotlinType, val to: ConeKotlinType, val name: String, val canFail: Boolean)
