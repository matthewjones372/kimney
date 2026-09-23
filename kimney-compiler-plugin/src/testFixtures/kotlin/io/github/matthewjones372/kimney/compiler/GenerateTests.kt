package io.github.matthewjones372.kimney.compiler

import io.github.matthewjones372.kimney.compiler.runners.AbstractJvmAgreementTest
import io.github.matthewjones372.kimney.compiler.runners.AbstractJvmBoxTest
import io.github.matthewjones372.kimney.compiler.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testsRoot = args[0], testDataRoot = args[1]) {
            testClass<AbstractJvmBoxTest> { model("box") }
            testClass<AbstractJvmDiagnosticTest> { model("diagnostics") }
            testClass<AbstractJvmAgreementTest> { model("box") }
        }
    }
}
