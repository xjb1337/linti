package com.xjb.linti.ui

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.xjb.linti.ui.components.UpdateDialog
import com.xjb.linti.ui.components.LocalAiExplanationSettings
import com.xjb.linti.ui.screens.AiQuestionScreen
import com.xjb.linti.ui.screens.AnswerSheetScreen
import com.xjb.linti.ui.screens.ChapterScreen
import com.xjb.linti.ui.screens.PastPapersScreen
import com.xjb.linti.ui.screens.BankHomeScreen
import com.xjb.linti.ui.screens.BanksScreen
import com.xjb.linti.ui.screens.ExamHistoryDetailScreen
import com.xjb.linti.ui.screens.ExamHistoryScreen
import com.xjb.linti.ui.screens.ExamPaperReviewScreen
import com.xjb.linti.ui.screens.ExamSetupScreen
import com.xjb.linti.ui.screens.FavoritesScreen
import com.xjb.linti.ui.screens.FlashcardsScreen
import com.xjb.linti.ui.screens.HomeScreen
import com.xjb.linti.ui.screens.ImageOcrScreen
import com.xjb.linti.ui.screens.ImportReviewScreen
import com.xjb.linti.ui.screens.NotesScreen
import com.xjb.linti.ui.screens.PdfOcrScreen
import com.xjb.linti.ui.screens.PracticeHubScreen
import com.xjb.linti.ui.screens.QuestionEditScreen
import com.xjb.linti.ui.screens.QuizScreen
import com.xjb.linti.ui.screens.ReciteScreen
import com.xjb.linti.ui.screens.ResultScreen
import com.xjb.linti.ui.screens.SearchScreen
import com.xjb.linti.ui.screens.SettingsScreen
import com.xjb.linti.ui.screens.SplitImportScreen
import com.xjb.linti.ui.screens.TopicPracticeSetupScreen
import com.xjb.linti.ui.screens.WrongBookScreen

