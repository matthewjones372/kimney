package io.github.matthewjones372.kimney.compiler.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.NonGroupingPhaseTestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase

// The compiler test framework's names up to Kotlin 2.4.10. src/testFixturesStage has them from 2.4.20 on.

typealias KimneyConfigurationBuilder = NonGroupingPhaseTestConfigurationBuilder

abstract class KimneyBlackBoxTestBase : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree)
