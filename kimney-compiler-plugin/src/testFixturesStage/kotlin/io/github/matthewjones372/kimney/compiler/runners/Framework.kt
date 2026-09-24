package io.github.matthewjones372.kimney.compiler.runners

import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.NonGroupingStageTestConfigurationBuilder
import org.jetbrains.kotlin.test.runners.codegen.AbstractJvmBlackBoxCodegenTestBase

// The compiler test framework's names from Kotlin 2.4.20 on. src/testFixturesPhase has them up to 2.4.10.

typealias KimneyConfigurationBuilder = NonGroupingStageTestConfigurationBuilder

abstract class KimneyBlackBoxTestBase : AbstractJvmBlackBoxCodegenTestBase(FirParser.LightTree)
