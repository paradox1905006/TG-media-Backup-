package com.dparadox.tgbackup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dparadox.tgbackup.ui.MainViewModel
import com.dparadox.tgbackup.ui.theme.*

@Composable
fun TermsScreen(viewModel: MainViewModel, onAccepted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "TG x Media Backup",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            "Where Your Memories Get To\nLive Forever",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Text(
            "Before we begin, please read and\nunderstand the following important\ninformation:",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        TermsCard(
            icon = Icons.Default.PrivacyTip,
            title = "Privacy & Data Handling",
            items = listOf(
                "Your images are synced directly to YOUR Telegram bot as-is",
                "We do NOT store, access, or transmit your data to any servers",
                "All data remains under YOUR complete control",
                "Zero analytics, tracking, or third-party data sharing",
                "Your bot token and chat ID are encrypted locally using AES-256",
                "You can delete all data at any time"
            )
        )

        Spacer(Modifier.height(16.dp))

        TermsCard(
            icon = Icons.Default.Gavel,
            title = "Terms of Use",
            items = listOf(
                "Ensure your bot token is kept secure and not shared",
                "Use this app only for legitimate Images synchronization purposes",
                "Comply with Telegram's Terms of Service and local laws",
                "This app is not intended for spam or malicious activities",
                "Use at your own responsibility and discretion",
                "This app requires READ_IMAGES permission to access your Images",
                "Internet permission is used ONLY for Telegram API communication",
                "The app is provided \"as-is\" without warranties",
                "We are not liable for any data loss or service interruptions"
            )
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.settings.termsAccepted = true
                onAccepted()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("I Acknowledge & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TermsCard(icon: ImageVector, title: String, items: List<String>) {
    Surface(
        color = Color(0xFF2C2C2C),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF404040),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            
            Spacer(Modifier.height(16.dp))
            
            items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                    Text(item, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}
