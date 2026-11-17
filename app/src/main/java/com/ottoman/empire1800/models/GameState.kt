package com.ottoman.empire1800.models

import com.ottoman.empire1800.engine.HalfYearReport
import com.ottoman.empire1800.services.MilestoneRules
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** سطر في سجل القرارات. */
data class HistoryEntry(
    val round: Int,
    val category: String,
    val title: String,
    val choice: String,
    val result: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("round", round)
        put("category", category)
        put("title", title)
        put("choice", choice)
        put("result", result)
    }

    companion object {
        fun fromJson(obj: JSONObject): HistoryEntry {
            fun s(k: String): String = obj.opt(k) as? String ?: ""
            val r = obj.opt("round")
            return HistoryEntry(
                round = if (r is Number) r.toInt() else 0,
                category = s("category"),
                title = s("title"),
                choice = s("choice"),
                result = s("result"),
            )
        }
    }
}

/** أثر مؤجل ينتظر موعده (بعد عدد من أنصاف السنوات). */
data class PendingEffect(
    val dueRound: Int,
    val effects: Effects = Effects(),
    val lines: List<LineChange> = emptyList(),
    val source: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("due", dueRound)
        put("effects", effects.toJson())
        put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })
        put("source", source)
    }

    companion object {
        fun fromJson(obj: JSONObject): PendingEffect {
            val lines = mutableListOf<LineChange>()
            val raw = obj.optJSONArray("lines")
            if (raw != null) {
                for (i in 0 until raw.length()) {
                    val lc = LineChange.fromJson(raw.opt(i))
                    if (lc.line.isNotEmpty()) lines.add(lc)
                }
            }
            val due = obj.opt("due")
            return PendingEffect(
                dueRound = if (due is Number) due.toInt() else 0,
                effects = Effects.fromJson(obj.opt("effects")),
                lines = lines,
                source = obj.opt("source") as? String ?: "",
            )
        }
    }
}

