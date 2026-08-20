package com.scansfer.app.ui

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scansfer.app.core.TransferProfile
import com.scansfer.app.ui.components.ChunkyProgress
import com.scansfer.app.ui.components.SelectableRow
import com.scansfer.app.ui.components.StatTile
import com.scansfer.app.ui.theme.MonoNumber
import com.scansfer.app.util.Format
import com.scansfer.app.util.findActivity
import com.scansfer.app.util.VideoInfo
import com.scansfer.app.util.VideoSource

@Composable
fun SendScreen(onBack: () -> Unit, viewModel: SendViewModel = viewModel()) {
    val engine = viewModel.engine
    if (engine != null) {
        val state by engine.state.collectAsStateWithLifecycle()
        BeamScreen(
            state = state,
            profile = viewModel.profile,
            fileName = viewModel.video?.displayName.orEmpty(),
            onTogglePause = engine::togglePaused,
            onStop = viewModel::stop,
        )
        return
    }

    SendSetupScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
private fun SendSetupScreen(viewModel: SendViewModel, onBack: () -> Unit) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.choose(uri) }

    fun openPicker() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Send a video", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            val video = viewModel.video
            if (video == null) {
                EmptyPicker(
                    loading = viewModel.inspecting,
                    error = viewModel.pickError,
                    onPick = ::openPicker,
                )
            } else {
                VideoCard(video = video, onChange = ::openPicker, onClear = viewModel::clear)
                Spacer(Modifier.height(28.dp))

                Text("Transfer speed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Denser codes move more data per frame, but need a steadier camera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferProfile.entries.forEach { option ->
                        SelectableRow(
                            title = option.label,
                            subtitle = option.tagline,
                            trailing = "~${Format.duration(option.estimateSeconds(video.sizeBytes))}",
                            selected = viewModel.profile == option,
                            onClick = { viewModel.selectProfile(option) },
                        )
                    }
                }

                if (video.sizeBytes > VideoSource.COMFORTABLE_LIMIT_BYTES) {
                    Spacer(Modifier.height(16.dp))
                    LongTransferNotice(
                        estimate = viewModel.profile.estimateSeconds(video.sizeBytes),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        AnimatedVisibility(
            visible = viewModel.video != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = viewModel::start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start sending", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open Scansfer on the other phone and tap Receive first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyPicker(loading: Boolean, error: String?, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("Reading the video…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Icon(
                    Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text("Choose a video", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Short clips work best — a few megabytes goes over in minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onPick, shape = RoundedCornerShape(16.dp)) {
                    Text("Browse videos")
                }
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoInfo, onChange: () -> Unit, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = video.thumbnail
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (video.durationMs > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                    ) {
                        Text(
                            Format.clock(video.durationMs),
                            style = MaterialTheme.typography.labelMedium.merge(MonoNumber),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        video.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Format.bytes(video.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChange) { Text("Change") }
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove video")
                }
            }
        }
    }
}

@Composable
private fun LongTransferNotice(estimate: Long) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "This one is big. Expect around ${Format.duration(estimate)} of both phones " +
                    "sitting still. A shorter clip will feel much better.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The broadcast view. White background, biggest possible code, brightness pinned
 * to full — everything here exists to make the other phone's job easy.
 */
@Composable
private fun BeamScreen(
    state: com.scansfer.app.send.SenderState,
    profile: TransferProfile,
    fileName: String,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    KeepScreenBright()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, contentDescription = "Stop sending", tint = Color.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        maxLines = 1,
                    )
                    Text(
                        if (state.paused) "Paused" else "Sending • pass ${state.passNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.55f),
                    )
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (state.paused) "Resume" else "Pause",
                        tint = Color.Black,
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val frame = state.frame
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Transfer frame ${state.tick}",
                        // Nearest-neighbour keeps module edges crisp when scaled up.
                        filterQuality = FilterQuality.None,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White),
                    )
                } else if (state.error != null) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(32.dp),
                    )
                } else {
                    CircularProgressIndicator(color = Color.Black)
                }
            }

            Surface(
                color = Color(0xFF101018),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    ChunkyProgress(
                        progress = state.passProgress,
                        track = Color.White.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatTile("Frames", "${state.framesSent}", accent = Color.White)
                        StatTile("Rate", "${profile.fps}/s", accent = Color.White)
                        StatTile("Elapsed", Format.duration(state.elapsedMs / 1000), accent = Color.White)
                        StatTile("Pass", "${state.passNumber}", accent = Color.White)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Keep this going until the other phone says it's done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** Pins brightness to maximum and blocks the screen timeout while beaming. */
@Composable
private fun KeepScreenBright() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness
        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.attributes = it.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        }
        onDispose {
            window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply {
                    screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

