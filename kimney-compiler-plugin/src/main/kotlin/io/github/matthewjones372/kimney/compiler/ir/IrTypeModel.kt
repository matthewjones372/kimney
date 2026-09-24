package io.github.matthewjones372.kimney.compiler.ir

import io.github.matthewjones372.kimney.derive.Case
import io.github.matthewjones372.kimney.derive.Construction
import io.github.matthewjones372.kimney.derive.Container
import io.github.matthewjones372.kimney.derive.Param
import io.github.matthewjones372.kimney.derive.TypeModel
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.FqName

/** The IR side of [TypeModel]. Every eligibility rule mirrors `FirTypeModel`, or checker and lowering disagree. */
class IrTypeModel(context: IrPluginContext) : TypeModel<IrType> {
    private val builtIns = context.irBuiltIns
    private val typeSystem = IrTypeSystemContextImpl(builtIns)

    override fun render(type: IrType): String {
        val simple = rigid(type) as? IrSimpleType ?: return type.toString()
        val name = when (val classifier = simple.classifier) {
            is IrClassSymbol -> relativeName(classifier.owner) +
                simple.arguments.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ", "<", ">") { (it as? IrTypeProjection)?.type?.let(::render) ?: "*" }
                    .orEmpty()

            is IrTypeParameterSymbol -> classifier.owner.name.asString()

            else -> simple.toString()
        }
        return if (simple.isMarkedNullable()) "$name?" else name
    }

    override fun isSubtypeOf(sub: IrType, sup: IrType): Boolean = rigid(sub).isSubtypeOf(rigid(sup), typeSystem)

    override fun construction(type: IrType): Construction<IrType> {
        val irClass = constructible(type) ?: return Construction.NotAClass
        val primary = irClass.primaryConstructor
        val substitution = substitution(irClass, type)
        return when {
            primary == null -> Construction.SecondaryOnly

            substitution == null -> Construction.NotAClass

            primary.visibility != DescriptorVisibilities.PUBLIC -> Construction.NotPublic(primary.visibility.name)

            else -> Construction.Primary(
                primary.parameters.filter { it.kind == IrParameterKind.Regular }.map {
                    Param(it.name.asString(), it.type.substitute(substitution), it.defaultValue != null)
                },
            )
        }
    }

    override fun enumEntries(type: IrType): List<String>? =
        classOf(type)?.takeIf { it.kind == ClassKind.ENUM_CLASS }
            ?.declarations
            ?.filterIsInstance<IrEnumEntry>()
            ?.map { it.name.asString() }

    /** Direct subclasses, each a case only if it is itself non-generic, as `FirTypeModel` has it. */
    override fun sealedCases(type: IrType): List<Case<IrType>>? {
        val irClass = classOf(type)?.takeIf { it.modality == Modality.SEALED && it.typeParameters.isEmpty() }
            ?: return null
        val cases = irClass.sealedSubclasses.map { it.owner }.map { case ->
            case.takeIf { it.typeParameters.isEmpty() }?.let { Case(it.name.asString(), it.defaultType as IrType) }
        }
        return cases.filterNotNull().takeIf { it.size == cases.size }
    }

    override fun caseName(type: IrType): String? = classOf(type)
        ?.takeIf { case -> case.superTypes.any { it.classOrNull?.owner?.modality == Modality.SEALED } }
        ?.name?.asString()

    override fun isObject(type: IrType): Boolean = classOf(type)?.kind == ClassKind.OBJECT

    fun classOf(type: IrType): IrClass? = type.takeUnless { isNullable(it) }?.classOrNull?.owner

    // IR carries a Java platform type as nullable with this annotation; FIR takes its non-null bound, and so must this.
    override fun isNullable(type: IrType): Boolean =
        type.isMarkedNullable() && !type.hasAnnotation(FLEXIBLE_NULLABILITY)

    override fun nonNull(type: IrType): IrType = type.makeNotNull()

    /** A platform type at its non-null bound, which is how FIR has already judged it. */
    private fun rigid(type: IrType): IrType = if (type.hasAnnotation(FLEXIBLE_NULLABILITY)) type.makeNotNull() else type

    override fun valueClass(type: IrType): Param<IrType>? {
        val irClass = classOf(type)?.takeIf { it.isValue && it.typeParameters.isEmpty() } ?: return null
        val inner = irClass.primaryConstructor?.parameters?.singleOrNull { it.kind == IrParameterKind.Regular }
        return inner?.let { Param(it.name.asString(), it.type, hasDefault = false) }
    }

    /** The read-only interfaces and `Array` only, as `FirTypeModel` has them. */
    override fun container(type: IrType): Container<IrType>? {
        val simple = rigid(type).takeUnless { it.isMarkedNullable() } as? IrSimpleType ?: return null
        val kind = containers[simple.classifier] ?: return null
        val arguments = simple.arguments.map { (it as? IrTypeProjection)?.type ?: return null }
        return if (kind == Container.Kind.MAP) {
            Container(kind, arguments[1], key = arguments[0])
        } else {
            Container(kind, arguments[0])
        }
    }

    private val containers = mapOf(
        builtIns.listClass to Container.Kind.LIST,
        builtIns.setClass to Container.Kind.SET,
        builtIns.collectionClass to Container.Kind.COLLECTION,
        builtIns.iterableClass to Container.Kind.ITERABLE,
        builtIns.mapClass to Container.Kind.MAP,
        builtIns.arrayClass to Container.Kind.ARRAY,
    )

    override fun property(owner: IrType, name: String): IrType? {
        val irClass = owner.takeUnless { it.isMarkedNullable() }?.classOrNull?.owner ?: return null
        val getter = readable(irClass, name)?.getter ?: return null
        return substitution(irClass, owner)?.let { getter.returnType.substitute(it) }
    }

    /** The declared, public, non-extension property [name] of [irClass]; inherited ones are fake overrides here. */
    fun readable(irClass: IrClass, name: String): IrProperty? = irClass.properties.firstOrNull {
        it.name.asString() == name && !it.isFakeOverride && it.visibility == DescriptorVisibilities.PUBLIC &&
            it.getter?.parameters?.none { p -> p.kind == IrParameterKind.ExtensionReceiver } == true
    }

    private fun constructible(type: IrType): IrClass? {
        val irClass = type.takeUnless { it.isMarkedNullable() }?.classOrNull?.owner ?: return null
        val eligible = irClass.kind == ClassKind.CLASS &&
            irClass.modality in setOf(Modality.FINAL, Modality.OPEN) &&
            !irClass.isInner &&
            irClass.packageFqName?.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME) != true &&
            irClass.origin != IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
        return irClass.takeIf { eligible }
    }

    private fun substitution(irClass: IrClass, type: IrType): Map<IrTypeParameterSymbol, IrType>? {
        val arguments = (type as? IrSimpleType)?.arguments?.map { (it as? IrTypeProjection)?.type ?: return null }
            ?: return null
        return irClass.typeParameters.map { it.symbol }.zip(arguments).toMap()
    }
}

private fun relativeName(irClass: IrClass): String =
    generateSequence(irClass) {
        it.parent as? IrClass
    }.toList().asReversed().joinToString(".") { it.name.asString() }

private val FLEXIBLE_NULLABILITY = FqName("kotlin.internal.ir.FlexibleNullability")
