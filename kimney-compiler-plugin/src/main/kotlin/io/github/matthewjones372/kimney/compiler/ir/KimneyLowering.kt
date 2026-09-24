package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.compiler.KimneyErrors
import io.github.matthewjones372.kimney.compiler.TRANSFORM
import io.github.matthewjones372.kimney.compiler.TRANSFORMER
import io.github.matthewjones372.kimney.compiler.TRANSFORM_INTO
import io.github.matthewjones372.kimney.compiler.guarded
import io.github.matthewjones372.kimney.derive.Arg
import io.github.matthewjones372.kimney.derive.Container
import io.github.matthewjones372.kimney.derive.Derived
import io.github.matthewjones372.kimney.derive.Plan
import io.github.matthewjones372.kimney.derive.Supplied
import io.github.matthewjones372.kimney.derive.derive
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irIs
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name

/** Replaces each `transformInto` call with the constructor calls the engine planned, evaluating the source once. */
class KimneyLowering(private val context: IrPluginContext) : IrElementTransformerVoidWithContext() {
    private val model = IrTypeModel(context)
    private val containers = ContainerLowering(context)
    private val enums = EnumLowering(model)
    private val transformFunction = context.referenceClass(TRANSFORMER)?.owner?.functions
        ?.single { it.name.asString() == "transform" }

    override fun visitCall(expression: IrCall): IrExpression {
        val call = super.visitCall(expression) as? IrCall ?: return expression
        val report = { message: String -> report(call, message) }
        return guarded(fallback = { call }, report = report) {
            when (call.kimneyId) {
                TRANSFORM_INTO -> call.arguments[0]?.let { lowered(call, IrChain(it, emptyList(), emptyList())) }
                    ?: call

                TRANSFORM -> readChain(call)?.let { lowered(call, it) }
                    ?: disagreed(call, "Its override chain cannot be read back to into().")

                else -> call
            }
        }
    }

    private fun lowered(call: IrCall, chain: IrChain): IrExpression {
        val inContext = allScopes.contextTransformers(start = chain.given.size)
        val transformers = chain.transformers + inContext.map { it.first }
        return when (val derived = derive(model, chain.source.type, call.type, chain.overrides, transformers)) {
            is Derived.Planned -> builder(call).irBlock(resultType = call.type) {
                val source = irTemporary(chain.source)
                // Evaluated here, in written order, so side effects happen as the chain reads.
                val given = Given(
                    chain.given.map { it?.let { expression -> irTemporary(given(expression, source)) } } +
                        inContext.map { it.second },
                )
                +lower(derived.plan, irGet(source), given)
            }

            is Derived.Failed ->
                disagreed(call, derived.message(model.render(chain.source.type), model.render(call.type)))
        }
    }

    // The checker reports these first, so reaching one means the two adapters disagree.
    private fun disagreed(call: IrCall, why: String): IrCall = call.also {
        report(call, "kimney's checker accepted a call its lowering cannot build. This is a bug in kimney.\n$why")
    }

    private fun report(call: IrCall, message: String) {
        context.diagnosticReporter.at(call, currentFile).report(KimneyErrors.KIMNEY_INTERNAL_ERROR, message)
    }

    /** A const's expression as written; a computed lambda as a direct call of a local function, not an object. */
    private fun IrStatementsBuilder<*>.given(expression: IrExpression, source: IrVariable): IrExpression {
        val lambda = (expression as? IrFunctionExpression)?.function ?: return expression
        lambda.origin = IrDeclarationOrigin.LOCAL_FUNCTION
        +lambda
        return irCall(lambda.symbol).apply { arguments[0] = irGet(source) }
    }

    private fun builder(call: IrCall): IrBuilderWithScope {
        val owner = planned(currentScope, "an enclosing scope").scope.scopeOwnerSymbol
        return DeclarationIrBuilder(context, owner, call.startOffset, call.endOffset)
    }

    private fun IrStatementsBuilder<*>.lower(
        plan: Plan<IrType>,
        value: IrExpression,
        given: Given,
    ): IrExpression =
        when (plan) {
            Plan.Identity -> value

            is Plan.Named -> named(plan, value, given)

            // Neither adapter derives in partial mode yet, so the engine plans no check.
            is Plan.Required ->
                error("kimney planned ${plan::class.simpleName}, which this lowering does not build yet")

            is Plan.Reference -> irCall(planned(given.named[plan.depth], "the plan it refers back to").symbol).apply {
                arguments[0] = value
            }

            is Plan.Transformed -> {
                val transform = planned(transformFunction, "Transformer.transform on the classpath")
                val call = irCall(transform.symbol, context.irBuiltIns.anyNType).apply {
                    arguments[0] = irGet(planned(given[plan.index], "a transformer value"))
                    arguments[1] = value
                }
                irImplicitCast(call, plan.target)
            }

            is Plan.ObjectInstance -> irGetObjectValue(plan.target, plan.target.classOrFail)

            is Plan.EnumByName -> with(enums) { enumByName(plan, value) }

            is Plan.SealedByName -> sealedByName(plan, value, given)

            is Plan.NullSafe -> nullSafe(plan, value, given)

            is Plan.Wrap -> {
                val constructor = planned(plan.target.classOrFail.owner.primaryConstructor, "a value class constructor")
                irCallConstructor(constructor.symbol, emptyList()).apply {
                    arguments[0] = lower(plan.plan, value, given)
                }
            }

            is Plan.Unwrap -> lower(plan.plan, read(irTemporary(value), plan.property), given)

            is Plan.Elements -> elements(plan, value, given)

            is Plan.Entries -> {
                val from = planned(model.container(value.type), "a source map")
                with(containers) {
                    entries(plan.target, from, value, { lower(plan.key, it, given) }, { lower(plan.value, it, given) })
                }
            }

            is Plan.Construct -> {
                val source = irTemporary(value)
                val constructor = planned(plan.target.classOrFail.owner.primaryConstructor, "a primary constructor")
                val params = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
                val typeArguments = (plan.target as IrSimpleType).arguments.map { (it as IrTypeProjection).type }
                irCallConstructor(constructor.symbol, typeArguments).apply {
                    plan.args.forEach { arg ->
                        val index = params.single { it.name.asString() == arg.param }.indexInParameters
                        when (arg) {
                            is Arg.FromProperty -> arguments[index] = lower(arg.plan, read(source, arg.property), given)

                            is Arg.Const -> arguments[index] = irGet(planned(given[arg.index], "a const value"))

                            is Arg.Computed -> arguments[index] = irGet(planned(given[arg.index], "a computed value"))

                            // Left null: the backend's default-argument lowering fills it, as for a written call.
                            is Arg.Default -> Unit
                        }
                    }
                }
            }
        }

