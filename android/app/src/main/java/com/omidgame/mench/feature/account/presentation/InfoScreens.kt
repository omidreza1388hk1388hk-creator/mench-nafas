package com.omidgame.mench.feature.account.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors

private data class InfoSection(val heading: String, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(title: String, onBackClick: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun SectionList(sections: List<InfoSection>) {
    sections.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(section.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(section.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HelpScreen(onBackClick: () -> Unit) {
    val items = listOf(
        InfoSection(stringResource(R.string.faq_q1), stringResource(R.string.faq_a1)),
        InfoSection(stringResource(R.string.faq_q2), stringResource(R.string.faq_a2)),
        InfoSection(stringResource(R.string.faq_q3), stringResource(R.string.faq_a3)),
        InfoSection(stringResource(R.string.faq_q4), stringResource(R.string.faq_a4)),
        InfoSection(stringResource(R.string.faq_q5), stringResource(R.string.faq_a5)),
        InfoSection(stringResource(R.string.faq_q6), stringResource(R.string.faq_a6)),
    )
    var expanded by remember { mutableIntStateOf(-1) }
    InfoScaffold(stringResource(R.string.help_title), onBackClick) {
        items.forEachIndexed { index, item ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (expanded == index) -1 else index }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.heading, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(
                        if (expanded == index) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(visible = expanded == index) {
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                HorizontalDivider(color = MenchBrand.hairline())
            }
        }
    }
}

@Composable
fun TermsScreen(onBackClick: () -> Unit) {
    InfoScaffold(stringResource(R.string.terms_title), onBackClick) {
        SectionList(
            listOf(
                InfoSection(stringResource(R.string.terms_h1), stringResource(R.string.terms_b1)),
                InfoSection(stringResource(R.string.terms_h2), stringResource(R.string.terms_b2)),
                InfoSection(stringResource(R.string.terms_h3), stringResource(R.string.terms_b3)),
                InfoSection(stringResource(R.string.terms_h4), stringResource(R.string.terms_b4)),
                InfoSection(stringResource(R.string.terms_h5), stringResource(R.string.terms_b5)),
            ),
        )
    }
}

@Composable
fun PrivacyPolicyScreen(onBackClick: () -> Unit) {
    InfoScaffold(stringResource(R.string.privacy_title), onBackClick) {
        SectionList(
            listOf(
                InfoSection(stringResource(R.string.privacy_h1), stringResource(R.string.privacy_b1)),
                InfoSection(stringResource(R.string.privacy_h2), stringResource(R.string.privacy_b2)),
                InfoSection(stringResource(R.string.privacy_h3), stringResource(R.string.privacy_b3)),
                InfoSection(stringResource(R.string.privacy_h4), stringResource(R.string.privacy_b4)),
                InfoSection(stringResource(R.string.privacy_h5), stringResource(R.string.privacy_b5)),
            ),
        )
    }
}

@Composable
fun ChangelogScreen(onBackClick: () -> Unit) {
    InfoScaffold(stringResource(R.string.changelog_title), onBackClick) {
        SectionList(
            listOf(
                InfoSection(stringResource(R.string.changelog_v12_h), stringResource(R.string.changelog_v12_b)),
                InfoSection(stringResource(R.string.changelog_v11_h), stringResource(R.string.changelog_v11_b)),
                InfoSection(stringResource(R.string.changelog_v10_h), stringResource(R.string.changelog_v10_b)),
            ),
        )
    }
}
