package com.ottoman.empire1800.models

import com.ottoman.empire1800.data.FrontierRegionDef
import com.ottoman.empire1800.data.economicSystemDefs
import com.ottoman.empire1800.data.factoryDef
import com.ottoman.empire1800.data.frontierRegionDef
import com.ottoman.empire1800.data.frontierRegionDefs
import com.ottoman.empire1800.data.subProvinceDef
import com.ottoman.empire1800.data.subProvinceDefs
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

fun clampD(v: Number, lo: Double, hi: Double): Double {
    val d = v.toDouble()
    if (!d.isFinite()) return lo
    return d.coerceIn(lo, hi)
}

private fun numOpt(v: Any?, d: Double): Double =
    if (v is Number && v.toDouble().isFinite()) v.toDouble() else d

private fun intOpt(v: Any?, d: Int): Int =
    if (v is Number && v.toDouble().isFinite()) v.toDouble().roundToInt() else d

private fun strOpt(v: Any?): String = v as? String ?: ""
private fun boolOpt(v: Any?, d: Boolean): Boolean = v as? Boolean ?: d

// ───────────────────────── الاستقرار ─────────────────────────

/** الاستقرار الكلي هو متوسط ستة أبعاد، وكل بعد يتحرك بأسبابه. */
class StabilityState private constructor(private val values: MutableMap<String, Double>) {

    operator fun get(k: String): Double = values[k] ?: 55.0

    fun shift(k: String, d: Double) {
        if (values.containsKey(k)) {
            values[k] = clampD((values[k] ?: 55.0) + d, 0.0, 100.0)
        }
    }

    fun shiftAll(d: Double) {
        for (k in keys) {
            values[k] = clampD((values[k] ?: 55.0) + d, 0.0, 100.0)
        }
    }

    val total: Double get() = keys.sumOf { values[it] ?: 55.0 } / keys.size
    val rounded: Int get() = total.roundToInt()

    /** عدد الأبعاد التي هبطت إلى [threshold] أو أقل. */
    fun countBelow(threshold: Double): Int = keys.count { (values[it] ?: 55.0) <= threshold }

    /** أضعف بعد (للتنبيه في الواجهة). */
    val weakest: String
        get() {
            var best = keys.first()
            for (k in keys) {
                if ((values[k] ?: 55.0) < (values[best] ?: 55.0)) best = k
            }
            return best
        }

    fun toJson(): JSONObject = JSONObject().apply {
        for (k in keys) put(k, values[k] ?: 55.0)
    }

    fun copy(): StabilityState = StabilityState(values.toMutableMap())

    companion object {
        val keys: List<String> = listOf(
            "economic",
            "social",
            "military",
            "political",
            "religious",
            "regional",
        )

        val labels: Map<String, String> = mapOf(
            "economic" to "اقتصادي",
            "social" to "اجتماعي",
            "military" to "عسكري",
            "political" to "سياسي",
            "religious" to "ديني",
            "regional" to "إقليمي",
        )

        /** فروق البدء عن المتوسط (مجموعها صفر). */
        val offsets: Map<String, Double> = mapOf(
            "economic" to -5.0,
            "social" to 0.0,
            "military" to 0.0,
            "political" to 5.0,
            "religious" to 10.0,
            "regional" to -10.0,
        )

        fun fromTotal(total: Int): StabilityState {
            val t = total.coerceIn(0, 100)
            val scale = (min(t, 100 - t) / 10.0).coerceIn(0.0, 1.0)
            val map = linkedMapOf<String, Double>()
            for (k in keys) {
                map[k] = clampD(t + (offsets[k] ?: 0.0) * scale, 0.0, 100.0)
            }
            return StabilityState(map)
        }

        fun fromJson(raw: Any?, fallbackTotal: Int): StabilityState {
            val obj = raw as? JSONObject ?: return fromTotal(fallbackTotal)
            val map = linkedMapOf<String, Double>()
            for (k in keys) {
                map[k] = clampD(numOpt(obj.opt(k), fallbackTotal.toDouble()), 0.0, 100.0)
            }
            return StabilityState(map)
        }
    }
}

// ───────────────────────── قوى الدولة ─────────────────────────

data class PowerDef(
    val key: String,
    val name: String,
    val about: String,
    val militancy: Double,
    val influence: Double,
    val satisfaction: Double,
)

