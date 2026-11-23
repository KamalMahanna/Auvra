package com.music.bitchord.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.icons.SfIcons

/**
 * Equalizer screen defaulting to the system equalizer.
 */
@Composable
fun EqualizerScreen(
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionId by AppSettings.audioSessionId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        openEqualizer(context, sessionId)
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        SettingsGroup(footer = stringResource(R.string.system_equalizer_footer)) {
            SettingsRow(
                icon = SfIcons.GraphicEq,
                title = stringResource(R.string.system_equalizer),
                subtitle = stringResource(R.string.system_equalizer_subtitle),
                onClick = { openEqualizer(context, sessionId) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
