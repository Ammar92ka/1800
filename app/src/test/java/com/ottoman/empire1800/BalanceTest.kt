package com.ottoman.empire1800

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.support.playBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTest {

    @Test
    fun sameSeedProducesIdenticalCampaignOutcomes() {
        val a = GameState(seed = 77)
        val b = GameState(seed = 77)
        val runA = playBot(a, prudent = true, maxRounds = 40)
        val runB = playBot(b, prudent = true, maxRounds = 40)
        assertEquals(runA.rounds, runB.rounds)
        assertEquals(a.treasury, b.treasury, 0.01)
        assertEquals(a.debt, b.debt, 0.01)
        assertEquals(a.legitimacy, b.legitimacy)
        assertEquals(a.stability, b.stability)
    }

    @Test
    fun noopBotSurvivesEarlyYearsInBothPaces() {
        val half = playBot(GameState(seed = 5, ppy = 2), prudent = false, maxRounds = 40)
        assertTrue("Expected ppy=2 no-op survival >=24, got ${half.rounds}", half.rounds >= 24)

        val quarter = playBot(GameState(seed = 5, ppy = 4), prudent = false, maxRounds = 60)
        assertTrue("Expected ppy=4 no-op survival >=40, got ${quarter.rounds}", quarter.rounds >= 40)
    }

    @Test
    fun prudentBotSurvivesLongTermAcrossMostSeeds() {
        var survived100 = 0
        for (seed in 1..10) {
            val res = playBot(GameState(seed = seed), prudent = true, maxRounds = 100)
            if (res.rounds >= 100 || res.ending?.isVictory == true) {
                survived100++
            }
        }
        assertTrue("Expected >=8/10 seeds to survive 100 rounds, got $survived100", survived100 >= 8)
    }
}
