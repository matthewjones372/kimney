package io.github.matthewjones372.kimney.compiler.fir

import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.resolvedType

/** The properties a selector reads from its parameter, outermost first: `{ it.address?.zip }` is address, zip. */
internal fun selected(selector: FirAnonymousFunctionExpression): List<String>? {
    val parameter = selector.anonymousFunction.valueParameters.singleOrNull()?.symbol ?: return null
    val body = selector.anonymousFunction.body?.statements?.singleOrNull()
    val expression = (body as? FirReturnExpression)?.result ?: body as? FirExpression

    fun path(expression: FirExpression?): List<String>? = when (expression) {
        is FirSafeCallExpression ->
            path(expression.receiver)?.let { above -> name(expression.selector as? FirExpression)?.let { above + it } }

        is FirQualifiedAccessExpression -> when (val symbol = expression.calleeReference.symbol) {
            parameter -> emptyList()

            is FirPropertySymbol ->
                path(expression.explicitReceiver.unwrapSafeSubject())?.plus(symbol.name.asString())

            else -> null
        }

        else -> null
    }
    return path(expression)?.takeIf { it.isNotEmpty() }
}

private fun name(expression: FirExpression?): String? =
    ((expression as? FirQualifiedAccessExpression)?.calleeReference?.symbol as? FirPropertySymbol)?.name?.asString()

// Inside a safe call the selector's receiver is a stand-in for the value checked; its place is taken by the receiver.
private fun FirExpression?.unwrapSafeSubject(): FirExpression? =
    if (this is FirCheckedSafeCallSubject) originalReceiverRef.value else this

/** The type of what [lambda] returns from its last statement, when that is an expression. */
internal fun returned(lambda: FirAnonymousFunctionExpression): ConeKotlinType? {
    val last = lambda.anonymousFunction.body?.statements?.lastOrNull()
    return ((last as? FirReturnExpression)?.result ?: last as? FirExpression)?.resolvedType
}
