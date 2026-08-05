package com.hdfc.docupload.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hdfc.docupload.ui.login.LoginScreen
import com.hdfc.docupload.ui.search.SearchScreen
import com.hdfc.docupload.ui.upload.UploadScreen
import com.hdfc.docupload.ui.uploaded.UploadedDocumentsScreen

private const val ANIM_DURATION = 350

@Composable
fun AppNavHost(startDestination: String = Screen.Login.route) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeOut(tween(ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeOut(tween(ANIM_DURATION))
        }
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Search.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onSearchSuccess = { appNo ->
                    navController.navigate(Screen.Upload.createRoute(appNo))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Upload.route,
            arguments = listOf(navArgument(Screen.ARG_APP_NO) { type = NavType.StringType })
        ) {
            UploadScreen(
                onBack = { navController.popBackStack() },
                onViewUploaded = { appNo ->
                    navController.navigate(Screen.Uploaded.createRoute(appNo))
                }
            )
        }

        composable(
            route = Screen.Uploaded.route,
            arguments = listOf(navArgument(Screen.ARG_APP_NO) { type = NavType.StringType })
        ) {
            UploadedDocumentsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
