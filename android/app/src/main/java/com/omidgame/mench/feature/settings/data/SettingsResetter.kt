package com.omidgame.mench.feature.settings.data

import android.content.Context
import com.omidgame.mench.feature.security.domain.AppLockRepository
import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import com.omidgame.mench.feature.settings.domain.SettingsResetPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes [SettingsResetPlan]. Removing a key makes the owning store fall back to its own default, so the
 * defaults stay defined in exactly one place. Never deletes messages, accounts, PINs or encryption keys:
 * the only security value touched is the auto-lock delay (PIN and biometrics are left as they are).
 */
@Singleton
class SettingsResetter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockRepository: AppLockRepository,
) {
    suspend fun resetCategory(category: SettingsCategory) {
        SettingsResetPlan.prefTargets(category).forEach { target ->
            val prefs = context.getSharedPreferences(target.file, Context.MODE_PRIVATE)
            val toRemove = SettingsResetPlan.keysToRemove(prefs.all.keys, target)
            if (toRemove.isNotEmpty()) {
                prefs.edit().apply { toRemove.forEach { key -> remove(key) } }.apply()
            }
        }
        if (category == SettingsCategory.CALLS) com.omidgame.mench.core.webrtc.CallSettingsStore.load(context)
        if (category == SettingsCategory.CHATS) {
            // The repository reads an in-memory copy of the chat settings; refresh it so the reset applies immediately.
            com.omidgame.mench.feature.chat.presentation.ChatSettingsStore.load(context)
        }
        if (SettingsResetPlan.resetsAppLockTimeout(category) && appLockRepository.isAppLockEnabled()) {
            appLockRepository.setAutoLockTimeout(AppLockTimeoutOption.AFTER_30_SECONDS)
        }
    }

    suspend fun resetAll() {
        SettingsResetPlan.resettableCategories.forEach { resetCategory(it) }
    }
}
