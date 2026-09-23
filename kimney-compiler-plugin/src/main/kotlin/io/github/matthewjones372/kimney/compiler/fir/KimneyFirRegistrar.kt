package io.github.matthewjones372.kimney.compiler.fir

import io.github.matthewjones372.kimney.compiler.KimneyErrors
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KimneyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::KimneyCheckers
        registerDiagnosticContainers(KimneyErrors)
    }
}
