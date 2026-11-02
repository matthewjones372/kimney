plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    // The runtime's binary surface as a file somebody reads in a diff.
    alias(libs.plugins.bcv)
    alias(libs.plugins.maven.publish) apply false
    base
}

// Handed to ktlint directly: Spotless does not read .editorconfig for every
// source set, and this is the one override that has to hold everywhere.
val ktlintOverrides = mapOf("ktlint_standard_kdoc" to "disabled")
val ktlintVersion: String = libs.versions.ktlint.get()

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
    }
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(90)
                }
            }
            filters {
                excludes {
                    // The example proves the consumer path by compiling; it has
                    // no behaviour of its own to test.
                    classes("example.*")
                    // Run by JMH in forked JVMs, never by a test.
                    classes("kimney.benchmarks.*")
                }
            }
        }
    }
}

apiValidation {
    // Only the runtime is linked against by user code; the rest is loaded by
    // the compiler or is the example.
    ignoredProjects += listOf("kimney-derive", "kimney-compiler-plugin", "example", "benchmarks", "ic-test")
}

/** What each published artifact is, as a Maven search result should say. */
val published = mapOf(
    "kimney-runtime" to "The calls kimney's compiler plugin replaces: transformInto, the override chain, Partial.",
    "kimney-derive" to "kimney's derivation engine: source and target types to a plan, or every reason not.",
    "kimney-compiler-plugin" to "The K2 compiler plugin that derives transformations between Kotlin types.",
)

// The modules AGENTS.md promises depend on the standard library alone. Each
// one's NoThirdPartyDependenciesTest reads the classpath handed over here.
val stdlibOnly = setOf("kimney-runtime", "kimney-derive")

/** Whether this build publishes a release, the only time a signature is required. */
val releasing = gradle.startParameter.taskNames.any { "MavenCentral" in it || "publishPlugins" in it }

dependencies {
    subprojects.forEach { kover(project(it.path)) }
}

// The inner loop for engine and docs work: every check but the compiler plugin's
// tests, which are the one suite that compiles Kotlin per test. `build` still
// runs everything.
tasks.register("quickCheck") {
    group = "verification"
    description = "Runs every check except the compiler plugin's compile-and-run tests."
    dependsOn(subprojects.filter { it.name !in setOf("kimney-compiler-plugin", "ic-test") }.map { "${it.path}:check" })
    dependsOn(":kimney-compiler-plugin:detektMain", ":kimney-compiler-plugin:spotlessCheck", "spotlessCheck")
}

// Snapshots (surefold spec 0281): snapshot.yml runs this with the workflow's token as githubPackagesUsername and
// githubPackagesPassword. It reaches the included Gradle plugin build as publishToMavenLocal does below.
tasks.register("publishSnapshot") {
    group = "publishing"
    description = "Publishes every kimney artifact, the Gradle plugin and its marker included, to GitHub Packages."
    val publish = "publishAllPublicationsToGithubPackagesRepository"
    dependsOn(subprojects.filter { it.name in published }.map { "${it.path}:$publish" })
    dependsOn(gradle.includedBuild("kimney-gradle-plugin").task(":$publish"))
}

// The Gradle plugin is an included build, so a root publishToMavenLocal would
// otherwise publish every artifact but the one a consumer applies first.
tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes every kimney artifact, the Gradle plugin and its marker included, to Maven Local."
    dependsOn(gradle.includedBuild("kimney-gradle-plugin").task(":publishToMavenLocal"))
}

