package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.derive.Case
import io.github.matthewjones372.kimney.derive.Construction
import io.github.matthewjones372.kimney.derive.Container
import io.github.matthewjones372.kimney.derive.Param
import io.github.matthewjones372.kimney.derive.TypeModel
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.collectEnumEntries
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.getSealedClassInheritors
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds

/** Every class it reads is recorded in [lookups], so incremental compilation recompiles a call when one changes. */
class FirTypeModel(private val session: FirSession, private val lookups: Lookups) : TypeModel<ConeKotlinType> {

    override fun render(type: ConeKotlinType): String {
        val rigid = type.lowerBoundIfFlexible()
        val name = when (rigid) {
            is ConeClassLikeType -> rigid.lookupTag.classId.relativeClassName.asString() +
                rigid.typeArguments.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ", "<", ">") { (it as? ConeKotlinType)?.let(::render) ?: "*" }
                    .orEmpty()

            is ConeTypeParameterType -> rigid.lookupTag.name.asString()

            else -> rigid.toString()
        }
        return if (rigid.isMarkedNullable) "$name?" else name
    }

    override fun isSubtypeOf(sub: ConeKotlinType, sup: ConeKotlinType): Boolean {
        expanded(sub)
        expanded(sup)
        return sub.isSubtypeOf(sup, session)
    }

    override fun construction(type: ConeKotlinType): Construction<ConeKotlinType> {
        val (classType, symbol) = constructible(type) ?: return Construction.NotAClass
        lookups.member(symbol.classId, SpecialNames.INIT.asString())
        val primary = symbol.constructors(session).firstOrNull { it.isPrimary }
        val substitutor = substitutor(symbol, classType)
        return when {
            primary == null -> Construction.SecondaryOnly

            substitutor == null -> Construction.NotAClass

            primary.resolvedStatus.visibility != Visibilities.Public ->
                Construction.NotPublic(primary.resolvedStatus.visibility.name)

            else -> Construction.Primary(
                primary.valueParameterSymbols.map {
                    Param(it.name.asString(), substitutor.substituteOrSelf(it.resolvedReturnType), it.hasDefaultValue)
                },
            )
        }
    }

    override fun enumEntries(type: ConeKotlinType): List<String>? =
        classOf(type)?.takeIf { it.classKind == ClassKind.ENUM_CLASS }
            ?.let { enum ->
                enum.collectEnumEntries(session).map { it.name.asString() }.onEach { lookups.member(enum.classId, it) }
            }

    /** Direct inheritors, each a case only if it is itself non-generic: generic hierarchies are not modelled. */
    @OptIn(SymbolInternals::class)
    override fun sealedCases(type: ConeKotlinType): List<Case<ConeKotlinType>>? {
        val classType = expanded(type) as? ConeClassLikeType
        val symbol = classOf(type)?.takeIf { it.resolvedStatus.modality == Modality.SEALED } ?: return null
        val arguments = classType?.typeArguments?.map { (it as? ConeKotlinTypeProjection)?.type ?: return null }
            ?: return null
        val cases = symbol.fir.getSealedClassInheritors(session).map { id ->
            lookups.type(id)
            (session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol)
                ?.let { caseType(it, symbol.classId, arguments) }
                ?.let { Case(id.shortClassName.asString(), it) }
        }
        return cases.filterNotNull().takeIf { it.size == cases.size }
    }

    /**
     * A case as a concrete type: its parameters solved from where its sealed supertype's arguments name them
     * directly, `Ok<T> : Result<T>` meeting `Result<User>` being `Ok<User>`. Null when one appears only inside
     * another type, `Many<T> : Box<List<T>>`, which is not solved.
     */
    private fun caseType(
        case: FirRegularClassSymbol,
        sealed: ClassId,
        arguments: List<ConeKotlinType>,
    ): ConeKotlinType? {
        if (case.typeParameterSymbols.isEmpty()) return case.defaultType()
        val supertype = case.resolvedSuperTypes
            .firstOrNull { (it as? ConeClassLikeType)?.lookupTag?.classId == sealed } as? ConeClassLikeType
            ?: return null
        val solved = case.typeParameterSymbols.map { parameter ->
            val at = supertype.typeArguments.indexOfFirst {
                ((it as? ConeKotlinTypeProjection)?.type as? ConeTypeParameterType)?.lookupTag?.typeParameterSymbol ==
                    parameter
            }
            arguments.getOrNull(at) ?: return null
        }
        return case.constructType(solved.toTypedArray<ConeTypeProjection>(), isMarkedNullable = false)
    }

    override fun caseName(type: ConeKotlinType): String? = classOf(type)
        ?.takeIf { case ->
            case.resolvedSuperTypes.any {
                it.toRegularClassSymbol(session)?.resolvedStatus?.modality == Modality.SEALED
            }
        }
        ?.classId?.shortClassName?.asString()

    override fun isObject(type: ConeKotlinType): Boolean = classOf(type)?.classKind == ClassKind.OBJECT