val powerDefs: List<PowerDef> = listOf(
    PowerDef("janissaries", "الانكشارية", "جند العاصمة وتجارها؛ يخلعون السلاطين إذا غضبوا", 1.1, 90.0, 35.0),
    PowerDef("ulema", "العلماء", "القضاء والتعليم والفتوى وأوقاف ضخمة", 0.65, 80.0, 60.0),
    PowerDef("governors", "الأعيان والولاة", "أعيان الأناضول والروملي وولاة الأطراف", 0.8, 70.0, 50.0),
    PowerDef("tax_farmers", "الملتزمون والصرافون", "ملتزمو الضرائب والمقرضون في العاصمة", 0.75, 65.0, 45.0),
    PowerDef("tribes", "العشائر", "عشائر البادية وأطراف الشام والعراق والحجاز", 0.7, 50.0, 55.0),
    PowerDef("mamluks", "المماليك وأمراء الأطراف", "أمراء مصر وبقايا النفوذ المملوكي", 1.0, 75.0, 30.0),
    PowerDef("merchants", "التجار", "تجار الموانئ والمحميون الأجانب", 0.45, 60.0, 65.0),
    PowerDef("minorities", "الملل والأقليات", "الروم والأرمن واليهود والسلاف المسيحيون", 0.55, 45.0, 50.0),
)

fun powerDef(key: String): PowerDef? = powerDefs.firstOrNull { it.key == key }
fun isPowerKey(key: String): Boolean = powerDef(key) != null

class PowerState(
    val key: String,
    var influence: Double,
    var satisfaction: Double,
    var dissolved: Boolean = false,
    var merged: Boolean = false,
    var reformed: Boolean = false,
    var containedUntil: Int = -1,
) {
    val def: PowerDef get() = powerDef(key)!!
    val name: String get() = def.name

    /** الخطر مشتق: النفوذ مضروبا في الغضب ومعامل الميل للعنف ومضاعف الإصلاح. */
    val danger: Double
        get() = if (dissolved) 0.0
        else clampD(
            influence * (0.4 + 0.6 * (100.0 - satisfaction) / 100.0) * def.militancy * if (reformed) 0.65 else 1.0,
            0.0,
            100.0,
        )

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("influence", influence)
        put("satisfaction", satisfaction)
        put("dissolved", dissolved)
        put("merged", merged)
        put("reformed", reformed)
        put("containedUntil", containedUntil)
    }

    fun copy(): PowerState = PowerState(key, influence, satisfaction, dissolved, merged, reformed, containedUntil)
}

fun defaultPowers(): MutableMap<String, PowerState> {
    val map = linkedMapOf<String, PowerState>()
    for (d in powerDefs) {
        map[d.key] = PowerState(d.key, d.influence, d.satisfaction)
    }
    return map
}

fun powersFromJson(raw: Any?): MutableMap<String, PowerState> {
    val out = defaultPowers()
    val arr = raw as? JSONArray ?: return out
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val key = strOpt(p.opt("key"))
        val cur = out[key] ?: continue
        cur.influence = clampD(numOpt(p.opt("influence"), cur.influence), 0.0, 100.0)
        cur.satisfaction = clampD(numOpt(p.opt("satisfaction"), cur.satisfaction), 0.0, 100.0)
        cur.dissolved = boolOpt(p.opt("dissolved"), false)
        cur.merged = boolOpt(p.opt("merged"), false)
        cur.reformed = boolOpt(p.opt("reformed"), false)
        cur.containedUntil = intOpt(p.opt("containedUntil"), -1).coerceIn(-1, 100_000)
    }
    return out
}

// ───────────────────────── الصدر الأعظم ─────────────────────────

data class GrandVizierDef(
    val key: String,
    val name: String,
    val bonus: String,
    val tradeoff: String,
    val promptHint: String,
)

val grandVizierDefs: List<GrandVizierDef> = listOf(
    GrandVizierDef(
        key = "reformer",
        name = "صدر أعظم إصلاحي",
        bonus = "يرفع التعليم (+1 كل نصف سنة) ويخفض الفساد تدريجيا",
        tradeoff = "يغضب العلماء والإنكشارية قليلا",
        promptHint = "إصلاحي يركز على تحديث المعارف والإدارة ويثير تحفظ المحافظين والعسكر",
    ),
    GrandVizierDef(
        key = "military",
        name = "صدر أعظم عسكري",
        bonus = "يقوي القتال (+10% قوة جيشنا) ويرفع الجيش (+1 كل نصف سنة)",
        tradeoff = "يرفع تكاليف الجيش (+6% من ميزانية الجيش)",
        promptHint = "عسكري يشدد القبضة على الجبهات والثكنات ويكلف الخزينة نفقات حرب إضافية",
    ),
    GrandVizierDef(
        key = "economist",
        name = "صدر أعظم اقتصادي (دفتردار)",
        bonus = "يرفع التصنيف الائتماني (+5) ويخفض تكلفة المشاريع القومية (-10%)",
        tradeoff = "يضغط على استقرار البلاط السياسي والإنكشارية",
        promptHint = "اقتصادي دفتردار يضبط الميزانية والمشاريع على حساب رضا القوى التقليدية",
    ),
    GrandVizierDef(
        key = "traditional",
        name = "صدر أعظم محافظ تقليدي",
        bonus = "يعزز الشرعية ويرضي العلماء والعشائر تدريجيا",
        tradeoff = "يبطئ نمو التعليم (-1 كل نصف سنة)",
        promptHint = "محافظ تقليدي يصون الأعراف والشرعية والعلماء مع تحفظ تجاه التحديث المدني",
    ),
)

