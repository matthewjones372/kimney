package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_COMPUTED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_CONST
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_RENAMED
import io.github.matthewjones372.kimney.derive.Override
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.callableId

/** The chain behind a `transform()` call: the source, each override in written order, and the expression each gave. */
data class IrChain(val source: IrExpression, val overrides: List<Override<IrType>>, val given: List<IrExpression?>)

/** Null when the chain is not the one expression the checker accepted. */
fun readChain(transform: IrCall): IrChain? {
    // Stops at into(): the source may itself be a call, and is not part of the chain.
    val calls = generateSequence(transform.arguments[0] as? IrCall) {
        (it.arguments[0] as? IrCall)?.takeUnless { _ -> it.symbol.owner.callableId == INTO }
    }
        .toList()
        .asReversed()
    val source = calls.firstOrNull()?.takeIf { it.symbol.owner.callableId == INTO }?.arguments?.get(0)
    val links = calls.drop(1).mapIndexed { index, call -> link(call, index) }
    val complete = links.filterNotNull().takeIf { it.size == links.size }
    return if (source == null || complete == null) {
        null
    } else {
        IrChain(source, complete.map { it.first }, complete.map { it.second })
    }
}

private fun link(call: IrCall, index: Int): Pair<Override<IrType>, IrExpression?>? {
    val args = call.symbol.owner.parameters.associate { it.name.asString() to call.arguments[it.indexInParameters] }
    val field = field(args[if (call.symbol.owner.callableId == WITH_FIELD_RENAMED) "to" else "field"]) ?: return null
    return when (call.symbol.owner.callableId) {
        WITH_FIELD_CONST -> args["value"]?.let { Override.Const(field, it.type, index) to it }

        WITH_FIELD_COMPUTED -> (args["compute"] as? IrFunctionExpression)
            ?.let { Override.Computed(field, it.function.returnType, index) to it }

        WITH_FIELD_RENAMED -> field(args["from"])?.let { Override.Renamed(field, it) to null }

        else -> null
    }
}

private fun field(reference: IrExpression?): String? =
    (reference as? IrPropertyReference)?.symbol?.owner?.name?.asString()
