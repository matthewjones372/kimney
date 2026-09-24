package io.github.matthewjones372.kimney.ic

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

/** The petshop under Gradle's configuration cache, which the plugin declares it supports (spec 0023). */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ConfigurationCacheTest {

    @Test
    fun `the plugin is stored in the configuration cache, reused, and still derives from it`(@TempDir dir: File) {
        val shop = Petshop(dir)

        val first = shop.build(CACHE).build().output
        first shouldContain "Configuration cache entry stored."
        first shouldNotContain "problem"

        shop.build(CACHE).build().output shouldContain "Reusing configuration cache."

        shop.edit("domain/src/main/kotlin/domain/Species.kt", "Cat, Dog", "Cat, Dog, Rabbit")
        val changed = shop.build(CACHE).buildAndFail().output
        changed shouldContain "Reusing configuration cache."
        changed shouldContain "Species.Rabbit has no entry of the same name in SpeciesDto."
    }

    private companion object {
        const val CACHE = "--configuration-cache"
    }
}
