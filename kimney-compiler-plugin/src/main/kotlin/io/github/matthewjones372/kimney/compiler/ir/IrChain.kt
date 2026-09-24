package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_COMPUTED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_CONST
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_RENAMED
import io.github.matthewjones372.kimney.compiler.WITH_PARTIAL_TRANSFORMER
import io.github.matthewjones372.kimney.compiler.WITH_TRANSFORMER
import io.github.matthewjones372.kimney.derive.Override
import io.github.matthewjones372.kimney.derive.Supplied
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.name.CallableId

/**
 * The chain behind a `transform()` call: the source, each override and transformer in written order, and the
 * expression each link gave, indexed by its place in the chain.
 */
data class IrChain(
    val source: IrExpression,
    val overrides: List<Override<IrType>>,
    val given: List<IrExpression?>,
    val transformers: List<Supplied<IrType>> = emptyList(),
)

private sealed interface Link {
    val given: IrExpression?

    data class Overriding(val override: Override<IrType>, override val given: IrExpression?) : Link

    data class Transforming(val supplied: Supplied<IrType>, override val given: IrExpression) : Link
}

/**
 * The call's id, or null for a local function: `callableId` throws for one, and every call in a file passes through
 * the lowering, so asking a local function's call for it failed any file that made one (found on 2.4.10).
 */
internal val IrCall.kimneyId: CallableId?
    get() = symbol.owner.takeUnless { it.isLocal }?.callableId

/** Null when the chain is not the one expression the checker accepted. */
fun readChain(transform: IrCall): IrChain? {
    // Stops at into(): the source may itself be a call, and is not part of the chain.
    val calls = generateSequence(transform.arguments[0] as? IrCall) {
        (it.arguments[0] as? IrCall)?.takeUnless { _ -> it.kimneyId == INTO }
    }
        .toList()
        .asReversed()
    val source = calls.firstOrNull()?.takeIf { it.kimneyId == INTO }?.arguments?.get(0)
    val links = calls.drop(1).mapIndexed { index, call -> link(call, index) }
    val complete = links.filterNotNull().takeIf { it.size == links.size }
    return if (source == null || complete == null) {
        null
    } else {
        IrChain(
            source,
            complete.filterIsInstance<Link.Overriding>().map { it.override },
            complete.map { it.given },
            complete.filterIsInstance<Link.Transforming>().map { it.supplied },
        )
    }
}

private fun link(call: IrCall, index: Int): Link? {
    val args = call.symbol.owner.parameters.associate { it.name.asString() to call.arguments[it.indexInParameters] }
    if (call.kimneyId == WITH_TRANSFORMER || call.kimneyId == WITH_PARTIAL_TRANSFORMER) {
        // The transformer's types are the call's own type arguments, as the checker read them.
        val (from, to) = call.typeArguments
        val transformer = args["transformer"]
        return if (from == null || to == null || transformer == null) {
            null
        } else {
            val canFail = call.kimneyId == WITH_PARTIAL_TRANSFORMER
            Link.Transforming(Supplied(from, to, index, canFail = canFail), transformer)
        }
    }
    val field = field(args[if (call.kimneyId == WITH_FIELD_RENAMED) "to" else "field"]) ?: return null
    return when (call.kimneyId) {
        WITH_FIELD_CONST -> args["value"]?.let { Link.Overriding(Override.Const(field, it.type, index), it) }

        WITH_FIELD_COMPUTED -> (args["compute"] as? IrFunctionExpression)
            ?.let { Link.Overriding(Override.Computed(field, it.function.returnType, index), it) }

        WITH_FIELD_RENAMED -> field(args["from"])?.let { Link.Overriding(Override.Renamed(field, it), null) }

        else -> null
    }
}

private fun field(reference: IrExpression?): String? =
    (reference as? IrPropertyReference)?.symbol?.owner?.name?.asString()
