package com.ottoman.empire1800.engine

import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.isBudgetLine
import com.ottoman.empire1800.models.isRevenueLine
import com.ottoman.empire1800.services.DiplomacyRules
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToDouble
import kotlin.math.roundToInt

/** معايرة ما يكتبه الذكاء الاصطناعي مع الواقع الاقتصادي للحملة. */
object Calibrator {
    const val MAX_COST_SHARE: Double = 0.60
    const val MAX_LOAN_SHARE: Double = 1.00
    const val MAX_LINE_SHARE: Double = 0.15

    const val MAX_NOW: Int = 8
    const val MAX_LATER: Int = 12
    const val MAX_DELAY: Int = 10
    const val MAX_LINE_CHANGES: Int = 3

    const val MAX_RELATION: Int = 8
    const val MAX_POWER_MOOD: Int = 10
    const val MAX_MAP_KEYS: Int = 3

    const val MAX_TOTAL_GAIN: Int = 16

    const val MAX_TITLE: Int = 90
    const val MAX_SCENE: Int = 800
    const val MAX_DILEMMA: Int = 500
    const val MAX_OPTION_TEXT: Int = 420
    const val MAX_RESULT: Int = 500
    const val MAX_REACTION: Int = 420
    const val MAX_NOTE: Int = 300
    const val MAX_BRIEFING: Int = 1000
    const val MAX_CHRONICLE: Int = 700

    fun capNow(f: Double): Int = (MAX_NOW * f).roundToInt().coerceIn(1, MAX_NOW)
    fun capLater(f: Double): Int = (MAX_LATER * f).roundToInt().coerceIn(1, MAX_LATER)
    fun capGain(f: Double): Int = (MAX_TOTAL_GAIN * f).roundToInt().coerceIn(1, MAX_TOTAL_GAIN)
    fun capRelation(f: Double): Int = (MAX_RELATION * f).roundToInt().coerceIn(1, MAX_RELATION)
    fun capPowerMood(f: Double): Int = (MAX_POWER_MOOD * f).roundToInt().coerceIn(1, MAX_POWER_MOOD)
    fun capDelay(f: Double): Int = MAX_DELAY * (1.0 / f).roundToInt()

    fun calibrate(raw: RoundPlan, s: GameState): RoundPlan {
        val revenue = s.budget.totalRevenue
        val credit = DiplomacyRules.creditFactor(s)
        return RoundPlan(
            headline = cut(raw.headline, MAX_TITLE),
            briefing = cut(raw.briefing, MAX_BRIEFING),
            chronicle = cut(raw.chronicle, MAX_CHRONICLE),
            world = raw.world,
            decisions = raw.decisions.map { d ->
                Decision(
                    category = d.category,
                    title = cut(d.title, MAX_TITLE),
                    place = cut(d.place, 70),
                    scene = cut(d.scene, MAX_SCENE),
                    dilemma = cut(d.dilemma, MAX_DILEMMA),
                    options = d.options.map { o ->
                        val cal = calibrateOption(o, revenue, creditFactor = credit, f = s.f)
                        val activeMood = cal.powerMood.filterKeys { k -> s.powers[k]?.dissolved != true }
                        val activeDissolve = if (cal.dissolve.isNotEmpty() && s.powers[cal.dissolve]?.dissolved == true) {
                            ""
                        } else {
                            cal.dissolve
                        }
                        val activeConquer = if (cal.conquer.isNotEmpty() && s.econ.frontierRegions[cal.conquer]?.controlled == true) {
                            ""
                        } else {
                            cal.conquer
                        }
                        if (activeMood.size != cal.powerMood.size || activeDissolve != cal.dissolve || activeConquer != cal.conquer) {
                            cal.copy(powerMood = activeMood, dissolve = activeDissolve, conquer = activeConquer)
                        } else {
                            cal
                        }
                    },
                )
            },
        )
    }

