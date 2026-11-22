package com.music.bitchord.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_logo
import org.jetbrains.compose.resources.painterResource
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.MESH_CYCLE_MILLIS
import com.music.bitchord.ui.player.WelcomeMeshPalettes
import com.music.bitchord.ui.player.cyclingMeshPalette

/**
 * Welcome page: a name for Listen Together and local identity.
 */
@Composable
fun OnboardingScreen(
    initialName: String,
    onNameChosen: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current
    val palette = cyclingMeshPalette(WelcomeMeshPalettes)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = 0,
            continuous = true,
            driftMillis = 6_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )

        OnboardingPage(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            NamePage(
                name = name,
                onNameChange = { name = it.take(NAME_LIMIT) },
                onNext = next@{
                    val chosen = name.trim()
                    if (chosen.isEmpty()) return@next
                    focusManager.clearFocus()
                    onNameChosen(chosen)
                    onFinish()
                },
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 28.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.NamePage(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val keyboardUp = WindowInsets.isImeVisible
    AnimatedVisibility(
        visible = !keyboardUp,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Spacer(Modifier.height(56.dp))
            Image(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(width = 46.dp, height = 30.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Title(stringResource(R.string.onboarding_title))

    Spacer(Modifier.weight(1f))

    Text(
        text = stringResource(R.string.onboarding_name_prompt),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 17.sp,
        color = Color.White.copy(alpha = 0.78f),
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    NameField(value = name, onValueChange = onNameChange, onDone = onNext)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_name_footer),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Spacer(Modifier.height(28.dp))
    PillButton(
        label = stringResource(R.string.onboarding_next),
        onClick = onNext,
        enabled = name.isNotBlank(),
        primary = true,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-1.2).sp,
        ),
        color = Color.White,
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        color = Color.White,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_name_hint),
                style = style,
                color = Color.White.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "pillScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "pillAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = if (primary) Color.Black else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private const val NAME_LIMIT = 40
