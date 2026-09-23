package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_COMPUTED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_CONST
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_RENAMED
import io.github.matthewjones372.kimney.derive.Override
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId

/** An override chain read back from its `transform()` call: the source type and each override, in written order. */
data class FirChain(val source: ConeKotlinType, val overrides: List<Override<ConeKotlinType>>)

val FirFunctionCall.callableId: CallableId?
    get() = (calleeReference.symbol as? FirNamedFunctionSymbol)?.callableId

/** Null when the chain is not one expression of literal references and lambdas back to `into()`. */
fun readChain(transform: FirFunctionCall): FirChain? {
    val calls = generateSequence(transform.explicitReceiver as? FirFunctionCall) {
        it.explicitReceiver as? FirFunctionCall
    }
        .toList()
        .asReversed()
    val source = calls.firstOrNull()?.takeIf { it.callableId == INTO }?.extensionReceiver?.resolvedType
    val overrides = calls.drop(1).mapIndexed { index, call -> override(call, index) }
    return if (source == null || null in overrides) null else FirChain(source, overrides.filterNotNull())
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
