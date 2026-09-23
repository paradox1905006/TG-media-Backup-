package com.dparadox.tgbackup.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dparadox.tgbackup.ui.theme.*

// ── Bounce-click modifier ──────────────────────────────────────────────────

enum class ButtonState { Pressed, Idle }

@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit = {}): Modifier {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val scale by animateFloatAsState(
        targetValue = if (buttonState == ButtonState.Pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bounce"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(false)
                    buttonState = ButtonState.Pressed
                    waitForUpOrCancellation()
                    buttonState = ButtonState.Idle
                }
            }
        }
}

// ── Glowing border modifier ────────────────────────────────────────────────

fun Modifier.glowingBorder(
    color: Color,
    cornerRadius: Dp = 20.dp,
    glowRadius: Dp = 6.dp,
    borderWidth: Dp = 1.dp,
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val bw = borderWidth.toPx()
    val gr = glowRadius.toPx()
    // glow halo
    drawRoundRect(
        color = color.copy(alpha = 0.25f),
        topLeft = Offset(-gr, -gr),
        size = androidx.compose.ui.geometry.Size(size.width + gr * 2, size.height + gr * 2),
        cornerRadius = CornerRadius(cr + gr),
        style = Stroke(width = gr * 2)
    )
    // crisp border
    drawRoundRect(
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(bw / 2, bw / 2),
        size = androidx.compose.ui.geometry.Size(size.width - bw, size.height - bw),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = bw)
    )
}

// ── Premium card ───────────────────────────────────────────────────────────

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    glowing: Boolean = false,
    glowColor: Color = Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Surface)
            .then(
                if (glowing)
                    Modifier.glowingBorder(glowColor, cornerRadius)
                else
                    Modifier.border(1.dp, Border, shape)
            )
    ) {
        Column(content = content)
    }
}

// ── Section label ──────────────────────────────────────────────────────────

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Primary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.3f)))
                )
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.4.sp,
            color = TextMuted
        )
    }
}

// ── Stat tile ─────────────────────────────────────────────────────────────

@Composable
fun StatTile(
    label: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    PremiumCard(modifier = modifier) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(17.dp))
                }
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = accentColor)
            }
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.5).sp)
        }
    }
}

// ── Status badge / pill ────────────────────────────────────────────────────

@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Text(
            label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Pulsing dot indicator ─────────────────────────────────────────────────

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = alpha))
    )
}

// ── Shimmer skeleton ──────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue  = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_offset"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(SurfaceAlt, SurfaceElevated, SurfaceAlt),
                    start  = Offset(offset * 400, 0f),
                    end    = Offset(offset * 400 + 400, 0f)
                )
            )
    )
}

// ── Gradient accent line ───────────────────────────────────────────────────

@Composable
fun GradientDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Border, Border, Color.Transparent)
                )
            )
    )
}

// ── Empty state placeholder ────────────────────────────────────────────────

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, fontSize = 48.sp)
        Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp)
        Text(
            subtitle,
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ── Delete confirmation dialog ─────────────────────────────────────────────

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    isPermanent: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (isPermanent) "Delete Permanently?" else "Move to Recycle Bin?",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                if (isPermanent) {
                    "Are you sure you want to permanently delete $count item${if (count > 1) "s" else ""}? This action cannot be undone."
                } else {
                    "Selected $count item${if (count > 1) "s" else ""} will be moved to the Recycle Bin. They will be stored there for 30 days before being permanently deleted."
                },
                color = TextSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isPermanent) "Delete" else "Move to Bin", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
