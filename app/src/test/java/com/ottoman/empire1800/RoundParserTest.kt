package com.ottoman.empire1800

import com.ottoman.empire1800.engine.PlanFormatException
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.support.FakeAi
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundParserTest {
    private suspend fun validJson(): JSONObject = JSONObject(FakeAi().call(""))

    @Test
    fun parsesAndOrdersCategoriesProperly() = runTest {
        val json = validJson()
        val arr = json.getJSONArray("decisions")
        val reversed = JSONArray().apply {
            for (i in arr.length() - 1 downTo 0) put(arr.get(i))
        }
        json.put("decisions", reversed)
        val plan = RoundParser.parse(json.toString())
        assertEquals(3, plan.decisions.size)
        assertEquals(DecisionCategory.INTERNAL, plan.decisions[0].category)
        assertEquals(DecisionCategory.ECONOMY, plan.decisions[1].category)
        assertEquals(DecisionCategory.FOREIGN, plan.decisions[2].category)
        assertEquals(3, plan.decisions.first().options.size)
    }

    @Test
    fun handlesCodeFencesAndSurroundingText() = runTest {
        val raw = validJson().toString()
        assertEquals(3, RoundParser.parse("```json\n$raw\n```").decisions.size)
        assertEquals(3, RoundParser.parse("تفضل: $raw انتهى").decisions.size)
    }

    @Test
    fun parsesStringAndArabicIndicNumbers() {
        assertEquals(2_500_000.0, RoundParser.toNum("2,500,000")!!, 0.01)
        assertEquals(3_000.0, RoundParser.toNum("٣٠٠٠")!!, 0.01)
        assertEquals(5.0, RoundParser.toNum("+5")!!, 0.01)
        assertNull(RoundParser.toNum("abc"))
        assertNull(RoundParser.toNum(null))
    }

    @Test(expected = PlanFormatException::class)
    fun invalidJsonThrowsPlanFormatException() {
        RoundParser.parse("لا يوجد شيء")
    }

    @Test
    fun parsesRelPowDissolveAndWorldMemorySafely() {
        fun opt(extra: String) =
            """{"text":"خيار","result":"نتيجة","cost":0,"loan":0,"delay":0$extra}"""
        fun dec(cat: String, extra: String) =
            """{"category":"$cat","title":"ع","scene":"م","dilemma":"م","options":[${opt(extra)},${opt("")},${opt("")}]}"""
        fun wrap(extra: String, top: String = "") =
            """{"headline":"ع","briefing":"م"$top,"decisions":[${dec("internal", extra)},${dec("economy", "")},${dec("foreign", "")}]}"""

        val raw = RoundParser.parse(
            wrap(""","rel":{"britain":4,"mars":9,"russia":0},"pow":{"ulema":-6,"nobody":2},"dissolve":"janissaries""""),
        )
        val o = raw.decisions.first().options.first()
        assertEquals(mapOf("britain" to 4), o.relations)
        assertEquals(mapOf("ulema" to -6), o.powerMood)
        assertEquals("janissaries", o.dissolve)

        assertEquals(
            "mamluks",
            RoundParser.parse(wrap(""","dissolve":["x","mamluks"]""")).decisions.first().options.first().dissolve,
        )
        assertEquals(
            "",
            RoundParser.parse(wrap(""","dissolve":"???"""")).decisions.first().options.first().dissolve,
        )
        assertTrue(
            RoundParser.parse(wrap(""","rel":"bad","pow":[1]""")).decisions.first().options.first().relations.isEmpty(),
        )

        val withWorld = RoundParser.parse(wrap("", top = ""","world":{"wars":["حرب مع روسيا"],"alliances":[],"promises":["وعد"]}"""))
        assertNotNull(withWorld.world)
        assertEquals(listOf("حرب مع روسيا"), withWorld.world!!.wars)
        assertEquals(listOf("وعد"), withWorld.world!!.promises)
        assertNull(RoundParser.parse(wrap("")).world)
    }
}
