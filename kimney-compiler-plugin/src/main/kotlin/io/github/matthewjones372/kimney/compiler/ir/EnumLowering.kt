package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.derive.Plan
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEqeqeq
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrType

internal class EnumLowering(private val model: IrTypeModel) {

    /** Entries compared by identity, not by ordinal: an enum compiled elsewhere may be reordered after this build. */
    fun IrStatementsBuilder<*>.enumByName(plan: Plan.EnumByName<IrType>, value: IrExpression): IrExpression {
        val source = irTemporary(value)
        val branches = plan.entries.map { name ->
            irBranch(irEqeqeq(irGet(source), entry(plan.source, name)), entry(plan.target, name))
        }
        val otherwise = irElseBranch(irCall(context.irBuiltIns.noWhenBranchMatchedExceptionSymbol))
        return irWhen(plan.target, branches + otherwise)
    }

    private fun IrBuilderWithScope.entry(type: IrType, name: String): IrExpression {
        val entry = planned(
            model.classOf(type)?.declarations?.filterIsInstance<IrEnumEntry>()?.firstOrNull {
                it.name.asString() == name
            },
            "an entry '$name'",
        )
        return IrGetEnumValueImpl(startOffset, endOffset, type, entry.symbol)
    }
}
