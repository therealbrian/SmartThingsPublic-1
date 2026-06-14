package com.plexbooks.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plexbooks.ui.auth.LoginScreen
import com.plexbooks.ui.auth.ServerSelectScreen
import com.plexbooks.ui.detail.BookDetailScreen
import com.plexbooks.ui.home.HomeScreen
import com.plexbooks.ui.library.LibraryScreen
import com.plexbooks.ui.player.PlayerScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ServerSelect : Screen("server_select")
    object Home : Screen("home")
    object Library : Screen("library/{sectionId}/{sectionTitle}") {
        fun go(sectionId: String, title: String) = "library/$sectionId/${title.encode()}"
    }
    object BookDetail : Screen("detail/{ratingKey}") {
        fun go(ratingKey: String) = "detail/$ratingKey"
    }
    object Player : Screen("player/{ratingKey}/{title}") {
        fun go(ratingKey: String, title: String) = "player/$ratingKey/${title.encode()}"
    }
}

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ServerSelect.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ServerSelect.route) {
            ServerSelectScreen(
                onServerSelected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ServerSelect.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onLibraryClick = { id, title ->
                    navController.navigate(Screen.Library.go(id, title))
                },
                onBookClick = { ratingKey ->
                    navController.navigate(Screen.BookDetail.go(ratingKey))
                },
                onResumeClick = { ratingKey, title ->
                    navController.navigate(Screen.Player.go(ratingKey, title))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Library.route,
            arguments = listOf(
                navArgument("sectionId") { type = NavType.StringType },
                navArgument("sectionTitle") { type = NavType.StringType }
            )
        ) { backStack ->
            LibraryScreen(
                sectionId = backStack.arguments?.getString("sectionId") ?: "",
                sectionTitle = java.net.URLDecoder.decode(
                    backStack.arguments?.getString("sectionTitle") ?: "", "UTF-8"
                ),
                onBookClick = { ratingKey -> navController.navigate(Screen.BookDetail.go(ratingKey)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(navArgument("ratingKey") { type = NavType.StringType })
        ) { backStack ->
            BookDetailScreen(
                ratingKey = backStack.arguments?.getString("ratingKey") ?: "",
                onPlay = { ratingKey, title -> navController.navigate(Screen.Player.go(ratingKey, title)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("ratingKey") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStack ->
            PlayerScreen(
                ratingKey = backStack.arguments?.getString("ratingKey") ?: "",
                title = java.net.URLDecoder.decode(
                    backStack.arguments?.getString("title") ?: "", "UTF-8"
                ),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
