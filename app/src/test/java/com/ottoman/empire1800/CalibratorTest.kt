package com.ottoman.empire1800

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibratorTest {
    private val revenue = 18_000_000.0

    private fun opt(
        cost: Double = 0.0,
        loan: Double = 0.0,
        now: Effects = Effects(),
        later: Effects = Effects(),
        delay: Int = 0,
        lines: List<LineChange> = emptyList(),
    ) = DecisionOption(
        text = "خيار",
        result = "نتيجة",
        cost = cost,
        loan = loan,
        now = now,
        later = later,
        delay = delay,
        lineChanges = lines,
    )

    @Test
    fun reasonableNumbersUnchangedWithoutCalibratedFlag() {
        val o = Calibrator.calibrateOption(
            opt(
                cost = 2_000_000.0,
                loan = 3_000_000.0,
                now = Effects(stability = 3, army = -2),
                later = Effects(education = 5),
                delay = 4,
                lines = listOf(LineChange("civil", 400_000.0)),
            ),
            revenue,
        )
        assertEquals(2_000_000.0, o.cost, 1.0)
        assertEquals(3_000_000.0, o.loan, 1.0)
        assertEquals(3, o.now.stability)
        assertEquals(4, o.delay)
        assertFalse(o.calibrated)
    }

    @Test
    fun costAndLoanCappedToRevenueShare() {
        val o = Calibrator.calibrateOption(opt(cost = 50_000_000.0, loan = 90_000_000.0), revenue)
        assertEquals(revenue * Calibrator.MAX_COST_SHARE, o.cost, 1.0)
        assertEquals(revenue * Calibrator.MAX_LOAN_SHARE, o.loan, 1.0)
        assertTrue(o.calibrated)
    }

    @Test
    fun effectsAndTotalPositiveGainAreClamped() {
        val neg = Calibrator.calibrateOption(
            opt(now = Effects(legitimacy = -30), later = Effects(stability = -40)),
            revenue,
        )
        assertEquals(-Calibrator.MAX_NOW, neg.now.legitimacy)
        assertEquals(-Calibrator.MAX_LATER, neg.later.stability)
        assertTrue(neg.calibrated)

        val pos = Calibrator.calibrateOption(
            opt(
                now = Effects(legitimacy = 8, stability = 8, army = 8),
                later = Effects(foreign = 12, education = 12),
            ),
            revenue,
        )
        val gain = pos.now.legitimacy + pos.now.stability + pos.now.army + pos.later.foreign + pos.later.education
        assertTrue(gain <= Calibrator.MAX_TOTAL_GAIN)
        assertTrue(pos.calibrated)
    }

    @Test
    fun lineChangesFilteredMergedAndDelayed() {
        val o = Calibrator.calibrateOption(
            opt(lines = listOf(LineChange("nonsense", 100_000.0), LineChange("army", 300_000.0), LineChange("army", 200_000.0))),
            revenue,
        )
        assertEquals(1, o.lineChanges.size)
        assertEquals("army", o.lineChanges.first().line)
        assertEquals(500_000.0, o.lineChanges.first().delta, 1.0)
        assertTrue(o.calibrated)

        val revRaise = Calibrator.calibrateOption(opt(delay = 0, lines = listOf(LineChange("customs", 500_000.0))), revenue)
        assertTrue(revRaise.delay >= 1)
        assertTrue(revRaise.calibrated)
    }

    @Test
    fun longTextsTruncated() {
        val long = "ك".repeat(5000)
        val plan = Calibrator.calibrate(
            RoundPlan(
                headline = long,
                briefing = long,
                chronicle = long,
                decisions = DecisionCategory.entries.map { c ->
                    Decision(
                        category = c,
                        title = "عنوان",
                        scene = long,
                        dilemma = long,
                        options = List(3) { opt() },
                    )
                },
            ),
            GameState(),
        )
        assertTrue(plan.briefing.length <= Calibrator.MAX_BRIEFING)
        assertTrue(plan.chronicle.length <= Calibrator.MAX_CHRONICLE)
        assertTrue(plan.decisions.first().scene.length <= Calibrator.MAX_SCENE)
    }

    @Test
    fun quarterlyCalibratorScalesRelationsPowerMoodAndRevenueDelay() {
        val raw = DecisionOption(
            text = "خيار ربع سنوي",
            result = "نتيجة",
            delay = 0,
            lineChanges = listOf(LineChange("customs", 2_000_000.0)),
            relations = mapOf("britain" to 8, "france" to -8),
            powerMood = mapOf("janissaries" to 10, "ulema" to -10),
        )
        val cal = Calibrator.calibrateOption(raw, revenue, f = 0.5)
        assertEquals(2, cal.delay)
        assertEquals(4, cal.relations["britain"])
        assertEquals(-4, cal.relations["france"])
        assertEquals(5, cal.powerMood["janissaries"])
        assertEquals(-5, cal.powerMood["ulema"])
        assertTrue(cal.calibrated)
    }
}
