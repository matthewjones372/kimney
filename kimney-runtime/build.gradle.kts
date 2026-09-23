// The DSL user code calls and links against, so it targets the oldest JVM a
// user is likely to run rather than the one the build compiles with.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

java { targetCompatibility = JavaVersion.VERSION_11 }