fun grandVizierDef(key: String): GrandVizierDef =
    grandVizierDefs.firstOrNull { it.key == key } ?: grandVizierDefs.last()

// ───────────────────────── الإنجازات السلطانية ─────────────────────────

data class MilestoneDef(
    val id: String,
    val title: String,
    val description: String,
    val hint: String,
)

val milestoneDefs: List<MilestoneDef> = listOf(
    MilestoneDef(
        id = "abolish_janissaries",
        title = "الواقعة الخيرية",
        description = "حل فرق الانكشارية نهائيا وتطهير ثكنات العاصمة.",
        hint = "ارفع جاهزية الحل (رضا العلماء ≥55، جيش ≥55 أو مشروع حديث، نفوذ ≤65) ونفذ حل الانكشارية.",
    ),
    MilestoneDef(
        id = "build_railway",
        title = "سكة الحديد السلطانية",
        description = "إتمام تشييد خط سكك حديد رئيسية أو سكة حديد الحجاز.",
        hint = "أكمل مشروع \"سكك حديد رئيسية\" (من 1856) أو \"سكة حديد الحجاز\" (من 1898).",
    ),
    MilestoneDef(
        id = "survive_50_years",
        title = "صمود نصف قرن",
        description = "الحفاظ على استمرار الدولة العثمانية حتى عام 1850.",
        hint = "بلغ عام 1850 دون التعرض للخلع أو الانهيار أو الإفلاس.",
    ),
    MilestoneDef(
        id = "clear_foreign_debt",
        title = "تصفية الدين الخارجي",
        description = "سداد كافة الديون الخارجية المستحقة حتى الصفر بعد الاقتراض الخارجي.",
        hint = "بعد نشوء دين خارجي (من 1854 أو تعويض حرب)، سدد الدين العام حتى يبلغ الدين الخارجي صفرا.",
    ),
    MilestoneDef(
        id = "win_major_war",
        title = "نصر عسكري حاسم",
        description = "انتصار مشرف في حرب كبرى أو توقيع صلح بشروط رابحة للدولة.",
        hint = "اكسب حربا بالنقاط (+60) أو فرض صلح بشروط لصالحنا (+20 نقطة فأكثر).",
    ),
    MilestoneDef(
        id = "preserve_empire_1875",
        title = "وحدة الولايات العثمانية",
        description = "بلوغ عام 1875 وما بعده دون فقدان أو انفصال أي ولاية.",
        hint = "حافظ على بقاء الولايات الست داخل الدولة حتى سنة 1875 فما فوق.",
    ),
)

/** مشروع طويل على قوة: إصلاح أو احتواء أو دمج، يعمل كل نصف سنة حتى ينتهي. */
class PowerProject(
    val power: String,
    val kind: String,
    var remaining: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("power", power)
        put("kind", kind)
        put("remaining", remaining)
    }

    companion object {
        val kindLabels: Map<String, String> = mapOf(
            "reform" to "إصلاح",
            "contain" to "احتواء",
            "merge" to "دمج",
        )

        fun fromJson(raw: Any?): PowerProject? {
            val obj = raw as? JSONObject ?: return null
            val p = strOpt(obj.opt("power"))
            val k = strOpt(obj.opt("kind"))
            if (!isPowerKey(p) || !kindLabels.containsKey(k)) return null
            val r = intOpt(obj.opt("remaining"), 0)
            if (r <= 0) return null
            return PowerProject(p, k, r.coerceIn(1, 20))
        }
    }
}

// ───────────────────────── الاقتصاد العميق ─────────────────────────

data class ProvinceDef(
    val key: String,
    val name: String,
    val weight: Double,
    val mood: Double,
    val sep: Double,
    val autonomy: Double,
)

