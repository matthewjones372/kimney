package io.github.matthewjones372.kimney.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.types.IrType

/**
 * The properties a selector reads from its parameter, outermost first, as the checker read them. A safe call arrives
 * as a block binding the receiver to a temporary and a `when` reading through it, so a temporary stands for its path.
 */
internal fun selected(selector: IrFunctionExpression): List<String>? {
    val parameter = selector.function.parameters.singleOrNull() ?: return null
    val statement = (selector.function.body as? IrBlockBody)?.statements?.singleOrNull()
    val result = (statement as? IrReturn)?.value ?: statement as? IrExpression
    return path(result, mapOf(parameter to emptyList()))?.takeIf { it.isNotEmpty() }
}

private fun path(expression: IrExpression?, bound: Map<IrValueDeclaration, List<String>>): List<String>? =
    when (expression) {
        is IrGetValue -> bound[expression.symbol.owner]

        // The read through a safe call's temporary is cast to its non-null type first (seen on 2.4.10).
        is IrTypeOperatorCall -> path(expression.argument, bound)

        is IrCall -> expression.symbol.owner.correspondingPropertySymbol?.owner?.name?.asString()
            ?.let { name -> path(expression.arguments.firstOrNull(), bound)?.plus(name) }

        is IrBlock -> {
            val temporary = expression.statements.firstOrNull() as? IrVariable
            val read = (expression.statements.lastOrNull() as? IrWhen)?.branches?.lastOrNull()?.result
            temporary?.let { path(it.initializer, bound) }?.let { path(read, bound + (temporary to it)) }
        }

        else -> null
    }

/** The type of what [lambda] returns from its last statement. */
internal fun returned(lambda: IrFunctionExpression): IrType? {
    val last = (lambda.function.body as? IrBlockBody)?.statements?.lastOrNull()
    return ((last as? IrReturn)?.value ?: last as? IrExpression)?.type
}
