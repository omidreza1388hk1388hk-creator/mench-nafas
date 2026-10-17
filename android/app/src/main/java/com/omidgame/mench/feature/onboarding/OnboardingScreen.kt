package com.omidgame.mench.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.feature.chat.presentation.LivingKind
import com.omidgame.mench.feature.chat.presentation.livingLayer
import kotlinx.coroutines.launch

private data class IntroPage(val icon: ImageVector, val title: Int, val body: Int)

private val introPages = listOf(
    IntroPage(Icons.Filled.Favorite, R.string.onboarding_1_title, R.string.onboarding_1_body),
    IntroPage(Icons.Filled.Lock, R.string.onboarding_2_title, R.string.onboarding_2_body),
    IntroPage(Icons.Filled.QrCode2, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntroFinished: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAvatarPicked: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onSkipProfile: () -> Unit,
    onFinish: () -> Unit,
) {
    when (state.step) {
        OnboardingStep.LOADING, OnboardingStep.DONE ->
            Box(Modifier.fillMaxSize().background(MenchBrand.heroBrush()), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        OnboardingStep.INTRO -> IntroSlides(onFinished = onIntroFinished)
        OnboardingStep.PROFILE -> ProfileStep(
            state = state,
            onDisplayNameChange = onDisplayNameChange,
            onUsernameChange = onUsernameChange,
            onBioChange = onBioChange,
            onAvatarPicked = onAvatarPicked,
            onSave = onSaveProfile,
            onSkip = onSkipProfile,
        )
        OnboardingStep.PERMISSIONS -> PermissionsStep(onFinish = onFinish)
    }
}

@Composable
private fun IntroSlides(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { introPages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == introPages.lastIndex

    Box(Modifier.fillMaxSize().background(MenchBrand.heroBrush()).livingLayer(LivingKind.HEARTS)) {
        TextButton(
            onClick = onFinished,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) { Text(stringResource(R.string.onboarding_skip), color = Color.White) }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val item = introPages[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        stringResource(item.title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(item.body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(introPages.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 1f else 0.45f)),
                    )
                }
            }
            Button(
                onClick = {
                    if (isLast) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            ) {
                Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun ProfileStep(
    state: OnboardingUiState,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAvatarPicked: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAvatarPicked(uri.toString())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.onboarding_profile_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.clickable { pickPhoto.launch("image/*") }) {
            if (state.avatarUri != null) {
                AsyncImage(
                    model = state.avatarUri,
                    contentDescription = stringResource(R.string.account_change_photo),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            } else {
                UserAvatar(userId = null, version = null, name = state.displayName.ifBlank { null }, size = 96.dp)
            }
        }
        TextButton(onClick = { pickPhoto.launch("image/*") }) { Text(stringResource(R.string.account_change_photo)) }

        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.account_display_name_hint)) },
            singleLine = true,
            isError = state.error == OnboardingError.NAME_REQUIRED,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.account_username_hint)) },
            singleLine = true,
            isError = state.error == OnboardingError.USERNAME_INVALID || state.error == OnboardingError.USERNAME_TAKEN,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.bio,
            onValueChange = onBioChange,
            label = { Text(stringResource(R.string.account_bio_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { error ->
            Text(
                stringResource(
                    when (error) {
                        OnboardingError.NAME_REQUIRED -> R.string.onboarding_error_name
                        OnboardingError.USERNAME_INVALID -> R.string.onboarding_error_username
                        OnboardingError.USERNAME_TAKEN -> R.string.onboarding_error_taken
                        OnboardingError.OFFLINE -> R.string.find_offline
                        OnboardingError.UNKNOWN -> R.string.find_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.onboarding_continue))
        }
        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_for_now)) }
    }
}

@Composable
private fun PermissionsStep(onFinish: () -> Unit) {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsGranted by remember {
        mutableStateOf(!needsNotificationPermission || granted(Manifest.permission.POST_NOTIFICATIONS))
    }
    var contactsGranted by remember { mutableStateOf(granted(Manifest.permission.READ_CONTACTS)) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsGranted = it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.onboarding_permissions_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionRow(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.onboarding_perm_notifications_title),
            body = stringResource(R.string.onboarding_perm_notifications_body),
            granted = notificationsGranted,
            onAllow = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
        PermissionRow(
            icon = Icons.Filled.Contacts,
            title = stringResource(R.string.onboarding_perm_contacts_title),
            body = stringResource(R.string.onboarding_perm_contacts_body),
            granted = contactsGranted,
            onAllow = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
        )
        Text(
            stringResource(R.string.onboarding_perm_call_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_finish))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    onAllow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onAllow) { Text(stringResource(R.string.onboarding_allow)) }
        }
    }
}
