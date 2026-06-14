package com.plexbooks.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

import coil.compose.AsyncImage
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.displayAuthor
import com.plexbooks.ui.theme.PlexOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLibraryClick: (id: String, title: String) -> Unit,
    onBookClick: (ratingKey: String) -> Unit,
    onResumeClick: (ratingKey: String, title: String) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val progress by vm.progressItems.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to access your library.") },
            confirmButton = {
                TextButton(onClick = { vm.logout(onLogout) }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PlexBooks", style = MaterialTheme.typography.titleLarge)
                        if (state.serverName.isNotBlank()) {
                            Text(
                                state.serverName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, "Sign out")
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
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PlexOrange
                    )
                }
                state.error != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Continue listening
                        if (progress.isNotEmpty()) {
                            item {
                                SectionHeader("Continue Listening")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(progress.take(10)) { p ->
                                        ProgressCard(
                                            title = p.title,
                                            thumb = p.thumb,
                                            percent = p.percentComplete,
                                            onClick = { onResumeClick(p.ratingKey, p.title) }
                                        )
                                    }
                                }
                            }
                        }

                        // Libraries
                        if (state.libraries.isNotEmpty()) {
                            item { SectionHeader("Libraries") }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.libraries) { lib ->
                                        LibraryChip(lib) { onLibraryClick(lib.key, lib.title) }
                                    }
                                }
                            }
                        }

                        // On deck
                        if (state.onDeck.isNotEmpty()) {
                            item { SectionHeader("On Deck") }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.onDeck) { item ->
                                        MediaCard(
                                            item = item,
                                            serverUri = state.serverUri,
                                            serverToken = state.serverToken,
                                            onClick = { onBookClick(item.ratingKey) }
                                        )
                                    }
                                }
                            }
                        }

                        // Recently added
                        if (state.recentlyAdded.isNotEmpty()) {
                            item { SectionHeader("Recently Added") }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.recentlyAdded) { item ->
                                        MediaCard(
                                            item = item,
                                            serverUri = state.serverUri,
                                            serverToken = state.serverToken,
                                            onClick = { onBookClick(item.ratingKey) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun MediaCard(
    item: PlexMediaItem,
    serverUri: String,
    serverToken: String,
    onClick: () -> Unit
) {
    val thumbUrl = if (!item.thumb.isNullOrBlank())
        "$serverUri${item.thumb}?X-Plex-Token=$serverToken" else null

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        item.displayAuthor()?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryChip(section: PlexLibrarySection, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(section.title) },
        leadingIcon = {
            Icon(
                when (section.type) {
                    "music" -> Icons.Default.Headphones
                    "book" -> Icons.Default.MenuBook
                    else -> Icons.Default.LibraryBooks
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun ProgressCard(title: String, thumb: String?, percent: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.Headphones,
                    contentDescription = null,
                    tint = PlexOrange,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = PlexOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
