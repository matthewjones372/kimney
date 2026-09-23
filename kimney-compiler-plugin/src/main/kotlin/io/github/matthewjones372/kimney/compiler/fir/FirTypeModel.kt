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
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability

class FirTypeModel(private val session: FirSession) : TypeModel<ConeKotlinType> {

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

    override fun isSubtypeOf(sub: ConeKotlinType, sup: ConeKotlinType): Boolean = sub.isSubtypeOf(sup, session)

    override fun construction(type: ConeKotlinType): Construction<ConeKotlinType> {
        val (classType, symbol) = constructible(type) ?: return Construction.NotAClass
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
            ?.collectEnumEntries(session)
            ?.map { it.name.asString() }

    /** Direct inheritors, each a case only if it is itself non-generic: generic hierarchies are not modelled. */
    @OptIn(SymbolInternals::class)
    override fun sealedCases(type: ConeKotlinType): List<Case<ConeKotlinType>>? {
        val symbol = classOf(type)
            ?.takeIf { it.resolvedStatus.modality == Modality.SEALED && it.typeParameterSymbols.isEmpty() }
            ?: return null
        val cases = symbol.fir.getSealedClassInheritors(session).map { id ->
            (session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol)
                ?.takeIf { it.typeParameterSymbols.isEmpty() }
                ?.let { Case(id.shortClassName.asString(), it.defaultType() as ConeKotlinType) }
        }
        return cases.filterNotNull().takeIf { it.size == cases.size }
    }

    override fun isObject(type: ConeKotlinType): Boolean = classOf(type)?.classKind == ClassKind.OBJECT

    private fun classOf(type: ConeKotlinType): FirRegularClassSymbol? =
        (type.fullyExpandedType(session).lowerBoundIfFlexible() as? ConeClassLikeType)
            ?.takeUnless { it.isMarkedNullable }
            ?.toRegularClassSymbol(session)

    // A platform type is taken at its non-null bound, as Kotlin lets it be used.
    override fun isNullable(type: ConeKotlinType): Boolean =
        type.fullyExpandedType(session).lowerBoundIfFlexible().isMarkedNullable

    override fun nonNull(type: ConeKotlinType): ConeKotlinType =
        type.fullyExpandedType(session).lowerBoundIfFlexible().withNullability(false, session.typeContext)

    override fun valueClass(type: ConeKotlinType): Param<ConeKotlinType>? {
        val symbol = classOf(type)
            ?.takeIf { (it.resolvedStatus.isInline || it.resolvedStatus.isValue) && it.typeParameterSymbols.isEmpty() }
            ?: return null
        val inner = symbol.constructors(session).firstOrNull { it.isPrimary }?.valueParameterSymbols?.singleOrNull()
        return inner?.let { Param(it.name.asString(), it.resolvedReturnType, hasDefault = false) }
    }

    // Containers are modelled from spec 0010's next entries; until then none is seen.
    override fun container(type: ConeKotlinType): Container<ConeKotlinType>? = null

    override fun property(owner: ConeKotlinType, name: String): ConeKotlinType? {
        val classType = owner.fullyExpandedType(session).lowerBoundIfFlexible() as? ConeClassLikeType
        val symbol = classType?.takeUnless { it.isMarkedNullable }?.toRegularClassSymbol(session) ?: return null
        val property = symbol.declaredProperties(session).firstOrNull {
            it.name.asString() == name && it.resolvedStatus.visibility == Visibilities.Public &&
                it.receiverParameterSymbol == null
        } ?: return null
        return substitutor(symbol, classType)?.substituteOrSelf(property.resolvedReturnType)
    }

    /** A class kimney builds by its constructor: not a standard-library or Java type, which have no rule yet. */
    private fun constructible(type: ConeKotlinType): Pair<ConeClassLikeType, FirRegularClassSymbol>? {
        val classType = type.fullyExpandedType(session).lowerBoundIfFlexible() as? ConeClassLikeType ?: return null
        val symbol = classType.takeUnless { it.isMarkedNullable }?.toRegularClassSymbol(session) ?: return null
        val status = symbol.resolvedStatus
        val eligible = symbol.classKind == ClassKind.CLASS &&
            status.modality in setOf(Modality.FINAL, Modality.OPEN) &&
            !status.isInner &&
            !symbol.classId.packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME) &&
            symbol.origin !is FirDeclarationOrigin.Java
        return if (eligible) classType to symbol else null
    }

    private fun substitutor(symbol: FirRegularClassSymbol, type: ConeClassLikeType): ConeSubstitutor? {
        val arguments = type.typeArguments.map { it as? ConeKotlinType ?: return null }
        return substitutorByMap(symbol.typeParameterSymbols.zip(arguments).toMap(), session)
    }
}
