package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearTextOnly
import org.churchpresenter.liveoutput.restoreText
import org.churchpresenter.liveoutput.textCleared
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview_take
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.strings.generated.resources.tooltip_clear_text
import org.churchpresenter.strings.generated.resources.tooltip_restore_text
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PANIC_BUTTON_HEIGHT = 38.dp
private val PANIC_BUTTON_SHAPE = AppShape(8.dp)
private val STATUS_DOT_SIZE = 7.dp

private val COLOR_BLACKOUT_BG = Color(0xFF381010)
private val COLOR_BLACKOUT_BORDER = Color(0xFFFF4D4D)
private val COLOR_LIVE_RED = Color(0xFFFF3344)
private val COLOR_TAKE_READY_BG = Color(0xFF006622)
private val COLOR_TAKE_READY_BORDER = Color(0xFF00E676)
private val COLOR_CLEARED_AMBER_BG = Color(0xFF4A3000)
private val COLOR_CLEARED_AMBER_BORDER = Color(0xFFFFB300)

/** Test handle for preview mode's Take button. */
internal const val STUDIO_TAKE_TAG = "preview_take"

/**
 * Master Control panic and switcher bar for live church broadcasting.
 * Features immediate one-click Blackout, Clear/Restore Text, and Transition (Take) actions.
 */
@Composable
internal fun StudioMasterPanicBar(
    presenterManager: PresenterManager,
    onClearDisplay: () -> Unit,
    previewModeEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isAnythingLive = presenterManager.anythingLive
    val isTextCleared = presenterManager.textCleared
    val bus = presenterManager.previewBus
    val isTakeReady = bus.anythingCued

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StudioStatusBar(isAnythingLive, isTakeReady, previewModeEnabled)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PanicBlackoutButton(
                onClick = onClearDisplay,
                modifier = Modifier.weight(1f),
            )
            PanicClearTextButton(
                presenterManager = presenterManager,
                isCleared = isTextCleared,
                modifier = Modifier.weight(1f),
            )
            if (previewModeEnabled) {
                PanicTakeButton(
                    presenterManager = presenterManager,
                    isReady = isTakeReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StudioStatusBar(isLive: Boolean, isCued: Boolean, previewMode: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(STATUS_DOT_SIZE)
                    .clip(CircleShape)
                    .background(if (isLive) COLOR_LIVE_RED else MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = if (isLive) "AO VIVO • PROGRAM" else "STANDBY • AGUARDANDO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = if (isLive) COLOR_LIVE_RED else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (previewMode) {
            Text(
                text = if (isCued) "PRÉVIA ENGATILHADA" else "MODO PRÉVIA",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCued) COLOR_TAKE_READY_BORDER else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PanicBlackoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.tooltip_clear_display)
    Row(
        modifier = modifier
            .height(PANIC_BUTTON_HEIGHT)
            .clip(PANIC_BUTTON_SHAPE)
            .background(COLOR_BLACKOUT_BG)
            .border(1.dp, COLOR_BLACKOUT_BORDER.copy(alpha = 0.5f), PANIC_BUTTON_SHAPE)
            .clickable(onClick = onClick)
            .guideTarget(GuideTargets.CLEAR_OUTPUT)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_close),
            contentDescription = null,
            tint = COLOR_BLACKOUT_BORDER,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "BLECAUTE",
            color = COLOR_BLACKOUT_BORDER,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun PanicClearTextButton(
    presenterManager: PresenterManager,
    isCleared: Boolean,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(if (isCleared) Res.string.tooltip_restore_text else Res.string.tooltip_clear_text)
    val bgColor by animateColorAsState(
        targetValue = if (isCleared) COLOR_CLEARED_AMBER_BG else MaterialTheme.colorScheme.surfaceVariant,
        label = "clear_text_bg",
    )
    val tintColor = if (isCleared) COLOR_CLEARED_AMBER_BORDER else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .height(PANIC_BUTTON_HEIGHT)
            .clip(PANIC_BUTTON_SHAPE)
            .background(bgColor)
            .border(
                1.dp,
                if (isCleared) COLOR_CLEARED_AMBER_BORDER.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                PANIC_BUTTON_SHAPE,
            )
            .clickable {
                if (isCleared) presenterManager.restoreText() else presenterManager.clearTextOnly()
            }
            .semantics { contentDescription = desc }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isCleared) Icons.Outlined.TextFields else Icons.Outlined.FormatClear,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isCleared) "RESTAURAR" else "TEXTO",
            color = tintColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun PanicTakeButton(
    presenterManager: PresenterManager,
    isReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val bus = presenterManager.previewBus
    val desc = stringResource(Res.string.preview_take)
    val bgColor by animateColorAsState(
        targetValue = if (isReady) COLOR_TAKE_READY_BG else MaterialTheme.colorScheme.surfaceVariant,
        label = "take_bg",
    )
    val tintColor = if (isReady) COLOR_TAKE_READY_BORDER else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .height(PANIC_BUTTON_HEIGHT)
            .clip(PANIC_BUTTON_SHAPE)
            .background(bgColor)
            .border(
                1.dp,
                if (isReady) COLOR_TAKE_READY_BORDER.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                PANIC_BUTTON_SHAPE,
            )
            .clickable(enabled = isReady) { bus.take() }
            .testTag(STUDIO_TAKE_TAG)
            .guideTarget(GuideTargets.TAKE)
            .semantics { contentDescription = desc }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "TAKE",
            color = tintColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        )
    }
}
