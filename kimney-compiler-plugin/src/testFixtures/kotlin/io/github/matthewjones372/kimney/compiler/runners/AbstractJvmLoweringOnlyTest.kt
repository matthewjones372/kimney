package io.github.matthewjones372.kimney.compiler.runners

import io.github.matthewjones372.kimney.compiler.ir.KimneyIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.NonGroupingPhaseTestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.services.TestServices

/**
 * Registers the lowering without the checker, so a call the checker would have refused reaches it: the condition
 * its disagreement report exists for, made on purpose. Goldens pin that report and its position.
 */
open class AbstractJvmLoweringOnlyTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: NonGroupingPhaseTestConfigurationBuilder) = with(builder) {
        super.configure(this)
        defaultDirectives {
            +DiagnosticsDirectives.RENDER_IR_DIAGNOSTICS_FULL_TEXT
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            // Avoids loading R8 from the classpath.
            +CodegenTestDirectives.IGNORE_DEXING
            TestPhaseDirectives.RUN_PIPELINE_TILL with TestPhase.FIR2IR
        }
        useConfigurators(::LoweringOnly)
        configureKimneyRuntime()
    }
}

private class LoweringOnly(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        IrGenerationExtension.registerExtension(KimneyIrExtension())
    }
}
