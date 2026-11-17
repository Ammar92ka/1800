package com.ottoman.empire1800.support

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.Ending
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.services.PowerRules

private val neutralOption = DecisionOption(text = "محايد", result = "لا شيء")

fun neutralPlan(): RoundPlan {
    fun d(c: DecisionCategory) = Decision(
        category = c,
        title = "t",
        scene = "s",
        dilemma = "d",
        options = listOf(neutralOption, neutralOption, neutralOption),
    )
    return RoundPlan(
        headline = "h",
        briefing = "b",
        decisions = DecisionCategory.entries.map { d(it) },
    )
}

fun realisticPlan(): RoundPlan {
    fun d(c: DecisionCategory, o: List<DecisionOption>) = Decision(
        category = c,
        title = "t",
        scene = "s",
        dilemma = "d",
        options = o,
    )
    return RoundPlan(
        headline = "h",
        briefing = "b",
        decisions = listOf(
            d(
                DecisionCategory.INTERNAL,
                listOf(
                    DecisionOption(
                        text = "إصلاح",
                        result = "ن",
                        cost = 400_000.0,
                        now = Effects(legitimacy = 1, stability = -1),
                        later = Effects(army = 2),
                        delay = 2,
                    ),
                    DecisionOption(
                        text = "ترضية",
                        result = "ن",
                        cost = 300_000.0,
                        now = Effects(stability = 1),
                        powerMood = mapOf("janissaries" to 6),
                    ),
                    DecisionOption(
                        text = "تشدد",
                        result = "ن",
                        now = Effects(stability = 2, legitimacy = -2),
                    ),
                ),
            ),
            d(
                DecisionCategory.ECONOMY,
                listOf(
                    DecisionOption(
                        text = "تقشف",
                        result = "ن",
                        now = Effects(stability = -1, legitimacy = -1),
                        delay = 1,
                        lineChanges = listOf(LineChange("palace", -500_000.0)),
                    ),
                    DecisionOption(
                        text = "ضريبة",
                        result = "ن",
                        now = Effects(stability = -1),
                        delay = 2,
                        lineChanges = listOf(LineChange("land", 600_000.0)),
                    ),
                    DecisionOption(text = "لا شيء", result = "ن"),
                ),
            ),
            d(
                DecisionCategory.FOREIGN,
                listOf(
                    DecisionOption(
                        text = "هدية",
                        result = "ن",
                        cost = 250_000.0,
                        now = Effects(foreign = 1),
                        relations = mapOf("britain" to 4),
                    ),
                    DecisionOption(text = "حياد", result = "ن"),
                    DecisionOption(
                        text = "استعراض",
                        result = "ن",
                        cost = 500_000.0,
                        now = Effects(army = 1, foreign = 1),
                    ),
                ),
            ),
        ),
    )
}

data class BotRun(val rounds: Int, val ending: Ending?)

fun playBot(
    s: GameState,
    prudent: Boolean,
    maxRounds: Int = 230,
    check: ((GameState) -> Unit)? = null,
): BotRun {
    val engine = GameEngine()
    var r = 0
    while (r < maxRounds) {
        if (prudent) {
            s.plan = Calibrator.calibrate(realisticPlan(), s)
            val rev = s.budget.totalRevenue
            val halfRev = rev / 2.0 * s.f
            val jan = s.powers["janissaries"]!!
            val internal = if (s.stability < 52 || (jan.satisfaction < 35.0 && !jan.dissolved)) 1 else 0
            var econ = 2
            if (s.treasury < 0.6 * halfRev || s.debt / rev > 1.2) {
                econ = if (s.econ.avgMood < 42.0 || s.budget.lineValue("palace") > 1.5e6 * s.econ.priceIndex) 0 else 1
            }
            val foreign = if (s.foreign < 35) 2 else 1
            s.picks = mutableListOf(internal, econ, foreign)
            if (s.treasury > 1.5 * halfRev) s.repay = s.treasury - halfRev
            if (jan.satisfaction < 30.0 && !jan.dissolved && s.courtActions > 0) {
                PowerRules.appease(s, "janissaries")
            }
        } else {
            s.plan = Calibrator.calibrate(neutralPlan(), s)
            s.picks = mutableListOf(0, 0, 0)
        }
        val report = engine.resolveRound(s)
        check?.invoke(s)
        if (report.ending != null) return BotRun(r + 1, report.ending)
        r++
    }
    return BotRun(r, null)
}
