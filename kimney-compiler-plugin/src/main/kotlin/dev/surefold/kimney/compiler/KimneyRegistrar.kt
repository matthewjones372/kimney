package dev.surefold.kimney.compiler

import dev.surefold.kimney.compiler.fir.KimneyFirRegistrar
import dev.surefold.kimney.compiler.ir.KimneyIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

const val KIMNEY_PLUGIN_ID = "dev.surefold.kimney"

class KimneyRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String get() = KIMNEY_PLUGIN_ID
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(KimneyFirRegistrar())
        IrGenerationExtension.registerExtension(KimneyIrExtension())
    }
}
