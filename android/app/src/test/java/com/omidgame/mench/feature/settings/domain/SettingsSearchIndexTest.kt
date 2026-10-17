package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSearchIndexTest {

    // Fake string table: resource id -> text. Ids are arbitrary here; the index only calls resolve().
    private val strings = mapOf(
        1 to "Privacy & Security", 2 to "App lock, PIN and biometrics", 3 to "app lock pin fingerprint قفل برنامه رمز",
        4 to "Notifications", 5 to "Previews and quiet hours", 6 to "sound vibration ساعت سکوت",
        7 to "قفل برنامه", 8 to "رمز و اثر انگشت", 9 to "پین رمز",
        10 to "Backup", 11 to "Encrypted chat backup", 12 to "restore export",
    )
    private val route = SettingsTarget.Route("x")
    private val keys = listOf(
        SettingKey("sec", SettingsCategory.SECURITY, 1, 2, 3, route),
        SettingKey("notif", SettingsCategory.NOTIFICATIONS, 4, 5, 6, route),
        SettingKey("fa_lock", SettingsCategory.SECURITY, 7, 8, 9, route),
        SettingKey("backup", SettingsCategory.SECURITY, 10, 11, 12, route),
    )
    private val index = SettingsSearchIndex(keys) { strings.getValue(it) }

    private fun ids(q: String) = index.search(q).map { it.key.id }

    @Test fun `blank query returns nothing`() {
        assertThat(ids("")).isEmpty()
        assertThat(ids("   ")).isEmpty()
    }

    @Test fun `title prefix outranks keyword match`() {
        // "pin" appears only in keywords/subtitle of sec, so notif is not returned and sec is.
        assertThat(ids("pin")).contains("sec")
        assertThat(ids("notif").first()).isEqualTo("notif")
    }

    @Test fun `all tokens must match`() {
        assertThat(ids("quiet hours")).containsExactly("notif")
        assertThat(ids("quiet banana")).isEmpty()
    }

    @Test fun `persian query with arabic letters and zwnj still matches`() {
        assertThat(ids("قفل برنامه")).contains("fa_lock")
        assertThat(ids("پيـن")).contains("fa_lock") // Arabic yeh + tatweel
    }

    @Test fun `digit style does not matter`() {
        val withDigits = SettingsSearchIndex(
            listOf(SettingKey("two", SettingsCategory.SECURITY, 20, null, null, route)),
        ) { if (it == 20) "تایید ۲ مرحله‌ای" else "" }
        assertThat(withDigits.search("2 مرحله").map { it.key.id }).containsExactly("two")
        assertThat(withDigits.search("۲ مرحله").map { it.key.id }).containsExactly("two")
    }

    @Test fun `exact title is ranked first`() {
        assertThat(ids("backup").first()).isEqualTo("backup")
    }

    @Test fun `ties keep registry order`() {
        val same = SettingsSearchIndex(
            listOf(
                SettingKey("a", SettingsCategory.HELP, 30, null, null, route),
                SettingKey("b", SettingsCategory.HELP, 31, null, null, route),
            ),
        ) { "help" }
        assertThat(same.search("help").map { it.key.id }).containsExactly("a", "b").inOrder()
    }

    @Test fun `substring in title scores below prefix`() {
        val idx = SettingsSearchIndex(
            listOf(
                SettingKey("sub", SettingsCategory.HELP, 40, null, null, route),
                SettingKey("pre", SettingsCategory.HELP, 41, null, null, route),
            ),
        ) { if (it == 40) "xxlockxx" else "lock screen" }
        assertThat(idx.search("lock").map { it.key.id }).containsExactly("pre", "sub").inOrder()
    }
}
