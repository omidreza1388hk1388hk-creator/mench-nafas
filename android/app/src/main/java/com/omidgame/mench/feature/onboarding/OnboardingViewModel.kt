package com.omidgame.mench.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.settings.domain.ProfileRepository
import com.omidgame.mench.feature.settings.domain.ProfileUpdateFailureReason
import com.omidgame.mench.feature.settings.domain.ProfileUpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { LOADING, INTRO, PROFILE, PERMISSIONS, DONE }

enum class OnboardingError { NAME_REQUIRED, USERNAME_INVALID, USERNAME_TAKEN, OFFLINE, UNKNOWN }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LOADING,
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUri: String? = null,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
)

private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,32}$")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: OnboardingStore,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var needsProfile = true

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            needsProfile = profile?.displayName.isNullOrBlank()
            val completed = store.isCompleted()
            val first = when {
                completed && !needsProfile -> OnboardingStep.DONE
                completed -> OnboardingStep.PROFILE
                else -> OnboardingStep.INTRO
            }
            _uiState.update { it.copy(step = first, userId = profile?.id.orEmpty()) }
        }
    }

    fun onIntroFinished() = _uiState.update {
        it.copy(step = if (needsProfile) OnboardingStep.PROFILE else OnboardingStep.PERMISSIONS)
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, error = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onBioChange(value: String) = _uiState.update { it.copy(bio = value, error = null) }
    fun onAvatarPicked(uri: String) = _uiState.update { it.copy(avatarUri = uri) }

    /** Safe skip path: the person can finish the profile later from Settings → Account. */
    fun onSkipProfile() = _uiState.update { it.copy(step = OnboardingStep.PERMISSIONS, error = null) }

    fun onSaveProfile() {
        val current = _uiState.value
        if (current.isSaving) return
        val name = current.displayName.trim()
        val username = current.username.trim().removePrefix("@")
        if (name.isEmpty()) {
            _uiState.update { it.copy(error = OnboardingError.NAME_REQUIRED) }
            return
        }
        if (username.isNotEmpty() && !USERNAME_REGEX.matches(username)) {
            _uiState.update { it.copy(error = OnboardingError.USERNAME_INVALID) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (
                val result = profileRepository.updateProfile(
                    displayName = name,
                    username = username.ifEmpty { null },
                    bio = current.bio.trim(),
                )
            ) {
                is ProfileUpdateResult.Success -> {
                    // The photo is a nicety: a failed upload must not trap the person on this step.
                    current.avatarUri?.let { profileRepository.uploadAvatar(it) }
                    _uiState.update { it.copy(isSaving = false, step = OnboardingStep.PERMISSIONS) }
                }
                is ProfileUpdateResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = when (result.reason) {
                            ProfileUpdateFailureReason.USERNAME_TAKEN -> OnboardingError.USERNAME_TAKEN
                            ProfileUpdateFailureReason.NETWORK_UNAVAILABLE -> OnboardingError.OFFLINE
                            ProfileUpdateFailureReason.UNKNOWN -> OnboardingError.UNKNOWN
                        },
                    )
                }
            }
        }
    }

    fun onFinish() {
        viewModelScope.launch {
            store.markCompleted()
            _uiState.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
}
