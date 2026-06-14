package com.plexbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.ui.nav.AppNavigation
import com.plexbooks.ui.nav.Screen
import com.plexbooks.ui.theme.PlexBooksTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PlexPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Show previous crash if any
        val crashPrefs = getSharedPreferences("crash", 0)
        val lastCrash = crashPrefs.getString("last", null)
        if (lastCrash != null) {
            crashPrefs.edit().remove("last").apply()
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "CRASH:\n\n$lastCrash",
                            fontSize = 10.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
            return
        }

        val startDest = MutableStateFlow<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDest.value == null }

        lifecycleScope.launch {
            val token = prefs.authToken.first()
            val serverUri = prefs.serverUri.first()
            startDest.value = when {
                token.isNullOrBlank() -> Screen.Login.route
                serverUri.isNullOrBlank() -> Screen.ServerSelect.route
                else -> Screen.Home.route
            }
        }

        setContent {
            val dest by startDest.collectAsState()
            PlexBooksTheme {
                dest?.let { AppNavigation(startDestination = it) }
            }
        }
    }
}
