package com.omidgame.mench.feature.settings.domain

import com.omidgame.mench.R

/**
 * The settings registry (Phase 1A). Adding a setting = adding one line here plus its strings
 * (sx_sub_<id>, sx_kw_<id>) in BOTH values/ and values-fa/. The home-screen search is built from this list.
 */
object SettingsRegistry {

    val all: List<SettingKey> = listOf(
        SettingKey("account", SettingsCategory.ACCOUNT, R.string.settings_account, R.string.sx_sub_account, R.string.sx_kw_account, SettingsTarget.Route("settings/account")),
        SettingKey("data_export", SettingsCategory.ACCOUNT, R.string.sx_exp_title, R.string.sx_sub_data_export, R.string.sx_kw_data_export, SettingsTarget.Route("settings/data-export")),
        SettingKey("devices", SettingsCategory.ACCOUNT, R.string.devices_title, R.string.sx_sub_devices, R.string.sx_kw_devices, SettingsTarget.Route("settings/devices")),
        SettingKey("accounts", SettingsCategory.ACCOUNT, R.string.accounts_title, R.string.sx_sub_accounts, R.string.sx_kw_accounts, SettingsTarget.Product("accounts")),
        SettingKey("referrals", SettingsCategory.ACCOUNT, R.string.referral_title, R.string.sx_sub_referrals, R.string.sx_kw_referrals, SettingsTarget.Product("referrals")),
        SettingKey("delete_account", SettingsCategory.ACCOUNT, R.string.delete_account_title, R.string.sx_sub_delete_account, R.string.sx_kw_delete_account, SettingsTarget.Product("delete_account")),
        SettingKey("security_hub", SettingsCategory.SECURITY, R.string.sx_hub_title, R.string.sx_sub_security_hub, R.string.sx_kw_security_hub, SettingsTarget.Route("settings/security-hub")),
        SettingKey("privacy_security", SettingsCategory.SECURITY, R.string.settings_privacy_security, R.string.sx_sub_privacy_security, R.string.sx_kw_privacy_security, SettingsTarget.Route("settings/privacy-security")),
        SettingKey("two_step", SettingsCategory.SECURITY, R.string.two_step_title, R.string.sx_sub_two_step, R.string.sx_kw_two_step, SettingsTarget.Route("settings/two-step")),
        SettingKey("privacy", SettingsCategory.SECURITY, R.string.privacy_settings_title, R.string.sx_sub_privacy, R.string.sx_kw_privacy, SettingsTarget.Route("settings/privacy")),
        SettingKey("contact_privacy", SettingsCategory.SECURITY, R.string.contact_privacy_title, R.string.sx_sub_contact_privacy, R.string.sx_kw_contact_privacy, SettingsTarget.Product("contact_privacy")),
        SettingKey("blocked", SettingsCategory.SECURITY, R.string.blocked_title, R.string.sx_sub_blocked, R.string.sx_kw_blocked, SettingsTarget.Product("blocked")),
        SettingKey("notifications", SettingsCategory.NOTIFICATIONS, R.string.settings_notifications, R.string.sx_sub_notifications, R.string.sx_kw_notifications, SettingsTarget.Route("settings/notifications-hub")),
        SettingKey("notification_privacy", SettingsCategory.NOTIFICATIONS, R.string.settings_notification_privacy_title, R.string.sx_sub_notification_privacy, R.string.sx_kw_notification_privacy, SettingsTarget.Route("settings/notification-privacy")),
        SettingKey("battery", SettingsCategory.NOTIFICATIONS, R.string.settings_battery, R.string.sx_sub_battery, R.string.sx_kw_battery, SettingsTarget.Action("battery")),
        SettingKey("chat_settings", SettingsCategory.CHATS, R.string.sx_c_title, R.string.sx_sub_chat_settings, R.string.sx_kw_chat_settings, SettingsTarget.Route("settings/chats")),
        SettingKey("wallpaper", SettingsCategory.CHATS, R.string.wallpaper_title, R.string.sx_sub_wallpaper, R.string.sx_kw_wallpaper, SettingsTarget.Action("wallpaper")),
        SettingKey("effects", SettingsCategory.CHATS, R.string.effects_settings_title, R.string.sx_sub_effects, R.string.sx_kw_effects, SettingsTarget.Action("effects")),
        SettingKey("saved", SettingsCategory.CHATS, R.string.saved_title, R.string.sx_sub_saved, R.string.sx_kw_saved, SettingsTarget.Product("saved")),
        SettingKey("us_settings", SettingsCategory.US, R.string.sx_us_title, R.string.sx_sub_us_settings, R.string.sx_kw_us_settings, SettingsTarget.Route("settings/us")),
        SettingKey("planner", SettingsCategory.US, R.string.planner_title, R.string.sx_sub_planner, R.string.sx_kw_planner, SettingsTarget.Product("planner")),
        SettingKey("calls_settings", SettingsCategory.CALLS, R.string.sx_calls_title, R.string.sx_sub_calls_settings, R.string.sx_kw_calls_settings, SettingsTarget.Route("settings/calls")),
        SettingKey("data_storage", SettingsCategory.DATA, R.string.settings_data_storage, R.string.sx_sub_data_storage, R.string.sx_kw_data_storage, SettingsTarget.Route("settings/data-storage")),
        SettingKey("data_cleanup", SettingsCategory.DATA, R.string.sx_dc_title, R.string.sx_sub_data_cleanup, R.string.sx_kw_data_cleanup, SettingsTarget.Route("settings/data-cleanup")),
        SettingKey("data_usage", SettingsCategory.DATA, R.string.usage_title, R.string.sx_sub_data_usage, R.string.sx_kw_data_usage, SettingsTarget.Product("data_usage")),
        SettingKey("sync", SettingsCategory.DATA, R.string.sync_title, R.string.sx_sub_sync, R.string.sx_kw_sync, SettingsTarget.Product("sync")),
        SettingKey("appearance", SettingsCategory.APPEARANCE, R.string.settings_appearance, R.string.sx_sub_appearance, R.string.sx_kw_appearance, SettingsTarget.Route("settings/appearance")),
        SettingKey("appearance_extras", SettingsCategory.APPEARANCE, R.string.sx_ap_title, R.string.sx_sub_appearance_extras, R.string.sx_kw_appearance_extras, SettingsTarget.Route("settings/appearance-extras")),
        SettingKey("language_region", SettingsCategory.LANGUAGE, R.string.sx_lang_title, R.string.sx_sub_language_region, R.string.sx_kw_language_region, SettingsTarget.Route("settings/language")),
        SettingKey("about", SettingsCategory.HELP, R.string.sx_about_title, R.string.sx_sub_about, R.string.sx_kw_about, SettingsTarget.Route("settings/about")),
        SettingKey("diagnostics", SettingsCategory.HELP, R.string.diagnostics_title, R.string.sx_sub_diagnostics, R.string.sx_kw_diagnostics, SettingsTarget.Route("diagnostics")),
        SettingKey("help", SettingsCategory.HELP, R.string.help_title, R.string.sx_sub_help, R.string.sx_kw_help, SettingsTarget.Product("help")),
        SettingKey("feedback", SettingsCategory.HELP, R.string.feedback_title, R.string.sx_sub_feedback, R.string.sx_kw_feedback, SettingsTarget.Product("feedback")),
        SettingKey("legal", SettingsCategory.HELP, R.string.legal_title, R.string.sx_sub_legal, R.string.sx_kw_legal, SettingsTarget.Product("legal")),
        SettingKey("changelog", SettingsCategory.HELP, R.string.changelog_title, R.string.sx_sub_changelog, R.string.sx_kw_changelog, SettingsTarget.Product("changelog")),
        SettingKey("link_device", SettingsCategory.ACCOUNT, R.string.link_device_title, R.string.sx_sub_link_device, R.string.sx_kw_link_device, SettingsTarget.Product("link_device")),
        SettingKey("find_people", SettingsCategory.ACCOUNT, R.string.find_people_title, R.string.sx_sub_find_people, R.string.sx_kw_find_people, SettingsTarget.Product("find_people")),
        SettingKey("plus", SettingsCategory.ACCOUNT, R.string.plus_title, R.string.sx_sub_plus, R.string.sx_kw_plus, SettingsTarget.Product("plus")),
        SettingKey("transparency", SettingsCategory.HELP, R.string.transparency_title, R.string.sx_sub_transparency, R.string.sx_kw_transparency, SettingsTarget.Product("transparency")),
    )

    fun byCategory(category: SettingsCategory): List<SettingKey> = all.filter { it.category == category }

    fun byId(id: String): SettingKey? = all.firstOrNull { it.id == id }
}
