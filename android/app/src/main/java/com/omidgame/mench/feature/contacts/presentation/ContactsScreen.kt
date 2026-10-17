package com.omidgame.mench.feature.contacts.presentation

import androidx.compose.foundation.background
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.omidgame.mench.R
import com.omidgame.mench.feature.contacts.domain.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onContactClick: (Contact) -> Unit,
    onPermissionGranted: () -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) onPermissionGranted()
    }

    // Same "check once, then react" shape as ChatScreen's mic-permission
    // handling, but on first composition rather than a button tap — a
    // contacts screen with nothing to show until you press a button
    // would be a confusing empty screen; asking immediately on open
    // matches what every other messenger's contacts tab does.
    LaunchedEffect(Unit) {
        if (permissionGranted) onPermissionGranted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionGranted -> PermissionRationale(
                    onGrantClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.contacts.isEmpty() -> Text(
                    text = stringResource(R.string.contacts_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.filteredContacts, key = { it.userId }) { contact ->
                            ContactRow(
                                contact = contact,
                                isStarting = state.startingConversationForUserId == contact.userId,
                                onClick = { onContactClick(contact) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.contacts_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrantClick, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.contacts_permission_grant_action))
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, isStarting: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.deviceContactName) },
        supportingContent = {
            // The MENCH display name, when the person set one, is a more
            // reliable "who this actually is on MENCH today" signal than
            // whatever name got saved in the address book — shown as a
            // secondary line rather than replacing the contact name
            // outright, so the row still reads as "this address-book
            // entry, and here's who they are on MENCH".
            contact.displayName?.let { Text(it) }
        },
        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
        trailingContent = { if (isStarting) CircularProgressIndicator(modifier = Modifier.size(20.dp)) },
        modifier = Modifier.clickable(enabled = !isStarting, onClick = onClick),
    )
}
