package io.github.matthewjones372.kimney.ic

import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A petshop in two modules, `domain` and `api`, built once, changed where `api/Dtos.kt` never names, and built
 * again. The second build must fail as a clean one would.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class IncrementalCompilationTest {

    @Test
    fun `a clean build of the changed petshop reports the new enum entry`(@TempDir dir: File) {
        val shop = Petshop(dir)
        shop.edit("domain/src/main/kotlin/domain/Species.kt", "Cat, Dog", "Cat, Dog, Rabbit")

        shop.build().buildAndFail().output shouldContain SPECIES_MESSAGE
    }

    @Disabled("spec 0020, spec-0020-lookups: the checker records none of the types it reads")
    @Test
    fun `an enum entry added in another module fails the next incremental build`(@TempDir dir: File) {
        val shop = Petshop(dir)
        shop.build().build()
        shop.edit("domain/src/main/kotlin/domain/Species.kt", "Cat, Dog", "Cat, Dog, Rabbit")

        shop.build().buildAndFail().output shouldContain SPECIES_MESSAGE
    }

    @Disabled("spec 0020, spec-0020-lookups: the checker records none of the types it reads")
    @Test
    fun `a sealed case added in another module fails the next incremental build`(@TempDir dir: File) {
        val shop = Petshop(dir)
        shop.build().build()
        shop.edit(
            "domain/src/main/kotlin/domain/Event.kt",
            "data class Sold(val to: String) : Event",
            "data class Sold(val to: String) : Event\n    data class Lost(val at: String) : Event",
        )

        shop.build().buildAndFail().output shouldContain "Event.Lost has no subclass of the same name in EventDto."
    }

    @Disabled("spec 0020, spec-0020-lookups: the checker records none of the types it reads")
    @Test
    fun `a constructor parameter added to a nested target in another file fails the next incremental build`(
        @TempDir dir: File,
    ) {
        val shop = Petshop(dir)
        shop.build().build()
        shop.edit("api/src/main/kotlin/api/Models.kt", "class OwnerDto(val name: String)", OWNER_DTO_WITH_PHONE)

        shop.build().buildAndFail().output shouldContain "Owner has no property 'phone'."
    }

    @Disabled("spec 0020, spec-0020-lookups: the checker records none of the types it reads")
    @Test
    fun `a nested property whose type changed in another module fails the next incremental build`(
        @TempDir dir: File,
    ) {
        val shop = Petshop(dir)
        shop.build().build()
        shop.edit("domain/src/main/kotlin/domain/Owner.kt", "val name: String", "val name: Int")

        shop.build().buildAndFail().output shouldContain "PetDto.owner.name: String"
    }

    private companion object {
        const val SPECIES_MESSAGE = "Species.Rabbit has no entry of the same name in SpeciesDto."
        const val OWNER_DTO_WITH_PHONE = "class OwnerDto(val name: String, val phone: String)"
    }
}

/** The consumer, written out as a user's build is, resolving kimney by its coordinates from the build's repos. */
private class Petshop(private val dir: File) {
    init {
        val kotlin = System.getProperty("kimney.kotlin")
        val version = System.getProperty("kimney.version")
        write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    maven(url = "${File(System.getProperty("kimney.pluginRepo")).toURI()}")
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven(url = "${File(System.getProperty("kimney.icRepo")).toURI()}")
                    mavenCentral()
                }
            }
            rootProject.name = "petshop"
            include("domain", "api")
            """,
        )
        // In-process, so a finished test leaves no Kotlin daemon behind; incremental compilation runs there too.
        write(
            "gradle.properties",
            """
            kotlin.incremental=true
            kotlin.compiler.execution.strategy=in-process
            org.gradle.caching=false
            """,
        )
        write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$kotlin" apply false
                id("io.github.matthewjones372.kimney") version "$version" apply false
            }
            """,
        )
        write("domain/build.gradle.kts", """plugins { kotlin("jvm") }""")
        write("domain/src/main/kotlin/domain/Species.kt", "package domain\n\nenum class Species { Cat, Dog }")
        write(
            "domain/src/main/kotlin/domain/Event.kt",
            """
            package domain

            sealed interface Event {
                data class Born(val on: String) : Event
                data class Sold(val to: String) : Event
            }
            """,
        )
        write("domain/src/main/kotlin/domain/Owner.kt", "package domain\n\nclass Owner(val name: String)")
        write(
            "domain/src/main/kotlin/domain/Pet.kt",
            "package domain\n\nclass Pet(val name: String, val species: Species, val owner: Owner, " +
                "val history: List<Event>)",
        )
        write(
            "api/build.gradle.kts",
            """
            plugins {
                kotlin("jvm")
                id("io.github.matthewjones372.kimney")
            }
            dependencies { implementation(project(":domain")) }
            """,
        )
        write(
            "api/src/main/kotlin/api/Models.kt",
            """
            package api

            enum class SpeciesDto { Cat, Dog }

            sealed interface EventDto {
                data class Born(val on: String) : EventDto
                data class Sold(val to: String) : EventDto
            }

            class OwnerDto(val name: String)

            class PetDto(val name: String, val species: SpeciesDto, val owner: OwnerDto, val history: List<EventDto>)
            """,
        )
        write(
            "api/src/main/kotlin/api/Dtos.kt",
            """
            package api

            import domain.Pet
            import io.github.matthewjones372.kimney.transformInto

            fun Pet.toDto(): PetDto = transformInto()
            """,
        )
    }

    fun build(): GradleRunner =
        GradleRunner.create().withProjectDir(dir).withArguments(":api:classes", "--stacktrace")

    fun edit(path: String, from: String, to: String) {
        val file = dir.resolve(path)
        val text = file.readText()
        check(from in text) { "$path does not contain '$from'" }
        file.writeText(text.replace(from, to))
    }

    private fun write(path: String, text: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(text.trimIndent() + "\n")
    }
}
