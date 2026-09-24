package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.derive.Container
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.ClassId

/** The loops `map` compiles to, written out: one new collection, filled element by element in source order. */
internal class ContainerLowering(context: IrPluginContext) {
    private val builtIns = context.irBuiltIns
    private val arrayList = context.referenceClass(ClassId.fromString("java/util/ArrayList"))
    private val linkedHashSet = context.referenceClass(ClassId.fromString("java/util/LinkedHashSet"))
    private val linkedHashMap = context.referenceClass(ClassId.fromString("java/util/LinkedHashMap"))

    /** A list is presized to the source where its size is known; a set is not, since its argument is a capacity. */
    fun IrStatementsBuilder<*>.iterables(
        kind: Container.Kind,
        target: IrType,
        from: IrType,
        value: IrExpression,
        element: IrStatementsBuilder<*>.(IrExpression) -> IrExpression,
    ): IrExpression {
        val source = irTemporary(value)
        val builder = checkNotNull(if (kind == Container.Kind.SET) linkedHashSet else arrayList) {
            "the JDK collection classes are on every JVM classpath"
        }
        val sized = kind == Container.Kind.LIST || kind == Container.Kind.COLLECTION
        val constructor = builder.owner.constructors.single { constructor ->
            val params = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
            if (sized) params.singleOrNull()?.type == builtIns.intType else params.isEmpty()
        }
        val out = irTemporary(
            construct(constructor.symbol, listOf(argument(target))).apply {
                if (sized) arguments[0] = size(builtIns.collectionClass, source)
            },
        )
        val add = member(builtIns.mutableCollectionClass, "add")
        loop(source, from) { item ->
            +irCall(add.symbol).apply {
                arguments[0] = irGet(out)
                arguments[1] = element(item)
            }
        }
        return irGet(out)
    }

    fun IrStatementsBuilder<*>.array(
        target: IrType,
        from: IrType,
        value: IrExpression,
        element: IrStatementsBuilder<*>.(IrExpression) -> IrExpression,
    ): IrExpression {
        val source = irTemporary(value)
        val targetElement = argument(target)
        val out = irTemporary(
            irCall(builtIns.arrayOfNulls, builtIns.arrayClass.typeWith(targetElement.makeNullable())).apply {
                typeArguments[0] = targetElement
                arguments[0] = size(builtIns.arrayClass, source)
            },
        )
        val index = irTemporary(irInt(0), isMutable = true)
        val set = member(builtIns.arrayClass, "set")
        val plus = builtIns.intClass.owner.functions.single {
            it.name.asString() == "plus" && it.parameters.singleOrNull { p -> p.kind == IrParameterKind.Regular }
                ?.type == builtIns.intType
        }
        loop(source, from) { item ->
            +irCall(set.symbol).apply {
                arguments[0] = irGet(out)
                arguments[1] = irGet(index)
                arguments[2] = element(item)
            }
            +irSet(
                index,
                irCall(plus.symbol).apply {
                    arguments[0] = irGet(index)
                    arguments[1] = irInt(1)
                },
            )
        }
        // Every slot has been filled, so the array holds no null; the cast is the one `Array(size) { … }` makes.
        return irImplicitCast(irGet(out), target)
    }

    /** Each entry of the source, in its order, into a new `LinkedHashMap`, the key and value each through its plan. */
    fun IrStatementsBuilder<*>.entries(
        target: IrType,
        from: Container<IrType>,
        source: IrExpression,
        key: IrStatementsBuilder<*>.(IrExpression) -> IrExpression,
        value: IrStatementsBuilder<*>.(IrExpression) -> IrExpression,
    ): IrExpression {
        val sourceKey = checkNotNull(from.key) { "a map has a key" }
        val map = irTemporary(source)
        val builder = checkNotNull(linkedHashMap) { "the JDK collection classes are on every JVM classpath" }
        val constructor = builder.owner.constructors.single { constructor ->
            constructor.parameters.none { it.kind == IrParameterKind.Regular }
        }
        val targetArguments = (target as IrSimpleType).arguments.map { checkNotNull((it as? IrTypeProjection)?.type) }
        val out = irTemporary(construct(constructor.symbol, targetArguments))
        val entryType = builtIns.mapEntryClass.typeWith(sourceKey, from.element)
        val entriesGetter =
            checkNotNull(builtIns.mapClass.owner.properties.single { it.name.asString() == "entries" }.getter)
        val entries = irTemporary(
            irCall(entriesGetter.symbol, builtIns.setClass.typeWith(entryType)).apply { arguments[0] = irGet(map) },
        )
        val put = member(builtIns.mutableMapClass, "put")
        loop(entries, entryType) { item ->
            val entry = irTemporary(item)
            // `put` returns the previous value, typed as the target's `V?`, not as `MutableMap`'s own `V`.
            +irCall(put.symbol, targetArguments[1].makeNullable()).apply {
                arguments[0] = irGet(out)
                arguments[1] = key(entryPart(entry, "key", sourceKey))
                arguments[2] = value(entryPart(entry, "value", from.element))
            }
        }
        return irGet(out)
    }

    private fun IrStatementsBuilder<*>.entryPart(entry: IrVariable, name: String, type: IrType): IrExpression {
        val getter = checkNotNull(builtIns.mapEntryClass.owner.properties.single { it.name.asString() == name }.getter)
        return irCall(getter.symbol, type).apply { arguments[0] = irGet(entry) }
    }

    /** `for (item in source)`, through the iterator an `Iterable` or an `Array` hands out. */
    private fun IrStatementsBuilder<*>.loop(
        source: IrVariable,
        element: IrType,
        body: IrStatementsBuilder<*>.(IrExpression) -> Unit,
    ) {
        val owner = if (source.type.isArray()) builtIns.arrayClass else builtIns.iterableClass
        val iterator = irTemporary(
            irCall(member(owner, "iterator").symbol, builtIns.iteratorClass.typeWith(element)).apply {
                arguments[0] = irGet(source)
            },
        )
        val hasNext = member(builtIns.iteratorClass, "hasNext")
        val next = member(builtIns.iteratorClass, "next")
        val loop = IrWhileLoopImpl(startOffset, endOffset, builtIns.unitType, null)
        loop.condition = irCall(hasNext.symbol).apply { arguments[0] = irGet(iterator) }
        loop.body = irBlock { body(irCall(next.symbol, element).apply { arguments[0] = irGet(iterator) }) }
        +loop
    }

    private fun IrStatementsBuilder<*>.size(owner: IrClassSymbol, source: IrVariable): IrExpression {
        val getter = checkNotNull(owner.owner.properties.single { it.name.asString() == "size" }.getter)
        return irCall(getter.symbol).apply { arguments[0] = irGet(source) }
    }

    private fun member(owner: IrClassSymbol, name: String): IrSimpleFunction =
        owner.owner.functions.first { it.name.asString() == name }

    private fun IrType.isArray(): Boolean = (this as? IrSimpleType)?.classifier == builtIns.arrayClass

    private fun argument(type: IrType): IrType =
        checkNotNull(((type as IrSimpleType).arguments.first() as? IrTypeProjection)?.type) {
            "a container has an element"
        }
}
