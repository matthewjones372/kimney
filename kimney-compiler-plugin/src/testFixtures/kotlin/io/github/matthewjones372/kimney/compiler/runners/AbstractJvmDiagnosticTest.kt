package io.github.matthewjones372.kimney.compiler.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.NonGroupingPhaseTestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/**
 * Checks the markers in a `testData/diagnostics` file, and pins each diagnostic's full text in the `.diag.txt`
 * beside it — the message is the product, so the golden holds the words, not just the position.
 */
open class AbstractJvmDiagnosticTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: NonGroupingPhaseTestConfigurationBuilder) = with(builder) {
        super.configure(this)
        defaultDirectives {
            +DiagnosticsDirectives.RENDER_DIAGNOSTICS_FULL_TEXT
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            // Avoids loading R8 from the classpath.
            +CodegenTestDirectives.IGNORE_DEXING
        }
        configureKimney()
    }
}
