package com.music.bitchord.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.SongInfo
import com.music.bitchord.data.SongInfoRepository
import com.music.bitchord.data.compactCount
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.SnapshotBackdrop
import com.music.bitchord.ui.player.PlayerBackHandler
import com.music.bitchord.ui.player.additiveChrome
import com.music.bitchord.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val INFO_BODY_MAX_HEIGHT = 360.dp
private val SONG_INFO_DIALOG_WIDTH = 280.dp
private const val STILL_WAIT_MS = 150L

/**
 * Drawn using the same themed iOS menu styling and liquid glass / frosted backdrop
 * as the three-dot menu: [IosMenuShape] continuous corner silhouette, [iosMenuPanel]
 * translucent surface sampling [backdrop] or [hazeState], and theme-reactive [iosMenuColors].
 */
@Composable
internal fun SongInfoDialog(
    song: Song,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
) {
    var retry by remember(song.videoId) { mutableIntStateOf(0) }
    val info by produceState<SongInfo?>(null, song, retry) {
        value = null
        value = SongInfoRepository.load(song)
    }
    val menuColors = iosMenuColors()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val plain by AppSettings.disableBlendEffects.collectAsStateWithLifecycle()

    if (backdrop != null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val appReduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
        val systemAnimationsOff = remember(context) {
            runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
        }
        val reduceMotion = appReduceAnimation || systemAnimationsOff

        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (progress.value == 0f) {
                withFrameNanos { }
                withFrameNanos { }
                (backdrop as? SnapshotBackdrop)?.takeIf {
                    isGlassSupported() && !AppSettings.reduceDynamicBlur.value
                }?.let { still ->
                    withTimeoutOrNull(STILL_WAIT_MS) { snapshotFlow { still.hasStill }.first { it } }
                }
            }
            progress.animateTo(
                1f,
                if (reduceMotion) {
                    tween(durationMillis = 120, easing = LinearEasing)
                } else {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)
                },
            )
        }

        val dismissAction: () -> Unit = {
            scope.launch {
                progress.animateTo(
                    0f,
                    tween(durationMillis = if (reduceMotion) 100 else 170, easing = FastOutSlowInEasing),
                )
                onDismiss()
            }
        }

        PlayerBackHandler(enabled = true, onBack = dismissAction)

        val scrim = Color.Black.copy(alpha = if (dark) 0.30f else 0.14f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .containSheetGestures(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismissAction,
                    ),
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val p = progress.value
                        alpha = (p * 2.5f).coerceIn(0f, 1f)
                        if (!reduceMotion) {
                            val scale = lerp(0.85f, 1f, p)
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                    .iosMenuShadow(IosMenuShape),
            ) {
                Box(
                    modifier = Modifier
                        .width(SONG_INFO_DIALOG_WIDTH)
                        .pointerInput(Unit) {}
                        .iosMenuPanel(
                            colors = menuColors,
                            translucent = true,
                            backdrop = backdrop,
                            hazeState = hazeState,
                            castsShadow = false,
                        ),
                    propagateMinConstraints = true,
                ) {
                    Column(
                        Modifier.then(
                            if (dark && !plain) {
                                Modifier.additiveChrome(true)
                            } else {
                                Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            },
                        ),
                    ) {
                        SongInfoPanelContent(
                            song = song,
                            info = info,
                            menuColors = menuColors,
                            onRetry = { retry++ },
                            onDismiss = dismissAction,
                        )
                    }
                }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .width(SONG_INFO_DIALOG_WIDTH)
                    .iosMenuShadow(IosMenuShape)
                    .iosMenuPanel(
                        colors = menuColors,
                        translucent = hazeState != null,
                        hazeState = hazeState,
                        castsShadow = false,
                    ),
                propagateMinConstraints = true,
            ) {
                Column(
                    Modifier.then(
                        if (dark && !plain) {
                            Modifier.additiveChrome(true)
                        } else {
                            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        },
                    ),
                ) {
                    SongInfoPanelContent(
                        song = song,
                        info = info,
                        menuColors = menuColors,
                        onRetry = { retry++ },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongInfoPanelContent(
    song: Song,
    info: SongInfo?,
    menuColors: IosMenuColors,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.song_info),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = menuColors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = song.title,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = menuColors.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (song.artist.isNotBlank()) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = menuColors.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(menuColors.separator),
    )
    Column(
        Modifier
            .heightIn(max = INFO_BODY_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        val unavailable = stringResource(R.string.song_info_unavailable)
        val rows = listOf(
            R.string.song_info_album to (info?.album ?: song.albumName),
            R.string.song_info_release_date to (info?.releaseDate ?: info?.releaseYear),
            R.string.song_info_views to info?.views?.let(::compactCount),
            R.string.song_info_likes to info?.likes,
            R.string.song_info_comments to info?.comments,
        )
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .padding(horizontal = 18.dp)
                        .background(menuColors.separator),
                )
            }
            SongInfoRow(
                label = stringResource(label),
                value = value ?: unavailable.takeIf { info != null },
                colors = menuColors,
            )
        }
        if (info?.failed == true) {
            Text(
                text = stringResource(R.string.song_info_error),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(menuColors.separator),
    )
    if (info?.failed == true) {
        MenuAction(
            label = stringResource(R.string.song_info_retry),
            colors = menuColors,
            emphasised = false,
            onClick = onRetry,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(menuColors.separator),
        )
    }
    MenuAction(
        label = stringResource(R.string.done),
        colors = menuColors,
        emphasised = true,
        onClick = onDismiss,
    )
}

/** Label on the left, value on the right; a small spinner stands in for the value while it loads. */
@Composable
private fun SongInfoRow(label: String, value: String?, colors: IosMenuColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.secondaryLabel,
                    strokeWidth = 1.75.dp,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.label,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun MenuAction(
    label: String,
    colors: IosMenuColors,
    onClick: () -> Unit,
    emphasised: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (pressed) colors.pressed else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
