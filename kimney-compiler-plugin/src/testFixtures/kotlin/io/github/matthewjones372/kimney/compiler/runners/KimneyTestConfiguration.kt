package io.github.matthewjones372.kimney.compiler.runners

import io.github.matthewjones372.kimney.compiler.KimneyRegistrar
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilderBase
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File

/** Registers the plugin and puts `kimney-runtime` on both the compile and the run classpath of the code under test. */
fun TestConfigurationBuilderBase<*, *>.configureKimney() {
    useConfigurators(::KimneyExtensions, ::KimneyRuntimeOnCompileClasspath)
    useCustomRuntimeClasspathProviders(::KimneyRuntimeOnRunClasspath)
}

private class KimneyExtensions(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        with(KimneyRegistrar()) { registerExtensions(configuration) }
    }
}

private class KimneyRuntimeOnCompileClasspath(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.addJvmClasspathRoots(runtimeUnderTest)
    }
}

private class KimneyRuntimeOnRunClasspath(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> = runtimeUnderTest
}

private val runtimeUnderTest: List<File> by lazy {
    val property = "kimney.runtimeUnderTest.classpath"
    val raw = System.getProperty(property) ?: error("the build must pass -D$property; see its build.gradle.kts")
    raw.split(File.pathSeparator).map(::File)
}