/** حالة الحملة كاملة. تحفظ وتستعاد بما فيها الجولة الحالية التي كتبها الذكاء الاصطناعي. */
class GameState(
    var treasury: Double = 3_000_000.0,
    var debt: Double = 6_000_000.0,
    var legitimacy: Int = 65,
    stability: Int = 55,
    stab: StabilityState? = null,
    var army: Int = 45,
    var foreign: Int = 45,
    var education: Int = 15,
    budget: Budget? = null,
    powers: MutableMap<String, PowerState>? = null,
    projects: MutableList<PowerProject>? = null,
    econ: EconomyState? = null,
    countries: MutableMap<String, CountryState>? = null,
    var domesticSpy: Int = 0,
    intel: MutableList<SpyReport>? = null,
    demog: DemographyState? = null,
    world: WorldMemory? = null,
    courtActions: Int? = null,
    var repay: Double = 0.0,
    courtLog: MutableList<String>? = null,
    decrees: MutableList<String>? = null,
    events: MutableList<String>? = null,
    var seed: Int = DEFAULT_SEED,
    var ppy: Int = 2,
    var difficulty: Int = 1,
    wars: MutableList<War>? = null,
    var foreignDebt: Double = 0.0,
    var defaultRound: Int = -1,
    var emergencyStreak: Int = 0,
    var dadActive: Boolean = false,
    var grandVizier: String = "traditional",
    var lastAusterityRound: Int = -999,
    var project: ActiveProject? = null,
    completed: MutableList<String>? = null,
    milestones: MutableList<String>? = null,
    var hadForeignDebt: Boolean = false,
    var wonMajorWar: Boolean = false,
    series: MutableList<StatPoint>? = null,
    var round: Int = 0,
    pending: MutableList<PendingEffect>? = null,
    history: MutableList<HistoryEntry>? = null,
    var chronicle: String = "",
    var lastReport: String = "",
    var lastFullReport: HalfYearReport? = null,
    var plan: RoundPlan? = null,
    picks: MutableList<Int?>? = null,
) {
    var stab: StabilityState = stab ?: StabilityState.fromTotal(stability)
    var budget: Budget = budget ?: Budget.initial1800()
    var powers: MutableMap<String, PowerState> = powers ?: defaultPowers()
    var projects: MutableList<PowerProject> = projects ?: mutableListOf()
    var econ: EconomyState = econ ?: EconomyState()
    var countries: MutableMap<String, CountryState> = countries ?: defaultCountries()
    var intel: MutableList<SpyReport> = intel ?: mutableListOf()
    var demog: DemographyState = demog ?: DemographyState()
    var world: WorldMemory = world ?: WorldMemory()
    var courtActions: Int = courtActions ?: if (ppy == 4) 1 else 2
    var courtLog: MutableList<String> = courtLog ?: mutableListOf()
    var decrees: MutableList<String> = decrees ?: mutableListOf()
    var events: MutableList<String> = events ?: mutableListOf()
    var wars: MutableList<War> = wars ?: mutableListOf()
    var completed: MutableList<String> = completed ?: mutableListOf()
    var milestones: MutableList<String> = milestones ?: mutableListOf()
    var series: MutableList<StatPoint> = series ?: mutableListOf()
    var pending: MutableList<PendingEffect> = pending ?: mutableListOf()
    var history: MutableList<HistoryEntry> = history ?: mutableListOf()
    var picks: MutableList<Int?> = picks ?: mutableListOf(null, null, null)

    val diff: Difficulty get() = Difficulty(difficulty)

    /** جزء نصف السنة الذي تمثله الفترة: 1.0 (6 أشهر) أو 0.5 (3 أشهر). */
    val f: Double get() = 2.0 / if (ppy == 4) 4 else 2

    /** عدد الفترات في نصف السنة (1 أو 2). */
    val k: Int get() = if (ppy == 4) 2 else 1

    /** السنة الحالية. */
    val year: Int get() = 1800 + round / (if (ppy == 4) 4 else 2)

    /** هل تنتهي الآن فترة تُتم نصف سنة؟ */
    val tick: Boolean get() = ppy == 2 || round % 2 != 0

    /** الاستقرار الكلي: متوسط الأبعاد الستة. */
    var stability: Int
        get() = stab.rounded
        set(value) {
            stab.shiftAll(value.coerceIn(0, 100) - stab.total)
        }

    val hasAllPicks: Boolean get() = picks.size == 3 && picks.all { it != null }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("treasury", treasury)
        put("debt", debt)
        put("legitimacy", legitimacy)
        put("stability", stability)
        put("stab", stab.toJson())
        put("army", army)
        put("foreign", foreign)
        put("education", education)
        put("budget", budget.toJson())
        put("powers", JSONArray().apply { powers.values.forEach { put(it.toJson()) } })
        put("projects", JSONArray().apply { projects.forEach { put(it.toJson()) } })
        put("econ", econ.toJson())
        put("countries", JSONArray().apply { countries.values.forEach { put(it.toJson()) } })
        put("domesticSpy", domesticSpy)
        put("intel", JSONArray().apply { intel.forEach { put(it.toJson()) } })
        put("demog", demog.toJson())
        put("world", world.toJson())
        put("courtActions", courtActions)
        put("repay", repay)
        put("courtLog", JSONArray(courtLog))
        put("decrees", JSONArray(decrees))
        put("events", JSONArray(events))
        put("seed", seed)
        put("ppy", ppy)
        put("difficulty", difficulty)
        put("wars", JSONArray().apply { wars.forEach { put(it.toJson()) } })
        put("foreignDebt", foreignDebt)
        put("defaultRound", defaultRound)
        put("emergencyStreak", emergencyStreak)
        put("dadActive", dadActive)
        put("grandVizier", grandVizier)
        put("lastAusterityRound", lastAusterityRound)
        put("project", project?.toJson() ?: JSONObject.NULL)
        put("completed", JSONArray(completed))
        put("milestones", JSONArray(milestones))
        put("hadForeignDebt", hadForeignDebt)
        put("wonMajorWar", wonMajorWar)
        put("series", JSONArray().apply { series.forEach { put(it.toJsonArray()) } })
        put("round", round)
        put("pending", JSONArray().apply { pending.forEach { put(it.toJson()) } })
        put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
        put("chronicle", chronicle)
        put("lastReport", lastReport)
        put("lastFullReport", lastFullReport?.toJson() ?: JSONObject.NULL)
        put("plan", plan?.toJson() ?: JSONObject.NULL)
        put("picks", JSONArray().apply { picks.forEach { put(it ?: JSONObject.NULL) } })
    }

    fun copy(): GameState = fromJson(toJson())

    companion object {
        const val SCHEMA: Int = 4
        val readableSchemas: Set<Int> = setOf(2, 3, 4)
        const val DEFAULT_SEED: Int = 1800

        fun fromJson(json: JSONObject): GameState {
            val sc = json.opt("schema")
            if (sc !is Number || !readableSchemas.contains(sc.toInt())) {
                throw IllegalArgumentException("صيغة حفظ غير مدعومة")
            }

            fun money(k: String, d: Double): Double {
                val v = json.opt(k)
                return if (v is Number && v.toDouble().isFinite()) v.toDouble().coerceIn(0.0, 1e15) else d
            }

            fun ind(k: String, d: Int): Int {
                val v = json.opt(k)
                return if (v is Number && v.toDouble().isFinite()) v.toDouble().roundToInt().coerceIn(0, 100) else d
            }

            fun strings(raw: Any?, max: Int): MutableList<String> {
                val arr = raw as? JSONArray ?: return mutableListOf()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val x = arr.opt(i)
                    if (x is String && x.trim().isNotEmpty()) {
                        out.add(x.trim())
                        if (out.size >= max) break
                    }
                }
                return out
            }

            val history = mutableListOf<HistoryEntry>()
            val rawHistory = json.optJSONArray("history")
            if (rawHistory != null) {
                for (i in 0 until rawHistory.length()) {
                    val h = rawHistory.optJSONObject(i)
                    if (h != null) history.add(HistoryEntry.fromJson(h))
                }
            }

            val pending = mutableListOf<PendingEffect>()
            val rawPending = json.optJSONArray("pending")
            if (rawPending != null) {
                for (i in 0 until rawPending.length()) {
                    val p = rawPending.optJSONObject(i)
                    if (p != null) pending.add(PendingEffect.fromJson(p))
                }
            }

            var plan: RoundPlan? = null
            val rawPlan = json.optJSONObject("plan")
            if (rawPlan != null) {
                val parsed = RoundPlan.fromJson(rawPlan)
                if (parsed.decisions.size == 3) plan = parsed
            }

            val picks = mutableListOf<Int?>(null, null, null)
            val rawPicks = json.optJSONArray("picks")
            if (plan != null && rawPicks != null) {
                for (i in 0 until minOf(3, rawPicks.length())) {
                    val p = rawPicks.opt(i)
                    if (p is Number) {
                        val idx = p.toInt()
                        if (idx in 0 until plan.decisions[i].options.size) picks[i] = idx
                    }
                }
            }

            val projects = mutableListOf<PowerProject>()
            val rawProjects = json.optJSONArray("projects")
            if (rawProjects != null) {
                for (i in 0 until rawProjects.length()) {
                    PowerProject.fromJson(rawProjects.opt(i))?.let { projects.add(it) }
                }
            }

            val intel = mutableListOf<SpyReport>()
            val rawIntel = json.optJSONArray("intel")
            if (rawIntel != null) {
                for (i in 0 until rawIntel.length()) {
                    SpyReport.fromJson(rawIntel.opt(i))?.let { intel.add(it) }
                }
            }

            val wars = mutableListOf<War>()
            val rawWars = json.optJSONArray("wars")
            if (rawWars != null) {
                for (i in 0 until rawWars.length()) {
                    val war = War.fromJson(rawWars.opt(i))
                    if (war != null && wars.none { it.enemy == war.enemy }) wars.add(war)
                }
            }

            val series = mutableListOf<StatPoint>()
            val rawSeries = json.optJSONArray("series")
            if (rawSeries != null) {
                for (i in 0 until rawSeries.length()) {
                    StatPoint.fromJsonArray(rawSeries.opt(i))?.let { series.add(it) }
                }
            }

            val debtNow = money("debt", 6_000_000.0)
            val foreignRaw = money("foreignDebt", 0.0)
            val foreignDebtVal = if (foreignRaw > debtNow) debtNow else foreignRaw
            val roundVal = (json.opt("round") as? Number)?.toInt()?.coerceIn(0, 100000) ?: 0
            val diffVal = (json.opt("difficulty") as? Number)?.toInt()?.coerceIn(0, 2) ?: 1
            val ppyVal = if ((json.opt("ppy") as? Number)?.toInt() == 4) 4 else 2
            val defaultVal = (json.opt("defaultRound") as? Number)?.toInt() ?: -1
            val streakVal = (json.opt("emergencyStreak") as? Number)?.toInt()?.coerceIn(0, 1000) ?: 0
            val stabilityTotal = ind("stability", 55)
            val seedVal = (json.opt("seed") as? Number)?.toInt() ?: DEFAULT_SEED
            val actionsVal = (json.opt("courtActions") as? Number)?.toInt()?.coerceIn(0, 9) ?: if (ppyVal == 4) 1 else 2
            val spyVal = (json.opt("domesticSpy") as? Number)?.toInt()?.coerceIn(0, 2) ?: 0

            val vizierRaw = json.opt("grandVizier") as? String ?: "traditional"
            val vizierVal = if (grandVizierDefs.any { it.key == vizierRaw }) vizierRaw else "traditional"
            val lastAusterityVal = (json.opt("lastAusterityRound") as? Number)?.toInt()?.coerceIn(-999, 100_000) ?: -999

            val validMilestones = milestoneDefs.map { it.id }.toSet()
            val milestonesList = strings(json.opt("milestones"), 16)
                .filter { validMilestones.contains(it) }
                .distinct()
                .toMutableList()
            val dadActiveVal = json.opt("dadActive") == true
            val hadForeignDebtVal = (json.opt("hadForeignDebt") as? Boolean)
                ?: (foreignDebtVal > 0.0 || dadActiveVal || defaultVal >= 0)
            val wonMajorWarVal = json.opt("wonMajorWar") == true

            val restored = GameState(
                treasury = money("treasury", 3_000_000.0),
                debt = debtNow,
                legitimacy = ind("legitimacy", 65),
                stability = stabilityTotal,
                stab = StabilityState.fromJson(json.opt("stab"), stabilityTotal),
                army = ind("army", 45),
                foreign = ind("foreign", 45),
                education = ind("education", 15),
                budget = Budget.fromJson(json.opt("budget")),
                powers = powersFromJson(json.opt("powers")),
                projects = projects,
                econ = EconomyState.fromJson(json.opt("econ")),
                countries = countriesFromJson(json.opt("countries")),
                domesticSpy = spyVal,
                intel = if (intel.size > 8) intel.subList(intel.size - 8, intel.size).toMutableList() else intel,
                demog = DemographyState.fromJson(json.opt("demog")),
                world = WorldMemory.fromJson(json.opt("world")),
                courtActions = actionsVal,
                repay = money("repay", 0.0),
                courtLog = strings(json.opt("courtLog"), 12),
                decrees = strings(json.opt("decrees"), 8),
                events = strings(json.opt("events"), 16),
                seed = seedVal,
                ppy = ppyVal,
                difficulty = diffVal,
                wars = if (wars.size > 3) wars.subList(0, 3).toMutableList() else wars,
                foreignDebt = foreignDebtVal,
                defaultRound = defaultVal,
                emergencyStreak = streakVal,
                dadActive = dadActiveVal,
                grandVizier = vizierVal,
                lastAusterityRound = lastAusterityVal,
                project = ActiveProject.fromJson(json.opt("project")),
                completed = strings(json.opt("completed"), 40),
                milestones = milestonesList,
                hadForeignDebt = hadForeignDebtVal,
                wonMajorWar = wonMajorWarVal,
                series = if (series.size > 900) series.subList(series.size - 900, series.size).toMutableList() else series,
                round = roundVal,
                pending = pending,
                history = history,
                chronicle = json.opt("chronicle") as? String ?: "",
                lastReport = json.opt("lastReport") as? String ?: "",
                lastFullReport = HalfYearReport.fromJson(json.opt("lastFullReport")),
                plan = plan,
                picks = picks,
            )
            MilestoneRules.evaluate(restored)
            return restored
        }
    }
}
