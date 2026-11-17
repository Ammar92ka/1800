package com.ottoman.empire1800.support

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class FakeAi(seed: Int = 1, val wild: Boolean = false) {
    private val rnd = Random(seed)
    var calls: Int = 0

    private fun effects(scale: Int): JSONObject = JSONObject().apply {
        put("legitimacy", rnd.nextInt(scale * 2 + 1) - scale)
        put("stability", rnd.nextInt(scale * 2 + 1) - scale)
        put("army", rnd.nextInt(scale * 2 + 1) - scale)
        put("foreign", rnd.nextInt(scale * 2 + 1) - scale)
        put("education", rnd.nextInt(scale * 2 + 1) - scale)
    }

    private fun option(name: String): JSONObject {
        val big = if (wild) 50 else 1
        val lines = listOf("customs", "land", "provinces", "other", "army", "palace", "admin", "civil")
        return JSONObject().apply {
            put("text", "خيار $name")
            put("result", "نتيجة $name")
            put("reaction", "رد فعل $name")
            put("note", "أساس $name")
            put("cost", rnd.nextInt(3_000_000) * big)
            put("loan", if (rnd.nextInt(4) == 0) rnd.nextInt(4_000_000) * big else 0)
            put("now", effects(if (wild) 30 else 4))
            put("later", effects(if (wild) 40 else 6))
            put("delay", rnd.nextInt(if (wild) 40 else 6))
            put(
                "line_changes",
                JSONArray().put(
                    JSONObject().apply {
                        put("line", lines[rnd.nextInt(lines.size)])
                        put("delta", (rnd.nextInt(2_000_000) - 1_000_000) * big)
                    },
                ),
            )
        }
    }

    private fun decision(category: String): JSONObject = JSONObject().apply {
        put("category", category)
        put("title", "قرار $category")
        put("place", "إسطنبول")
        put("scene", "مشهد $category")
        put("dilemma", "معضلة $category")
        put(
            "options",
            JSONArray().apply {
                put(option("أ"))
                put(option("ب"))
                put(option("ج"))
            },
        )
    }

    suspend fun call(@Suppress("UNUSED_PARAMETER") prompt: String): String {
        calls++
        return JSONObject().apply {
            put("headline", "عنوان")
            put("briefing", "ملخص الوضع")
            put("chronicle", "ذاكرة العالم")
            put(
                "decisions",
                JSONArray().apply {
                    put(decision("internal"))
                    put(decision("economy"))
                    put(decision("foreign"))
                },
            )
        }.toString()
    }
}
