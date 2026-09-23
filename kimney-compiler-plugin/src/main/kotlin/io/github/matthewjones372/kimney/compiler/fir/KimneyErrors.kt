package io.github.matthewjones372.kimney.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

/** The message is rendered by the engine, so each diagnostic carries it whole. */
object KimneyErrors : KtDiagnosticsContainer() {
    val KIMNEY_CANNOT_TRANSFORM by error1<KtElement, String>()
    val KIMNEY_INTERNAL_ERROR by error1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KimneyMessages
}

object KimneyMessages : BaseDiagnosticRendererFactory() {
    @Suppress("ktlint:standard:property-naming") // The compiler's name for it; an override cannot rename.
    override val MAP by KtDiagnosticFactoryToRendererMap("Kimney") { map ->
        map.put(KimneyErrors.KIMNEY_CANNOT_TRANSFORM, "{0}", CommonRenderers.STRING)
        map.put(KimneyErrors.KIMNEY_INTERNAL_ERROR, "{0}", CommonRenderers.STRING)
    }
}