val provinceDefs: List<ProvinceDef> = listOf(
    ProvinceDef("rumelia", "الروملي والبلقان", 0.26, 45.0, 0.7, 20.0),
    ProvinceDef("anatolia", "الأناضول", 0.34, 65.0, 0.05, 3.0),
    ProvinceDef("egypt", "مصر", 0.20, 40.0, 0.9, 35.0),
    ProvinceDef("syria", "الشام", 0.10, 60.0, 0.35, 12.0),
    ProvinceDef("iraq", "العراق", 0.07, 55.0, 0.35, 15.0),
    ProvinceDef("hijaz", "الحجاز واليمن", 0.03, 50.0, 0.45, 20.0),
)

class ProvinceState(
    val key: String,
    var mood: Double,
    var harvest: Double = 1.0,
    var harvestRounds: Int = 0,
    var autonomy: Double = provinceDefs.first { it.key == key }.autonomy,
    var status: String = LOYAL,
) {
    val def: ProvinceDef get() = provinceDefs.first { it.key == key }
    val name: String get() = def.name
    val isLost: Boolean get() = status == INDEPENDENT

    val effWeight: Double
        get() = when (status) {
            LOYAL -> def.weight
            AUTONOMOUS_STATUS -> def.weight * 0.4
            else -> 0.0
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("mood", mood)
        put("harvest", harvest)
        put("harvestRounds", harvestRounds)
        put("autonomy", autonomy)
        put("status", status)
    }

    companion object {
        const val LOYAL = "loyal"
        const val AUTONOMOUS_STATUS = "autonomous"
        const val INDEPENDENT = "independent"

        val statusLabels: Map<String, String> = mapOf(
            LOYAL to "موالية",
            AUTONOMOUS_STATUS to "شبه مستقلة",
            INDEPENDENT to "مفقودة",
        )
    }
}

class FactoryInstance(
    val type: String,
    var level: Int = 1,
    var subsidized: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("level", level)
        put("subsidized", subsidized)
    }

    companion object {
        fun fromJson(raw: Any?): FactoryInstance? {
            val obj = raw as? JSONObject ?: return null
            val type = strOpt(obj.opt("type"))
            if (factoryDef(type) == null) return null
            return FactoryInstance(
                type = type,
                level = intOpt(obj.opt("level"), 1).coerceIn(1, 3),
                subsidized = boolOpt(obj.opt("subsidized"), false),
            )
        }
    }
}

class SubProvinceState(
    val key: String,
    val factories: MutableList<FactoryInstance> = mutableListOf(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("factories", JSONArray().apply { factories.forEach { put(it.toJson()) } })
    }
}

class FrontierRegionState(
    val key: String,
    var controlled: Boolean = false,
    var mood: Double = frontierRegionDef(key)?.mood ?: 55.0,
    var autonomy: Double = frontierRegionDef(key)?.autonomy ?: 20.0,
    var status: String = ProvinceState.LOYAL,
    var harvest: Double = 1.0,
    var harvestRounds: Int = 0,
) {
    val def: FrontierRegionDef get() = frontierRegionDef(key)!!
    val name: String get() = def.name
    val isLost: Boolean get() = status == ProvinceState.INDEPENDENT

    val effWeight: Double
        get() {
            if (!controlled || status == ProvinceState.INDEPENDENT) return 0.0
            return when (status) {
                ProvinceState.LOYAL -> def.weight
                ProvinceState.AUTONOMOUS_STATUS -> def.weight * 0.4
                else -> 0.0
            }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("controlled", controlled)
        put("mood", mood)
        put("autonomy", autonomy)
        put("status", status)
        put("harvest", harvest)
        put("harvestRounds", harvestRounds)
    }
}

