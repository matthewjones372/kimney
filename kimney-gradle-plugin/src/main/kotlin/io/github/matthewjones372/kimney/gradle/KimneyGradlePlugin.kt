package io.github.matthewjones372.kimney.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KimneyGradlePlugin : KotlinCompilerPluginSupportPlugin {

    // Refused here, at configuration: a compiler plugin loaded into another Kotlin's compiler fails with a
    // NoSuchMethodError deep inside it, which reads as a compiler bug rather than as ours.
    override fun apply(target: Project) {
        target.plugins.withType(KotlinBasePlugin::class.java) { kotlin ->
            kotlinMismatch(builtFor = BuildConfig.KOTLIN_VERSION, found = kotlin.pluginVersion)
                ?.let { throw GradleException(it) }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType == KotlinPlatformType.jvm

    override fun getCompilerPluginId(): String = "io.github.matthewjones372.kimney"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(BuildConfig.KIMNEY_GROUP, "kimney-compiler-plugin", BuildConfig.KIMNEY_VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("${BuildConfig.KIMNEY_GROUP}:kimney-runtime:${BuildConfig.KIMNEY_VERSION}")
        }
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}

internal fun kotlinMismatch(builtFor: String, found: String): String? =
    if (builtFor == found) {
        null
    } else {
        "kimney ${BuildConfig.KIMNEY_VERSION} is built for Kotlin $builtFor; this build uses $found. " +
            "Use Kotlin $builtFor, or a kimney release built for $found."
    }
