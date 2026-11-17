package com.ottoman.empire1800

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.EndingType
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.revenueLines
import com.ottoman.empire1800.models.spendingLines
import com.ottoman.empire1800.support.FakeAi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val engine = GameEngine()

    private fun plan(
        first: DecisionOption = DecisionOption(text = "أ", result = "ن"),
        second: DecisionOption = DecisionOption(text = "أ", result = "ن"),
        third: DecisionOption = DecisionOption(text = "أ", result = "ن"),
    ): RoundPlan {
        val neutral = DecisionOption(text = "محايد", result = "ن")
        fun d(c: DecisionCategory, o: DecisionOption) = Decision(
            category = c,
            title = "قرار ${c.id}",
            scene = "مشهد",
            dilemma = "معضلة",
            options = listOf(o, neutral, neutral),
        )
        return RoundPlan(
            headline = "ع",
            briefing = "ملخص",
            decisions = listOf(
                d(DecisionCategory.INTERNAL, first),
                d(DecisionCategory.ECONOMY, second),
                d(DecisionCategory.FOREIGN, third),
            ),
        )
    }

    private fun ready(plan: RoundPlan, base: GameState? = null): GameState {
        val s = base ?: GameState()
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)
        return s
    }

    @Test
    fun calendarLabelsAndTotalRounds() {
        assertEquals(222, GameEngine.totalRounds)
        assertEquals(1800, GameEngine.yearOf(0))
        assertEquals(1800, GameEngine.yearOf(1))
        assertEquals(1801, GameEngine.yearOf(2))
        assertEquals("كانون الثاني - حزيران 1800", GameEngine.periodLabel(0))
        assertEquals("تموز - كانون الأول 1800", GameEngine.periodLabel(1))
        assertEquals("تموز - كانون الأول 1910", GameEngine.periodLabel(221))
    }

    @Test(expected = IllegalStateException::class)
    fun throwsWhenNoPlan() {
        engine.resolveRound(GameState())
    }

    @Test(expected = IllegalStateException::class)
    fun throwsWhenMissingPicks() {
        val s = GameState().apply { this.plan = plan() }
        engine.resolveRound(s)
    }

    @Test
    fun budgetLoansCostsAndTreasuryMath() {
        val s = ready(
            plan(
                first = DecisionOption(text = "أ", result = "ن", cost = 1_000_000.0),
                second = DecisionOption(text = "أ", result = "ن", loan = 2_000_000.0),
            ),
        )
        val report = engine.resolveRound(s)
        assertEquals(8_392_500.0, report.revenue, 0.01)
        assertEquals(8_500_000.0, report.spending, 0.01)
        assertEquals(480_000.0, report.interest, 0.01)
        assertEquals(1_000_000.0, report.oneTimeCosts, 0.01)
        assertEquals(2_000_000.0, report.loans, 0.01)
        assertEquals(3_412_500.0, s.treasury, 0.01)
        assertEquals(8_000_000.0, s.debt, 0.01)
        assertEquals(1, s.round)
        assertNull(s.plan)
        assertFalse(s.hasAllPicks)
        assertEquals(3, s.history.size)
        assertEquals("كانون الثاني - حزيران 1800", report.period)
        assertTrue(s.lastReport.isNotEmpty())
    }

    @Test
    fun immediateEffectsClampedToZeroToHundred() {
        val s = ready(
            plan(
                first = DecisionOption(text = "أ", result = "ن", now = Effects(legitimacy = 8, army = -8)),
            ),
        )
        engine.resolveRound(s)
        assertEquals(73, s.legitimacy)
        assertEquals(37, s.army)

        val low = GameState(army = 3).apply {
            this.plan = plan(first = DecisionOption(text = "أ", result = "ن", now = Effects(army = -8)))
            this.picks = mutableListOf(0, 0, 0)
        }
        engine.resolveRound(low)
        assertEquals(0, low.army)
    }

    @Test
    fun delayedEffectsAppearOnDueRound() {
        val s = ready(
            plan(
                first = DecisionOption(
                    text = "مدارس",
                    result = "ن",
                    later = Effects(education = 5),
                    delay = 2,
                    lineChanges = listOf(LineChange("civil", 600_000.0)),
                ),
            ),
        )
        val r1 = engine.resolveRound(s)
        assertTrue(r1.dueNotes.isEmpty())
        assertEquals(1, s.pending.size)
        assertEquals(2, s.pending.first().dueRound)
        assertEquals(15, s.education)
        val civilBefore = s.budget.lineValue("civil")

        s.plan = plan()
        s.picks = mutableListOf(0, 0, 0)
        val r2 = engine.resolveRound(s)
        assertEquals(1, r2.dueNotes.size)
        assertTrue(s.pending.isEmpty())
        assertEquals(20, s.education)
        assertTrue(s.budget.lineValue("civil") > civilBefore + 590_000.0)
    }

    @Test
    fun delayZeroAppliesImmediately() {
        val s = ready(
            plan(first = DecisionOption(text = "أ", result = "ن", later = Effects(foreign = 4), delay = 0)),
        )
        engine.resolveRound(s)
        assertTrue(s.pending.isEmpty())
        assertEquals(49, s.foreign)
    }

    @Test
    fun treasuryDepletionTriggersEmergencyLoan() {
        val s = GameState(treasury = 100_000.0).apply {
            this.plan = plan()
            this.picks = mutableListOf(0, 0, 0)
        }
        val debtBefore = s.debt
        val report = engine.resolveRound(s)
        assertEquals(0.0, s.treasury, 0.001)
        assertTrue(report.emergencyBorrowed > 0.0)
        assertTrue(s.debt > debtBefore)
        assertTrue(s.army < 45)
        assertTrue(s.legitimacy < 65)
    }

    @Test
    fun endingsEvaluateProperly() {
        assertNull(engine.evaluate(GameState()))
        assertEquals(EndingType.DEPOSED, engine.evaluate(GameState(legitimacy = 15))!!.type)
        assertEquals(EndingType.COLLAPSE, engine.evaluate(GameState(stability = 12))!!.type)
        assertEquals(EndingType.BANKRUPTCY, engine.evaluate(GameState(debt = 108_000_000.0))!!.type)
        assertEquals(EndingType.CONQUEST, engine.evaluate(GameState(army = 8, foreign = 12))!!.type)
        assertNull(engine.evaluate(GameState(army = 8, foreign = 30)))

        val n = GameEngine.totalRounds
        val gold = engine.evaluate(
            GameState(round = n, legitimacy = 80, stability = 75, army = 70, foreign = 70, education = 60, debt = 1_000_000.0),
        )
        assertNotNull(gold)
        assertTrue(gold!!.isVictory)
        assertEquals("إمبراطورية مزدهرة", gold.title)

        val solid = engine.evaluate(
            GameState(round = n, legitimacy = 55, stability = 55, army = 50, foreign = 50, education = 45, debt = 20_000_000.0),
        )
        assertEquals("دولة متماسكة", solid!!.title)

        val barely = engine.evaluate(
            GameState(round = n, legitimacy = 30, stability = 30, army = 30, foreign = 30, education = 30, debt = 60_000_000.0),
        )
        assertEquals("نجوت بالكاد", barely!!.title)

        assertEquals(
            EndingType.DEPOSED,
            engine.evaluate(GameState(round = GameEngine.totalRounds, legitimacy = 10))!!.type,
        )
    }

    @Test
    fun fullCampaignWithFakeAiStaysBounded() = runTest {
        suspend fun play(ai: FakeAi, seed: Int, maxRounds: Int = 300): GameState {
            val rnd = Random(seed)
            val s = GameState()
            for (i in 0 until maxRounds) {
                val raw = RoundParser.parse(ai.call(""))
                s.plan = Calibrator.calibrate(raw, s)
                s.picks = mutableListOf(rnd.nextInt(3), rnd.nextInt(3), rnd.nextInt(3))
                val report = engine.resolveRound(s)
                assertTrue(s.treasury >= 0.0)
                assertTrue(s.debt >= 0.0)
                for (v in listOf(s.legitimacy, s.stability, s.army, s.foreign, s.education)) {
                    assertTrue(v in 0..100)
                }
                for (l in revenueLines + spendingLines) {
                    assertTrue(s.budget.lineValue(l.key) >= 0.0)
                }
                if (report.ending != null) break
            }
            return s
        }
        for (seed in 1..25) {
            play(FakeAi(seed = seed), seed)
            play(FakeAi(seed = seed, wild = true), seed)
        }
        val finished = play(FakeAi(seed = 5), 5, maxRounds = 400)
        assertNotNull(engine.evaluate(finished))
    }
}
