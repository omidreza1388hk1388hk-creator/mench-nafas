package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGroupScreen(
    state: NewGroupUiState,
    onSubmit: (title: String, memberPhoneNumbers: List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("+") }
    var members by remember { mutableStateOf(listOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.chat_new_group_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.chat_group_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text(stringResource(R.string.chat_add_member_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                modifier = Modifier.padding(start = 8.dp),
                enabled = phoneInput.length >= 8 && phoneInput !in members,
                onClick = {
                    members = members + phoneInput
                    phoneInput = "+"
                },
            ) {
                Text(stringResource(R.string.chat_add_member_action))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { phone ->
                AssistChip(
                    onClick = { members = members - phone },
                    label = { Text(phone) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_remove_member)) },
                )
            }
        }
        if (state is NewGroupUiState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state is NewGroupUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onSubmit(title, members) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && members.isNotEmpty(),
            ) {
                Text(stringResource(R.string.chat_create_group_action))
            }
        }
    }
}
