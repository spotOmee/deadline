package com.deadline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deadline.data.local.DeadlineRepository
import com.deadline.service.AmbientSoundPlayer
import com.deadline.ui.components.animateDismiss
import com.deadline.service.PomodoroService
import com.deadline.ui.components.GlobalUndoSnackbarHost
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.habits.HabitsScreen
import com.deadline.ui.navigation.DeadlineBottomBar
import com.deadline.ui.navigation.DeadlineTab
import com.deadline.ui.navigation.NavigationZoningDefaults
import com.deadline.ui.navigation.globalNavigationZoneSwipe
import com.deadline.ui.navigation.resolveStartupTab
import com.deadline.ui.notes.NotesScreen
import com.deadline.ui.profile.ProfileScreen
import com.deadline.ui.projects.ProjectsScreen
import com.deadline.ui.stats.StatsScreen
import com.deadline.ui.theme.DeadlineTheme
import com.deadline.ui.theme.ThemeManager
import com.deadline.ui.timer.TimerScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: DeadlineRepository
    private val ambientPlayer = AmbientSoundPlayer()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = DeadlineRepository(this)
        val initialTheme = ThemeManager.readSavedThemePreference(this) ?: repository.getSavedThemeMode()
        ThemeManager.applyThemeMode(initialTheme)

        PomodoroService.init(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val userSettings by repository.userSettings.collectAsState()
            DeadlineTheme(themeMode = userSettings.themeMode) {
                val startupTab = remember {
                    resolveStartupTab(repository.getSavedStartupScreen())
                }
                val mainPagerState = rememberPagerState(initialPage = startupTab.ordinal) { DeadlineTab.entries.size }
                val coroutineScope = rememberCoroutineScope()
                var showProfileSheet by remember { mutableStateOf(false) }

                LaunchedEffect(userSettings.defaultStartupScreen) {
                    val targetTab = resolveStartupTab(userSettings.defaultStartupScreen)
                    if (mainPagerState.currentPage != targetTab.ordinal) {
                        mainPagerState.scrollToPage(targetTab.ordinal)
                    }
                }

                LaunchedEffect(Unit) {
                    repository.refreshAll()
                }

                val currentTab = DeadlineTab.entries[mainPagerState.currentPage]
                val undoEvent by repository.undoEvent.collectAsState()

                var activeScrollJob by remember { mutableStateOf<Job?>(null) }
                val scrollToTab: (DeadlineTab) -> Unit = { tab ->
                    if (mainPagerState.currentPage != tab.ordinal) {
                        HapticHelper.selection(this@MainActivity)
                        activeScrollJob?.cancel()
                        activeScrollJob = coroutineScope.launch {
                            mainPagerState.animateScrollToPage(
                                tab.ordinal,
                                animationSpec = NavigationZoningDefaults.ButterSmoothNavSpring
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .globalNavigationZoneSwipe(
                                pagerState = mainPagerState,
                                coroutineScope = coroutineScope,
                                onTabSettled = { _ ->
                                    HapticHelper.selection(this@MainActivity)
                                },
                                activeAnimationJob = { activeScrollJob },
                                onActiveAnimationJobChanged = { job -> activeScrollJob = job }
                            ),
                        snackbarHost = {
                            GlobalUndoSnackbarHost(
                                undoPayload = undoEvent,
                                onUndo = {
                                    coroutineScope.launch {
                                        repository.undoLastDeletion()
                                    }
                                },
                                onDismiss = {
                                    repository.dismissUndo()
                                },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        },
                        bottomBar = {
                            DeadlineBottomBar(
                                currentTab = currentTab,
                                pagerState = mainPagerState,
                                onTabSelected = scrollToTab
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            HorizontalPager(
                                state = mainPagerState,
                                beyondViewportPageCount = 1,
                                userScrollEnabled = false,
                                flingBehavior = PagerDefaults.flingBehavior(
                                    state = mainPagerState,
                                    snapAnimationSpec = NavigationZoningDefaults.ButterSmoothNavSpring
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val pageOffset = ((mainPagerState.currentPage - page) + mainPagerState.currentPageOffsetFraction).absoluteValue
                                            val fraction = (1f - pageOffset.coerceIn(0f, 1f))
                                            alpha = 0.35f + 0.65f * fraction
                                            scaleX = 0.98f + 0.02f * fraction
                                            scaleY = 0.98f + 0.02f * fraction
                                        }
                                ) {
                                    when (DeadlineTab.entries[page]) {
                                        DeadlineTab.STATS -> StatsScreen(repository = repository, onOpenProfile = { showProfileSheet = true })
                                        DeadlineTab.HABITS -> HabitsScreen(
                                            repository = repository,
                                            onOpenProfile = { showProfileSheet = true },
                                            onNavigateToTimer = { scrollToTab(DeadlineTab.TIMER) }
                                        )
                                        DeadlineTab.TIMER -> TimerScreen(repository = repository, ambientPlayer = ambientPlayer, onOpenProfile = { showProfileSheet = true })
                                        DeadlineTab.PROJECTS -> ProjectsScreen(repository = repository, onOpenProfile = { showProfileSheet = true })
                                        DeadlineTab.NOTES -> NotesScreen(repository = repository, onOpenProfile = { showProfileSheet = true })
                                    }
                                }
                            }
                        }
                    }

                    if (showProfileSheet) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = {
                                sheetState.animateDismiss(coroutineScope) {
                                    showProfileSheet = false
                                }
                            },
                            sheetState = sheetState,
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                        ) {
                            ProfileScreen(
                                repository = repository,
                                onClose = {
                                    sheetState.animateDismiss(coroutineScope) {
                                        showProfileSheet = false
                                    }
                                },
                                onSelectTab = { tab ->
                                    sheetState.animateDismiss(coroutineScope) {
                                        showProfileSheet = false
                                        scrollToTab(tab)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            com.deadline.service.AlarmRingtonePlayer.stop()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            ambientPlayer.stop()
            com.deadline.service.AlarmRingtonePlayer.stop()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