    private fun IrStatementsBuilder<*>.elements(
        plan: Plan.Elements<IrType>,
        value: IrExpression,
        given: Given,
    ): IrExpression {
        val from = planned(model.container(value.type), "a source container").element
        val element: IrStatementsBuilder<*>.(IrExpression) -> IrExpression = { item -> lower(plan.plan, item, given) }
        return with(containers) {
            if (plan.kind == Container.Kind.ARRAY) {
                array(plan.target, from, value, element)
            } else {
                iterables(plan.kind, plan.target, from, value, element)
            }
        }
    }

    /**
     * A plan its own pair refers back to, as a local function: declared here, called here, and called again by each
     * reference inside it. It stays inside the call's block, so nothing is added to the class or the file.
     */
    private fun IrStatementsBuilder<*>.named(
        plan: Plan.Named<IrType>,
        value: IrExpression,
        given: Given,
    ): IrExpression {
        val function = context.irFactory.buildFun {
            name = Name.identifier("transform\$${plan.depth}")
            returnType = plan.target
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION
        }
        function.parent = scope.getLocalDeclarationParent()
        val source = function.addValueParameter("source", plan.source)
        val inside = given.copy(named = given.named + (plan.depth to function))
        function.body = DeclarationIrBuilder(context, function.symbol).irBlockBody {
            +irReturn(lower(plan.plan, irGet(source), inside))
        }
        +function
        return irCall(function.symbol).apply { arguments[0] = value }
    }

    /** The source held once; the inner plan in a block of its own, so nothing it declares runs for a null. */
    private fun IrStatementsBuilder<*>.nullSafe(
        plan: Plan.NullSafe<IrType>,
        value: IrExpression,
        given: Given,
    ): IrExpression {
        val source = irTemporary(value)
        val present = irBlock { +lower(plan.plan, irImplicitCast(irGet(source), source.type.makeNotNull()), given) }
        return irIfNull(present.type.makeNullable(), irGet(source), irNull(), present)
    }

    /** Each arm is a block of its own, so the temporaries it declares run only once the case has matched. */
    private fun IrStatementsBuilder<*>.sealedByName(
        plan: Plan.SealedByName<IrType>,
        value: IrExpression,
        given: Given,
    ): IrExpression {
        val source = irTemporary(value)
        val branches = plan.arms.map { arm ->
            val body = irBlock(resultType = arm.target) {
                +lower(arm.plan, irImplicitCast(irGet(source), arm.source), given)
            }
            irBranch(irIs(irGet(source), arm.source), body)
        }
        val otherwise = irElseBranch(irCall(context.irBuiltIns.noWhenBranchMatchedExceptionSymbol))
        return irWhen(plan.target, branches + otherwise)
    }

    private fun IrStatementsBuilder<*>.read(source: IrVariable, name: String): IrExpression {
        val getter = planned(model.readable(source.type.classOrFail.owner, name)?.getter, "a getter for '$name'")
        val type = planned(model.property(source.type, name), "a type for '$name'")
        return irCall(getter.symbol, type).apply { dispatchReceiver = irGet(source) }
    }
}

/** The engine planned from these same lookups, so a miss here is the adapters disagreeing with themselves. */
internal fun <A : Any> planned(value: A?, what: String): A =
    checkNotNull(value) { "the plan relies on $what that the IR does not have" }

/** Every `Transformer` context parameter of the functions and lambdas around the call, innermost first. */
internal fun List<ScopeWithIr>.contextTransformers(start: Int): List<Pair<Supplied<IrType>, IrValueParameter>> =
    asReversed()
        .mapNotNull { it.irElement as? IrFunction }
        .flatMap { function -> function.parameters.filter { it.kind == IrParameterKind.Context } }
        .mapNotNull { parameter ->
            val type = parameter.type as? IrSimpleType
            val arguments = type?.takeIf { it.classOrNull?.owner?.let(::isTransformer) == true }?.arguments
                ?.map { (it as? IrTypeProjection)?.type }
            val (from, to) = arguments ?: return@mapNotNull null
            if (from == null || to == null) null else Triple(from, to, parameter)
        }
        .mapIndexed { i, (from, to, parameter) ->
            Supplied(from, to, start + i, context = parameter.name.asString()) to parameter
        }

private fun isTransformer(irClass: IrClass): Boolean = irClass.classId == TRANSFORMER

/** What a plan's lowering can reach: the chain's values by index, and the named plans around it by depth. */
internal data class Given(val values: List<IrValueDeclaration?>, val named: Map<Int, IrSimpleFunction> = emptyMap()) {
    operator fun get(index: Int): IrValueDeclaration? = values.getOrNull(index)
}
