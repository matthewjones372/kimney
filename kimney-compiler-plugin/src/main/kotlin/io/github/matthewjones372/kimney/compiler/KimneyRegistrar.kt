package io.github.matthewjones372.kimney.compiler

import io.github.matthewjones372.kimney.compiler.fir.KimneyFirRegistrar
import io.github.matthewjones372.kimney.compiler.ir.KimneyIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

const val KIMNEY_PLUGIN_ID = "io.github.matthewjones372.kimney"

class KimneyRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String get() = KIMNEY_PLUGIN_ID
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(KimneyFirRegistrar())
        IrGenerationExtension.registerExtension(
            KimneyIrExtension(configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)),
        )
    }
}
