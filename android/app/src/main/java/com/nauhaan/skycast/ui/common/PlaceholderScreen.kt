package com.nauhaan.skycast.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nauhaan.skycast.R
import com.nauhaan.skycast.core.designsystem.theme.Spacing

/**
 * Scaffolding for a screen whose navigation, routing and back behaviour are finished but
 * whose content is not yet built.
 *
 * Every placeholder is a **real, reachable destination** with working back navigation and
 *, where relevant, a button that exercises the onward route. That is deliberate: the
 * navigation hierarchy can be demonstrated, screenshotted and covered by a UI test before
 * any of the feature content exists.
 *
 * Delete each usage as its screen is implemented. None of these should survive into the
 * final submission.
 *
 * @param onNavigateBack when non-null, a top app bar with a back arrow is shown. Pushed
 *   destinations pass this; tab destinations do not, because tabs have no "back".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    plannedContent: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (onNavigateBack != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                // Labelled, not decorative: this is the only affordance
                                // conveying "go back" to a screen-reader user.
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The title is already in the app bar on pushed screens; repeating it in the
            // body would make TalkBack announce it twice.
            if (onNavigateBack == null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = plannedContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = Spacing.lg),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
