package com.ottoman.empire1800.models

import com.ottoman.empire1800.data.isFrontierRegionKey
import com.ottoman.empire1800.data.isProjectKey
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** أثر على المؤشرات الخمسة (من 0 إلى 100). */
data class Effects(
    val legitimacy: Int = 0,
    val stability: Int = 0,
    val army: Int = 0,
    val foreign: Int = 0,
    val education: Int = 0,
) {
    val isZero: Boolean
        get() = legitimacy == 0 && stability == 0 && army == 0 && foreign == 0 && education == 0

    fun clampTo(limit: Int): Effects = Effects(
        legitimacy = legitimacy.coerceIn(-limit, limit),
        stability = stability.coerceIn(-limit, limit),
        army = army.coerceIn(-limit, limit),
        foreign = foreign.coerceIn(-limit, limit),
        education = education.coerceIn(-limit, limit),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("legitimacy", legitimacy)
        put("stability", stability)
        put("army", army)
        put("foreign", foreign)
        put("education", education)
    }

    companion object {
        fun fromJson(raw: Any?): Effects {
            val obj = raw as? JSONObject ?: return Effects()
            fun n(k: String): Int {
                val v = obj.opt(k)
                return if (v is Number && v.toDouble().isFinite()) v.toDouble().roundToInt() else 0
            }
            return Effects(
                legitimacy = n("legitimacy"),
                stability = n("stability"),
                army = n("army"),
                foreign = n("foreign"),
                education = n("education"),
            )
        }
    }
}

/** تغيير سنوي في بند من الميزانية (بالقروش). الموجب يزيد البند والسالب ينقصه. */
data class LineChange(val line: String, val delta: Double) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("line", line)
        put("delta", delta)
    }

    companion object {
        fun fromJson(raw: Any?): LineChange {
            val obj = raw as? JSONObject ?: return LineChange("", 0.0)
            val line = obj.opt("line") as? String ?: return LineChange("", 0.0)
            val delta = obj.opt("delta")
            if (delta is Number && delta.toDouble().isFinite()) {
                return LineChange(line, delta.toDouble())
            }
            return LineChange("", 0.0)
        }
    }
}

/** أنواع القرارات الثلاثة في كل جولة. */
enum class DecisionCategory(val id: String, val label: String) {
    INTERNAL("internal", "سياسة داخلية"),
    ECONOMY("economy", "اقتصاد ومالية"),
    FOREIGN("foreign", "سياسة خارجية");

    companion object {
        fun fromId(raw: Any?): DecisionCategory? {
            val s = (raw as? String)?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == s }
        }
    }
}

