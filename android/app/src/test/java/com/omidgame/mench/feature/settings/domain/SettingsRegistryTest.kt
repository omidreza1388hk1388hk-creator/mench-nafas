package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Static checks on the registry: unique ids, and every referenced string exists in en AND fa. */
class SettingsRegistryTest {

    private val nameRegex = Regex("""<string\s+name="([^"]+)"""")
    private fun names(path: String) = nameRegex.findAll(File(path).readText()).map { it.groupValues[1] }.toSet()

    @Test fun `ids are unique`() {
        val ids = SettingsRegistry.all.map { it.id }
        assertThat(ids.groupingBy { it }.eachCount().filterValues { it > 1 }).isEmpty()
    }

    @Test fun `every category has at least one setting except future ones`() {
        val empty = SettingsCategory.entries.filter { SettingsRegistry.byCategory(it).isEmpty() }
        // Every category has at least one setting now.
        assertThat(empty).isEmpty()
    }

    @Test fun `every referenced string exists in both languages`() {
        val source = File("src/main/java/com/omidgame/mench/feature/settings/domain/SettingsRegistry.kt").readText()
        val used = Regex("""R\.string\.(\w+)""").findAll(source).map { it.groupValues[1] }.toSet()
        assertThat(used).isNotEmpty()
        assertThat(used - names("src/main/res/values/strings.xml")).isEmpty()
        assertThat(used - names("src/main/res/values-fa/strings.xml")).isEmpty()
    }

    @Test fun `route targets stay inside the settings graph or known screens`() {
        val routes = SettingsRegistry.all.map { it.target }.filterIsInstance<SettingsTarget.Route>().map { it.route }
        assertThat(routes.all { it.startsWith("settings/") || it == "diagnostics" }).isTrue()
    }

    @Test fun `category route keys round trip`() {
        SettingsCategory.entries.forEach { assertThat(SettingsCategory.fromRouteKey(it.routeKey)).isEqualTo(it) }
        assertThat(SettingsCategory.fromRouteKey("nope")).isNull()
    }
}
