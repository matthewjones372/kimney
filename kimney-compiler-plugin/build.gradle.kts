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

val sourceTestData = layout.projectDirectory.dir("testData")
val testGenDir = layout.buildDirectory.dir("test-gen")

// The Kotlin whose compiler the tests run on: the catalog's, or `-Pkimney.kotlinUnderTest` (below).
val kotlinUnderTest: String = providers.gradleProperty("kimney.kotlinUnderTest").getOrElse(libs.versions.kotlin.get())

/** The compiler test framework renamed its builders in 2.4.20; each side of that has a shim of its own. */
fun frameworkShim(kotlin: String): String {
    val (major, minor, patch) = kotlin.substringBefore('-').split('.').map(String::toInt)
    return if (KotlinVersion(major, minor, patch) >= KotlinVersion(2, 4, 20)) "Stage" else "Phase"
}

val builtFor: String = libs.versions.kotlin.get()

/**
 * The testData the tests read: this directory on the catalog's Kotlin, and a converted copy on any other.
 *
 * From 2.4.20 the framework names a golden `x.diag.txt`, not `x.fir.diag.txt`, and gives a position as `line:col`
 * rather than as an offset range in the file with its markers taken out. The messages are the same, so the goldens
 * are kept once, in the catalog Kotlin's form, and converted here: nothing checked in can drift from its twin.
 */
val testDataDir: Directory = if (kotlinUnderTest == builtFor) {
    sourceTestData
} else {
    layout.buildDirectory.dir("testData-$kotlinUnderTest").get()
}

val convertTestData = tasks.register("convertTestData") {
    description = "Copies testData into the form the compiler test framework of -Pkimney.kotlinUnderTest reads"
    val stage = frameworkShim(kotlinUnderTest) == "Stage"
    val from = sourceTestData.asFile
    val into = testDataDir.asFile
    onlyIf { into != from }
    inputs.dir(from).withPropertyName("testData").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(into)
    doLast {
        into.deleteRecursively()
        from.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(from).path
            val golden = stage && relative.endsWith(".diag.txt") && ".fir." in relative
            if (!golden) {
                file.copyTo(into.resolve(relative))
            } else {
                val source = from.resolve(relative.replace(Regex("""\.fir(\.ir)?\.diag\.txt$"""), ".kt")).readText()
                    .replace(Regex("<!.*?!>|<!>"), "")
                val converted = file.readText().replace(Regex(""":\((\d+),\d+\):""")) { match ->
                    val before = source.take(match.groupValues[1].toInt())
                    ":${before.count { it == '\n' } + 1}:${before.length - before.lastIndexOf('\n')}:"
                }
                into.resolve(relative.replace(".fir.", ".")).apply { parentFile.mkdirs() }.writeText(converted)
            }
        }
    }
}

sourceSets {
    testFixtures {
        kotlin.srcDir("src/testFixtures${frameworkShim(kotlinUnderTest)}/kotlin")
    }
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
    dependsOn(convertTestData)
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
                // Compiling a test's Java sources reads the standard library from here instead.
                "-Dkotlin.full.stdlib.path=${jar("kotlin-stdlib")}",
                "-Dkotlin.reflect.jar.path=${jar("kotlin-reflect")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-stdlib-jdk8=${jar("kotlin-stdlib-jdk8")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-reflect=${jar("kotlin-reflect")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-test=${jar("kotlin-test")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-script-runtime=${jar("kotlin-script-runtime")}",
                "-Dorg.jetbrains.kotlin.test.kotlin-annotations-jvm=${jar("kotlin-annotations-jvm")}",
            )
        },
    )
    // `-Pkimney.updateTestData=true` has the framework write what it saw back into testData, to read the
    // difference behind "Actual data differs"; review the diff, never commit it unread.
    systemProperty("kotlin.test.update.test.data", providers.gradleProperty("kimney.updateTestData").getOrElse("false"))
    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", rootDir)
}

// The fixtures are test harness, run by every test and asserted by none.
kover { currentProject { sources { excludedSourceSets.add("testFixtures") } } }

// Nor are they published: `java-test-fixtures` adds its variants to the component, and a POM, which has no
// variants, would fold the compiler test framework and JUnit into the plugin's compile dependencies.
listOf("testFixturesApiElements", "testFixturesRuntimeElements", "testFixturesSourcesElements").forEach { variant ->
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(configurations[variant]) {
        skip()
    }
}

// `-Pkimney.kotlinUnderTest=2.4.0` runs every compiler test on that Kotlin's compiler. The plugin itself stays
// compiled against the catalog's Kotlin, as it is published: what is tested is that jar, loaded into another
// compiler, which is what a user on that Kotlin runs.
if (kotlinUnderTest != libs.versions.kotlin.get()) {
    listOf(
        "testFixturesCompileClasspath",
        "testFixturesRuntimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
        "testArtifacts",
    ).forEach { name ->
        configurations.named(name) {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") useVersion(kotlinUnderTest)
            }
        }
    }
}

tasks.processTestResources { dependsOn(convertTestData) }