    fun calibrateOption(
        o: DecisionOption,
        annualRevenue: Double,
        creditFactor: Double = 1.0,
        f: Double = 1.0,
    ): DecisionOption {
        var changed = false
        val nowCap = capNow(f)
        val laterCap = capLater(f)
        val gainCap = capGain(f)
        val delayScale = (1.0 / f).roundToInt()

        fun clampMoney(v: Double, maxVal: Double): Double {
            val c = if (v.isFinite()) v.coerceIn(0.0, maxVal) else 0.0
            if (abs(c - v) > maxVal * 0.005) changed = true
            return c.roundToDouble()
        }

        val cost = clampMoney(o.cost, annualRevenue * MAX_COST_SHARE * f)
        val loan = clampMoney(o.loan, annualRevenue * MAX_LOAN_SHARE * creditFactor * f)

        var now = o.now.clampTo(nowCap)
        var later = o.later.clampTo(laterCap)
        if (differs(now, o.now) || differs(later, o.later)) changed = true

        val gain = positiveSum(now) + positiveSum(later)
        if (gain > gainCap) {
            val k = gainCap.toDouble() / gain
            now = scalePositive(now, k)
            later = scalePositive(later, k)
            changed = true
        }

        val merged = linkedMapOf<String, Double>()
        for (c in o.lineChanges) {
            if (!isBudgetLine(c.line)) {
                changed = true
                continue
            }
            merged[c.line] = (merged[c.line] ?: 0.0) + c.delta
        }
        val lines = mutableListOf<LineChange>()
        val limit = annualRevenue * MAX_LINE_SHARE * f
        for ((key, rawVal) in merged) {
            if (lines.size >= MAX_LINE_CHANGES) {
                changed = true
                break
            }
            val v = if (rawVal.isFinite()) rawVal.coerceIn(-limit, limit) else 0.0
            if (abs(v - rawVal) > limit * 0.005) changed = true
            if (abs(v) >= 1.0) lines.add(LineChange(key, v.roundToDouble()))
        }

        var delay = o.delay.coerceIn(0, MAX_DELAY * delayScale)
        if (delay != o.delay) changed = true
        val raisesRevenue = lines.any { isRevenueLine(it.line) && it.delta > 0.0 }
        if (raisesRevenue && delay < delayScale) {
            delay = delayScale
            changed = true
        }

        fun limitMap(raw: Map<String, Int>, maxVal: Int): Map<String, Int> {
            val out = linkedMapOf<String, Int>()
            for ((k, rawV) in raw) {
                if (out.size >= MAX_MAP_KEYS) {
                    changed = true
                    break
                }
                val v = rawV.coerceIn(-maxVal, maxVal)
                if (v != rawV) changed = true
                if (v != 0) out[k] = v
            }
            return out
        }

        val relations = limitMap(o.relations, capRelation(f))
        val powerMood = limitMap(o.powerMood, capPowerMood(f))

        return DecisionOption(
            text = cut(o.text, MAX_OPTION_TEXT),
            result = cut(o.result, MAX_RESULT),
            reaction = cut(o.reaction, MAX_REACTION),
            note = cut(o.note, MAX_NOTE),
            cost = cost,
            loan = loan,
            now = now,
            later = later,
            delay = delay,
            lineChanges = lines,
            relations = relations,
            powerMood = powerMood,
            dissolve = o.dissolve,
            war = o.war,
            peace = o.peace,
            project = o.project,
            conquer = o.conquer,
            calibrated = changed,
        )
    }

    private fun differs(a: Effects, b: Effects): Boolean =
        a.legitimacy != b.legitimacy ||
            a.stability != b.stability ||
            a.army != b.army ||
            a.foreign != b.foreign ||
            a.education != b.education

    private fun positiveSum(e: Effects): Int =
        listOf(e.legitimacy, e.stability, e.army, e.foreign, e.education)
            .filter { it > 0 }
            .sum()

    private fun scalePositive(e: Effects, k: Double): Effects {
        fun f(v: Int): Int = if (v > 0) floor(v * k).toInt() else v
        return Effects(
            legitimacy = f(e.legitimacy),
            stability = f(e.stability),
            army = f(e.army),
            foreign = f(e.foreign),
            education = f(e.education),
        )
    }

    private fun cut(s: String, maxLen: Int): String {
        val t = s.trim()
        if (t.length <= maxLen) return t
        return "${t.substring(0, maxLen - 1).trimEnd()}…"
    }
}
