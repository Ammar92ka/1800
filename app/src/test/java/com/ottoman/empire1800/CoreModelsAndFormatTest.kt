package com.ottoman.empire1800

import com.ottoman.empire1800.models.Budget
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.EconomyState
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.HistoryEntry
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.PendingEffect
import com.ottoman.empire1800.models.PowerProject
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.SpyReport
import com.ottoman.empire1800.models.StabilityState
import com.ottoman.empire1800.models.WorldMemory
import com.ottoman.empire1800.models.isPowerKey
import com.ottoman.empire1800.models.powerDefs
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.ui.util.formatMoney
import com.ottoman.empire1800.ui.util.formatMoneySigned
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelsAndFormatTest {
    private fun samplePlan() = RoundPlan(
        headline = "عنوان",
        briefing = "ملخص",
        chronicle = "ذاكرة",
        decisions = DecisionCategory.entries.map { c ->
            Decision(
                category = c,
                title = "قرار ${c.id}",
                scene = "مشهد",
                dilemma = "معضلة",
                options = List(3) { i ->
                    DecisionOption(
                        text = "خيار $i",
                        result = "نتيجة $i",
                        cost = 1000.0 * i,
                        later = Effects(education = 3),
                        delay = 2,
                        lineChanges = listOf(LineChange("civil", 500.0)),
                    )
                },
            )
        },
    )

    @Test
    fun gameStateSaveAndRestoreRoundTrip() {
        val s = GameState(
            treasury = 1_234_567.0,
            debt = 7_000_000.0,
            legitimacy = 71,
            stability = 48,
            army = 52,
            foreign = 33,
            education = 21,
            round = 24,
            chronicle = "ذاكرة العالم",
            lastReport = "تقرير",
            plan = samplePlan(),
            picks = mutableListOf(1, null, 2),
            pending = mutableListOf(
                PendingEffect(
                    dueRound = 26,
                    effects = Effects(army = 4),
                    lines = listOf(LineChange("army", 100_000.0)),
                    source = "مدرسة",
                ),
            ),
            history = mutableListOf(
                HistoryEntry(round = 23, category = "اقتصاد", title = "عنوان", choice = "خيار"),
            ),
        )
        val back = GameState.fromJson(s.toJson())
        assertEquals(1_234_567.0, back.treasury, 0.01)
        assertEquals(7_000_000.0, back.debt, 0.01)
        assertEquals(71, back.legitimacy)
        assertEquals(33, back.foreign)
        assertEquals(24, back.round)
        assertEquals("ذاكرة العالم", back.chronicle)
        assertNotNull(back.plan)
        assertEquals(3, back.plan!!.decisions[2].options[1].later.education)
        assertEquals("civil", back.plan!!.decisions[0].options[2].lineChanges.first().line)
        assertEquals(1, back.picks[0])
        assertNull(back.picks[1])
        assertEquals(2, back.picks[2])
        assertEquals(1, back.pending.size)
        assertEquals(1, back.history.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSchemaThrows() {
        GameState.fromJson(JSONObject().put("schema", 1))
    }

    @Test
    fun schemaV2AndV3UpgradeSafely() {
        val old2 = GameState(treasury = 777.0, debt = 888.0, legitimacy = 40, stability = 33, army = 22).toJson()
        for (k in listOf("stab", "powers", "projects", "econ", "countries", "domesticSpy", "intel", "demog", "world", "courtActions", "repay", "courtLog", "events", "seed")) {
            old2.remove(k)
        }
        old2.put("schema", 2)
        val s2 = GameState.fromJson(old2)
        assertEquals(777.0, s2.treasury, 0.01)
        assertEquals(40, s2.legitimacy)
        assertEquals(33, s2.stability)
        assertEquals(8, s2.powers.size)
        assertEquals(6, s2.countries.size)
        assertEquals(1.0, s2.econ.revenueMultiplier, 1e-6)
    }

    @Test
    fun stabilityAndPowersAndEconomyDefaults() {
        val s = GameState()
        assertEquals(55, s.stability)
        assertEquals(listOf(50, 55, 55, 60, 65, 45), StabilityState.keys.map { Math.round(s.stab[it]).toInt() })
        assertEquals(8, powerDefs.size)
        assertTrue(isPowerKey("ulema"))
        assertFalse(isPowerKey("nobody"))
        assertEquals(1.0, EconomyState().revenueMultiplier, 1e-9)
        assertEquals(0.01, EconomyState().nominalGrowth, 1e-12)
    }

    @Test
    fun budgetInitialAndEconomyRules() {
        val b = Budget.initial1800()
        assertEquals(18_000_000.0, b.totalRevenue, 0.01)
        assertEquals(17_000_000.0, b.totalSpending, 0.01)
        assertEquals(0.9325, EconomyRules.collectionEfficiency(55), 1e-9)
        assertEquals(8_392_500.0, EconomyRules.halfYearRevenue(b, 55), 0.01)
        assertEquals(-2, EconomyRules.armyDrift(0.25))
        assertEquals(1, EconomyRules.educationDrift(0.12))
    }

    @Test
    fun systemsAndWorldMemoryCopyCleanly() {
        val s = GameState(seed = 77, courtActions = 1, repay = 123_456.0, domesticSpy = 2)
        s.projects.add(PowerProject("tribes", "contain", 4))
        s.intel.add(SpyReport(round = 3, target = "russia", claim = "ادعاء", reliability = 75, truth = true))
        s.world = WorldMemory(wars = listOf("حرب مع روسيا"))
        val c = s.copy()
        assertEquals(77, c.seed)
        assertEquals(1, c.projects.size)
        assertEquals("ادعاء", c.intel.first().claim)
        assertEquals(listOf("حرب مع روسيا"), c.world.wars)
    }

    @Test
    fun formatMoneyAndSignedWorkProperly() {
        assertEquals("3 مليون", formatMoney(3_000_000))
        assertEquals("2.5 مليون", formatMoney(2_500_000))
        assertEquals("18.18 مليون", formatMoney(18_180_000))
        assertEquals("800 ألف", formatMoney(800_000))
        assertEquals("1.5 ألف", formatMoney(1_500))
        assertEquals("950", formatMoney(950))
        assertEquals("0", formatMoney(0))
        assertEquals("2 مليار", formatMoney(2_000_000_000L))
        assertEquals("+1.5 مليون", formatMoneySigned(1_500_000))
        assertEquals("-250 ألف", formatMoneySigned(-250_000))
        assertEquals("0", formatMoneySigned(0))
    }

    @Test
    fun picksAndSeriesSanitizationOnRestore() {
        val json = GameState(plan = samplePlan(), picks = mutableListOf(0, 1, 2)).toJson()
        json.put("picks", JSONArray(listOf(0, 9, -1)))
        val s = GameState.fromJson(json)
        assertEquals(0, s.picks[0])
        assertNull(s.picks[1])
        assertNull(s.picks[2])
    }
}
