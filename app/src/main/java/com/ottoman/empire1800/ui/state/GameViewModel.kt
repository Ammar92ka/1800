package com.ottoman.empire1800.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ottoman.empire1800.engine.Ending
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.HalfYearReport
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.AiClient
import com.ottoman.empire1800.services.AiException
import com.ottoman.empire1800.services.AiSettings
import com.ottoman.empire1800.services.CourtResult
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.RoundGenerator
import com.ottoman.empire1800.services.SaveService
import com.ottoman.empire1800.services.SettingsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AppScreen {
    START,
    GAME,
    COURT,
    CHARTS,
    END,
}

data class GameUiState(
    val screen: AppScreen = AppScreen.START,
    val loadingSaved: Boolean = true,
    val savedGame: GameState? = null,
    val activeGame: GameState? = null,
    val settings: AiSettings = AiSettings(),
    val showSettings: Boolean = false,
    val loadingPlan: Boolean = false,
    val resolving: Boolean = false,
    val attempt: Int = 1,
    val problem: String? = null,
    val error: String? = null,
    val activeReport: HalfYearReport? = null,
    val reportIsReplay: Boolean = false,
    val showMilestones: Boolean = false,
    val courtInitialTab: Int = 0,
    val activeEnding: Ending? = null,
    val snackbarMessage: String? = null,
    val revision: Long = 0L,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GameEngine()
    private val save = SaveService(application)
    private val settingsService = SettingsService(application)

    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private var ensurePlanJob: Job? = null

    init {
        refreshStartState()
    }

    fun refreshStartState() {
        viewModelScope.launch {
            _ui.update { it.copy(loadingSaved = true) }
            val loadedSettings = settingsService.load()
            var saved = save.loadState()
            if (saved != null && saved.round >= GameEngine.totalRoundsFor(saved.ppy)) {
                save.clear()
                saved = null
            }
            _ui.update {
                it.copy(
                    loadingSaved = false,
                    savedGame = saved,
                    settings = loadedSettings,
                    revision = it.revision + 1,
                )
            }
        }
    }

    fun openSettings() {
        viewModelScope.launch {
            val s = settingsService.load()
            _ui.update { it.copy(settings = s, showSettings = true) }
        }
    }

    fun dismissSettings() {
        _ui.update { it.copy(showSettings = false) }
    }

    suspend fun saveSettings(s: AiSettings) {
        settingsService.save(s)
        _ui.update { it.copy(settings = s) }
    }

    fun consumeSnackbar() {
        _ui.update { it.copy(snackbarMessage = null) }
    }

    fun notify(message: String) {
        _ui.update { it.copy(snackbarMessage = message) }
    }

    fun exportSavedJson(): String? = _ui.value.savedGame?.let { save.exportJson(it) }

    fun importSavedJson(rawText: String) {
        viewModelScope.launch {
            val trimmed = rawText.trim()
            val loaded = try {
                if (trimmed.isEmpty()) null else GameState.fromJson(JSONObject(trimmed))
            } catch (_: Throwable) {
                null
            }
            if (loaded == null) {
                notify("لم أجد حفظا صالحا في الحافظة. انسخ نص الحفظ كاملا ثم أعد المحاولة.")
                return@launch
            }
            save.saveState(loaded)
            val refreshed = save.loadState()
            _ui.update {
                it.copy(
                    savedGame = refreshed,
                    snackbarMessage = "تم استيراد الحفظ.",
                    revision = it.revision + 1,
                )
            }
        }
    }

    fun continueSaved() {
        val saved = _ui.value.savedGame ?: return
        openGame(saved)
    }

    fun startNewGame(difficulty: Int, ppy: Int) {
        viewModelScope.launch {
            val currentSettings = settingsService.load()
            _ui.update { it.copy(settings = currentSettings) }
            if (!currentSettings.hasKey) {
                _ui.update {
                    it.copy(
                        snackbarMessage = "أضف مفتاح الذكاء الاصطناعي أولا، فهو من يكتب أحداث اللعبة.",
                        showSettings = true,
                    )
                }
                return@launch
            }
            save.clear()
            openGame(GameState(difficulty = difficulty, ppy = ppy))
        }
    }

    private fun openGame(state: GameState) {
        ensurePlanJob?.cancel()
        ensurePlanJob = null
        _ui.update {
            it.copy(
                screen = AppScreen.GAME,
                activeGame = state,
                loadingPlan = state.plan == null,
                resolving = false,
                error = null,
                attempt = 1,
                problem = null,
                activeReport = null,
                reportIsReplay = false,
                showMilestones = false,
                courtInitialTab = 0,
                activeEnding = null,
                revision = it.revision + 1,
            )
        }
        ensurePlan()
    }

    fun ensurePlan() {
        val job = ensurePlanJob
        if (job != null && job.isActive) return
        ensurePlanJob = viewModelScope.launch {
            loadPlanInternal()
        }
    }

    private suspend fun loadPlanInternal() {
        val state = _ui.value.activeGame ?: return
        if (state.plan != null) {
            _ui.update { it.copy(loadingPlan = false, error = null) }
            return
        }
        _ui.update {
            it.copy(
                loadingPlan = true,
                error = null,
                attempt = 1,
                problem = null,
            )
        }
        val currentSettings = settingsService.load()
        _ui.update { it.copy(settings = currentSettings) }
        if (!currentSettings.hasKey) {
            _ui.update {
                it.copy(
                    loadingPlan = false,
                    error = "أضف مفتاح الذكاء الاصطناعي من الإعدادات حتى تُكتب الجولة.",
                )
            }
            return
        }
        try {
            val client = AiClient.fromSettings(currentSettings)
            val generator = RoundGenerator(
                call = { prompt -> client.generateJson(prompt) },
                onAttempt = { att, prob ->
                    _ui.update { it.copy(attempt = att, problem = prob) }
                },
            )
            val plan = generator.generate(state)
            state.plan = plan
            state.picks = mutableListOf(null, null, null)
            if (plan.chronicle.isNotEmpty()) state.chronicle = plan.chronicle
            val w = plan.world
            if (w != null && !w.isEmpty) state.world = w
            PowerRules.syncPowerReformsToWorld(state)
            save.saveState(state)
            _ui.update {
                it.copy(
                    loadingPlan = false,
                    error = null,
                    revision = it.revision + 1,
                )
            }
        } catch (e: AiException) {
            _ui.update { it.copy(loadingPlan = false, error = e.message) }
        } catch (_: Throwable) {
            _ui.update { it.copy(loadingPlan = false, error = "حدث خطأ غير متوقع. حاول مرة أخرى.") }
        }
    }

    fun pickOption(decisionIndex: Int, optionIndex: Int) {
        val state = _ui.value.activeGame ?: return
        state.picks[decisionIndex] = optionIndex
        _ui.update { it.copy(revision = it.revision + 1) }
        viewModelScope.launch { save.saveState(state) }
    }

    fun setRepay(amount: Double, persist: Boolean = false) {
        val state = _ui.value.activeGame ?: return
        state.repay = amount
        _ui.update { it.copy(revision = it.revision + 1) }
        if (persist) {
            viewModelScope.launch { save.saveState(state) }
        }
    }

    fun resolveRound() {
        val state = _ui.value.activeGame ?: return
        if (_ui.value.resolving || !state.hasAllPicks) return
        _ui.update { it.copy(resolving = true) }

        val report = engine.resolveRound(state)
        viewModelScope.launch {
            if (report.ending != null) {
                save.clear()
            } else {
                save.saveState(state)
                // تبدأ كتابة الجولة التالية أثناء عرض التقرير لتقليل الانتظار
                ensurePlan()
            }
            _ui.update {
                it.copy(
                    activeReport = report,
                    reportIsReplay = false,
                    revision = it.revision + 1,
                )
            }
        }
    }

    fun dismissReport() {
        val report = _ui.value.activeReport ?: return
        if (_ui.value.reportIsReplay) {
            _ui.update {
                it.copy(
                    activeReport = null,
                    reportIsReplay = false,
                    revision = it.revision + 1,
                )
            }
            return
        }
        val ending = report.ending
        if (ending != null) {
            _ui.update {
                it.copy(
                    activeReport = null,
                    reportIsReplay = false,
                    resolving = false,
                    activeEnding = ending,
                    screen = AppScreen.END,
                    revision = it.revision + 1,
                )
            }
        } else {
            _ui.update {
                it.copy(
                    activeReport = null,
                    reportIsReplay = false,
                    resolving = false,
                    revision = it.revision + 1,
                )
            }
        }
    }

    fun reopenLastReport() {
        val report = _ui.value.activeGame?.lastFullReport ?: return
        _ui.update {
            it.copy(
                activeReport = report,
                reportIsReplay = true,
                revision = it.revision + 1,
            )
        }
    }

    fun openMilestones() {
        if (_ui.value.activeGame == null) return
        _ui.update { it.copy(showMilestones = true) }
    }

    fun dismissMilestones() {
        _ui.update { it.copy(showMilestones = false) }
    }

    fun openCourt(tabIndex: Int = 0) {
        if (_ui.value.activeGame?.plan == null) return
        _ui.update {
            it.copy(
                courtInitialTab = tabIndex.coerceIn(0, 5),
                screen = AppScreen.COURT,
            )
        }
    }

    fun openCharts() {
        if (_ui.value.activeGame == null) return
        _ui.update { it.copy(screen = AppScreen.CHARTS) }
    }

    fun backToGame() {
        _ui.update { it.copy(screen = AppScreen.GAME, revision = it.revision + 1) }
    }

    fun backToStart() {
        ensurePlanJob?.cancel()
        ensurePlanJob = null
        _ui.update {
            it.copy(
                screen = AppScreen.START,
                activeGame = null,
                activeReport = null,
                reportIsReplay = false,
                showMilestones = false,
                courtInitialTab = 0,
                activeEnding = null,
                loadingPlan = false,
                resolving = false,
                error = null,
            )
        }
        refreshStartState()
    }

    fun restartFromEnd() {
        val prev = _ui.value.activeGame
        viewModelScope.launch {
            save.clear()
            val fresh = GameState(
                difficulty = prev?.difficulty ?: 1,
                ppy = prev?.ppy ?: 2,
            )
            openGame(fresh)
        }
    }

    fun runCourtAction(action: (GameState) -> CourtResult) {
        val state = _ui.value.activeGame ?: return
        val res = action(state)
        _ui.update {
            it.copy(
                snackbarMessage = if (res.message.isNotEmpty()) res.message else it.snackbarMessage,
                revision = it.revision + 1,
            )
        }
        viewModelScope.launch { save.saveState(state) }
    }
}
