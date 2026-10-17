package com.omidgame.mench.localization

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Guards localization (Phase 4A): every string must exist in both the English and the
 * Persian resource files, and format placeholders must match. Gradle runs unit tests with
 * the module directory (app/) as the working directory.
 */
class StringsParityTest {

    private val keyRegex = Regex("""<string\s+name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholderRegex = Regex("""%(\d+\$)?[sdf]""")

    private fun load(path: String): Map<String, String> {
        val file = File(path)
        assertThat(file.exists()).isTrue()
        return keyRegex.findAll(file.readText())
            .filter { !it.groupValues[2].contains("translatable=\"false\"") }
            .associate { it.groupValues[1] to it.groupValues[3] }
    }

    private val en get() = load("src/main/res/values/strings.xml")
    private val fa get() = load("src/main/res/values-fa/strings.xml")

    @Test
    fun `no resource name is declared twice in a file`() {
        listOf("src/main/res/values/strings.xml", "src/main/res/values-fa/strings.xml").forEach { path ->
            val names = keyRegex.findAll(File(path).readText()).map { it.groupValues[1] }.toList()
            val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertThat(duplicates).isEmpty()
        }
    }

    @Test
    fun `persian has every english key`() {
        assertThat(en.keys - fa.keys).isEmpty()
    }

    @Test
    fun `english has every persian key`() {
        assertThat(fa.keys - en.keys).isEmpty()
    }

    @Test
    fun `placeholders match between languages`() {
        val e = en
        val f = fa
        val mismatched = e.keys.intersect(f.keys).filter { key ->
            placeholderRegex.findAll(e.getValue(key)).map { it.value }.sorted().toList() !=
                placeholderRegex.findAll(f.getValue(key)).map { it.value }.sorted().toList()
        }
        assertThat(mismatched).isEmpty()
    }
}
