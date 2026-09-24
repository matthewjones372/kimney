package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.ENUM_LINKS
import io.github.matthewjones372.kimney.compiler.INTO
import io.github.matthewjones372.kimney.compiler.SEALED_LINKS
import io.github.matthewjones372.kimney.compiler.WITH_ENUM_ENTRY_RENAMED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_COMPUTED
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_CONST
import io.github.matthewjones372.kimney.compiler.WITH_FIELD_RENAMED
import io.github.matthewjones372.kimney.compiler.WITH_PARTIAL_TRANSFORMER
import io.github.matthewjones372.kimney.compiler.WITH_SEALED_CASE_RENAMED
import io.github.matthewjones372.kimney.compiler.WITH_TRANSFORMER
import io.github.matthewjones372.kimney.derive.EnumOverride
import io.github.matthewjones372.kimney.derive.Override
import io.github.matthewjones372.kimney.derive.SealedOverride
import io.github.matthewjones372.kimney.derive.Supplied
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId

/**
 * An override chain read back from its `transform()` call: the source type, each override, transformer and enum link
 * in written order, and each link's call by its place in the chain, for a warning to point at. [notAnEntry] is the
 * first enum or sealed link given something not written out, which the chain cannot be derived without.
 */
data class FirChain(
    val source: ConeKotlinType,
    val overrides: List<Override<ConeKotlinType>>,
    val transformers: List<Supplied<ConeKotlinType>>,
    val enums: List<EnumOverride<ConeKotlinType>>,
    val sealed: List<SealedOverride<ConeKotlinType>>,
    val calls: Map<Int, FirFunctionCall>,
    val links: Int,
    val notAnEntry: NotAnEntry? = null,
)

/** A link's argument that is not written out as [kind] requires: [type] is the enum, class or object it names. */
data class NotAnEntry(val method: String, val type: ConeKotlinType, val kind: Kind = Kind.ENTRY) {
    enum class Kind { ENTRY, CLASS_LITERAL, OBJECT }
}

private sealed interface Link {
    data class Overriding(val override: Override<ConeKotlinType>) : Link

    data class Transforming(val supplied: Supplied<ConeKotlinType>, val call: FirFunctionCall) : Link

    data class Mapping(val enum: EnumOverride<ConeKotlinType>, val call: FirFunctionCall) : Link

    data class Casing(val case: SealedOverride<ConeKotlinType>, val call: FirFunctionCall) : Link

    data class Unreadable(val notAnEntry: NotAnEntry) : Link
}

val FirFunctionCall.callableId: CallableId?
    get() = (calleeReference.symbol as? FirNamedFunctionSymbol)?.callableId

/** Null when the chain is not one expression of literal references and lambdas back to `into()`. */
fun readChain(transform: FirFunctionCall): FirChain? {
    // Stops at into(): the source may itself be a call, and is not part of the chain.
    val calls = generateSequence(transform.explicitReceiver as? FirFunctionCall) { call ->
        (call.explicitReceiver as? FirFunctionCall)?.takeUnless { call.callableId == INTO }
    }
        .toList()
        .asReversed()
    val source = calls.firstOrNull()?.takeIf { it.callableId == INTO }?.extensionReceiver?.resolvedType
    val links = calls.drop(1).mapIndexed { index, call -> link(call, index) }
    return if (source == null || null in links) {
        null
    } else {
        val transforming = links.filterIsInstance<Link.Transforming>()
        val mapping = links.filterIsInstance<Link.Mapping>()
        val casing = links.filterIsInstance<Link.Casing>()
        FirChain(
            source,
            links.filterIsInstance<Link.Overriding>().map { it.override },
            transforming.map { it.supplied },
            mapping.map { it.enum },
            casing.map { it.case },
            transforming.associate { it.supplied.index to it.call } + mapping.associate { it.enum.index to it.call } +
                casing.associate { it.case.index to it.call },
            links.size,
            links.filterIsInstance<Link.Unreadable>().firstOrNull()?.notAnEntry,
        )
    }
}

/** A transformer's types are the call's own type arguments, so a class implementing `Transformer` serves as well. */
private fun link(call: FirFunctionCall, index: Int): Link? = if (call.callableId in TRANSFORMER_LINKS) {
    val (source, target) = call.typeArguments.map { (it as? FirTypeProjectionWithVariance)?.typeRef?.coneType }
    val canFail = call.callableId == WITH_PARTIAL_TRANSFORMER
    if (source == null ||
        target == null
    ) null else Link.Transforming(Supplied(source, target, index, canFail = canFail), call)
} else if (call.callableId in ENUM_LINKS) {
    enumLink(call, index)
} else if (call.callableId in SEALED_LINKS) {
    sealedLink(call, index)
} else {
    override(call, index)?.let { Link.Overriding(it) }
}

