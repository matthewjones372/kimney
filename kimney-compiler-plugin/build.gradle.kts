import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-test-fixtures`
}

// Loaded by the compiler, which runs on 17 at the oldest.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val testDataDir = layout.projectDirectory.dir("testData")
val testGenDir = layout.buildDirectory.dir("test-gen")

sourceSets {
    test {
        java.srcDir(testGenDir)
        resources.srcDir(testDataDir)
    }
}

// What the compiler test framework puts on the classpath of the code under
// test. It finds each jar by a system property rather than by resolving.
val testArtifacts: Configuration by configurations.creating
val runtimeUnderTest: Configuration by configurations.creating { isTransitive = false }

dependencies {
    implementation(project(":kimney-derive"))
    compileOnly(libs.kotlin.compiler)

    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.test.framework)
    testFixturesApi(libs.kotlin.compiler)
    // The framework's runners still reach for JUnit 4 classes at load time.
    testFixturesRuntimeOnly(libs.junit4)

    runtimeUnderTest(project(":kimney-runtime"))

    testArtifacts(libs.kotlin.stdlib)
    testArtifacts(libs.kotlin.stdlib.jdk8)
    testArtifacts(libs.kotlin.reflect)
    testArtifacts(libs.kotlin.test)
    testArtifacts(libs.kotlin.script.runtime)
    testArtifacts(libs.kotlin.annotations.jvm)
}

// One JUnit class per directory under testData, regenerated whenever a test
// data file is added. The generated sources are never committed.
val generateTests = tasks.register<JavaExec>("generateTests") {
    inputs.dir(testDataDir).withPropertyName("testData").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(testGenDir).withPropertyName("generatedTests")
    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass.set("io.github.matthewjones372.kimney.compiler.GenerateTestsKt")
    workingDir = rootDir
    args(testGenDir.get().asFile.absolutePath, testDataDir.asFile.absolutePath)
    // The generator only writes, so a test for a removed testData directory would otherwise outlive it.
    val generated = testGenDir
    doFirst { generated.get().asFile.deleteRecursively() }
}

tasks.compileTestKotlin { dependsOn(generateTests) }
tasks.compileTestJava { dependsOn(generateTests) }

tasks.test {
    dependsOn(testArtifacts, runtimeUnderTest)
    workingDir = rootDir

    val runtimePath = runtimeUnderTest.asPath
    val artifacts = testArtifacts
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            fun jar(name: String) = artifacts.files.first { """$name-\d.*""".toRegex().matches(it.name) }.absolutePath
            listOf(
                "-Dkimney.runtimeUnderTest.classpath=$runtimePath",
                "-Dorg.jetbrains.kotlin.test.kotlin-stdlib=${jar("kotlin-stdlib")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-stdlib-jdk8=${jar("kotlin-stdlib-jdk8")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-reflect=${jar("kotlin-reflect")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-test=${jar("kotlin-test")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-script-runtime=${jar("kotlin-script-runtime")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-annotations-jvm=${jar("kotlin-annotations-jvm")}",
            )
        },
    )
    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", rootDir)
}

// The fixtures are test harness, run by every test and asserted by none.
kover { currentProject { sources { excludedSourceSets.add("testFixtures") } } }