@Composable
fun LintiRoot(viewModel: LintiViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingExamNavigation by viewModel.pendingExamNavigation.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    LaunchedEffect(pendingExamNavigation, currentRoute) {
        val token = pendingExamNavigation ?: return@LaunchedEffect
        if (navController.currentDestination?.route in setOf(Routes.Quiz, Routes.AnswerSheet) &&
            viewModel.consumeExamNavigation(token)
        ) {
            navController.navigate(Routes.Result) {
                popUpTo(Routes.Quiz) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LocalLintiPalette.current.background,
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
        CompositionLocalProvider(LocalAiExplanationSettings provides { navController.navigate(Routes.Settings) { launchSingleTop = true } }) {
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
                    onSplitImport = { navController.navigate(Routes.SplitImport) },
                    onImageOcr = { navController.navigate(Routes.ImageOcr) },
                    onPdfOcr = { navController.navigate(Routes.PdfOcr) },
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
                    onOpenHome = { navController.navigateTopLevel(Routes.Home) },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                    onExamSetup = { navController.navigate(Routes.examSetup(it)) },
                    onTopicPractice = { navController.navigate(Routes.topicSetup(null)) },
                    onExamHistory = { navController.navigate(Routes.ExamHistory) },
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
            composable(Routes.SplitImport) {
                SplitImportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onMatched = {
                        navController.navigate(Routes.Review) {
                            popUpTo(Routes.SplitImport) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ImageOcr) {
                ImageOcrScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRecognized = {
                        navController.navigate(Routes.Review) {
                            popUpTo(Routes.ImageOcr) { inclusive = true }
                        }
                    },
                    onOpenSettings = { navController.navigateTopLevel(Routes.Settings) },
                )
            }
            composable(Routes.PdfOcr) {
                PdfOcrScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRecognized = {
                        navController.navigate(Routes.Review) {
                            popUpTo(Routes.PdfOcr) { inclusive = true }
                        }
                    },
                    onOpenSettings = { navController.navigateTopLevel(Routes.Settings) },
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
                    onOpenHome = { navController.navigateTopLevel(Routes.Home) },
                    onQuiz = { navController.navigate(Routes.Quiz) },
                    onExamSetup = { navController.navigate(Routes.examSetup(bankId)) },
                    onTopicPractice = { navController.navigate(Routes.topicSetup(bankId)) },
                    onChapters = { navController.navigate(Routes.chapters(bankId)) },
                    onPastPapers = { navController.navigate(Routes.pastPapers(bankId)) },
                    onAiQuestions = { navController.navigate(Routes.aiQuestions(bankId)) },
                    onWrongBook = { navController.navigate(Routes.wrongBook(bankId)) },
                    onFavorites = { navController.navigate(Routes.favorites(bankId)) },
                    onNotes = { navController.navigate(Routes.notes(bankId)) },
                    onSearch = { navController.navigate(Routes.search(bankId)) },
                    onFlashcards = { navController.navigate(Routes.flashcards(bankId)) },
                    onRecite = { navController.navigate(Routes.recite(bankId)) },
                )
            }
            composable(route = Routes.AiQuestionsPattern, arguments = listOf(navArgument("bankId") { type = NavType.LongType })) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: return@composable
                AiQuestionScreen(viewModel, bankId, onBack = { navController.popBackStack() }, onSettings = { navController.navigate(Routes.Settings) })
            }
            composable(route = Routes.PastPapersPattern, arguments = listOf(navArgument("bankId") { type = NavType.LongType })) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: return@composable
                PastPapersScreen(viewModel, bankId, onBack = { navController.popBackStack() }, onQuiz = { navController.navigate(Routes.Quiz) })
            }
            composable(route = Routes.ChaptersPattern, arguments = listOf(navArgument("bankId") { type = NavType.LongType })) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: return@composable
                ChapterScreen(viewModel, bankId, onBack = { navController.popBackStack() })
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
                route = Routes.TopicSetupPattern,
                arguments = listOf(navArgument("bankId") { type = NavType.LongType }),
            ) { entry ->
                val bankId = entry.arguments?.getLong("bankId") ?: -1L
                TopicPracticeSetupScreen(
                    viewModel = viewModel,
                    initialBankId = bankId.takeIf { it > 0 },
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
                    onWrongBook = { bankId ->
                        navController.navigate(Routes.wrongBook(bankId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                    onEditQuestion = { questionId ->
                        navController.navigate(Routes.questionEdit(questionId))
                    },
                )
            }
            composable(Routes.AnswerSheet) {
                AnswerSheetScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
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
                    onReviewPaper = { sessionId, wrongOnly ->
                        navController.navigate(Routes.examPaperReview(sessionId, wrongOnly))
                    },
                    onRetryExam = { bankId ->
                        navController.navigate(Routes.examSetup(bankId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
            composable(Routes.ExamHistory) {
                ExamHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { sessionId ->
                        navController.navigate(Routes.examHistoryDetail(sessionId))
                    },
                )
            }
            composable(
                route = Routes.ExamHistoryDetailPattern,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { entry ->
                ExamHistoryDetailScreen(
                    viewModel = viewModel,
                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onReviewPaper = { sessionId, wrongOnly ->
                        navController.navigate(Routes.examPaperReview(sessionId, wrongOnly))
                    },
                )
            }
            composable(
                route = Routes.ExamPaperReviewPattern,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType },
                    navArgument("filter") {
                        type = NavType.StringType
                        defaultValue = "all"
                    },
                ),
            ) { entry ->
                ExamPaperReviewScreen(
                    viewModel = viewModel,
                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                    initialWrongOnly = entry.arguments?.getString("filter") == "wrong",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.QuestionEditPattern,
                arguments = listOf(navArgument("questionId") { type = NavType.LongType }),
            ) { entry ->
                QuestionEditScreen(
                    viewModel = viewModel,
                    questionId = entry.arguments?.getLong("questionId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        }
    }
    UpdateDialog(viewModel)
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
