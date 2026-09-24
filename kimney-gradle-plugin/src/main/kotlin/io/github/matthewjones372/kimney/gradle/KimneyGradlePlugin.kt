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

    // Refused here, at configuration: a compiler plugin loaded into another minor's compiler fails with a
    // NoSuchMethodError deep inside it, which reads as a compiler bug rather than as ours.
    override fun apply(target: Project) {
        target.plugins.withType(KotlinBasePlugin::class.java) { kotlin ->
            when (val check = checkKotlin(BuildConfig.KOTLIN_TESTED.split(','), found = kotlin.pluginVersion)) {
                KotlinCheck.Supported -> Unit
                is KotlinCheck.Untested -> target.logger.warn("w: ${check.message}")
                is KotlinCheck.Unsupported -> throw GradleException(check.message)
            }
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

/** What kimney makes of the Kotlin a build uses, against the ones its compiler tests ran on. */
internal sealed interface KotlinCheck {
    /** The tested minor, no newer than its newest tested patch. */
    data object Supported : KotlinCheck

    /** The tested minor, on a newer patch: applied, with a warning. */
    data class Untested(val message: String) : KotlinCheck

    /** Another minor: the compiler plugin API promises nothing across one. */
    data class Unsupported(val message: String) : KotlinCheck
}

/** [tested] is oldest first, all of one minor; a prerelease is read by its numbers, `2.4.30-RC` as `2.4.30`. */
internal fun checkKotlin(tested: List<String>, found: String): KotlinCheck {
    val oldest = checkNotNull(release(tested.first())) { "the tested Kotlins are releases" }
    val newest = checkNotNull(release(tested.last())) { "the tested Kotlins are releases" }
    val version = release(found)
    val range = "Kotlin ${tested.first()} to ${tested.last()}"
    return when {
        version == null || version.minor != newest.minor || version.major != newest.major || version < oldest ->
            KotlinCheck.Unsupported(
                "kimney ${BuildConfig.KIMNEY_VERSION} supports $range; this build uses $found. " +
                    "Use one of those, or a kimney release built for ${version?.let {
                        "${it.major}.${it.minor}"
                    } ?: found}.",
            )

        version > newest -> KotlinCheck.Untested(
            "kimney ${BuildConfig.KIMNEY_VERSION} is tested on $range; this build uses $found, which it has not been " +
                "tested on.",
        )

        else -> KotlinCheck.Supported
    }
}

/** `2.4.20` and `2.4.20-RC` as 2.4.20; anything not three numbers as nothing. */
private fun release(version: String): KotlinVersion? {
    val parts = version.substringBefore('-').split('.')
    val numbers = parts.mapNotNull(String::toIntOrNull)
    return numbers.takeIf { parts.size == RELEASE_PARTS && it.size == RELEASE_PARTS }
        ?.let { (major, minor, patch) -> KotlinVersion(major, minor, patch) }
}

private const val RELEASE_PARTS = 3
