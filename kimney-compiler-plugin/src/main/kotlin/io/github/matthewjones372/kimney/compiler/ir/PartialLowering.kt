package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.compiler.PARTIAL_ERROR
import io.github.matthewjones372.kimney.compiler.PARTIAL_ERRORS
import io.github.matthewjones372.kimney.compiler.PARTIAL_OK
import io.github.matthewjones372.kimney.compiler.RELOCATED_TO
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irIs
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * A partial transformation's runtime: an error list threaded through the call's block, checks that record into it,
 * and constructors that run only if nothing was recorded while their arguments were built. Every value in between
 * is typed `Any?`, so a part that failed is a null that never reaches a constructor or an unboxing.
 */
internal class PartialLowering(context: IrPluginContext) {
    private val builtIns = context.irBuiltIns
    private val arrayList = context.referenceClass(ClassId.fromString("java/util/ArrayList"))
    private val illegalArgument = context.referenceClass(ClassId.fromString("java/lang/IllegalArgumentException"))
    private val partialError = context.referenceClass(PARTIAL_ERROR)
    private val ok = context.referenceClass(PARTIAL_OK)
    private val errorsClass = context.referenceClass(PARTIAL_ERRORS)
    private val relocatedTo = context.referenceFunctions(RELOCATED_TO).singleOrNull()

    val anything: IrType get() = builtIns.anyNType

    fun IrStatementsBuilder<*>.errorList(): IrVariable {
        val list = planned(arrayList, "java.util.ArrayList")
        val constructor = list.owner.constructors.single { c ->
            c.parameters.none { it.kind == IrParameterKind.Regular }
        }
        return irTemporary(construct(constructor.symbol, listOf(type(partialError))))
    }

    /** A null records `is null` at [path]; a value goes on through [present]. */
    fun IrStatementsBuilder<*>.required(
        errors: IrVariable,
        path: String,
        value: IrExpression,
        present: IrStatementsBuilder<*>.(IrExpression) -> IrExpression,
    ): IrExpression {
        val source = irTemporary(value)
        val absent = irBlock(resultType = anything) {
            +record(errors, path, irString("is null"))
            +irNull()
        }
        val there = irBlock(resultType = anything) {
            +present(irImplicitCast(irGet(source), source.type.makeNotNull()))
        }
        return irIfNull(anything, irGet(source), absent, there)
    }

    /** The count before a construction's arguments are built, to tell afterwards whether any of them failed. */
    fun IrStatementsBuilder<*>.mark(errors: IrVariable): IrVariable = irTemporary(size(errors))

    /** [build] runs only if nothing was recorded since [mark]; an `IllegalArgumentException` from it is recorded. */
    fun IrStatementsBuilder<*>.guarded(
        errors: IrVariable,
        mark: IrVariable,
        path: String,
        build: () -> IrExpression,
    ): IrExpression {
        val iae = planned(illegalArgument, "java.lang.IllegalArgumentException")
        val caught = buildVariable(
            scope.getLocalDeclarationParent(),
            startOffset,
            endOffset,
            IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier("rejected"),
            iae.defaultType,
        )
        val getter = checkNotNull(property(builtIns.throwableClass, "message"))
        val recovered = irBlock(resultType = anything) {
            val message = irTemporary(irCall(getter.symbol).apply { arguments[0] = irGet(caught) })
            val text = irIfNull(builtIns.stringType, irGet(message), irString("is not valid"), irGet(message))
            +record(errors, path, text)
            +irNull()
        }
        val handler = IrCatchImpl(startOffset, endOffset, caught, recovered)
        val attempt = irTry(anything, build(), listOf(handler), null)
        return irIfThenElse(anything, irEquals(size(errors), irGet(mark)), attempt, irNull())
    }

    /** A partial transformer's result: its value, or its errors re-rooted at [path] and no value. */
    fun IrStatementsBuilder<*>.unwrapped(errors: IrVariable, path: String, result: IrExpression): IrExpression {
        val outcome = irTemporary(result, irType = anything)
        val okClass = planned(ok, "kimney-runtime's Partial.Ok")
        val value = okClass.owner.properties.single { it.name.asString() == "value" }.getter
        val relocate = planned(relocatedTo, "Partial.Errors.relocatedTo")
        val addAll = builtIns.mutableCollectionClass.owner.functions.first { it.name.asString() == "addAll" }
        val failed = irBlock(resultType = anything) {
            +irCall(addAll.symbol).apply {
                arguments[0] = irGet(errors)
                arguments[1] = irCall(relocate).apply {
                    arguments[0] = irImplicitCast(irGet(outcome), type(errorsClass))
                    arguments[1] = irString(path)
                }
            }
            +irNull()
        }
        // `Ok<Any?>`, not `Ok<T>`: this code is outside `Ok`, where its `T` means nothing.
        val ok = okClass.typeWith(anything)
        val succeeded = irCall(checkNotNull(value).symbol, anything).apply {
            arguments[0] = irImplicitCast(irGet(outcome), ok)
        }
        return irIfThenElse(anything, irIs(irGet(outcome), ok), succeeded, failed)
    }

    /** `Ok` with the value if nothing was recorded, `Errors` with everything that was otherwise. */
    fun IrStatementsBuilder<*>.result(
        resultType: IrType,
        target: IrType,
        errors: IrVariable,
        value: IrExpression,
    ): IrExpression {
        val built = irTemporary(value, irType = anything)
        val isEmpty = builtIns.collectionClass.owner.functions.single { it.name.asString() == "isEmpty" }
        val clean = irCall(isEmpty.symbol).apply { arguments[0] = irGet(errors) }
        val success = construct(constructorOf(ok), listOf(target)).apply {
            arguments[0] = irImplicitCast(irGet(built), target)
        }
        val failure = construct(constructorOf(errorsClass), emptyList()).apply { arguments[0] = irGet(errors) }
        return irIfThenElse(resultType, clean, success, failure)
    }

    private fun IrStatementsBuilder<*>.record(errors: IrVariable, path: String, message: IrExpression): IrExpression {
        val add = builtIns.mutableCollectionClass.owner.functions.first { it.name.asString() == "add" }
        return irCall(add.symbol).apply {
            arguments[0] = irGet(errors)
            arguments[1] = construct(constructorOf(partialError), emptyList()).apply {
                arguments[0] = irString(path)
                arguments[1] = message
            }
        }
    }

    private fun IrStatementsBuilder<*>.size(errors: IrVariable): IrExpression {
        val getter = checkNotNull(property(builtIns.collectionClass, "size"))
        return irCall(getter.symbol).apply { arguments[0] = irGet(errors) }
    }

    private fun property(owner: IrClassSymbol, name: String) =
        owner.owner.properties.single { it.name.asString() == name }.getter

    private fun constructorOf(owner: IrClassSymbol?) =
        planned(owner, "kimney-runtime's partial types").owner.constructors.single().symbol

    private fun type(owner: IrClassSymbol?): IrType = planned(owner, "kimney-runtime's PartialError").defaultType
}
