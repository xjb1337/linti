package com.xjb.linti.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xjb.linti.ui.nav.LintiBottomBar
import com.xjb.linti.ui.nav.Routes
import com.xjb.linti.ui.screens.AnswerSheetScreen
import com.xjb.linti.ui.screens.BankHomeScreen
import com.xjb.linti.ui.screens.BanksScreen
import com.xjb.linti.ui.screens.ExamSetupScreen
import com.xjb.linti.ui.screens.FavoritesScreen
import com.xjb.linti.ui.screens.FlashcardsScreen
import com.xjb.linti.ui.screens.HomeScreen
import com.xjb.linti.ui.screens.ImportReviewScreen
import com.xjb.linti.ui.screens.NotesScreen
import com.xjb.linti.ui.screens.PracticeHubScreen
import com.xjb.linti.ui.screens.QuizScreen
import com.xjb.linti.ui.screens.ReciteScreen
import com.xjb.linti.ui.screens.ResultScreen
import com.xjb.linti.ui.screens.SearchScreen
import com.xjb.linti.ui.screens.SettingsScreen
import com.xjb.linti.ui.screens.WrongBookScreen
import com.xjb.linti.ui.theme.Background

@Composable
fun LintiRoot(viewModel: LintiViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                LintiBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { navController.navigateTopLevel(it) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    viewModel = viewModel,
                    onReview = { navController.navigate(Routes.Review) },
                    onOpenBank = { navController.navigate(Routes.bank(it)) },
                )
            }
            composable(Routes.Banks) {
                BanksScreen(
                    viewModel = viewModel,
                    onOpenBank = { navController.navigate(Routes.bank(it)) },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(Routes.Practice) {
                PracticeHubScreen(
                    viewModel = viewModel,
                    onOpenBank = { navController.navigate(Routes.bank(it)) },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                    onExamSetup = { navController.navigate(Routes.examSetup(it)) },
                    onWrongBook = { navController.navigate(Routes.wrongBook(null)) },
                    onFavorites = { navController.navigate(Routes.favorites(null)) },
                    onFlashcards = { navController.navigate(Routes.flashcards(null)) },
                    onRecite = { navController.navigate(Routes.recite(null)) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(viewModel)
            }
            composable(Routes.Review) {
                ImportReviewScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onImported = { bankId ->
                        navController.navigate(Routes.bank(bankId)) {
                            popUpTo(Routes.Review) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.BankPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: return@composable
                BankHomeScreen(
                    viewModel = viewModel,
                    bankId = bankId,
                    onBack = { navController.popBackStack() },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                    onExamSetup = { navController.navigate(Routes.examSetup(bankId)) },
                    onWrongBook = { navController.navigate(Routes.wrongBook(bankId)) },
                    onFavorites = { navController.navigate(Routes.favorites(bankId)) },
                    onNotes = { navController.navigate(Routes.notes(bankId)) },
                    onSearch = { navController.navigate(Routes.search(bankId)) },
                    onFlashcards = { navController.navigate(Routes.flashcards(bankId)) },
                    onRecite = { navController.navigate(Routes.recite(bankId)) },
                )
            }
            composable(
                route = Routes.WrongBookPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                WrongBookScreen(
                    viewModel = viewModel,
                    initialBankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.navigateTopLevel(Routes.Practice) },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(
                route = Routes.ExamSetupPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: return@composable
                ExamSetupScreen(
                    viewModel = viewModel,
                    bankId = bankId,
                    onBack = { navController.popBackStack() },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(
                route = Routes.FavoritesPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                FavoritesScreen(
                    viewModel = viewModel,
                    bankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(
                route = Routes.NotesPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                NotesScreen(
                    viewModel = viewModel,
                    bankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(
                route = Routes.SearchPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                SearchScreen(
                    viewModel = viewModel,
                    bankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                )
            }
            composable(
                route = Routes.FlashcardsPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                FlashcardsScreen(
                    viewModel = viewModel,
                    bankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.RecitePattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                ReciteScreen(
                    viewModel = viewModel,
                    bankId = entry.arguments?.getLong("bankId")?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Quiz) {
                QuizScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onAnswerSheet = { navController.navigate(Routes.AnswerSheet) },
                    onFinished = {
                        navController.navigate(Routes.Result) {
                            popUpTo(Routes.Quiz) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.AnswerSheet) {
                AnswerSheetScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onFinished = {
                        navController.navigate(Routes.Result) {
                            popUpTo(Routes.Quiz) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Result) {
                ResultScreen(
                    viewModel = viewModel,
                    onClose = {
                        viewModel.clearLastResult()
                        navController.popBackStack()
                    },
                    onOpenBank = { bankId ->
                        navController.navigate(Routes.bank(bankId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                    onWrongBook = { bankId ->
                        navController.navigate(Routes.wrongBook(bankId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                    onRetryExam = { bankId ->
                        navController.navigate(Routes.examSetup(bankId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
