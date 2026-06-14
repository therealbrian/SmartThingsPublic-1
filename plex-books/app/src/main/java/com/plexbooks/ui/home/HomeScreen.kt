package com.plexbooks.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.plexbooks.data.api.model.PlexLibrarySection
import com.plexbooks.data.api.model.PlexMediaItem
import com.plexbooks.data.api.model.displayAuthor
import com.plexbooks.ui.theme.PlexOrange

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onLibraryClick: (id: String, title: String) -> Unit,
    onBookClick: (ratingKey: String) -> Unit,
    onResumeClick: (ratingKey: String, title: String) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val libraryItems by vm.libraryItems.collectAsState()
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
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search books…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { vm.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Search results
            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No results for \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(searchResults, key = { it.ratingKey }) { item ->
                            MediaCard(
                                item = item,
                                serverUri = state.serverUri,
                                serverToken = state.serverToken,
                                onClick = { onBookClick(item.ratingKey) }
                            )
                        }
                    }
                }
                return@Scaffold
            }

            // Normal home content
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = PlexOrange)
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
                            // My Library
                            if (libraryItems.isNotEmpty()) {
                                item { SectionHeader("My Library") }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(libraryItems, key = { it.ratingKey }) { entry ->
                                            LibraryCard(
                                                entry = entry,
                                                onClick = { onBookClick(entry.ratingKey) },
                                                onDeleteDownload = if (entry.isDownloaded) {
                                                    { vm.deleteDownload(entry.ratingKey) }
                                                } else null,
                                                onRemoveFromLibrary = { vm.removeFromLibrary(entry.ratingKey) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Continue listening (Plex on-deck)
                            if (state.onDeck.isNotEmpty()) {
                                item { SectionHeader("Continue Listening") }
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
                                                onClick = { onResumeClick(item.ratingKey, item.title) }
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

                            // All Books (paginated)
                            if (state.allBooks.isNotEmpty()) {
                                item {
                                    SectionHeader("All Books (${state.allBooks.size}${if (state.hasMoreBooks) "+" else ""})")
                                }
                                // Sort chips
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = state.allBooksSort == "titleSort",
                                                onClick = { vm.setSortOrder("titleSort") },
                                                label = { Text("A – Z") }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = state.allBooksSort == "addedAt:desc",
                                                onClick = { vm.setSortOrder("addedAt:desc") },
                                                label = { Text("Recently Added") }
                                            )
                                        }
                                    }
                                }
                                item {
                                    val allBooksListState = rememberLazyListState()
                                    LaunchedEffect(allBooksListState) {
                                        snapshotFlow {
                                            allBooksListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        }.collect { lastIndex ->
                                            val total = allBooksListState.layoutInfo.totalItemsCount
                                            if (total > 0 && lastIndex >= total - 5) vm.loadMoreBooks()
                                        }
                                    }
                                    LazyRow(
                                        state = allBooksListState,
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.allBooks, key = { it.ratingKey }) { item ->
                                            MediaCard(
                                                item = item,
                                                serverUri = state.serverUri,
                                                serverToken = state.serverToken,
                                                onClick = { onBookClick(item.ratingKey) }
                                            )
                                        }
                                        if (state.isLoadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier.size(120.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(28.dp),
                                                        strokeWidth = 2.dp,
                                                        color = PlexOrange
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onDeleteDownload: (() -> Unit)?,
    onRemoveFromLibrary: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove download?") },
            text = { Text("\"${entry.title}\" will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDownload?.invoke()
                    showDeleteConfirm = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove from Library?") },
            text = { Text("This will remove \"${entry.title}\" from My Library and delete any download.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFromLibrary()
                    showRemoveDialog = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showRemoveDialog = true }
            )
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (entry.thumbUrl != null) {
                AsyncImage(
                    model = entry.thumbUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = PlexOrange,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }

            if (entry.percentComplete > 0f) {
                LinearProgressIndicator(
                    progress = { entry.percentComplete },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    color = PlexOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Completed badge — green checkmark in top-start corner
            if (entry.isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.88f))
                        .padding(2.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Downloaded badge — tap to remove download
            if (entry.isDownloaded && onDeleteDownload != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .clickable { showDeleteConfirm = true }
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded — tap to remove",
                        tint = PlexOrange,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (entry.percentComplete > 0f) {
            Text(
                "${(entry.percentComplete * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
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
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.displayAuthor()?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
