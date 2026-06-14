package com.plexbooks.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.plexbooks.ui.home.MediaCard
import com.plexbooks.ui.theme.PlexOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sectionId: String,
    sectionTitle: String,
    onBookClick: (ratingKey: String) -> Unit,
    onBack: () -> Unit,
    vm: LibraryViewModel = hiltViewModel()
) {
    LaunchedEffect(sectionId) { vm.load(sectionId) }

    val state by vm.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { lastIndex ->
            val total = gridState.layoutInfo.totalItemsCount
            if (total > 0 && lastIndex >= total - 10) vm.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("$sectionTitle${if (state.items.isNotEmpty()) " (${state.items.size}${if (state.hasMore) "+" else ""})" else ""}")
                },
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
                state.items.isEmpty() -> Text(
                    "No items found in this library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.items, key = { it.ratingKey }) { item ->
                        MediaCard(
                            item = item,
                            serverUri = state.serverUri,
                            serverToken = state.serverToken,
                            onClick = { onBookClick(item.ratingKey) }
                        )
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
