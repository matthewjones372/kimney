package io.github.matthewjones372.kimney.compiler.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirLookupTrackerComponent
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.lookupTracker
import org.jetbrains.kotlin.fir.recordClassLikeLookup
import org.jetbrains.kotlin.fir.recordClassMemberLookup
import org.jetbrains.kotlin.name.ClassId

/**
 * Tells incremental compilation what a derivation read, against the checked call's file, so a change to any of it
 * recompiles that file. The tracker is null outside an incremental build, and every call is then a no-op.
 */
class Lookups(
    private val tracker: FirLookupTrackerComponent?,
    private val source: KtSourceElement?,
    private val file: KtSourceElement?,
) {
    fun type(id: ClassId) {
        tracker?.recordClassLikeLookup(id, source, file)
    }

    fun member(owner: ClassId, name: String) {
        tracker?.recordClassMemberLookup(name, owner, source, file)
    }
}

fun CheckerContext.lookups(source: KtSourceElement?): Lookups =
    Lookups(session.lookupTracker, source, containingFileSymbol?.source)