tasks.named("check") {
    dependsOn("koverVerify")
    // The Gradle plugin is an included build, which this one does not check
    // unless it is asked to.
    dependsOn(gradle.includedBuild("kimney-gradle-plugin").task(":check"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(libs.findLibrary("kotlin-test").get())
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        // Matchers and their failure messages; the tests still run on JUnit.
        "testImplementation"(libs.findLibrary("kotest-assertions").get())
        "testRuntimeOnly"(libs.findLibrary("junit-launcher").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("junit.jupiter.execution.timeout.default", "60s")
    }

    if (name in stdlibOnly) {
        tasks.named<Test>("test") {
            // The main runtime classpath, not the test one, which carries JUnit.
            val mainRuntime = configurations.named("runtimeClasspath")
            inputs.files(mainRuntime).withPropertyName("mainRuntimeClasspath")
            jvmArgumentProviders.add(
                CommandLineArgumentProvider {
                    listOf(
                        "-Dkimney.runtimeClasspath=" + mainRuntime.get().joinToString(File.pathSeparator) {
                            it.name
                        },
                    )
                },
            )
        }
    }

    apply(plugin = "org.jetbrains.kotlinx.kover")

    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
    // The plain `detekt` task cannot see types, so the rules that need them
    // are skipped there. `check` depends on the type-resolving pair instead.
    tasks.named("check") { dependsOn("detektMain", "detektTest") }

    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt", "test-fixtures/**/*.kt")
            ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
        }
    }

    published[name]?.let { summary ->
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // Central requires a javadoc jar; an empty one until KDoc rendering is decided (spec 0014).
            configure(
                com.vanniktech.maven.publish.KotlinJvm(
                    javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
                    sourcesJar = true,
                ),
            )
            pom { kimneyPom(this@subprojects.name, summary) }
        }
        // Where ic-test's nested builds resolve kimney from, so none of them reads ~/.m2.
        extensions.configure<PublishingExtension> {
            repositories { maven { name = "icTest"; url = uri(rootProject.layout.buildDirectory.dir("ic-repo")) } }
            repositories { githubPackages() }
            relocatedFromOldGroup(this@subprojects.name, version.toString())
        }
        onlyDevSurefoldToGithubPackages()
        onlyOneNamespaceToCentral()
        // A release version is signed for Central, but `build` also publishes it to icTest, which needs no key.
        tasks.withType<Sign>().configureEach { isRequired = releasing }
    }
}

/**
 * A POM-only publication at the coordinates kimney had before 0.5.0, relocating to the same artifact under
 * `dev.surefold` (spec 0025). Maven and Gradle follow it, so a build still on `io.github.matthewjones372` resolves
 * the new artifact. The Gradle plugin's build declares the same for its own artifact.
 */
fun PublishingExtension.relocatedFromOldGroup(artifact: String, version: String) {
    publications.register<MavenPublication>("relocation") {
        groupId = "io.github.matthewjones372"
        artifactId = artifact
        this.version = version
        pom {
            packaging = "pom"
            distributionManagement {
                relocation {
                    groupId.set("dev.surefold")
                    message.set("kimney moved to dev.surefold in 0.5.0; packages and the plugin ID moved with it.")
                }
            }
        }
    }
}

/** This repository's GitHub Packages registry, where snapshots go (surefold spec 0281); releases go to Central. */
fun RepositoryHandler.githubPackages() = maven("https://maven.pkg.github.com/surefold/kimney") {
    name = "githubPackages"
    credentials(PasswordCredentials::class)
}

/** The relocation POMs and anything else outside `dev.surefold` are for Central only, never a snapshot. */
fun Project.onlyDevSurefoldToGithubPackages() = tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("only dev.surefold goes to GitHub Packages") { task ->
        val publish = task as PublishToMavenRepository
        publish.repository.name != "githubPackages" || publish.publication.groupId == "dev.surefold"
    }
}

/**
 * The Central Portal refuses a deployment with files for two namespaces, and a Gradle run uploads one deployment, so
 * a release publishes to Central once per namespace: `dev.surefold` by default, then the relocation POMs with
 * `-PcentralNamespace=io.github.matthewjones372`. The Gradle plugin's build filters its own publications the same way.
 */
fun Project.onlyOneNamespaceToCentral() {
    val namespace = providers.gradleProperty("centralNamespace").getOrElse("dev.surefold")
    tasks.withType<PublishToMavenRepository>().configureEach {
        onlyIf("only $namespace goes to Central in this run") { task ->
            val publish = task as PublishToMavenRepository
            val group = publish.publication.groupId
            publish.repository.name != "mavenCentral" || group == namespace || group.startsWith("$namespace.")
        }
    }
}

/** The POM every kimney artifact shares; the Gradle plugin's build writes the same one. */
fun org.gradle.api.publish.maven.MavenPom.kimneyPom(artifact: String, summary: String) {
    name.set(artifact)
    description.set(summary)
    url.set("https://github.com/surefold/kimney")
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("matthewjones372")
            name.set("Matt Jones")
        }
    }
    scm {
        url.set("https://github.com/surefold/kimney")
        connection.set("scm:git:https://github.com/surefold/kimney.git")
        developerConnection.set("scm:git:ssh://git@github.com/surefold/kimney.git")
    }
}
