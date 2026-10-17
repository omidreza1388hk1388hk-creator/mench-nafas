package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationSettingsLogicTest {

    @Test fun `push types map to in-app types and unknown never gets swallowed`() {
        assertThat(NotificationType.fromPushType("miss_you")).isEqualTo(NotificationType.AFFECTION)
        assertThat(NotificationType.fromPushType("affection")).isEqualTo(NotificationType.AFFECTION)
        assertThat(NotificationType.fromPushType("letter")).isEqualTo(NotificationType.LETTERS)
        assertThat(NotificationType.fromPushType("message")).isEqualTo(NotificationType.MESSAGES)
        assertThat(NotificationType.fromPushType("group_added")).isEqualTo(NotificationType.MESSAGES)
        assertThat(NotificationType.fromPushType(null)).isEqualTo(NotificationType.MESSAGES)
        assertThat(NotificationType.fromPushType("something_new")).isEqualTo(NotificationType.MESSAGES)
    }

    @Test fun `master switch beats type switch`() {
        assertThat(NotificationGate.isAllowed(masterEnabled = false, typeEnabled = true)).isFalse()
        assertThat(NotificationGate.isAllowed(masterEnabled = true, typeEnabled = false)).isFalse()
        assertThat(NotificationGate.isAllowed(masterEnabled = true, typeEnabled = true)).isTrue()
    }

    private val now = 1_000L
    private fun prefs(vararg p: Pair<String, Any>) = mapOf(*p)

    @Test fun `chats with only default values are not overrides`() {
        val all = prefs("silent_a" to false, "vib_a" to "DEFAULT", "chan_rev_a" to 3, "mute_until_a" to 500L)
        assertThat(ChatOverrides.fromPrefs(all, now)).isEmpty()
    }

    @Test fun `mute silent vibration and sound each count`() {
        val all = prefs(
            "mute_until_m" to 5_000L,
            "silent_s" to true, "vib_s" to "DEFAULT",
            "vib_v" to "LONG", "silent_v" to false,
            "sound_uri_u" to "content://x",
        )
        val result = ChatOverrides.fromPrefs(all, now).associateBy { it.conversationId }
        assertThat(result.keys).containsExactly("m", "s", "v", "u")
        assertThat(result.getValue("m").mutedUntil).isEqualTo(5_000L)
        assertThat(result.getValue("s").silent).isTrue()
        assertThat(result.getValue("v").vibrationName).isEqualTo("LONG")
        assertThat(result.getValue("u").hasCustomSound).isTrue()
    }

    @Test fun `keysFor never includes the channel revision counter or other chats`() {
        val keys = ChatOverrides.keysFor("a")
        assertThat(keys).containsExactly("mute_until_a", "silent_a", "vib_a", "sound_uri_a")
    }

    @Test fun `vendor detection`() {
        assertThat(VendorGuide.detect("Xiaomi")).isEqualTo(PhoneVendor.XIAOMI)
        assertThat(VendorGuide.detect("samsung")).isEqualTo(PhoneVendor.SAMSUNG)
        assertThat(VendorGuide.detect("HUAWEI", "HONOR")).isEqualTo(PhoneVendor.HUAWEI)
        assertThat(VendorGuide.detect("realme")).isEqualTo(PhoneVendor.OPPO)
        assertThat(VendorGuide.detect("vivo")).isEqualTo(PhoneVendor.VIVO)
        assertThat(VendorGuide.detect("Google", "Pixel")).isEqualTo(PhoneVendor.OTHER)
        assertThat(VendorGuide.detect(null)).isEqualTo(PhoneVendor.OTHER)
    }

    @Test fun `only known vendors have auto start pages`() {
        assertThat(VendorGuide.autoStartComponents(PhoneVendor.OTHER)).isEmpty()
        assertThat(VendorGuide.autoStartComponents(PhoneVendor.XIAOMI)).isNotEmpty()
    }
}
