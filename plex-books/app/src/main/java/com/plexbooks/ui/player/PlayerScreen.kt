package com.plexbooks.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import coil.compose.AsyncImage
import com.plexbooks.ui.theme.PlexOrange
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    ratingKey: String,
    title: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(ratingKey) { vm.initPlayer(ratingKey) }
    val state by vm.state.collectAsState()
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSkipSettings by remember { mutableStateOf(false) }

    if (showSkipSettings) {
        val skipOptions = listOf(5, 10, 15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSkipSettings = false },
            title = { Text("Skip durations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Skip back", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        skipOptions.forEach { secs ->
                            FilterChip(
                                selected = state.skipBackSecs == secs,
                                onClick = { vm.setSkipBack(secs) },
                                label = { Text("${secs}s") }
                            )
                        }
                    }
                    Text("Skip forward", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        skipOptions.forEach { secs ->
                            FilterChip(
                                selected = state.skipForwardSecs == secs,
                                onClick = { vm.setSkipForward(secs) },
                                label = { Text("${secs}s") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkipSettings = false }) { Text("Done") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSkipSettings = true }) {
                        Icon(Icons.Default.Settings, "Skip settings")
                    }
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text("${state.speed}x", color = PlexOrange)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        vm.setSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.weight(0.5f))

            // Artwork
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (state.thumb != null) {
                    AsyncImage(
                        model = state.thumb,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = PlexOrange,
                        modifier = Modifier.align(Alignment.Center).size(80.dp)
                    )
                }
            }

            Spacer(Modifier.weight(0.5f))

            // Title, author, and current chapter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    state.title.ifBlank { title },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.author.isNotBlank()) {
                    Text(
                        state.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.currentChapterTitle.isNotBlank()) {
                    Text(
                        state.currentChapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlexOrange,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Seek bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.durationMs > 0) {
                    Slider(
                        value = state.positionMs.toFloat(),
                        onValueChange = { vm.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = PlexOrange, activeTrackColor = PlexOrange)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatTime(state.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatTime(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { vm.skipToPreviousChapter() },
                    enabled = state.chapterOffsets.isNotEmpty()
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous chapter", modifier = Modifier.size(32.dp),
                        tint = if (state.chapterOffsets.isNotEmpty()) LocalContentColor.current
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.skipBackward() }) {
                        Icon(Icons.Default.Replay, null, modifier = Modifier.size(32.dp))
                    }
                    Text("-${state.skipBackSecs}s", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                FloatingActionButton(
                    onClick = { vm.playPause() },
                    containerColor = PlexOrange,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.background, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.background
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.skipForward() }) {
                        Icon(Icons.Default.Forward30, null, modifier = Modifier.size(32.dp))
                    }
                    Text("+${state.skipForwardSecs}s", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(
                    onClick = { vm.skipToNextChapter() },
                    enabled = state.chapterOffsets.isNotEmpty()
                ) {
                    Icon(Icons.Default.SkipNext, "Next chapter", modifier = Modifier.size(32.dp),
                        tint = if (state.chapterOffsets.isNotEmpty()) LocalContentColor.current
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
