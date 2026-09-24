package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.KimneyErrors
import io.github.matthewjones372.kimney.derive.EnumOverride
import io.github.matthewjones372.kimney.derive.SealedOverride
import io.github.matthewjones372.kimney.derive.unusedEnumFallback
import io.github.matthewjones372.kimney.derive.unusedEnumRename
import io.github.matthewjones372.kimney.derive.unusedSealedFallback
import io.github.matthewjones372.kimney.derive.unusedSealedRename
import io.github.matthewjones372.kimney.derive.unusedTransformer
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.types.ConeKotlinType

/** A warning for one link of a chain the plan never used: which one, of which kind, and what it says. */
data class UnusedLink(val index: Int, val factory: KtDiagnosticFactory1<String>, val message: String)

/** Every link passed to [chain] and not in [used]. A transformer in context serves many calls, so is never one. */
fun FirTypeModel.unusedLinks(chain: FirChain?, used: Set<Int>): List<UnusedLink> {
    chain ?: return emptyList()
    val transformers = chain.transformers.filterNot { it.index in used }.map { unused ->
        val method = if (unused.canFail) "withPartialTransformer" else "withTransformer"
        val message = unusedTransformer(render(unused.source), render(unused.target), method)
        UnusedLink(unused.index, KimneyErrors.KIMNEY_UNUSED_TRANSFORMER, message)
    }
    val enums = chain.enums.filterNot { it.index in used }.map { unused ->
        UnusedLink(unused.index, KimneyErrors.KIMNEY_UNUSED_ENUM_MAPPING, enumMessage(unused))
    }
    val sealed = chain.sealed.filterNot { it.index in used }.map { unused ->
        val message = when (unused) {
            is SealedOverride.Renamed -> unusedSealedRename(render(unused.source), render(unused.target))
            is SealedOverride.Fallback -> unusedSealedFallback(render(unused.target))
        }
        UnusedLink(unused.index, KimneyErrors.KIMNEY_UNUSED_SEALED_MAPPING, message)
    }
    return transformers + enums + sealed
}

private fun FirTypeModel.enumMessage(unused: EnumOverride<ConeKotlinType>): String {
    val target = render(unused.target)
    return when (unused) {
        is EnumOverride.Renamed -> {
            val enum = render(unused.source)
            unusedEnumRename("$enum.${unused.from}", "$target.${unused.to}", enum, target)
        }

        is EnumOverride.Fallback -> unusedEnumFallback("$target.${unused.to}", target)
    }
}

/** What a link's argument must be written as, and a real one of that shape from the type it holds. */
fun FirTypeModel.writtenOut(given: NotAnEntry): Pair<String, String> {
    val named = render(given.type)
    return when (given.kind) {
        NotAnEntry.Kind.ENTRY ->
            "the entries themselves" to
                (enumEntries(given.type)?.firstOrNull()?.let { "$named.$it" } ?: named)

        NotAnEntry.Kind.CLASS_LITERAL -> "class literals" to "$named::class"

        NotAnEntry.Kind.OBJECT ->
            "the object itself" to
                (sealedCases(given.type)?.firstOrNull { isObject(it.type) }?.let { render(it.type) } ?: named)
    }
}