/** خيار واحد من ثلاثة، كتبه الذكاء الاصطناعي وعايرته اللعبة. */
data class DecisionOption(
    val text: String,
    val result: String,
    val reaction: String = "",
    val note: String = "",
    val cost: Double = 0.0,
    val loan: Double = 0.0,
    val now: Effects = Effects(),
    val later: Effects = Effects(),
    val delay: Int = 0,
    val lineChanges: List<LineChange> = emptyList(),
    val relations: Map<String, Int> = emptyMap(),
    val powerMood: Map<String, Int> = emptyMap(),
    val dissolve: String = "",
    val war: String = "",
    val peace: String = "",
    val project: String = "",
    val conquer: String = "",
    val calibrated: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("result", result)
        put("reaction", reaction)
        put("note", note)
        put("cost", cost)
        put("loan", loan)
        put("now", now.toJson())
        put("later", later.toJson())
        put("delay", delay)
        put("line_changes", JSONArray().apply { lineChanges.forEach { put(it.toJson()) } })
        put("rel", JSONObject().apply { relations.forEach { (k, v) -> put(k, v) } })
        put("pow", JSONObject().apply { powerMood.forEach { (k, v) -> put(k, v) } })
        put("dissolve", dissolve)
        put("war", war)
        put("peace", peace)
        put("project", project)
        put("conquer", conquer)
        put("calibrated", calibrated)
    }

    companion object {
        fun intMap(raw: Any?, allowed: (String) -> Boolean): Map<String, Int> {
            val obj = raw as? JSONObject ?: return emptyMap()
            val out = linkedMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                if (allowed(k) && v is Number && v.toDouble().isFinite()) {
                    val r = v.toDouble().roundToInt()
                    if (r != 0) out[k] = r
                }
            }
            return out
        }

        fun fromJson(obj: JSONObject): DecisionOption {
            fun s(k: String): String = (obj.opt(k) as? String)?.trim() ?: ""
            fun d(k: String): Double {
                val v = obj.opt(k)
                return if (v is Number && v.toDouble().isFinite()) v.toDouble() else 0.0
            }

            val changes = mutableListOf<LineChange>()
            val rawChanges = obj.optJSONArray("line_changes")
            if (rawChanges != null) {
                for (i in 0 until rawChanges.length()) {
                    val lc = LineChange.fromJson(rawChanges.opt(i))
                    if (lc.line.isNotEmpty()) changes.add(lc)
                }
            }
            val delayVal = obj.opt("delay")
            val delay = if (delayVal is Number && delayVal.toDouble().isFinite()) {
                delayVal.toDouble().roundToInt()
            } else {
                0
            }
            val dis = s("dissolve")
            val w = s("war")
            val p = s("peace")
            val pr = s("project")
            val cq = s("conquer")
            return DecisionOption(
                text = s("text"),
                result = s("result"),
                reaction = s("reaction"),
                note = s("note"),
                cost = d("cost"),
                loan = d("loan"),
                now = Effects.fromJson(obj.opt("now")),
                later = Effects.fromJson(obj.opt("later")),
                delay = delay,
                lineChanges = changes,
                relations = intMap(obj.opt("rel"), ::isCountryKey),
                powerMood = intMap(obj.opt("pow"), ::isPowerKey),
                dissolve = if (isPowerKey(dis)) dis else "",
                war = if (isCountryKey(w)) w else "",
                peace = if (isCountryKey(p)) p else "",
                project = if (isProjectKey(pr)) pr else "",
                conquer = if (isFrontierRegionKey(cq)) cq else "",
                calibrated = obj.optBoolean("calibrated", false),
            )
        }
    }
}

data class Decision(
    val category: DecisionCategory,
    val title: String,
    val place: String = "",
    val scene: String,
    val dilemma: String,
    val options: List<DecisionOption>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("category", category.id)
        put("title", title)
        put("place", place)
        put("scene", scene)
        put("dilemma", dilemma)
        put("options", JSONArray().apply { options.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): Decision {
            fun s(k: String): String = (obj.opt(k) as? String)?.trim() ?: ""
            val options = mutableListOf<DecisionOption>()
            val raw = obj.optJSONArray("options")
            if (raw != null) {
                for (i in 0 until raw.length()) {
                    val o = raw.optJSONObject(i)
                    if (o != null) options.add(DecisionOption.fromJson(o))
                }
            }
            return Decision(
                category = DecisionCategory.fromId(obj.opt("category")) ?: DecisionCategory.INTERNAL,
                title = s("title"),
                place = s("place"),
                scene = s("scene"),
                dilemma = s("dilemma"),
                options = options,
            )
        }
    }
}

/** جولة نصف سنوية: ثلاثة قرارات كتبها الذكاء الاصطناعي. */
data class RoundPlan(
    val headline: String,
    val briefing: String,
    val chronicle: String = "",
    val world: WorldMemory? = null,
    val decisions: List<Decision>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("headline", headline)
        put("briefing", briefing)
        put("chronicle", chronicle)
        put("world", world?.toJson() ?: JSONObject.NULL)
        put("decisions", JSONArray().apply { decisions.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): RoundPlan {
            fun s(k: String): String = (obj.opt(k) as? String)?.trim() ?: ""
            val decisions = mutableListOf<Decision>()
            val raw = obj.optJSONArray("decisions")
            if (raw != null) {
                for (i in 0 until raw.length()) {
                    val d = raw.optJSONObject(i)
                    if (d != null) decisions.add(Decision.fromJson(d))
                }
            }
            val rawWorld = obj.optJSONObject("world")
            return RoundPlan(
                headline = s("headline"),
                briefing = s("briefing"),
                chronicle = s("chronicle"),
                world = if (rawWorld != null) WorldMemory.fromJson(rawWorld) else null,
                decisions = decisions,
            )
        }
    }
}

fun describeLineChange(c: LineChange): String = budgetLineLabel(c.line)
