package io.github.matthewjones372.kimney.compiler

import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

/** kimney takes no options; the Gradle plugin still passes the id through one of these. */
class KimneyCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String get() = KIMNEY_PLUGIN_ID
    override val pluginOptions: Collection<CliOption> get() = emptyList()
}
