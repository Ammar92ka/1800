package com.ottoman.empire1800

import com.ottoman.empire1800.data.projectCatalog
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Difficulty
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.services.ClimateRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.support.neutralPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CampaignAndPaceTest {
    private val engine = GameEngine()

    @Test
    fun difficultyMultipliersMatchSpec() {
        val easy = Difficulty(0)
        val normal = Difficulty(1)
        val hard = Difficulty(2)

        assertEquals(1.06, easy.revenue, 1e-6)
        assertEquals(0.7, easy.events, 1e-6)
        assertEquals(0.8, easy.losses, 1e-6)

        assertEquals(1.0, normal.revenue, 1e-6)
        assertEquals(1.0, normal.events, 1e-6)
        assertEquals(1.0, normal.losses, 1e-6)

        assertEquals(0.94, hard.revenue, 1e-6)
        assertEquals(1.3, hard.events, 1e-6)
        assertEquals(1.25, hard.losses, 1e-6)

        // Harder losses amplify negative effects
        val sEasy = GameState(difficulty = 0).apply {
            plan = neutralPlan()
            plan!!.decisions[0].options[0] = DecisionOption(text = "ت", result = "ن", now = Effects(legitimacy = -8))
            picks = mutableListOf(0, 0, 0)
        }
        val sHard = GameState(difficulty = 2).apply {
            plan = neutralPlan()
            plan!!.decisions[0].options[0] = DecisionOption(text = "ت", result = "ن", now = Effects(legitimacy = -8))
            picks = mutableListOf(0, 0, 0)
        }
        engine.resolveRound(sEasy)
        engine.resolveRound(sHard)
        assertTrue(sEasy.legitimacy > sHard.legitimacy)
    }

    @Test
    fun quarterPacePpy4LabelsCourtActionsAndYearDuration() {
        val q = GameState(ppy = 4)
        assertEquals(1, q.courtActions)
        assertEquals(0.5, q.f, 1e-6)
        assertEquals(2, q.k)
        assertEquals(444, GameEngine.totalRoundsFor(4))
        assertEquals(1800, GameEngine.yearOf(3, 4))
        assertEquals(1801, GameEngine.yearOf(4, 4))
        assertEquals("نيسان - حزيران 1800", GameEngine.periodLabel(1, 4))

        // Calibrator scales f=0.5 caps properly
        val opt = DecisionOption(
            text = "ت",
            result = "ن",
            cost = 20_000_000.0,
            now = Effects(legitimacy = 8),
            delay = 2,
            lineChanges = listOf(LineChange("land", 1_000_000.0)),
        )
        val calHalf = Calibrator.calibrateOption(opt, 18_000_000.0, f = 1.0)
        val calQuarter = Calibrator.calibrateOption(opt, 18_000_000.0, f = 0.5)
        assertEquals(calHalf.cost * 0.5, calQuarter.cost, 1.0)
        assertEquals(4, calQuarter.now.legitimacy)
        assertTrue(calQuarter.calibrated)

        // Two quarters vs one half-year give comparable treasury trajectory under neutral choices
        val s2 = GameState(ppy = 2, treasury = 20_000_000.0)
        val s4 = GameState(ppy = 4, treasury = 20_000_000.0)
        s2.plan = neutralPlan(); s2.picks = mutableListOf(0, 0, 0)
        val r2 = engine.resolveRound(s2)

        s4.plan = neutralPlan(); s4.picks = mutableListOf(0, 0, 0)
        val r4a = engine.resolveRound(s4)
        s4.plan = neutralPlan(); s4.picks = mutableListOf(0, 0, 0)
        val r4b = engine.resolveRound(s4)

        assertEquals(1, s4.courtActions)
        assertEquals(2, s2.courtActions)
        assertTrue(abs((r4a.revenue + r4b.revenue) - r2.revenue) < r2.revenue * 0.05)
    }

    @Test
    fun nationalProjectsCatalogInstallmentsPauseAndCompletion() {
        assertEquals(26, projectCatalog.size)
        val s = GameState(treasury = 50_000_000.0)
        val printPress = projectDef("printing")!!
        val early = projectDef("ottoman_bank")!! // fromYear = 1855
        assertTrue(ProjectRules.isAvailable(s, printPress))
        assertFalse(ProjectRules.isAvailable(s, early))

        // Faster schedule costs more overall due to RUSH_PREMIUM
        val slowCost = ProjectRules.totalCost(s, printPress, printPress.maxHalves)
        val fastCost = ProjectRules.totalCost(s, printPress, printPress.minHalves)
        assertTrue(fastCost > slowCost)
        assertNotNull(ProjectRules.paybackYears(s, projectDef("irrigation")!!, 6))
        assertNull(ProjectRules.paybackYears(s, printPress, 4))

        val started = ProjectRules.start(s, "printing", printPress.minHalves)
        assertTrue(started.ok)
        assertNotNull(s.project)

        // Pausing skips installment payment and clears stalled
        ProjectRules.setPaused(s, true)
        val pausedStep = ProjectRules.fund(s)
        assertEquals(0.0, pausedStep.spent, 0.01)
        assertEquals(0, s.project!!.funded)

        // Insufficient treasury increments stalled
        ProjectRules.setPaused(s, false)
        val savedTreasury = s.treasury
        s.treasury = 1.0
        val stalledStep = ProjectRules.fund(s)
        assertEquals(0.0, stalledStep.spent, 0.01)
        assertEquals(1, s.project!!.stalled)
        assertTrue(stalledStep.notes.any { it.contains("تعثر") })

        // Restore treasury and complete remaining installments
        s.treasury = savedTreasury
        val eduBefore = s.education
        while (s.project != null) {
            val step = ProjectRules.fund(s)
            assertTrue(step.spent > 0.0)
        }
        assertTrue(ProjectRules.has(s, "printing"))
        assertTrue(s.education > eduBefore)
    }

    @Test
    fun projectSuggestionsPrioritizeCurrentCrises() {
        val s = GameState(treasury = 100_000.0, army = 20, education = 15)
        s.econ.grain = 160.0
        s.econ.corruption = 70.0
        s.econ.inflation = 8.0
        val suggestions = ProjectRules.suggestions(s, limit = 3)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.all { it.reason.isNotEmpty() })
    }

    @Test
    fun foreignEconomiesGrowDeterministicallyOverTime() {
        val a = GameState(seed = 42)
        val b = GameState(seed = 42)
        val britainStart = a.countries["britain"]!!.economy
        for (i in 0 until 20) {
            a.round = i
            b.round = i
            ForeignRules.step(a)
            ForeignRules.step(b)
        }
        assertTrue(a.countries["britain"]!!.economy > britainStart)
        assertEquals(a.countries["britain"]!!.economy, b.countries["britain"]!!.economy, 1e-6)
        assertTrue(ForeignRules.ottomanEconomy(a) > 0.0)
        assertEquals(
            EconomyRules.halfYearRevenue(a.budget, a.stability, econ = a.econ),
            EconomyRules.halfYearRevenue(b.budget, b.stability, econ = b.econ),
            1e-6,
        )
    }

    @Test
    fun emergencyBorrowingAppliesIntegerPenaltiesEveryKthQuarterInPpy4() {
        val s = GameState(ppy = 4, treasury = 100_000.0)
        val plan = neutralPlan()
        plan.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0)
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)

        val legStart = s.legitimacy
        val armyStart = s.army
        val rep1 = engine.resolveRound(s)
        assertTrue(rep1.emergencyBorrowed > 0.0)
        assertEquals(1, s.emergencyStreak)
        assertEquals(legStart, s.legitimacy)
        assertEquals(armyStart, s.army)
        assertEquals(4, rep1.ppy)

        val plan2 = neutralPlan()
        plan2.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0)
        s.plan = plan2
        s.picks = mutableListOf(0, 0, 0)
        val rep2 = engine.resolveRound(s)
        assertTrue(rep2.emergencyBorrowed > 0.0)
        assertEquals(2, s.emergencyStreak)
        assertEquals(legStart - 1, s.legitimacy)
        assertEquals(armyStart - 2, s.army)
    }

    @Test
    fun estimateRoundTreasuryAndDynamicPeriodWordingMatchRules() {
        val s = GameState(ppy = 4, treasury = 500_000.0)
        val plan = neutralPlan()
        plan.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 1_500_000.0)
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)

        val proj = GameEngine.estimateRoundTreasury(s)
        assertTrue(proj.willBorrowEmergency)
        assertTrue(proj.emergencyShortfall > 0.0)
        assertTrue(proj.estimatedEmergencyLoan > proj.emergencyShortfall)

        s.courtActions = 0
        assertEquals("استنفدت إجراءات الديوان لربع السنة هذا", PowerRules.appease(s, "janissaries").message)
    }

    @Test
    fun climateHarvestLastsFullTwoQuartersWhenTriggeredOnOddRound() {
        var foundSeed = -1
        for (seed in 1800 until 2500) {
            val probe = GameState(seed = seed, ppy = 4, round = 9)
            ClimateRules.step(probe)
            if (probe.econ.provinces.values.any { it.harvest != 1.0 }) {
                foundSeed = seed
                break
            }
        }
        assertTrue(foundSeed >= 1800)

        val s = GameState(seed = foundSeed, ppy = 4, round = 9)
        ClimateRules.step(s)
        val hit = s.econ.provinces.values.first { it.harvest != 1.0 }
        assertEquals(2, hit.harvestRounds)

        s.round = 0 // grace avoids triggering another shock while counting down
        ClimateRules.step(s)
        assertTrue(hit.harvest != 1.0)
        assertEquals(1, hit.harvestRounds)

        ClimateRules.step(s)
        assertEquals(1.0, hit.harvest, 1e-6)
        assertEquals(0, hit.harvestRounds)
    }
}
