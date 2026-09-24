import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// An included build does not read the including build's gradle.properties, and
// the plugin has to name the version of the artifacts it adds.
val rootProperties = Properties().apply { file("../gradle.properties").reader().use(::load) }
group = rootProperties.getProperty("group")
version = rootProperties.getProperty("version")

kotlin { jvmToolchain(21) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    // compileOnly: the consumer's Kotlin Gradle plugin is the one that runs.
    // Bundling this one would load a second copy the consumer's never sees.
    compileOnly(libs.kotlin.gradle.plugin.api)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.launcher)
}

buildConfig {
    packageName("io.github.matthewjones372.kimney.gradle")
    useKotlinOutput { internalVisibility = true }
    buildConfigField("String", "KIMNEY_GROUP", "\"$group\"")
    buildConfigField("String", "KIMNEY_VERSION", "\"$version\"")
    buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.GradlePlugin(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
    pom {
        name.set("kimney-gradle-plugin")
        description.set("Applies kimney's compiler plugin and adds kimney-runtime to every JVM compilation.")
        url.set("https://github.com/matthewjones372/kimney")
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
            url.set("https://github.com/matthewjones372/kimney")
            connection.set("scm:git:https://github.com/matthewjones372/kimney.git")
            developerConnection.set("scm:git:ssh://git@github.com/matthewjones372/kimney.git")
        }
    }
}

gradlePlugin {
    plugins {
        create("kimney") {
            id = "io.github.matthewjones372.kimney"
            implementationClass = "io.github.matthewjones372.kimney.gradle.KimneyGradlePlugin"
        }
    }
}

// The functional tests resolve the plugin from here by id, as a user's build
// would, so it shares a class loader with whichever Kotlin plugin they ask for.
val functionalRepo = layout.buildDirectory.dir("functional-repo")
publishing { repositories { maven { name = "functional"; url = uri(functionalRepo) } } }

tasks.test {
    useJUnitPlatform()
    dependsOn("publishAllPublicationsToFunctionalRepository")
    val repoPath = functionalRepo.get().asFile.absolutePath
    val pluginVersion = version.toString()
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dkimney.functionalRepo=$repoPath", "-Dkimney.version=$pluginVersion")
        },
    )
}

val ktlintOverrides = mapOf("ktlint_standard_kdoc" to "disabled")
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
}

detekt {
    config.setFrom(file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}
tasks.named("check") { dependsOn("detektMain", "detektTest") }
