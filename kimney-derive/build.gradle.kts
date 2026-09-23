// Loaded by the compiler, which runs on 17 at the oldest.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java { targetCompatibility = JavaVersion.VERSION_17 }
