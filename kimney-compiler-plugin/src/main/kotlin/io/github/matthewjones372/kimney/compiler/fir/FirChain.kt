package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_COMPUTED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_CONST
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_RENAMED
import io.github.matthewjones372.kimney.compiler.WITH_TRANSFORMER
import io.github.matthewjones372.kimney.derive.Override
import io.github.matthewjones372.kimney.derive.Supplied
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId

/**
 * An override chain read back from its `transform()` call: the source type, each override and each transformer in
 * written order, and the call that passed each transformer, for a warning to point at.
 */
data class FirChain(
    val source: ConeKotlinType,
    val overrides: List<Override<ConeKotlinType>>,
    val transformers: List<Supplied<ConeKotlinType>>,
    val transformerCalls: Map<Int, FirFunctionCall>,
    val links: Int,
)

private sealed interface Link {
    data class Overriding(val override: Override<ConeKotlinType>) : Link

    data class Transforming(val supplied: Supplied<ConeKotlinType>, val call: FirFunctionCall) : Link
}

val FirFunctionCall.callableId: CallableId?
    get() = (calleeReference.symbol as? FirNamedFunctionSymbol)?.callableId

/** Null when the chain is not one expression of literal references and lambdas back to `into()`. */
fun readChain(transform: FirFunctionCall): FirChain? {
    // Stops at into(): the source may itself be a call, and is not part of the chain.
    val calls = generateSequence(transform.explicitReceiver as? FirFunctionCall) { call ->
        (call.explicitReceiver as? FirFunctionCall)?.takeUnless { call.callableId == INTO }
    }
        .toList()
        .asReversed()
    val source = calls.firstOrNull()?.takeIf { it.callableId == INTO }?.extensionReceiver?.resolvedType
    val links = calls.drop(1).mapIndexed { index, call -> link(call, index) }
    return if (source == null || null in links) {
        null
    } else {
        val transforming = links.filterIsInstance<Link.Transforming>()
        FirChain(
            source,
            links.filterIsInstance<Link.Overriding>().map { it.override },
            transforming.map { it.supplied },
            transforming.associate { it.supplied.index to it.call },
            links.size,
        )
    }
}

/** A transformer's types are the call's own type arguments, so a class implementing `Transformer` serves as well. */
private fun link(call: FirFunctionCall, index: Int): Link? = if (call.callableId == WITH_TRANSFORMER) {
    val (source, target) = call.typeArguments.map { (it as? FirTypeProjectionWithVariance)?.typeRef?.coneType }
    if (source == null || target == null) null else Link.Transforming(Supplied(source, target, index), call)
} else {
    override(call, index)?.let { Link.Overriding(it) }
}

private fun override(call: FirFunctionCall, index: Int): Override<ConeKotlinType>? {
    val args = call.resolvedArgumentMapping?.entries?.associate { (arg, param) -> param.name.asString() to arg }
    val field = field(args?.get(if (call.callableId == WITH_FIELD_RENAMED) "to" else "field")) ?: return null
    return when (call.callableId) {
        WITH_FIELD_CONST -> args?.get("value")?.let { Override.Const(field, it.resolvedType, index) }

        WITH_FIELD_COMPUTED -> (args?.get("compute") as? FirAnonymousFunctionExpression)
            ?.let { Override.Computed(field, it.anonymousFunction.returnTypeRef.coneType, index) }

        WITH_FIELD_RENAMED -> field(args?.get("from"))?.let { Override.Renamed(field, it) }

        else -> null
    }
}

private fun field(reference: FirExpression?): String? =
    ((reference as? FirCallableReferenceAccess)?.calleeReference?.symbol as? FirPropertySymbol)?.name?.asString()
