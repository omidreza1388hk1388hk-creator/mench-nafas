package com.omidgame.mench.feature.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Remembers that the intro slides and permission primers were already shown on this device. */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val completedKey = booleanPreferencesKey("completed")

    suspend fun isCompleted(): Boolean = context.onboardingDataStore.data.first()[completedKey] ?: false

    suspend fun markCompleted() {
        context.onboardingDataStore.edit { it[completedKey] = true }
    }
}
