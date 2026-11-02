package dev.surefold.kimney.compiler.fir

import dev.surefold.kimney.compiler.KimneyErrors
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KimneyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::KimneyCheckers
        registerDiagnosticContainers(KimneyErrors)
    }
}
