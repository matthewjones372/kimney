package io.github.matthewjones372.kimney.compiler.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.NonGroupingPhaseTestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/** Compiles a `testData/box` file through the plugin, runs its `box()`, and expects `"OK"`. */
open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: NonGroupingPhaseTestConfigurationBuilder) = with(builder) {
        super.configure(this)
        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            // Avoids loading R8 from the classpath.
            +CodegenTestDirectives.IGNORE_DEXING
        }
        configureKimney()
    }
}