private fun enumLink(call: FirFunctionCall, index: Int): Link? {
    val args = call.resolvedArgumentMapping?.entries?.associate { (arg, param) -> param.name.asString() to arg }
    val method = call.callableId?.callableName?.asString()
    val from = args?.get("from")
    val to = args?.get("to")
    val unreadable = listOfNotNull(from, to).firstOrNull { entry(it) == null }
    return when {
        method == null || to == null -> null

        unreadable != null -> Link.Unreadable(NotAnEntry(method, unreadable.resolvedType))

        call.callableId == WITH_ENUM_ENTRY_RENAMED -> from?.let {
            val renamed =
                EnumOverride.Renamed(it.resolvedType, entry(it).orEmpty(), to.resolvedType, entry(to).orEmpty(), index)
            Link.Mapping(renamed, call)
        }

        else -> Link.Mapping(EnumOverride.Fallback(to.resolvedType, entry(to).orEmpty(), index), call)
    }
}

/**
 * A case rename's types are its type arguments; its arguments must be class literals, `Shape.Hexagon::class`, and a
 * fallback's the object itself. Read without `FirResolvedQualifier.symbol`, whose type changed in Kotlin 2.4.20.
 */
private fun sealedLink(call: FirFunctionCall, index: Int): Link? {
    val args = call.resolvedArgumentMapping?.entries?.associate { (arg, param) -> param.name.asString() to arg }
    val method = call.callableId?.callableName?.asString() ?: return null
    val types = call.typeArguments.map { (it as? FirTypeProjectionWithVariance)?.typeRef?.coneType }
    val to = args?.get("to") ?: return null
    return if (call.callableId == WITH_SEALED_CASE_RENAMED) {
        val literals = listOfNotNull(args["from"], to).zip(types)
        val unwritten = literals.firstOrNull { (arg, _) -> !isClassLiteral(arg) }
        val (source, target) = types
        when {
            unwritten != null ->
                unwritten.second?.let { Link.Unreadable(NotAnEntry(method, it, NotAnEntry.Kind.CLASS_LITERAL)) }

            source == null || target == null -> null

            else -> Link.Casing(SealedOverride.Renamed(source, target, index), call)
        }
    } else if (to is FirResolvedQualifier) {
        // Whether it is an object is the engine's to answer: a fallback that is not one fits no case, and is unused.
        Link.Casing(SealedOverride.Fallback(to.resolvedType, index), call)
    } else {
        Link.Unreadable(NotAnEntry(method, to.resolvedType, NotAnEntry.Kind.OBJECT))
    }
}

private fun isClassLiteral(expression: FirExpression): Boolean =
    (expression as? FirGetClassCall)?.argument is FirResolvedQualifier

/** The entry's name, when [expression] is written as the entry itself: `Status.ARCHIVED`. */
private fun entry(expression: FirExpression?): String? =
    ((expression as? FirQualifiedAccessExpression)?.calleeReference?.symbol as? FirEnumEntrySymbol)?.name?.asString()

private fun override(call: FirFunctionCall, index: Int): Override<ConeKotlinType>? {
    val args = call.resolvedArgumentMapping?.entries?.associate { (arg, param) -> param.name.asString() to arg }
    val field = field(args?.get(if (call.callableId == WITH_FIELD_RENAMED) "to" else "field")) ?: return null
    return when (call.callableId) {
        WITH_FIELD_CONST -> args?.get("value")?.let { Override.Const(field, it.resolvedType, index) }

        WITH_FIELD_COMPUTED -> (args?.get("compute") as? FirAnonymousFunctionExpression)
            ?.let { Override.Computed(field, it.anonymousFunction.returnTypeRef.coneType, index) }

        WITH_FIELD_RENAMED -> field(args?.get("from"))?.let { Override.Renamed(field, it) }

        else -> null
    }
}

private fun field(reference: FirExpression?): String? =
    ((reference as? FirCallableReferenceAccess)?.calleeReference?.symbol as? FirPropertySymbol)?.name?.asString()

private val TRANSFORMER_LINKS = setOf(WITH_TRANSFORMER, WITH_PARTIAL_TRANSFORMER)