class EconomyState(
    provinces: MutableMap<String, ProvinceState>? = null,
    subProvinces: MutableMap<String, SubProvinceState>? = null,
    frontierRegions: MutableMap<String, FrontierRegionState>? = null,
    var economicSystem: String = "state_capitalism",
    var lastEconSystemRound: Int = -999,
    oilDiscovered: MutableList<String>? = null,
    var grain: Double = 100.0,
    var inflation: Double = 2.0,
    var corruption: Double = 45.0,
    var deficitStreak: Int = 0,
    var surplusStreak: Int = 0,
    var priceIndex: Double = 1.0,
) {
    val provinces: MutableMap<String, ProvinceState> = provinces ?: linkedMapOf<String, ProvinceState>().apply {
        for (d in provinceDefs) put(d.key, ProvinceState(d.key, d.mood))
    }

    val subProvinces: MutableMap<String, SubProvinceState> = subProvinces ?: linkedMapOf<String, SubProvinceState>().apply {
        for (d in subProvinceDefs) put(d.key, SubProvinceState(d.key))
    }

    val frontierRegions: MutableMap<String, FrontierRegionState> = frontierRegions ?: linkedMapOf<String, FrontierRegionState>().apply {
        for (d in frontierRegionDefs) {
            put(d.key, FrontierRegionState(d.key, controlled = false, mood = d.mood, autonomy = d.autonomy))
        }
    }

    val oilDiscovered: MutableList<String> = oilDiscovered ?: mutableListOf()

    val activeWeight: Double get() = provinces.values.sumOf { it.effWeight }
    val frontierWeight: Double get() = frontierRegions.values.sumOf { it.effWeight }

    val avgMood: Double
        get() {
            val w = activeWeight
            if (w <= 0) return 0.0
            return provinces.values.sumOf { it.effWeight * it.mood } / w
        }

    val revenueMultiplier: Double
        get() {
            var base = 0.0
            for (p in provinces.values) {
                val moodF = 1.0 + 0.002 * (p.mood - moodRef)
                val harvestF = 1.0 - LAND_SHARE * (1.0 - p.harvest)
                base += p.effWeight * moodF * harvestF
            }
            for (fr in frontierRegions.values) {
                val fw = fr.effWeight
                if (fw <= 0.0) continue
                val moodF = 1.0 + 0.002 * (fr.mood - moodRef)
                val harvestF = 1.0 - LAND_SHARE * (1.0 - fr.harvest)
                base += fw * moodF * harvestF
            }
            val corr = clampD(1.0 - 0.003 * (corruption - CORRUPTION_REF), 0.85, 1.10)
            val m = base * corr
            return if (abs(m - 1.0) < 1e-9) 1.0 else m
        }

    val nominalGrowth: Double get() = clampD(inflation, 0.0, 40.0) / 200.0

    fun toJson(): JSONObject = JSONObject().apply {
        put("provinces", JSONArray().apply { provinces.values.forEach { put(it.toJson()) } })
        put("subProvinces", JSONArray().apply { subProvinces.values.forEach { put(it.toJson()) } })
        put("frontierRegions", JSONArray().apply { frontierRegions.values.forEach { put(it.toJson()) } })
        put("economicSystem", economicSystem)
        put("lastEconSystemRound", lastEconSystemRound)
        put("oilDiscovered", JSONArray(oilDiscovered))
        put("grain", grain)
        put("inflation", inflation)
        put("corruption", corruption)
        put("deficitStreak", deficitStreak)
        put("surplusStreak", surplusStreak)
        put("priceIndex", priceIndex)
    }

    companion object {
        val moodRef: Double = provinceDefs.sumOf { it.weight * it.mood }
        const val CORRUPTION_REF: Double = 45.0
        const val LAND_SHARE: Double = 0.5

        fun fromJson(raw: Any?): EconomyState {
            val e = EconomyState()
            val obj = raw as? JSONObject ?: return e
            val arr = obj.optJSONArray("provinces")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val cur = e.provinces[strOpt(p.opt("key"))] ?: continue
                    cur.mood = clampD(numOpt(p.opt("mood"), cur.mood), 0.0, 100.0)
                    cur.harvest = clampD(numOpt(p.opt("harvest"), 1.0), 0.2, 1.5)
                    val hr = p.opt("harvestRounds")
                    cur.harvestRounds = if (hr is Number) hr.toInt().coerceIn(0, 10) else if (cur.harvest != 1.0) 1 else 0
                    cur.autonomy = clampD(numOpt(p.opt("autonomy"), cur.autonomy), 0.0, 100.0)
                    val st = strOpt(p.opt("status"))
                    if (ProvinceState.statusLabels.containsKey(st)) cur.status = st
                }
            }

            // Sub-provinces & factories (supports JSONArray or JSONObject map)
            fun readSubProvince(spObj: JSONObject, fallbackKey: String = "") {
                val key = strOpt(spObj.opt("key")).ifEmpty { fallbackKey }
                val sDef = subProvinceDef(key) ?: return
                val cur = e.subProvinces[key] ?: SubProvinceState(key).also { e.subProvinces[key] = it }
                cur.factories.clear()
                val rawFacs = spObj.optJSONArray("factories") ?: return
                for (j in 0 until rawFacs.length()) {
                    if (cur.factories.size >= sDef.slots) break
                    val fac = FactoryInstance.fromJson(rawFacs.opt(j)) ?: continue
                    if (cur.factories.none { it.type == fac.type }) {
                        cur.factories.add(fac)
                    }
                }
            }
            val rawSubsArr = obj.optJSONArray("subProvinces")
            if (rawSubsArr != null) {
                for (i in 0 until rawSubsArr.length()) {
                    rawSubsArr.optJSONObject(i)?.let { readSubProvince(it) }
                }
            } else {
                val rawSubsObj = obj.optJSONObject("subProvinces")
                if (rawSubsObj != null) {
                    val keys = rawSubsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        rawSubsObj.optJSONObject(k)?.let { readSubProvince(it, k) }
                    }
                }
            }

            // Frontier regions (supports JSONArray or JSONObject map)
            fun readFrontier(frObj: JSONObject, fallbackKey: String = "") {
                val key = strOpt(frObj.opt("key")).ifEmpty { fallbackKey }
                val cur = e.frontierRegions[key] ?: return
                cur.controlled = boolOpt(frObj.opt("controlled"), false)
                cur.mood = clampD(numOpt(frObj.opt("mood"), cur.mood), 0.0, 100.0)
                cur.autonomy = clampD(numOpt(frObj.opt("autonomy"), cur.autonomy), 0.0, 100.0)
                val st = strOpt(frObj.opt("status"))
                if (ProvinceState.statusLabels.containsKey(st)) cur.status = st
                cur.harvest = clampD(numOpt(frObj.opt("harvest"), 1.0), 0.2, 1.5)
                cur.harvestRounds = intOpt(frObj.opt("harvestRounds"), 0).coerceIn(0, 10)
            }
            val rawFrArr = obj.optJSONArray("frontierRegions")
            if (rawFrArr != null) {
                for (i in 0 until rawFrArr.length()) {
                    rawFrArr.optJSONObject(i)?.let { readFrontier(it) }
                }
            } else {
                val rawFrObj = obj.optJSONObject("frontierRegions")
                if (rawFrObj != null) {
                    val keys = rawFrObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        rawFrObj.optJSONObject(k)?.let { readFrontier(it, k) }
                    }
                }
            }

            val sysStr = strOpt(obj.opt("economicSystem"))
            if (economicSystemDefs.any { it.key == sysStr }) {
                e.economicSystem = sysStr
            }
            e.lastEconSystemRound = intOpt(obj.opt("lastEconSystemRound"), -999)
            val oilArr = obj.optJSONArray("oilDiscovered")
            if (oilArr != null) {
                for (i in 0 until oilArr.length()) {
                    val k = oilArr.opt(i) as? String ?: continue
                    if (subProvinceDef(k) != null && !e.oilDiscovered.contains(k)) {
                        e.oilDiscovered.add(k)
                    }
                }
            }

            e.grain = clampD(numOpt(obj.opt("grain"), 100.0), 40.0, 400.0)
            e.inflation = clampD(numOpt(obj.opt("inflation"), 2.0), 0.0, 40.0)
            e.corruption = clampD(numOpt(obj.opt("corruption"), 45.0), 0.0, 100.0)
            e.deficitStreak = intOpt(obj.opt("deficitStreak"), 0).coerceIn(0, 1000)
            e.surplusStreak = intOpt(obj.opt("surplusStreak"), 0).coerceIn(0, 1000)
            e.priceIndex = clampD(numOpt(obj.opt("priceIndex"), 1.0), 0.1, 1000.0)
            return e
        }
    }
}

