package com.scansfer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scansfer.app.ui.components.ActionCard
import com.scansfer.app.ui.theme.Teal
import com.scansfer.app.ui.theme.Violet

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    0.45f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(40.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Violet, Teal))),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Scansfer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Send a photo\nwith your camera.",
                style = MaterialTheme.typography.displaySmall,
                lineHeight = 44.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "One phone plays a stream of QR codes. The other watches. " +
                    "Photos and videos, no Wi-Fi, no cable, no account — just line of sight.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            ActionCard(
                icon = Icons.AutoMirrored.Rounded.Send,
                title = "Send",
                subtitle = "Pick a photo or video and show it as QR codes",
                accent = Violet,
                onClick = onSend,
            )
            Spacer(Modifier.height(14.dp))
            ActionCard(
                icon = Icons.Rounded.CameraAlt,
                title = "Receive",
                subtitle = "Point your camera at the other screen",
                accent = Teal,
                onClick = onReceive,
            )

            Spacer(Modifier.height(32.dp))
            HowItWorks()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HowItWorks() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleMedium)
            Step(1, "Prop both phones up", "Face to face, 15–30 cm apart, screens steady.")
            Step(2, "Start sending", "The sender turns the file into a flickering QR stream.")
            Step(3, "Just watch", "Frames can be missed — the receiver fills the gaps on its own and saves to your gallery when it's done.")
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
