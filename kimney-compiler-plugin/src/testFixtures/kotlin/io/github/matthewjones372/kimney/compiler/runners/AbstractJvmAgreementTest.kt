package io.github.matthewjones372.kimney.compiler.runners

import org.jetbrains.kotlin.test.directives.TestPhaseDirectives
import org.jetbrains.kotlin.test.services.TestPhase

/**
 * Runs the checker over every `testData/box` file, where the markers are none: a checker that rejects what the
 * lowering builds is caught here rather than by a user.
 */
open class AbstractJvmAgreementTest : AbstractJvmDiagnosticTest() {
    override fun configure(builder: KimneyConfigurationBuilder) = with(builder) {
        super.configure(this)
        defaultDirectives { TestPhaseDirectives.RUN_PIPELINE_TILL with TestPhase.FRONTEND }
    }
}
