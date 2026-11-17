package com.ottoman.empire1800

import com.ottoman.empire1800.engine.EndingType
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.WarRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarsAndDebtTest {

    @Test
    fun warsEnforceMaxTwoComputePowerAndRequireMinimumHalvesForPeace() {
        val s = GameState(treasury = 20_000_000.0, army = 70, education = 40, stability = 80)
        assertTrue(WarRules.ourPower(s) > 70.0)
        assertTrue(WarRules.enemyPower(s.countries["russia"]!!) > 0.0)

        assertTrue(WarRules.declare(s, "russia", byUs = true).isNotEmpty())
        assertTrue(WarRules.declare(s, "persia", byUs = true).isNotEmpty())
        assertEquals("", WarRules.declare(s, "austria", byUs = true))
        assertEquals(WarRules.MAX_WARS, s.wars.size)
        assertTrue(WarRules.costs(s) > 0.0)

        // Cannot negotiate in early halves
        val tooEarly = WarRules.seekPeace(s, "russia")
        assertFalse(tooEarly.ok)

        s.wars.first().halves = 2
        val peace = WarRules.seekPeace(s, "russia")
        assertTrue(peace.ok)
        assertEquals(1, s.wars.size)
    }

    @Test
    fun humiliatingDefeatCedesFrontProvinceAndLongWarExhausts() {
        val s = GameState(treasury = 10_000_000.0)
        WarRules.declare(s, "russia", byUs = false)
        val w = WarRules.warWith(s, "russia")!!
        w.halves = 4
        w.score = -80.0
        val res = WarRules.seekPeace(s, "russia")
        assertTrue(res.ok)
        assertTrue(s.wars.isEmpty())
        assertTrue(s.econ.provinces["rumelia"]!!.isLost)
        assertTrue(s.foreignDebt > 0.0)

        // Exhaustion after EXHAUSTION_HALVES
        WarRules.declare(s, "persia", byUs = true)
        val w2 = WarRules.warWith(s, "persia")!!
        w2.halves = WarRules.EXHAUSTION_HALVES - 1
        s.round = 20
        WarRules.step(s)
        assertNullOrEmptyWars(s)
    }

    private fun assertNullOrEmptyWars(s: GameState) {
        assertTrue(s.wars.isEmpty())
    }

    @Test
    fun foreignDebtStartsFrom1854TriggersDefaultMoratoriumDadAndExit() {
        val s = GameState(round = 0)
        DebtRules.addLoan(s, 1_000_000.0)
        assertEquals(0.0, s.foreignDebt, 0.01)

        // Jump to 1854 (round 108 in ppy=2)
        s.round = 108
        DebtRules.addLoan(s, 10_000_000.0)
        assertEquals(7_000_000.0, s.foreignDebt, 0.01)
        assertEquals(9, DebtRules.ratingFactors(s).size)

        // Severe emergency streak causes default -> moratorium (zero foreign interest) -> DAD -> exit
        s.foreignDebt = 15_000_000.0
        s.debt = 20_000_000.0
        s.emergencyStreak = 3
        val defaultNotes = DebtRules.step(s)
        assertTrue(defaultNotes.isNotEmpty())
        assertEquals(108, s.defaultRound)
        assertTrue(DebtRules.inMoratorium(s))
        assertEquals(
            DebtRules.halfYearInterest(s),
            DebtRules.halfYearInterest(s.copy().apply { foreignDebt = 0.0 }),
            1.0,
        )

        // After MORATORIUM_HALVES, DAD activates
        s.round = 108 + DebtRules.MORATORIUM_HALVES
        assertFalse(DebtRules.inMoratorium(s))
        val dadNotes = DebtRules.step(s)
        assertTrue(s.dadActive)
        assertTrue(dadNotes.any { it.contains("إدارة للديون") })

        val dadPaid = DebtRules.dadPayment(s, 9_000_000.0)
        assertTrue(dadPaid > 0.0)

        // Paying down foreignDebt below 0.2 * rev dissolves DAD
        s.foreignDebt = 1_000_000.0
        val exitNotes = DebtRules.step(s)
        assertFalse(s.dadActive)
        assertTrue(exitNotes.any { it.contains("أنهت") })
    }

    @Test
    fun provincesTransitionThroughAutonomyAndDismembermentEnding() {
        val s = GameState(treasury = 10_000_000.0)
        val syria = s.econ.provinces["syria"]!!
        syria.mood = 20.0
        syria.autonomy = 75.0
        ProvinceRules.step(s)
        assertEquals(ProvinceState.AUTONOMOUS_STATUS, syria.status)

        assertTrue(ProvinceRules.privileges(s, "syria").ok)
        assertTrue(syria.mood > 20.0)
        assertTrue(ProvinceRules.suppress(s, "syria").ok)

        // Losing core high-weight provinces triggers DISMEMBERED ending
        val engine = GameEngine()
        for (k in listOf("rumelia", "egypt", "syria", "iraq", "hijaz")) {
            ProvinceRules.lose(s, s.econ.provinces[k]!!, how = "independence")
        }
        val ending = engine.evaluate(s)
        assertNotNull(ending)
        assertEquals(EndingType.DISMEMBERED, ending!!.type)
    }
}
