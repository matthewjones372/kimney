package io.github.matthewjones372.kimney.compiler.ir

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructedClass

/**
 * A constructor call typed with the class it builds, `Box<Int>`, rather than with the constructor's declared return
 * type, `Box<T>`: `irCallConstructor` leaves the latter, which names a type parameter outside its scope. Kotlin
 * 2.4.20 validates plugin IR and rejects it; earlier compilers accepted it.
 */
internal fun IrBuilderWithScope.construct(
    constructor: IrConstructorSymbol,
    typeArguments: List<IrType>,
): IrConstructorCall =
    irCallConstructor(constructor, typeArguments).apply {
        type = constructor.owner.constructedClass.typeWith(typeArguments)
    }
