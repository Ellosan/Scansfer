package com.scansfer.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.scansfer.app.receive.ReceiveStage
import com.scansfer.app.receive.ReceiverState
import com.scansfer.app.ui.components.ChunkyProgress
import com.scansfer.app.ui.components.StatTile
import com.scansfer.app.ui.components.StatusPill
import com.scansfer.app.ui.theme.Teal
import com.scansfer.app.util.Format

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit, viewModel: ReceiveViewModel = viewModel()) {
    val camera = rememberPermissionState(android.Manifest.permission.CAMERA)
    val state by viewModel.engine.state.collectAsStateWithLifecycle()

    if (!camera.status.isGranted) {
        PermissionGate(onRequest = camera::launchPermissionRequest, onBack = onBack)
        return
    }

    when (state.stage) {
        ReceiveStage.DONE -> CompletedScreen(state = state, onAgain = viewModel::reset, onBack = onBack)
        ReceiveStage.FAILED -> FailureScreen(state = state, onRetry = viewModel::reset, onBack = onBack)
        else -> ScanningScreen(
            state = state,
            analyzer = viewModel.analyzer,
            torchEnabled = viewModel.torchEnabled,
            onToggleTorch = viewModel::toggleTorch,
            onBack = onBack,
        )
    }
}

@Composable
private fun ScanningScreen(
    state: ReceiverState,
    analyzer: androidx.camera.core.ImageAnalysis.Analyzer,
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            analyzer = analyzer,
            torchEnabled = torchEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        Reticle(
            locked = state.isLocked,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 96.dp)
                .padding(horizontal = 36.dp)
                .aspectRatio(1f),
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                StatusPill(
                    text = when {
                        state.stage == ReceiveStage.SAVING -> "Saving…"
                        state.isLocked -> "Receiving"
                        state.framesSeen > 0 -> "Looking for a sender"
                        else -> "Point at the other screen"
                    },
                    container = if (state.isLocked) Teal.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.55f),
                    content = if (state.isLocked) Color(0xFF00201B) else Color.White,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleTorch) {
                    Icon(
                        if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = "Torch",
                        tint = Color.White,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            ProgressPanel(state)
        }
    }
}

@Composable
private fun ProgressPanel(state: ReceiverState) {
    Surface(
        color = Color(0xFF101018).copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            val manifest = state.manifest
            if (manifest == null) {
                Text(
                    "Waiting for a video",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hold your phone 15–30 cm from the sender's screen so the code " +
                        "fills the frame. Missed frames are fine — Scansfer catches up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            manifest.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Format.bytes(manifest.fileSize.toLong()),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        Format.percent(state.progress),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Teal,
                    )
                }
                Spacer(Modifier.height(14.dp))
                ChunkyProgress(
                    progress = state.progress,
                    track = Color.White.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatTile(
                        "Blocks",
                        "${state.blocksSolved}/${state.blocksTotal}",
                        accent = Color.White,
                    )
                    StatTile("Speed", Format.rate(state.bytesPerSecond), accent = Color.White)
                    StatTile(
                        "Remaining",
                        if (state.etaSeconds >= 0) Format.duration(state.etaSeconds) else "—",
                        accent = Color.White,
                    )
                }
                if (state.stage == ReceiveStage.SAVING) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Teal,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Checking and saving to your gallery…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

/** Animated corner brackets that turn teal once a sender is locked on. */
@Composable
private fun Reticle(locked: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "reticle")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = if (locked) Teal else Color.White.copy(alpha = pulse)

    Canvas(modifier) {
        val corner = size.minDimension * 0.16f
        val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
        val inset = 0f

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * corner, y), stroke.width, stroke.cap)
            drawLine(color, Offset(x, y), Offset(x, y + dy * corner), stroke.width, stroke.cap)
        }

        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)

        if (locked) {
            drawRect(
                color = Teal.copy(alpha = 0.06f),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
            )
        }
    }
}

@Composable
private fun CompletedScreen(state: ReceiverState, onAgain: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val uri = state.savedUri

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Video received", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            state.manifest?.fileName ?: "Saved to your gallery",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Movies › Scansfer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = { uri?.let { openVideo(context, it) } },
            enabled = uri != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play video")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { uri?.let { shareVideo(context, it, state.manifest?.mimeType) } },
            enabled = uri != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
        Spacer(Modifier.height(20.dp))
        Row {
            TextButton(onClick = onAgain) { Text("Receive another") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onBack) { Text("Done") }
        }
    }
}

@Composable
private fun FailureScreen(state: ReceiverState, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("That didn't finish", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            state.error ?: "Something went wrong while saving.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(18.dp)) { Text("Try again") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Scansfer needs the camera",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The camera is how the video arrives — it reads the QR codes on the " +
                "other phone's screen. Nothing is recorded or uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Allow camera access", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Not now") }
    }
}

private fun openVideo(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareVideo(context: android.content.Context, uri: Uri, mimeType: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType?.ifBlank { null } ?: "video/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share video")) }
}
