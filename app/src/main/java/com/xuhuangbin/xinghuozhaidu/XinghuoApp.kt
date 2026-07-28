package com.xuhuangbin.xinghuozhaidu

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.ui.MainViewModel
import com.xuhuangbin.xinghuozhaidu.ui.NoteOperationUiState
import com.xuhuangbin.xinghuozhaidu.ui.detail.CardDetailScreen
import com.xuhuangbin.xinghuozhaidu.ui.notes.NoteEditorScreen
import com.xuhuangbin.xinghuozhaidu.ui.notes.NotesScreen
import com.xuhuangbin.xinghuozhaidu.ui.reader.ReaderScreen
import com.xuhuangbin.xinghuozhaidu.ui.saved.MineScreen
import com.xuhuangbin.xinghuozhaidu.ui.saved.SavedScreen
import com.xuhuangbin.xinghuozhaidu.ui.search.SearchScreen
import com.xuhuangbin.xinghuozhaidu.ui.update.UpdateDialog
import com.xuhuangbin.xinghuozhaidu.ui.theme.Canvas
import com.xuhuangbin.xinghuozhaidu.ui.theme.Ink
import com.xuhuangbin.xinghuozhaidu.ui.theme.MutedInk
import com.xuhuangbin.xinghuozhaidu.ui.theme.SoftRed
import com.xuhuangbin.xinghuozhaidu.ui.theme.SpiritRed

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topDestinations = listOf(
    TopDestination("reader", "阅读", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TopDestination("saved", "收藏", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    TopDestination("notes", "笔记", Icons.Filled.EditNote, Icons.Outlined.EditNote),
    TopDestination("mine", "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun XinghuoApp() {
    val application = LocalContext.current.applicationContext as XinghuoApplication
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application.container.repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val noteOperationState by viewModel.noteOperationState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route.orEmpty()
    val showBottomBar = topDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) BottomNavigation(navController, currentRoute)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "reader",
            modifier = Modifier.padding(padding),
        ) {
            composable("reader") {
                ReaderScreen(
                    state = uiState.reader,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onRetry = viewModel::initialize,
                    onSearch = { navController.navigate("search") },
                    onPositionChanged = viewModel::updatePosition,
                    onRead = viewModel::markRead,
                    onLike = viewModel::toggleLike,
                    onFavorite = viewModel::toggleFavorite,
                    onNote = { cardId -> navController.navigate("note/new/card/$cardId") },
                    onNewRound = viewModel::startNewRound,
                )
            }
            composable("saved") {
                SavedScreen(
                    favorites = uiState.favorites,
                    liked = uiState.liked,
                    onCardClick = { card -> navController.openDetail(card) },
                )
            }
            composable("notes") {
                NotesScreen(
                    notes = uiState.notes,
                    cards = uiState.allCards,
                    onAddNote = { navController.navigate("note/new") },
                    onNoteClick = { note -> navController.navigate("note/edit/${note.id}") },
                    onCardClick = { card -> navController.openDetail(card) },
                )
            }
            composable("mine") {
                MineScreen(
                    contentState = uiState.contentState,
                    onCheckUpdate = viewModel::checkForUpdate,
                    updateEnabled = BuildConfig.CONTENT_MANIFEST_URL.isNotBlank(),
                )
            }
            composable("search") {
                SearchScreen(
                    query = query,
                    results = results,
                    history = searchHistory,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearchSubmit = viewModel::submitSearchQuery,
                    onHistoryDelete = viewModel::deleteSearchHistory,
                    onHistoryClear = viewModel::clearSearchHistory,
                    onBack = navController::popBackStack,
                    onCardClick = { card -> navController.openDetail(card) },
                )
            }
            composable("detail/{cardId}") { entry ->
                val cardId = entry.arguments?.getString("cardId")
                val card = uiState.allCards.firstOrNull { it.id == cardId }
                if (card != null) {
                    CardDetailScreen(
                        card = card,
                        onBack = navController::popBackStack,
                        onLike = { viewModel.toggleLike(card.id) },
                        onFavorite = { viewModel.toggleFavorite(card.id) },
                        onNote = { navController.navigate("note/new/card/${card.id}") },
                    )
                } else {
                    LaunchedEffect(cardId) { navController.popBackStack() }
                }
            }
            composable("note/new") {
                NoteEditorDestination(
                    note = null,
                    linkedCard = null,
                    linkedCardId = null,
                    operationState = noteOperationState,
                    viewModel = viewModel,
                    navController = navController,
                )
            }
            composable("note/new/card/{cardId}") { entry ->
                val cardId = entry.arguments?.getString("cardId")
                val card = uiState.allCards.firstOrNull { it.id == cardId }
                if (card != null) {
                    NoteEditorDestination(
                        note = null,
                        linkedCard = card,
                        linkedCardId = card.id,
                        operationState = noteOperationState,
                        viewModel = viewModel,
                        navController = navController,
                    )
                } else {
                    LaunchedEffect(cardId) { navController.popBackStack() }
                }
            }
            composable(
                route = "note/edit/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId")
                val note = uiState.notes.firstOrNull { it.id == noteId }
                if (note != null) {
                    NoteEditorDestination(
                        note = note,
                        linkedCard = note.cardId?.let { cardId ->
                            uiState.allCards.firstOrNull { it.id == cardId }
                        },
                        linkedCardId = note.cardId,
                        operationState = noteOperationState,
                        viewModel = viewModel,
                        navController = navController,
                    )
                } else {
                    LaunchedEffect(noteId) { navController.popBackStack() }
                }
            }
        }
    }
    UpdateDialog(
        state = updateState,
        onConfirm = viewModel::confirmUpdate,
        onDismiss = viewModel::dismissUpdate,
    )
}

@Composable
private fun NoteEditorDestination(
    note: PersonalNote?,
    linkedCard: QuoteCard?,
    linkedCardId: String?,
    operationState: NoteOperationUiState,
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    LaunchedEffect(note?.id, linkedCardId) { viewModel.clearNoteOperationState() }
    NoteEditorScreen(
        note = note,
        linkedCard = linkedCard,
        linkedCardId = linkedCardId,
        operationInProgress = operationState.inProgress,
        operationError = operationState.errorMessage,
        onBack = navController::popBackStack,
        onClearError = viewModel::clearNoteOperationState,
        onSave = { title, body ->
            viewModel.saveNote(
                noteId = note?.id,
                cardId = linkedCardId,
                title = title,
                body = body,
                onSaved = navController::popBackStack,
            )
        },
        onDelete = note?.let { existing ->
            { viewModel.deleteNote(existing.id, navController::popBackStack) }
        },
    )
}

@Composable
private fun BottomNavigation(navController: NavHostController, currentRoute: String) {
    NavigationBar(containerColor = Canvas, tonalElevation = 0.dp) {
        topDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SpiritRed,
                    selectedTextColor = Ink,
                    indicatorColor = SoftRed,
                    unselectedIconColor = MutedInk,
                    unselectedTextColor = MutedInk,
                ),
            )
        }
    }
}

private fun NavHostController.openDetail(card: QuoteCard) {
    navigate("detail/${card.id}")
}
