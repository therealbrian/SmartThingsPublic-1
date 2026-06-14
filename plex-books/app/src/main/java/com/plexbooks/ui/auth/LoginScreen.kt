package com.plexbooks.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import com.plexbooks.data.api.model.PlexResource
import com.plexbooks.ui.theme.PlexOrange

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoginSuccess()
        if (state is LoginUiState.WaitingForBrowser) {
            val url = (state as LoginUiState.WaitingForBrowser).authUrl
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = PlexOrange,
                modifier = Modifier.size(80.dp)
            )

            Text(
                text = "PlexBooks",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Your personal audiobook & book library",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is LoginUiState.Idle, is LoginUiState.Success -> {
                    Button(
                        onClick = { vm.startLogin() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sign in with Plex", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                is LoginUiState.Loading -> {
                    CircularProgressIndicator(color = PlexOrange)
                    Text("Connecting to Plex…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is LoginUiState.WaitingForBrowser -> {
                    CircularProgressIndicator(color = PlexOrange)
                    Text(
                        "Waiting for Plex sign-in in your browser…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { vm.reset() }) { Text("Cancel") }
                }
                is LoginUiState.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { vm.startLogin() }, shape = RoundedCornerShape(12.dp)) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectScreen(
    onServerSelected: () -> Unit,
    vm: ServerSelectViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Your Server") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is ServerSelectState.Loading -> CircularProgressIndicator(color = PlexOrange)
                is ServerSelectState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                is ServerSelectState.Servers -> {
                    if (s.list.isEmpty()) {
                        Text(
                            "No Plex servers found.\nMake sure your server is online.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (s.list.size == 1) {
                        LaunchedEffect(Unit) { vm.selectServer(s.list.first(), onServerSelected) }
                        CircularProgressIndicator(color = PlexOrange)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.list) { server ->
                                ServerCard(server) { vm.selectServer(server, onServerSelected) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(server: PlexResource, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = PlexOrange)
            Column {
                Text(server.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${server.connections.size} connection(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
