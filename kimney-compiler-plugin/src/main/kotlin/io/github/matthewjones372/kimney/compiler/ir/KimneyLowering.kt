package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.compiler.TRANSFORM_INTO
import io.github.matthewjones372.kimney.compiler.internalError
import io.github.matthewjones372.kimney.derive.Arg
import io.github.matthewjones372.kimney.derive.Derived
import io.github.matthewjones372.kimney.derive.Plan
import io.github.matthewjones372.kimney.derive.derive
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.primaryConstructor

/** Replaces each `transformInto` call with the constructor calls the engine planned, evaluating the source once. */
class KimneyLowering(
    private val context: IrPluginContext,
    private val messages: MessageCollector,
) : IrElementTransformerVoidWithContext() {
    private val model = IrTypeModel(context)

    override fun visitCall(expression: IrCall): IrExpression {
        val call = super.visitCall(expression) as? IrCall ?: return expression
        if (call.symbol.owner.callableId != TRANSFORM_INTO) return call
        val receiver = call.arguments[0] ?: return call

        return try {
            when (val derived = derive(model, receiver.type, call.type)) {
                is Derived.Planned -> builder(call).irBlock(resultType = call.type) {
                    +lower(derived.plan, receiver)
                }

                // The checker reports these first, so reaching one means the two adapters disagree.
                is Derived.Failed -> call.also {
                    messages.report(
                        CompilerMessageSeverity.ERROR,
                        "kimney's checker accepted a call its lowering cannot build. This is a bug in kimney.\n" +
                            derived.message(model.render(receiver.type), model.render(call.type)),
                    )
                }
            }
        } catch (e: Exception) {
            messages.report(CompilerMessageSeverity.ERROR, internalError(e))
            call
        }
    }

    private fun builder(call: IrCall): IrBuilderWithScope {
        val owner = planned(currentScope, "an enclosing scope").scope.scopeOwnerSymbol
        return DeclarationIrBuilder(context, owner, call.startOffset, call.endOffset)
    }

    private fun IrStatementsBuilder<*>.lower(plan: Plan<IrType>, value: IrExpression): IrExpression =
        when (plan) {
            Plan.Identity -> value

            is Plan.Construct -> {
                val source = irTemporary(value)
                val constructor = planned(plan.target.classOrFail.owner.primaryConstructor, "a primary constructor")
                val params = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
                val typeArguments = (plan.target as IrSimpleType).arguments.map { (it as IrTypeProjection).type }
                irCallConstructor(constructor.symbol, typeArguments).apply {
                    // A Default is left null: the backend's default-argument lowering fills it, as for a written call.
                    plan.args.filterIsInstance<Arg.FromProperty<IrType>>().forEach { arg ->
                        val param = params.single { it.name.asString() == arg.param }
                        arguments[param.indexInParameters] = lower(arg.plan, read(source, arg.param))
                    }
                }
            }
        }

    private fun IrStatementsBuilder<*>.read(source: IrVariable, name: String): IrExpression {
        val getter = planned(model.readable(source.type.classOrFail.owner, name)?.getter, "a getter for '$name'")
        val type = planned(model.property(source.type, name), "a type for '$name'")
        return irCall(getter.symbol, type).apply { dispatchReceiver = irGet(source) }
    }
}

/** The engine planned from these same lookups, so a miss here is the adapters disagreeing with themselves. */
private fun <A : Any> planned(value: A?, what: String): A =
    checkNotNull(value) { "the plan relies on $what that the IR does not have" }
