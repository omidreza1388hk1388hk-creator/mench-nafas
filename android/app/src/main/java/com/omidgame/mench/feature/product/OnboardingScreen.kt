package com.omidgame.mench.feature.product

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchLogo
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.feature.settings.domain.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val prefs: ProductPrefs,
    private val repo: ProductRepository,
) : ViewModel() {
    /** Saves whatever the user filled in (all optional) and marks onboarding finished. */
    fun finish(name: String, username: String, bio: String, photoUri: String?, skipped: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (!skipped) {
                if (name.isNotBlank() || username.isNotBlank() || bio.isNotBlank()) {
                    runCatching { profileRepository.updateProfile(name.trim().ifBlank { null }, username.trim().ifBlank { null }, bio.trim().ifBlank { null }) }
                }
                photoUri?.let { runCatching { profileRepository.uploadAvatar(it) } }
            }
            prefs.onboardingDone = true
            repo.track(if (skipped) "onboarding_skipped" else "onboarding_completed")
            onDone()
        }
    }
}

/**
 * Intro slides -> permission primers -> optional profile. Every step can be skipped; skipping never
 * blocks the app. Each permission is explained BEFORE the system dialog appears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val multiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun next() { scope.launch { if (pager.currentPage < 4) pager.animateScrollToPage(pager.currentPage + 1) } }
    fun finish(skipped: Boolean) = viewModel.finish(name, username, bio, photo?.toString(), skipped, onFinished)

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { finish(skipped = true) }) { Text(stringResource(R.string.onboarding_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = true) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> { MenchLogo(markSize = 110.dp); Slide(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body) }
                    1 -> Slide(R.string.onboarding_private_title, R.string.onboarding_private_body)
                    2 -> {
                        Slide(R.string.onboarding_notif_title, R.string.onboarding_notif_body)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            next()
                        }, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.permission_allow)) }
                    }
                    3 -> {
                        Slide(R.string.onboarding_perms_title, R.string.onboarding_perms_body)
                        Button(onClick = {
                            multiPermission.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                            next()
                        }, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.permission_allow)) }
                    }
                    else -> {
                        Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(if (photo == null) R.string.onboarding_pick_photo else R.string.onboarding_photo_picked))
                        }
                        OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text(stringResource(R.string.onboarding_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        OutlinedTextField(value = username, onValueChange = { username = it.removePrefix("@").take(32) }, label = { Text(stringResource(R.string.onboarding_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text(stringResource(R.string.onboarding_bio)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            if (pager.currentPage < 4) {
                Button(onClick = { next() }) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                Button(onClick = { finish(skipped = false) }) { Text(stringResource(R.string.onboarding_finish)) }
            }
        }
    }
}

@Composable
private fun Slide(titleRes: Int, bodyRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
    Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
}
