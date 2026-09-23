import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// The DSL user code calls and links against, so it targets the oldest JVM a
// user is likely to run rather than the one the build compiles with.
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java { targetCompatibility = JavaVersion.VERSION_11 }

// The tests are not shipped, and JUnit 6 needs 17: only main is held to 11.
listOf("testCompileClasspath", "testRuntimeClasspath").forEach {
    configurations.named(it) {
        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17) }
    }
}
tasks.named<KotlinJvmCompile>("compileTestKotlin") { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
tasks.named<JavaCompile>("compileTestJava") { options.release.set(17) }
