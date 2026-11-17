package com.ottoman.empire1800.engine

import com.ottoman.empire1800.data.isFrontierRegionKey
import com.ottoman.empire1800.data.isProjectKey
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.WorldMemory
import com.ottoman.empire1800.models.isBudgetLine
import com.ottoman.empire1800.models.isCountryKey
import com.ottoman.empire1800.models.isPowerKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/** خطأ في شكل ما كتبه الذكاء الاصطناعي. رسالته تعاد إليه عند إعادة المحاولة. */
class PlanFormatException(override val message: String) : Exception(message)

/** يحول رد الذكاء الاصطناعي (JSON) إلى جولة خام قبل المعايرة. */
object RoundParser {

    fun parse(text: String): RoundPlan {
        val json = extractJson(text)
        val headline = str(json.opt("headline"))
        val briefing = str(json.opt("briefing"))
        if (briefing.isEmpty()) {
            throw PlanFormatException("حقل briefing (ملخص الوضع) فارغ أو مفقود.")
        }

        val rawDecisions = json.optJSONArray("decisions")
            ?: throw PlanFormatException("حقل decisions يجب أن يكون قائمة من ثلاثة قرارات.")

        val byCategory = linkedMapOf<DecisionCategory, Decision>()
        for (i in 0 until rawDecisions.length()) {
            val raw = rawDecisions.optJSONObject(i) ?: continue
            val category = DecisionCategory.fromId(raw.opt("category")) ?: continue
            if (byCategory.containsKey(category)) continue
            byCategory[category] = decision(category, raw)
        }
        for (c in DecisionCategory.entries) {
            if (!byCategory.containsKey(c)) {
                throw PlanFormatException(
                    "يجب قرار واحد لكل فئة: internal و economy و foreign. الفئة المفقودة: ${c.id}.",
                )
            }
        }

        val rawWorld = json.optJSONObject("world")
        return RoundPlan(
            headline = headline,
            briefing = briefing,
            chronicle = str(json.opt("chronicle")),
            world = if (rawWorld != null) WorldMemory.fromJson(rawWorld) else null,
            decisions = DecisionCategory.entries.map { byCategory[it]!! },
        )
    }

    private fun extractJson(text: String): JSONObject {
        var t = text.trim()
        t = t.replace(Regex("^```[a-zA-Z]*\\s*"), "").replace(Regex("\\s*```$"), "")
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw PlanFormatException("الرد ليس كائن JSON.")
        }
        return try {
            JSONObject(t.substring(start, end + 1))
        } catch (e: JSONException) {
            throw PlanFormatException("JSON غير صالح: ${e.message}")
        }
    }

    private fun decision(category: DecisionCategory, raw: JSONObject): Decision {
        val title = str(raw.opt("title"))
        val scene = str(raw.opt("scene"))
        val dilemma = str(raw.opt("dilemma"))
        if (title.isEmpty() || scene.isEmpty() || dilemma.isEmpty()) {
            throw PlanFormatException(
                "القرار ${category.id} ناقص: يلزم title و scene و dilemma.",
            )
        }
        val rawOptions = raw.optJSONArray("options")
        val options = mutableListOf<DecisionOption>()
        if (rawOptions != null) {
            for (i in 0 until rawOptions.length()) {
                val o = rawOptions.optJSONObject(i)
                if (o != null && options.size < 3) {
                    options.add(option(category, o))
                }
            }
        }
        if (options.size < 3) {
            throw PlanFormatException("القرار ${category.id} يحتاج ثلاثة خيارات بالضبط.")
        }
        return Decision(
            category = category,
            title = title,
            place = str(raw.opt("place")),
            scene = scene,
            dilemma = dilemma,
            options = options,
        )
    }

    private fun option(category: DecisionCategory, raw: JSONObject): DecisionOption {
        val text = str(raw.opt("text"))
        val result = str(raw.opt("result"))
        if (text.isEmpty() || result.isEmpty()) {
            throw PlanFormatException("خيار في القرار ${category.id} بلا text أو result.")
        }
        val changes = mutableListOf<LineChange>()
        val rawChanges = raw.optJSONArray("line_changes")
        if (rawChanges != null) {
            for (i in 0 until rawChanges.length()) {
                val c = rawChanges.optJSONObject(i) ?: continue
                val line = c.opt("line") as? String
                val delta = toNum(c.opt("delta"))
                if (line != null && delta != null) changes.add(LineChange(line.trim(), delta))
            }
        }
        return DecisionOption(
            text = text,
            result = result,
            reaction = str(raw.opt("reaction")),
            note = str(raw.opt("note")),
            cost = toNum(raw.opt("cost")) ?: 0.0,
            loan = toNum(raw.opt("loan")) ?: 0.0,
            now = effects(raw.opt("now")),
            later = effects(raw.opt("later")),
            delay = (toNum(raw.opt("delay")) ?: 0.0).roundToInt(),
            lineChanges = changes,
            relations = intMap(raw.opt("rel"), ::isCountryKey),
            powerMood = intMap(raw.opt("pow"), ::isPowerKey),
            dissolve = dissolve(raw.opt("dissolve")),
            war = key(raw.opt("war"), ::isCountryKey),
            peace = key(raw.opt("peace"), ::isCountryKey),
            project = key(raw.opt("project"), ::isProjectKey),
            conquer = key(raw.opt("conquer"), ::isFrontierRegionKey),
        )
    }

    private fun key(raw: Any?, ok: (String) -> Boolean): String {
        val s = (raw as? String)?.trim() ?: return ""
        return if (ok(s)) s else ""
    }

    private fun dissolve(raw: Any?): String {
        val items: List<Any?> = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.opt(it) }
            else -> listOf(raw)
        }
        for (x in items) {
            if (x is String && isPowerKey(x.trim())) return x.trim()
        }
        return ""
    }

    private fun intMap(raw: Any?, allowed: (String) -> Boolean): Map<String, Int> {
        val obj = raw as? JSONObject ?: return emptyMap()
        val out = linkedMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val n = toNum(obj.opt(k))
            if (allowed(k.trim()) && n != null && n.roundToInt() != 0) {
                out[k.trim()] = n.roundToInt()
            }
        }
        return out
    }

    private fun effects(raw: Any?): Effects {
        val obj = raw as? JSONObject ?: return Effects()
        fun n(k: String): Int = (toNum(obj.opt(k)) ?: 0.0).roundToInt()
        return Effects(
            legitimacy = n("legitimacy"),
            stability = n("stability"),
            army = n("army"),
            foreign = n("foreign"),
            education = n("education"),
        )
    }

    fun toNum(v: Any?): Double? {
        if (v is Number) {
            val d = v.toDouble()
            return if (d.isFinite()) d else null
        }
        if (v is String) {
            var s = v.trim()
            val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
            for (i in arabicDigits.indices) {
                s = s.replace(arabicDigits[i], ('0' + i))
            }
            s = s.replace(",", "").replace("٬", "").replace("+", "")
            val parsed = s.toDoubleOrNull()
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return null
    }

    private fun str(v: Any?): String = if (v is String) v.trim() else ""

    fun knownLine(key: String): Boolean = isBudgetLine(key)
}
