package com.plexbooks.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.displayAuthor
import com.plexbooks.ui.theme.PlexOrange
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    ratingKey: String,
    onPlay: (ratingKey: String, title: String) -> Unit,
    onBack: () -> Unit,
    vm: BookDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(ratingKey) { vm.load(ratingKey) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = PlexOrange
                )
                state.error != null -> Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                state.item != null -> {
                    val item = state.item!!
                    val thumbUrl = if (!item.thumb.isNullOrBlank())
                        "${state.serverUri}${item.thumb}?X-Plex-Token=${state.serverToken}" else null

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Hero section
                        item {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (thumbUrl != null) {
                                        AsyncImage(
                                            model = thumbUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = PlexOrange,
                                            modifier = Modifier.align(Alignment.Center).size(48.dp)
                                        )
                                    }
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(item.title, style = MaterialTheme.typography.titleLarge)
                                    item.displayAuthor()?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    item.duration?.let { ms ->
                                        Text(
                                            formatDuration(ms),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    // Progress indicator
                                    state.progress?.let { p ->
                                        LinearProgressIndicator(
                                            progress = { p.percentComplete },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = PlexOrange
                                        )
                                        Text(
                                            "${(p.percentComplete * 100).toInt()}% complete",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Play button
                        item {
                            Button(
                                onClick = { onPlay(item.ratingKey, item.title) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.progress != null) "Resume" else "Play")
                            }
                        }

                        // Summary
                        if (!item.summary.isNullOrBlank()) {
                            item {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    ExpandableSummary(item.summary)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }

                        // Tracks
                        if (state.tracks.isNotEmpty()) {
                            item {
                                Text(
                                    "Tracks (${state.tracks.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(state.tracks, key = { it.ratingKey }) { track ->
                                TrackRow(
                                    track = track,
                                    onClick = { onPlay(track.ratingKey, track.title) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSummary(summary: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded }
    )
    if (!expanded && summary.length > 200) {
        Text(
            "Show more",
            style = MaterialTheme.typography.labelSmall,
            color = PlexOrange,
            modifier = Modifier.clickable { expanded = true }
        )
    }
}

@Composable
private fun TrackRow(track: PlexMediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        track.index?.let {
            Text(
                "$it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            track.duration?.let { ms ->
                Text(formatDuration(ms), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