// ───────────────────────── الدول والسفارات والجواسيس ─────────────────────────

data class CountryDef(
    val key: String,
    val name: String,
    val relation: Double,
    val embassy: Boolean,
    val economy: Double,
    val military: Double,
    val ecoGrowth: Double,
    val milGrowth: Double,
)

val countryDefs: List<CountryDef> = listOf(
    CountryDef("britain", "بريطانيا", 62.0, true, 220.0, 85.0, 0.024, 0.016),
    CountryDef("austria", "النمسا", 52.0, true, 110.0, 80.0, 0.011, 0.007),
    CountryDef("france", "فرنسا", 30.0, false, 170.0, 120.0, 0.016, 0.010),
    CountryDef("russia", "روسيا", 50.0, false, 140.0, 110.0, 0.015, 0.012),
    CountryDef("persia", "إيران", 45.0, false, 45.0, 30.0, 0.004, 0.003),
    CountryDef("germany", "بروسيا وألمانيا", 45.0, false, 70.0, 55.0, 0.024, 0.016),
)

fun countryDef(key: String): CountryDef? = countryDefs.firstOrNull { it.key == key }
fun isCountryKey(key: String): Boolean = countryDef(key) != null

class CountryState(
    val key: String,
    var relation: Double,
    var embassy: Boolean = false,
    var spy: Int = 0,
    var lastTreaty: Int = -999,
    var alliance: Boolean = false,
    var economy: Double = countryDef(key)!!.economy,
    var military: Double = countryDef(key)!!.military,
) {
    val name: String get() = countryDef(key)!!.name
    val intelQuality: Int get() = spy + if (embassy) 1 else 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("relation", relation)
        put("embassy", embassy)
        put("spy", spy)
        put("lastTreaty", lastTreaty)
        put("alliance", alliance)
        put("economy", economy)
        put("military", military)
    }
}

