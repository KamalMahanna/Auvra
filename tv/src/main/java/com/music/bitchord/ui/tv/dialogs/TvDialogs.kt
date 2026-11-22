package com.music.bitchord.ui.tv.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvPinThrottle
import com.music.bitchord.data.settings.TvSettings
import kotlin.math.ceil
import kotlinx.coroutines.delay
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.TvAppMark
import com.music.bitchord.ui.tv.auth.TvQrCodeView
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvDialogButton
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvTextField
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.components.tvPlatter
import com.music.bitchord.ui.tv.display.TvRefreshRateController
import com.music.bitchord.ui.tv.display.TvRefreshRatePreference
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Account dialog: no-op since accounts are removed. */
@Composable
fun TvAccountDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit = {},
    allowPhoneSignIn: Boolean = false,
) {
    LaunchedEffect(Unit) {
        onDismiss()
    }
}

@Composable
fun TvAboutDialog(onDismiss: () -> Unit) {
    TvDialog(title = "BitChord", message = "Version ${BuildConfig.VERSION_NAME}", onDismissRequest = onDismiss) {
        TvAppMark(size = 77.dp, modifier = Modifier.padding(bottom = 21.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TvListRow(title = "Kushagra Singh", value = "Creator", enabled = false, onClick = {})
            TvListRow(title = "Nithyanantha", value = "TV App", enabled = false, onClick = {})
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Free software under the GNU GPL v3. Not affiliated with YouTube or Google.",
            style = TvType.Caption,
            color = TvGlass.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        TvDialogButton(text = "OK", onClick = onDismiss, initialFocus = true)
    }
}

@Composable
fun TvRefreshRateDialog(onDismiss: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val capabilities by TvRefreshRateController.capabilities.collectAsState()
    val current by TvRefreshRateController.preference.collectAsState()

    TvDialog(
        title = "Refresh Rate",
        message = "Now ${capabilities.currentPhysicalWidth} × ${capabilities.currentPhysicalHeight} at " +
            String.format(java.util.Locale.US, "%.0f Hz", capabilities.actualRefreshRateHz),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TvRefreshRatePreference.entries.forEach { preference ->
                val supported = preference != TvRefreshRatePreference.SMOOTH_60 || capabilities.is60HzSupported
                TvListRow(
                    title = preference.label,
                    subtitle = preference.description,
                    trailingIcon = if (preference == current) Icons.Rounded.Check else null,
                    enabled = supported,
                    modifier = if (preference == current) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        activity?.let { TvRefreshRateController.setPreference(it, preference) }
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
fun TvThemeDialog(onDismiss: () -> Unit) {
    val currentId by TvSettings.tvTheme.collectAsState()
    val current = AppThemeOption.fromId(currentId)

    TvDialog(title = "Appearance", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AppThemeOption.entries.forEach { theme ->
                TvListRow(
                    title = theme.title,
                    subtitle = theme.description,
                    trailingIcon = if (theme == current) Icons.Rounded.Check else null,
                    modifier = if (theme == current) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        TvSettings.setTvTheme(theme.id)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Which lyric databases the player may ask, and in what order. Select turns a
 * source on or off; Left and Right move the focused one up or down the list,
 * since a remote has no way to drag. The last source left on can't be turned off.
 */
@Composable
fun TvLyricsSourcesDialog(onDismiss: () -> Unit) {
    val selected by AppSettings.lyricsSources.collectAsState()
    val savedOrder by AppSettings.lyricsSourceOrder.collectAsState()
    val wordFirst by AppSettings.prioritizeSyllableSync.collectAsState()
    val order = remember(savedOrder) { LyricsSource.ordered(savedOrder) }

    TvDialog(
        title = "Lyrics Sources",
        message = "Select to turn a source on or off. Press Left or Right to move it up or down; the first is asked first.",
        width = 512.dp,
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 304.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(order, key = { _, source -> source.name }) { index, source ->
                val checked = source in selected
                TvListRow(
                    title = source.label,
                    subtitle = source.detail,
                    value = "${index + 1}",
                    leadingIcon = if (checked) Icons.Rounded.Check else null,
                    modifier = Modifier
                        .tvInitialFocus(enabled = index == 0)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val target = when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> index - 1
                                KeyEvent.KEYCODE_DPAD_RIGHT -> index + 1
                                else -> return@onKeyEvent false
                            }
                            if (target in order.indices) {
                                AppSettings.setLyricsSourceOrder(
                                    order.toMutableList().apply { add(target, removeAt(index)) },
                                )
                            }
                            true
                        },
                    onClick = {
                        if (checked && selected.size <= 1) return@TvListRow
                        AppSettings.setLyricsSources(if (checked) selected - source else selected + source)
                    },
                )
            }
            item(key = "wordFirst") {
                TvListRow(
                    title = "Prefer Word-Synced",
                    subtitle = "Hold out for word-by-word lyrics before settling for line-by-line.",
                    value = if (wordFirst) "On" else "Off",
                    onClick = { AppSettings.setPrioritizeSyllableSync(!wordFirst) },
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        TvDialogButton(text = "Reset to Default", onClick = AppSettings::resetLyricsSourceSettings)
        Spacer(modifier = Modifier.height(6.dp))
        TvDialogButton(text = "Done", onClick = onDismiss)
    }
}
