package com.dparadox.tgbackup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dparadox.tgbackup.R
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.theme.*

// FIX: Replaced all 600–700 ms tween durations with 280–320 ms.
//      The original CTA button was hidden for ~1050 ms after the screen loaded
//      (350 ms delay + 700 ms fade), making the screen feel stuck on first open.
//      All delays are proportionally reduced so the stagger still reads as smooth
//      but the total entrance completes in ~500 ms instead of ~1200 ms.

@Composable
fun TermsScreen(viewModel: MainViewModel, onAccepted: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // Hero logo + wordmark
        // FIX: 600 ms → 300 ms; slide from -30 px above feels natural for logo entrance
        AnimatedVisibility(
            visible = appeared,
            enter   = fadeIn(tween(300)) + slideInVertically(tween(300, easing = EaseOutCubic)) { -30 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Logo container with glow ring
                Box(
                    Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(PrimaryDim, Color.Transparent)
                            )
                        )
                        .border(1.dp, PrimaryBorder, RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "TG × Media Backup",
                    fontSize      = 28.sp,
                    fontWeight    = FontWeight.Black,
                    color         = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your memories. Your cloud. Forever.",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color      = TextSecondary,
                    textAlign  = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // Subtitle — FIX: 700 ms + 100 ms delay → 280 ms + 60 ms delay
        AnimatedVisibility(
            visible = appeared,
            enter   = fadeIn(tween(280, delayMillis = 60))
        ) {
            Text(
                "Before we begin, please review our privacy practices and terms of use.",
                fontSize   = 14.sp,
                color      = TextMuted,
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp,
                modifier   = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Cards — FIX: 700 ms + 200 ms delay → 300 ms + 120 ms delay
        AnimatedVisibility(
            visible = appeared,
            enter   = fadeIn(tween(300, delayMillis = 120)) +
                      slideInVertically(tween(300, delayMillis = 120, easing = EaseOutCubic)) { 30 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TermsCard(
                    icon  = Icons.Default.PrivacyTip,
                    title = "Privacy & Data Handling",
                    color = Primary,
                    items = listOf(
                        "Images sync directly to YOUR Telegram bot — no intermediaries",
                        "We never store, access, or transmit your data to any servers",
                        "All data stays under your complete control",
                        "Zero analytics, tracking, or third-party sharing",
                        "Bot token and chat ID are encrypted locally using AES-256",
                        "You can delete all data from the app at any time"
                    )
                )

                TermsCard(
                    icon  = Icons.Default.Gavel,
                    title = "Terms of Use",
                    color = Warning,
                    items = listOf(
                        "Keep your bot token private — do not share it",
                        "Use only for legitimate media synchronization purposes",
                        "Comply with Telegram's Terms of Service and local laws",
                        "This app requires READ_IMAGES permission to access your media",
                        "Internet permission is used exclusively for Telegram API calls",
                        "App is provided \"as-is\" — no warranty, use at your own risk",
                        "We are not liable for data loss or service interruptions"
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // CTA button — FIX: 700 ms + 350 ms delay → 300 ms + 200 ms delay
        //      Previously the button wouldn't appear until ~1050 ms after load.
        //      Now it appears at ~500 ms, which feels instant.
        AnimatedVisibility(
            visible = appeared,
            enter   = fadeIn(tween(300, delayMillis = 200)) +
                      slideInVertically(tween(300, delayMillis = 200, easing = EaseOutCubic)) { 24 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.settings.termsAccepted = true
                        onAccepted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor   = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("I Agree — Let's Go", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }

                Text(
                    "By continuing you accept our privacy practices and terms of use.",
                    fontSize  = 11.sp,
                    color     = TextHint,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

// Smooth easing for slide animations
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

@Composable
private fun TermsCard(
    icon: ImageVector,
    title: String,
    color: Color,
    items: List<String>
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            // Card header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Text(
                    title,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 15.sp,
                    color         = TextPrimary,
                    letterSpacing = (-0.1).sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Divider
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent))
                    )
            )

            Spacer(Modifier.height(14.dp))

            // Items
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color.copy(alpha = 0.7f))
                        )
                        Text(
                            item,
                            color      = TextSecondary,
                            fontSize   = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}