fun defaultCountries(): MutableMap<String, CountryState> {
    val map = linkedMapOf<String, CountryState>()
    for (d in countryDefs) {
        map[d.key] = CountryState(d.key, d.relation, embassy = d.embassy)
    }
    return map
}

fun countriesFromJson(raw: Any?): MutableMap<String, CountryState> {
    val out = defaultCountries()
    val arr = raw as? JSONArray ?: return out
    for (i in 0 until arr.length()) {
        val c = arr.optJSONObject(i) ?: continue
        val cur = out[strOpt(c.opt("key"))] ?: continue
        cur.relation = clampD(numOpt(c.opt("relation"), cur.relation), 0.0, 100.0)
        cur.embassy = boolOpt(c.opt("embassy"), cur.embassy)
        cur.spy = intOpt(c.opt("spy"), 0).coerceIn(0, 2)
        cur.lastTreaty = intOpt(c.opt("lastTreaty"), -999)
        cur.alliance = boolOpt(c.opt("alliance"), false)
        cur.economy = clampD(numOpt(c.opt("economy"), cur.economy), 5.0, 5000.0)
        cur.military = clampD(numOpt(c.opt("military"), cur.military), 3.0, 400.0)
    }
    return out
}

class SpyReport(
    val round: Int,
    val target: String,
    val claim: String,
    val reliability: Int,
    val truth: Boolean,
    var revealed: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("round", round)
        put("target", target)
        put("claim", claim)
        put("reliability", reliability)
        put("truth", truth)
        put("revealed", revealed)
    }

    companion object {
        fun fromJson(raw: Any?): SpyReport? {
            val obj = raw as? JSONObject ?: return null
            val claim = strOpt(obj.opt("claim"))
            if (claim.isEmpty()) return null
            return SpyReport(
                round = intOpt(obj.opt("round"), 0),
                target = strOpt(obj.opt("target")),
                claim = claim,
                reliability = intOpt(obj.opt("reliability"), 50).coerceIn(0, 100),
                truth = boolOpt(obj.opt("truth"), false),
                revealed = boolOpt(obj.opt("revealed"), false),
            )
        }
    }
}

// ───────────────────────── السكان والتعليم ─────────────────────────

class DemographyState(
    var population: Double = 26.0,
    var literacy: Double = 3.0,
    var schools: Double = 9000.0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("population", population)
        put("literacy", literacy)
        put("schools", schools)
    }

    companion object {
        fun fromJson(raw: Any?): DemographyState {
            val obj = raw as? JSONObject ?: return DemographyState()
            return DemographyState(
                population = clampD(numOpt(obj.opt("population"), 26.0), 1.0, 500.0),
                literacy = clampD(numOpt(obj.opt("literacy"), 3.0), 0.0, 100.0),
                schools = clampD(numOpt(obj.opt("schools"), 9000.0), 0.0, 1e7),
            )
        }
    }
}

// ───────────────────────── ذاكرة العالم ─────────────────────────

class WorldMemory(
    wars: List<String>? = null,
    alliances: List<String>? = null,
    reforms: List<String>? = null,
    promises: List<String>? = null,
) {
    val wars: List<String> = clean(wars)
    val alliances: List<String> = clean(alliances)
    val reforms: List<String> = clean(reforms)
    val promises: List<String> = clean(promises)

    val isEmpty: Boolean
        get() = wars.isEmpty() && alliances.isEmpty() && reforms.isEmpty() && promises.isEmpty()

    fun withReforms(nextReforms: List<String>): WorldMemory =
        WorldMemory(wars = wars, alliances = alliances, reforms = nextReforms, promises = promises)

    fun withAlliances(nextAlliances: List<String>): WorldMemory =
        WorldMemory(wars = wars, alliances = nextAlliances, reforms = reforms, promises = promises)

    fun toJson(): JSONObject = JSONObject().apply {
        put("wars", JSONArray(wars))
        put("alliances", JSONArray(alliances))
        put("reforms", JSONArray(reforms))
        put("promises", JSONArray(promises))
    }

    companion object {
        const val MAX_ITEMS: Int = 6
        const val MAX_LEN: Int = 90

        private fun clean(raw: Any?): List<String> {
            val out = mutableListOf<String>()
            val items: Iterable<Any?> = when (raw) {
                is JSONArray -> (0 until raw.length()).map { raw.opt(it) }
                is List<*> -> raw
                else -> return out
            }
            for (x in items) {
                if (x !is String) continue
                var t = x.trim()
                if (t.isEmpty()) continue
                if (t.length > MAX_LEN) t = "${t.substring(0, MAX_LEN - 1).trimEnd()}…"
                out.add(t)
                if (out.size >= MAX_ITEMS) break
            }
            return out
        }

        fun fromJson(raw: Any?): WorldMemory {
            val obj = raw as? JSONObject ?: return WorldMemory()
            return WorldMemory(
                wars = clean(obj.opt("wars")),
                alliances = clean(obj.opt("alliances")),
                reforms = clean(obj.opt("reforms")),
                promises = clean(obj.opt("promises")),
            )
        }
    }
}

