package com.nauhaan.skycast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nauhaan.skycast.BuildConfig
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.theme.SkyCastTheme
import com.nauhaan.skycast.core.designsystem.theme.Spacing

/**
 * Attribution and dependency licences, discoverable **in the app** as well as in
 * `docs/licensing.md`.
 *
 * The list is hand-maintained rather than generated, and every entry corresponds to a row in that
 * document.
 *
 * The iOS counterpart is the `AboutScreen` in `Features/Settings/SettingsScreen.swift`, which
 * states that iOS has no third-party runtime dependencies at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeading(stringResource(R.string.about_data_heading))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_data_source)) },
                supportingContent = { Text(stringResource(R.string.about_data_url)) },
            )
            // NOAA's data is public domain and needs no attribution clause honoured. Credited anyway:
            // two of the app's three sources are theirs, and a screen that names only one of them
            // reads as though the other two came from nowhere.
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_data_source_noaa)) },
                supportingContent = { Text(stringResource(R.string.about_data_url_noaa)) },
            )
            HorizontalDivider()

            SectionHeading(stringResource(R.string.about_licences_heading))
            LICENCES.forEach { (name, licence) ->
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(licence) },
                )
            }
            HorizontalDivider()

            SectionHeading(stringResource(R.string.about_build_heading))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_api_key_configured)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (BuildConfig.OPEN_WEATHER_API_KEY.isBlank()) {
                                R.string.about_no
                            } else {
                                R.string.about_yes
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.xs,
        ),
    )
}

/**
 * Runtime dependencies and their licences.
 *
 * Not localised: a licence name is a legal identifier, and translating "Apache License 2.0" would
 * misstate which licence applies.
 */
private val LICENCES = listOf(
    "Jetpack Compose, Room, DataStore, Hilt (AndroidX / Google)" to "Apache License 2.0",
    "Retrofit, OkHttp (Square)" to "Apache License 2.0",
    "kotlinx.serialization, kotlinx.coroutines (JetBrains)" to "Apache License 2.0",
    "Material Symbols icons (Google)" to "Apache License 2.0",
)

@Preview(name = "About", showBackground = true)
@Composable
private fun AboutPreview() {
    SkyCastTheme {
        AboutScreen(onNavigateBack = {})
    }
}