    private fun classOf(type: ConeKotlinType): FirRegularClassSymbol? =
        (expanded(type) as? ConeClassLikeType)
            ?.takeUnless { it.isMarkedNullable }
            ?.toRegularClassSymbol(session)

    // A platform type is taken at its non-null bound, as Kotlin lets it be used.
    override fun isNullable(type: ConeKotlinType): Boolean =
        expanded(type).isMarkedNullable

    override fun nonNull(type: ConeKotlinType): ConeKotlinType =
        expanded(type).withNullability(false, session.typeContext)

    override fun valueClass(type: ConeKotlinType): Param<ConeKotlinType>? {
        val classType = expanded(type) as? ConeClassLikeType ?: return null
        val symbol = classOf(type)?.takeIf { it.resolvedStatus.isInline || it.resolvedStatus.isValue } ?: return null
        lookups.member(symbol.classId, SpecialNames.INIT.asString())
        val inner = symbol.constructors(session).firstOrNull { it.isPrimary }?.valueParameterSymbols?.singleOrNull()
        // A generic value class holds its property's type with the class's own arguments put in.
        val substitutor = substitutor(symbol, classType) ?: return null
        return inner?.let {
            Param(it.name.asString(), substitutor.substituteOrSelf(it.resolvedReturnType), hasDefault = false)
        }
    }

    /** The read-only interfaces and `Array` only: a mutable or concrete collection is not a container here. */
    override fun container(type: ConeKotlinType): Container<ConeKotlinType>? {
        val classType = expanded(type) as? ConeClassLikeType
        val kind = classType?.takeUnless { it.isMarkedNullable }?.let { CONTAINERS[it.lookupTag.classId] }
            ?: return null
        val arguments = classType.typeArguments.map { (it as? ConeKotlinTypeProjection)?.type ?: return null }
        return if (kind.readOnly == Container.Kind.MAP) {
            Container(kind, arguments[1], key = arguments[0])
        } else {
            Container(kind, arguments[0])
        }
    }

    override fun property(owner: ConeKotlinType, name: String): ConeKotlinType? {
        val classType = expanded(owner) as? ConeClassLikeType
        val symbol = classType?.takeUnless { it.isMarkedNullable }?.toRegularClassSymbol(session) ?: return null
        // Recorded whether or not it is found: adding it is the change that must recompile the call.
        lookups.member(symbol.classId, name)
        val property = symbol.declaredProperties(session).firstOrNull {
            it.name.asString() == name && it.resolvedStatus.visibility == Visibilities.Public &&
                it.receiverParameterSymbol == null
        } ?: return null
        return substitutor(symbol, classType)?.substituteOrSelf(property.resolvedReturnType)
    }

    /** A class kimney builds by its constructor: not a standard-library or Java type, which have no rule yet. */
    private fun constructible(type: ConeKotlinType): Pair<ConeClassLikeType, FirRegularClassSymbol>? {
        val classType = expanded(type) as? ConeClassLikeType ?: return null
        val symbol = classType.takeUnless { it.isMarkedNullable }?.toRegularClassSymbol(session) ?: return null
        val status = symbol.resolvedStatus
        val eligible = symbol.classKind == ClassKind.CLASS &&
            status.modality in setOf(Modality.FINAL, Modality.OPEN) &&
            !status.isInner &&
            !symbol.classId.packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME) &&
            symbol.origin !is FirDeclarationOrigin.Java
        return if (eligible) classType to symbol else null
    }

    /** [type] with aliases expanded, the alias and the class it names each recorded as read. */
    private fun expanded(type: ConeKotlinType): ConeKotlinType {
        (type.lowerBoundIfFlexible() as? ConeClassLikeType)?.let { lookups.type(it.lookupTag.classId) }
        val expanded = type.fullyExpandedType(session).lowerBoundIfFlexible()
        (expanded as? ConeClassLikeType)?.let { lookups.type(it.lookupTag.classId) }
        return expanded
    }

    private fun substitutor(symbol: FirRegularClassSymbol, type: ConeClassLikeType): ConeSubstitutor? {
        val arguments = type.typeArguments.map { it as? ConeKotlinType ?: return null }
        return substitutorByMap(symbol.typeParameterSymbols.zip(arguments).toMap(), session)
    }
}

private val CONTAINERS = mapOf(
    StandardClassIds.List to Container.Kind.LIST,
    StandardClassIds.Set to Container.Kind.SET,
    StandardClassIds.Collection to Container.Kind.COLLECTION,
    StandardClassIds.Iterable to Container.Kind.ITERABLE,
    StandardClassIds.Map to Container.Kind.MAP,
    StandardClassIds.Array to Container.Kind.ARRAY,
    StandardClassIds.MutableList to Container.Kind.MUTABLE_LIST,
    StandardClassIds.MutableSet to Container.Kind.MUTABLE_SET,
    StandardClassIds.MutableCollection to Container.Kind.MUTABLE_COLLECTION,
    StandardClassIds.MutableIterable to Container.Kind.MUTABLE_ITERABLE,
    StandardClassIds.MutableMap to Container.Kind.MUTABLE_MAP,
)