// ───────────────────────── الحروب والمشاريع والرسوم ─────────────────────────

class War(
    val enemy: String,
    val startRound: Int,
    val byUs: Boolean = false,
    var score: Double = 0.0,
    var halves: Int = 0,
) {
    val enemyName: String get() = countryDef(enemy)?.name ?: enemy

    fun toJson(): JSONObject = JSONObject().apply {
        put("enemy", enemy)
        put("startRound", startRound)
        put("byUs", byUs)
        put("score", score)
        put("halves", halves)
    }

    companion object {
        fun fromJson(raw: Any?): War? {
            val obj = raw as? JSONObject ?: return null
            val e = strOpt(obj.opt("enemy"))
            if (!isCountryKey(e)) return null
            return War(
                enemy = e,
                startRound = intOpt(obj.opt("startRound"), 0),
                byUs = boolOpt(obj.opt("byUs"), false),
                score = clampD(numOpt(obj.opt("score"), 0.0), -100.0, 100.0),
                halves = intOpt(obj.opt("halves"), 0).coerceIn(0, 1000),
            )
        }
    }
}

class ActiveProject(
    val key: String,
    val halves: Int,
    val installment: Double,
    val startRound: Int,
    var funded: Int = 0,
    var stalled: Int = 0,
    var paused: Boolean = false,
) {
    val progress: Double get() = if (halves <= 0) 0.0 else (funded.toDouble() / halves).coerceIn(0.0, 1.0)
    val remaining: Int get() = (halves - funded).coerceIn(0, halves)

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("halves", halves)
        put("installment", installment)
        put("startRound", startRound)
        put("funded", funded)
        put("stalled", stalled)
        put("paused", paused)
    }

    companion object {
        fun fromJson(raw: Any?): ActiveProject? {
            val obj = raw as? JSONObject ?: return null
            val k = strOpt(obj.opt("key"))
            val h = intOpt(obj.opt("halves"), 0)
            if (k.isEmpty() || h <= 0) return null
            val clampedH = h.coerceIn(1, 60)
            return ActiveProject(
                key = k,
                halves = clampedH,
                installment = clampD(numOpt(obj.opt("installment"), 0.0), 0.0, 1e13),
                startRound = intOpt(obj.opt("startRound"), 0),
                funded = intOpt(obj.opt("funded"), 0).coerceIn(0, clampedH),
                stalled = intOpt(obj.opt("stalled"), 0).coerceIn(0, 1000),
                paused = boolOpt(obj.opt("paused"), false),
            )
        }
    }
}

data class StatPoint(
    val round: Int,
    val treasury: Double,
    val debt: Double,
    val revenue: Double,
    val legitimacy: Int,
    val stability: Int,
    val army: Int,
    val foreign: Int,
    val education: Int,
    val population: Double,
    val literacy: Double,
    val inflation: Double,
) {
    fun toJsonArray(): JSONArray = JSONArray().apply {
        put(round)
        put(treasury)
        put(debt)
        put(revenue)
        put(legitimacy)
        put(stability)
        put(army)
        put(foreign)
        put(education)
        put(population)
        put(literacy)
        put(inflation)
    }

    companion object {
        const val FIELDS: Int = 12

        fun fromJsonArray(raw: Any?): StatPoint? {
            val arr = raw as? JSONArray ?: return null
            if (arr.length() < FIELDS) return null
            for (i in 0 until FIELDS) {
                val v = arr.opt(i)
                if (v !is Number || !v.toDouble().isFinite()) return null
            }
            fun d(i: Int): Double = (arr.opt(i) as Number).toDouble()
            fun n(i: Int): Int = (arr.opt(i) as Number).toDouble().roundToInt()
            return StatPoint(
                round = n(0),
                treasury = d(1),
                debt = d(2),
                revenue = d(3),
                legitimacy = n(4),
                stability = n(5),
                army = n(6),
                foreign = n(7),
                education = n(8),
                population = d(9),
                literacy = d(10),
                inflation = d(11),
            )
        }
    }
}
