package io.github.matthewjones372.kimney.compiler.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KimneyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // The call checker arrives with spec 0002.
    }
}
