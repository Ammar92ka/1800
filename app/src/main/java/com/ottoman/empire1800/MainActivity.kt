package com.ottoman.empire1800

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottoman.empire1800.ui.components.AiSettingsSheet
import com.ottoman.empire1800.ui.components.MilestonesDialog
import com.ottoman.empire1800.ui.components.ReportDialog
import com.ottoman.empire1800.ui.screens.ChartsScreen
import com.ottoman.empire1800.ui.screens.CourtScreen
import com.ottoman.empire1800.ui.screens.EndScreen
import com.ottoman.empire1800.ui.screens.GameScreen
import com.ottoman.empire1800.ui.screens.StartScreen
import com.ottoman.empire1800.ui.state.AppScreen
import com.ottoman.empire1800.ui.state.GameViewModel
import com.ottoman.empire1800.ui.theme.OttomanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OttomanTheme {
                val vm: GameViewModel = viewModel()
                val ui by vm.ui.collectAsState()
                val activeGame = ui.activeGame

                when (ui.screen) {
                    AppScreen.START -> {
                        StartScreen(
                            loadingSaved = ui.loadingSaved,
                            savedGame = ui.savedGame,
                            snackbarMessage = ui.snackbarMessage,
                            onConsumeSnackbar = vm::consumeSnackbar,
                            onContinueSaved = vm::continueSaved,
                            onStartNewGame = vm::startNewGame,
                            onOpenSettings = vm::openSettings,
                            onExportSave = vm::exportSavedJson,
                            onImportSave = vm::importSavedJson,
                            onNotify = vm::notify,
                        )
                    }
                    AppScreen.GAME -> {
                        BackHandler { vm.backToStart() }
                        if (activeGame != null) {
                            GameScreen(
                                state = activeGame,
                                revision = ui.revision,
                                loadingPlan = ui.loadingPlan,
                                resolving = ui.resolving,
                                attempt = ui.attempt,
                                problem = ui.problem,
                                error = ui.error,
                                onRetryPlan = vm::ensurePlan,
                                onPickOption = vm::pickOption,
                                onSetRepay = vm::setRepay,
                                onResolve = vm::resolveRound,
                                onOpenCourt = vm::openCourt,
                                onOpenLastReport = vm::reopenLastReport,
                                onOpenMilestones = vm::openMilestones,
                                onOpenCharts = vm::openCharts,
                                onOpenSettings = vm::openSettings,
                            )
                        }
                    }
                    AppScreen.COURT -> {
                        BackHandler { vm.backToGame() }
                        if (activeGame != null) {
                            CourtScreen(
                                state = activeGame,
                                revision = ui.revision,
                                initialTab = ui.courtInitialTab,
                                snackbarMessage = ui.snackbarMessage,
                                onConsumeSnackbar = vm::consumeSnackbar,
                                onRunAction = vm::runCourtAction,
                                onBack = vm::backToGame,
                            )
                        }
                    }
                    AppScreen.CHARTS -> {
                        BackHandler { vm.backToGame() }
                        if (activeGame != null) {
                            ChartsScreen(
                                state = activeGame,
                                onBack = vm::backToGame,
                            )
                        }
                    }
                    AppScreen.END -> {
                        BackHandler { vm.backToStart() }
                        val ending = ui.activeEnding
                        if (activeGame != null && ending != null) {
                            EndScreen(
                                ending = ending,
                                state = activeGame,
                                onNewGame = vm::restartFromEnd,
                                onHome = vm::backToStart,
                            )
                        }
                    }
                }

                ui.activeReport?.let { report ->
                    ReportDialog(
                        report = report,
                        isReplay = ui.reportIsReplay,
                        onContinue = vm::dismissReport,
                    )
                }

                if (ui.showMilestones && activeGame != null) {
                    MilestonesDialog(
                        state = activeGame,
                        onDismiss = vm::dismissMilestones,
                    )
                }

                if (ui.showSettings) {
                    AiSettingsSheet(
                        initial = ui.settings,
                        onDismiss = vm::dismissSettings,
                        onSave = { newSettings -> vm.saveSettings(newSettings) },
                    )
                }
            }
        }
    }
}
