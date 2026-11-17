package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.EconomyState
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.PendingEffect
import com.ottoman.empire1800.models.PowerProject
import com.ottoman.empire1800.models.PowerState
import com.ottoman.empire1800.models.SpyReport
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.grandVizierDefs
import com.ottoman.empire1800.models.powerDef
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToDouble
import kotlin.math.roundToInt
import kotlin.random.Random

data class JanissaryReadiness(
    val hasUlema: Boolean,
    val hasMilitary: Boolean,
    val lowInfluence: Boolean,
    val successChance: Double,
)

object GrandVizierRules {
    const val CHANGE_SHARE: Double = 0.010

    fun appoint(s: GameState, key: String): CourtResult {
        val def = grandVizierDefs.firstOrNull { it.key == key }
            ?: return CourtResult(false, "منصب صدر أعظم غير معروف")
        if (s.grandVizier == key) {
            return CourtResult(false, "${def.name} يتولى المنصب بالفعل")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, CHANGE_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لرسوم التعيين")
        s.treasury -= cost
        s.courtActions -= 1
        s.grandVizier = key
        s.courtLog.add("تعيين ${def.name}")
        recordRoyalDecree(s, "تعيين ${def.name}")
        return CourtResult(true, "تولى ${def.name} رئاسة الديوان (${def.bonus})")
    }
}

/** قوى الدولة: الرضا والنفوذ والخطر، والمشاريع الطويلة، والمؤامرات، وإجراءات الديوان. */
object PowerRules {
    private const val ARMY_SHARE_REF: Double = 8_000_000.0 / 18_000_000.0

    fun recordWorldReform(s: GameState, entry: String) {
        val clean = entry.trim().take(90)
        if (clean.isEmpty()) return
        val next = listOf(clean) + s.world.reforms.filter { it != clean }
        s.world = s.world.withReforms(next.take(6))
    }

    fun syncPowerReformsToWorld(s: GameState) {
        for (p in s.powers.values) {
            if (!p.dissolved) continue
            val pName = p.name
            val msg = when {
                p.merged -> "تم دمج $pName في مؤسسات الدولة"
                p.key == "janissaries" -> "الواقعة الخيرية: حل الانكشارية نهائيا"
                else -> "تم حل $pName نهائيا"
            }
            if (s.world.reforms.none { it.contains(pName) }) {
                recordWorldReform(s, msg)
            }
        }
    }

    fun janissaryReadiness(s: GameState): JanissaryReadiness {
        val ulema = s.powers["ulema"]
        val jan = s.powers["janissaries"]
        val hasUlema = ulema != null && !ulema.dissolved && ulema.satisfaction >= 55.0
        val hasModernProject = s.completed.contains("modern_army") || s.completed.contains("military_academy")
        val hasMilitary = s.army >= 55 || hasModernProject
        val inf = jan?.influence ?: 90.0
        val lowInfluence = inf <= 65.0

        var chance = 0.15
        chance += if (hasUlema) 0.25 else -0.15
        chance += if (hasMilitary) 0.30 + (if (hasModernProject) 0.10 else 0.0) else -0.15
        chance += clampD((70.0 - inf) * 0.012, -0.20, 0.35)
        return JanissaryReadiness(
            hasUlema = hasUlema,
            hasMilitary = hasMilitary,
            lowInfluence = lowInfluence,
            successChance = clampD(chance, 0.05, 0.95),
        )
    }

    fun signal(s: GameState, key: String, emergency: Boolean): Double {
        val rev = s.budget.totalRevenue
        val armyShare = if (rev > 0.0) s.budget.lineValue("army") / rev else 0.0
        val adminShare = if (rev > 0.0) s.budget.lineValue("admin") / rev else 0.0
        val e = s.econ
        return when (key) {
            "janissaries" -> 35.0 + 150.0 * (armyShare - ARMY_SHARE_REF) - if (emergency) 20.0 else 0.0
            "ulema" -> 60.0 + 0.5 * (s.legitimacy - 65) - 0.3 * (s.education - 15)
            "governors" -> 50.0 + 0.5 * (e.avgMood - EconomyState.moodRef) - 100.0 * 0.3 * (adminShare - 0.25)
            "tax_farmers" -> 45.0 + 0.5 * (e.corruption - EconomyState.CORRUPTION_REF)
            "tribes" -> 55.0 - 0.25 * (e.grain - 100.0)
            "mamluks" -> 30.0 + 0.3 * ((e.provinces["egypt"]?.mood ?: 40.0) - 40.0)
            "merchants" -> 65.0 + 0.3 * (s.foreign - 45) - 0.8 * (e.inflation - 2.0)
            "minorities" -> 50.0 + 0.4 * (s.stability - 55)
            else -> 50.0
        }
    }

    private fun applyMergeReward(s: GameState, p: PowerState): String {
        val pName = p.name
        p.merged = true
        p.dissolved = true
        p.influence = clampD(p.influence * 0.25, 0.0, 100.0)
        p.satisfaction = clampD(p.satisfaction + 10.0, 0.0, 100.0)
        s.projects.removeAll { it.power == p.key }
        recordWorldReform(s, "تم دمج $pName في مؤسسات الدولة")

        return when (p.key) {
            "janissaries" -> {
                s.army = (s.army + 5).coerceIn(0, 100)
                s.stab.shiftAll(2.0)
                "اكتمل دمج $pName في الجيش النظامي (+5 جيش، +2 استقرار)"
            }
            "tax_farmers" -> {
                s.econ.corruption = clampD(s.econ.corruption - 6.0, 0.0, 100.0)
                "اكتمل دمج $pName في الخزينة (-6 فساد)"
            }
            "tribes" -> {
                for (k in listOf("syria", "iraq", "hijaz")) {
                    val prov = s.econ.provinces[k]
                    if (prov != null && !prov.isLost) {
                        prov.mood = clampD(prov.mood + 6.0, 0.0, 100.0)
                        prov.autonomy = clampD(prov.autonomy - 4.0, 0.0, 100.0)
                    }
                }
                s.stab.shift("regional", 2.0)
                "اكتمل دمج $pName في حماية الحدود والطرق (تحسن مزاج الشام والعراق والحجاز وتباطأ الانفصال)"
            }
            "governors" -> {
                for (prov in s.econ.provinces.values) {
                    if (!prov.isLost) {
                        prov.mood = clampD(prov.mood + 4.0, 0.0, 100.0)
                        prov.autonomy = clampD(prov.autonomy - 5.0, 0.0, 100.0)
                    }
                }
                s.stab.shift("regional", 2.0)
                "اكتمل دمج $pName في الإدارة المركزية (ارتفع رضا الولايات وتباطأ الانفصال)"
            }
            "mamluks" -> {
                val egypt = s.econ.provinces["egypt"]
                if (egypt != null && !egypt.isLost) {
                    egypt.mood = clampD(egypt.mood + 10.0, 0.0, 100.0)
                    egypt.autonomy = clampD(egypt.autonomy - 12.0, 0.0, 100.0)
                }
                s.stab.shift("regional", 2.0)
                "اكتمل دمج $pName: استقرت مصر وتراجعت نزعة انفصالها"
            }
            "ulema" -> {
                s.legitimacy = (s.legitimacy + 3).coerceIn(0, 100)
                s.education = (s.education + 3).coerceIn(0, 100)
                s.stab.shift("religious", 2.0)
                "اكتمل دمج $pName في القضاء والمعارف (+3 شرعية، +3 تعليم)"
            }
            "merchants" -> {
                s.foreign = (s.foreign + 3).coerceIn(0, 100)
                s.econ.inflation = clampD(s.econ.inflation - 1.0, 1.0, 40.0)
                "اكتمل دمج $pName في الغرف التجارية (+3 مكانة دولية، تراجع التضخم)"
            }
            else -> {
                s.stab.shift("social", 4.0)
                s.stab.shift("regional", 3.0)
                s.foreign = (s.foreign + 2).coerceIn(0, 100)
                "اكتمل دمج $pName في المواطنة العثمانية (تحسن الاستقرار الاجتماعي والإقليمي)"
            }
        }
    }

    fun step(s: GameState, emergency: Boolean): List<String> {
        val notes = mutableListOf<String>()

        for (p in s.powers.values) {
            if (p.dissolved) continue
            val target = signal(s, p.key, emergency = emergency)
            val vizierDelta = when {
                s.grandVizier == "reformer" && (p.key == "ulema" || p.key == "janissaries") -> -0.6
                s.grandVizier == "economist" && p.key == "janissaries" -> -0.5
                s.grandVizier == "traditional" && (p.key == "ulema" || p.key == "tribes") -> 0.8
                else -> 0.0
            }
            p.satisfaction = clampD(p.satisfaction + 0.08 * s.f * (target - p.satisfaction) + vizierDelta * s.f, 0.0, 100.0)
        }

        val done = mutableListOf<PowerProject>()
        for (pr in s.projects) {
            val p = s.powers[pr.power]
            if (p == null || p.dissolved) {
                done.add(pr)
                continue
            }
            when (pr.kind) {
                "reform" -> {
                    p.influence = clampD(p.influence - 1.5 * s.f, 0.0, 100.0)
                    p.satisfaction = clampD(p.satisfaction + 1.5 * s.f, 0.0, 100.0)
                }
                "contain" -> {
                    p.influence = clampD(p.influence - 3.0 * s.f, 0.0, 100.0)
                    p.satisfaction = clampD(p.satisfaction - 1.0 * s.f, 0.0, 100.0)
                }
                "merge" -> {
                    p.influence = clampD(p.influence - 2.5 * s.f, 0.0, 100.0)
                    p.satisfaction = clampD(p.satisfaction + 1.0 * s.f, 0.0, 100.0)
                }
            }
            pr.remaining -= 1
            if (pr.remaining <= 0) {
                done.add(pr)
                when (pr.kind) {
                    "reform" -> {
                        p.reformed = true
                        p.influence = clampD(p.influence - 5.0, 0.0, 100.0)
                        s.stab.shiftAll(2.0)
                        s.legitimacy = (s.legitimacy + 2).coerceIn(0, 100)
                        notes.add("اكتمل إصلاح ${p.name}: انخفض خطرها الدائم (+2 استقرار، +2 شرعية)")
                    }
                    "contain" -> {
                        p.influence = clampD(p.influence - 15.0, 0.0, 100.0)
                        p.satisfaction = clampD(p.satisfaction - 6.0, 0.0, 100.0)
                        p.containedUntil = s.round + 6 * s.k
                        notes.add("اكتمل احتواء ${p.name}: انحسر نفوذها (-15) وتوقفت مؤامراتها")
                    }
                    else -> {
                        notes.add(applyMergeReward(s, p))
                    }
                }
            }
        }
        s.projects.removeAll(done.toSet())

        if (!inGrace(s)) notes.addAll(plots(s))
        return notes
    }

    private val detectChance: Map<String, Double> = mapOf(
        "0" to 0.0,
        "1" to 0.45,
        "2" to 0.80,
    )

    private fun plots(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val rng = rngFor(s, 11)
        for (p in s.powers.values) {
            if (p.dissolved || p.containedUntil > s.round) continue
            val danger = p.danger
            if (danger < 60.0) continue
            val chance = min(0.15, (danger - 60.0) / 300.0) *
                s.diff.events *
                s.f *
                if (ProjectRules.hasPerk(s, "security")) 0.7 else 1.0
            if (rng.nextDouble() >= chance) continue
            val detectP = detectChance["${s.domesticSpy}"] ?: 0.0
            if (rng.nextDouble() < detectP) {
                p.satisfaction = clampD(p.satisfaction - 4.0, 0.0, 100.0)
                s.legitimacy = (s.legitimacy + 1).coerceIn(0, 100)
                notes.add("كشفت الاستخبارات الداخلية مؤامرة لـ${p.name} وأُحبطت قبل أن تقع")
                s.intel.add(
                    SpyReport(
                        round = s.round,
                        target = "domestic",
                        claim = "أحبطنا مؤامرة لـ${p.name}",
                        reliability = 100,
                        truth = true,
                        revealed = true,
                    ),
                )
            } else {
                notes.add(explode(s, p))
            }
        }
        return notes
    }

    private fun explode(s: GameState, p: PowerState): String {
        val st = s.stab
        s.legitimacy = (s.legitimacy - 4).coerceIn(0, 100)
        return when (p.key) {
            "janissaries" -> {
                st.shift("political", -5.0)
                st.shift("military", -4.0)
                s.army = (s.army - 3).coerceIn(0, 100)
                "تمرد الانكشارية في العاصمة وطالبوا برؤوس الوزراء"
            }
            "ulema" -> {
                st.shift("religious", -6.0)
                st.shift("political", -3.0)
                "أصدر العلماء بيانا يطعن في سياسات السلطان وهيجوا العامة"
            }
            "governors" -> {
                st.shift("regional", -6.0)
                st.shift("economic", -2.0)
                "تمرد أعيان وولاة في الأطراف وحبسوا الضرائب عن الخزينة"
            }
            "tax_farmers" -> {
                st.shift("economic", -5.0)
                st.shift("political", -2.0)
                "أوقف الملتزمون والصيارفة الإقراض وحبسوا الأموال عن الدولة"
            }
            "tribes" -> {
                st.shift("regional", -5.0)
                st.shift("military", -2.0)
                "اجتاحت العشائر الطرق والقرى الحدودية"
            }
            "mamluks" -> {
                st.shift("regional", -5.0)
                st.shift("political", -3.0)
                "تمرد المماليك وأمراء الأطراف في مصر وعطلوا جباية الولاية"
            }
            "merchants" -> {
                st.shift("economic", -4.0)
                st.shift("social", -2.0)
                "أضرب التجار وأغلقوا الأسواق احتجاجا على السياسات"
            }
            else -> {
                st.shift("social", -5.0)
                st.shift("regional", -3.0)
                "اندلعت اضطرابات طائفية في الولايات المختلطة"
            }
        }
    }

    const val APPEASE_SHARE: Double = 0.015
    const val REFORM_SHARE: Double = 0.020
    const val CONTAIN_SHARE: Double = 0.010
    const val MERGE_SHARE: Double = 0.012
    const val DISSOLVE_SHARE: Double = 0.030

    private fun precheck(s: GameState, p: PowerState?, cost: Double): CourtResult? {
        if (p == null) return CourtResult(false, "قوة غير معروفة")
        if (p.dissolved) {
            return CourtResult(false, if (p.merged) "${p.name} مندمجة في الدولة بالفعل" else "${p.name} محلولة أصلا")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")
        return null
    }

    fun hasProject(s: GameState, key: String): Boolean = s.projects.any { it.power == key }

    private fun pay(s: GameState, cost: Double, log: String) {
        s.treasury -= cost
        s.courtActions -= 1
        s.courtLog.add(log)
    }

    fun appease(s: GameState, key: String): CourtResult {
        val p = s.powers[key]
        val cost = costShare(s, APPEASE_SHARE)
        precheck(s, p, cost)?.let { return it }
        p!!.satisfaction = clampD(p.satisfaction + 8.0, 0.0, 100.0)
        var rivalNote = ""
        if (key == "janissaries") {
            val merchants = s.powers["merchants"]
            val ulema = s.powers["ulema"]
            val rival = when {
                merchants != null && !merchants.dissolved -> merchants to "التجار"
                ulema != null && !ulema.dissolved -> ulema to "العلماء"
                else -> null
            }
            if (rival != null) {
                rival.first.satisfaction = clampD(rival.first.satisfaction - 2.0, 0.0, 100.0)
                rivalNote = " مع تذمر ${rival.second} (-2 رضا)"
            }
        }
        pay(s, cost, "ترضية ${p.name} بالعطايا والرواتب")
        return CourtResult(true, "رضا ${p.name} ارتفع$rivalNote")
    }

    fun startProject(s: GameState, key: String, kind: String): CourtResult {
        val p = s.powers[key]
        val share = when (kind) {
            "reform" -> REFORM_SHARE
            "contain" -> CONTAIN_SHARE
            else -> MERGE_SHARE
        }
        val cost = costShare(s, share)
        precheck(s, p, cost)?.let { return it }
        if (hasProject(s, key)) {
            return CourtResult(false, "لـ${p!!.name} مشروع جار بالفعل")
        }
        val halves = (when (kind) {
            "reform" -> 6
            "contain" -> 8
            else -> 5
        }) * s.k
        s.projects.add(PowerProject(key, kind, halves))
        pay(s, cost, "بدء مشروع ${PowerProject.kindLabels[kind]} ${p!!.name}")
        return CourtResult(
            true,
            "بدأ مشروع ${PowerProject.kindLabels[kind]} ${p.name} ويستغرق نحو ${ceil(halves.toDouble() / s.ppy).toInt()} سنوات",
        )
    }

    fun dissolveSuccessChance(s: GameState, p: PowerState): Double {
        if (p.key == "janissaries") return janissaryReadiness(s).successChance
        return if (p.danger > 70.0) 0.50 else 1.00
    }

    fun dissolve(s: GameState, key: String): CourtResult {
        val p = s.powers[key]
        val cost = costShare(s, DISSOLVE_SHARE)
        precheck(s, p, cost)?.let { return it }
        val target = p!!
        val chancePct = (dissolveSuccessChance(s, target) * 100.0).roundToInt()
        val dangerBefore = target.danger.roundToInt()
        val janBefore = if (key == "janissaries") janissaryReadiness(s) else null
        val backlash = dissolveCore(s, target, rngFor(s, 977 + stableHash(key) % 97))
        pay(s, cost, if (backlash) "محاولة حل ${target.name} فشلت وارتدت علينا" else "حل ${target.name}")
        if (backlash) {
            if (key == "janissaries" && janBefore != null) {
                val missing = buildList {
                    if (!janBefore.hasUlema) add("غياب دعم العلماء (رضا < 55)")
                    if (!janBefore.hasMilitary) add("ضعف الجيش (< 55 دون مشروع حديث)")
                    if (!janBefore.lowInfluence) add("بقاء نفوذ الانكشارية مرتفعا (> 65)")
                }
                val why = if (missing.isNotEmpty()) missing.joinToString("، ") else "تقلب الحظ رغم توازن القوى"
                return CourtResult(
                    ok = false,
                    message = "فشلت محاولة حل الانكشارية واشتعل تمرد دموي في الثكنات!",
                    detail = "نسبة الجاهزية كانت $chancePct%. السبب: $why. الخسائر: -10 شرعية، -8 جيش، وتراجع الاستقرار السياسي والعسكري.",
                )
            }
            return CourtResult(
                ok = false,
                message = "ارتدت محاولة حل ${target.name}: تمرد وخسارة هيبة",
                detail = "بلغ خطر ${target.name} $dangerBefore/100 (> 70)، فكانت نسبة النجاح $chancePct% ووقعت المواجهة: -6 شرعية، -3 جيش، وارتجاج سياسي وعسكري.",
            )
        }
        if (key == "janissaries") {
            recordRoyalDecree(s, "الواقعة الخيرية: حل الانكشارية نهائيا")
            val hasModern = s.completed.contains("modern_army")
            return CourtResult(
                ok = true,
                message = if (hasModern) {
                    "نجحت الواقعة الخيرية وحُلت الانكشارية دون صدمة بفضل الجيش النظامي"
                } else {
                    "نجحت الواقعة الخيرية! حُلت الانكشارية (+4 شرعية) وتراجع الجيش مؤقتا (-10) حتى بناء جيش حديث"
                },
                detail = if (hasModern) {
                    "نجحت العملية بنسبة جاهزية $chancePct%؛ سد الجيش النظامي الحديث الفراغ العسكري فارتفعت الشرعية (+4) وتقلصت نفقات الأوجاق (-8%)."
                } else {
                    "نجحت العملية بنسبة جاهزية $chancePct%؛ ارتفعت الشرعية (+4) والاستقرار السياسي (+3) وتقلصت نفقات الأوجاق (-8%) مقابل تراجع مؤقت للجيش (-10) حتى إتمام الجيش الحديث."
                },
            )
        }
        recordRoyalDecree(s, "حل ${target.name} نهائيا")
        val specDetail = when (key) {
            "tax_farmers" -> "كان خطر ${target.name} $dangerBefore/100 (فرصة نجاح $chancePct%). النتيجة: -8 فساد فورا وتحسن جباية الضرائب الزراعية بعد عام مقابل هزة اقتصادية مؤقتة."
            "mamluks" -> "كان خطر ${target.name} $dangerBefore/100 (فرصة نجاح $chancePct%). النتيجة: تراجع حكم المماليك في مصر (-10% انفصال) وتباطؤ نزعتها مستقبلا مع اضطراب إقليمي عابر."
            "ulema" -> "كان خطر ${target.name} $dangerBefore/100 (فرصة نجاح $chancePct%). النتيجة: صدمة دينية قوية (-10 استقرار ديني و-8 شرعية) لكن توقف نفوذهم المؤسسي."
            else -> "كان خطر ${target.name} $dangerBefore/100 (فرصة نجاح $chancePct%). انتهى دورها السياسي مقابل توتر مؤقت."
        }
        return CourtResult(
            ok = true,
            message = "حُلت ${target.name} نهائيا",
            detail = specDetail,
        )
    }

    fun dissolveFromDecision(s: GameState, key: String): String {
        val p = s.powers[key]
        if (p == null || p.dissolved) return ""
        val backlash = dissolveCore(s, p, rngFor(s, 977 + stableHash(key) % 97))
        return if (backlash) {
            "تمردت ${p.name} على قرار حلها ونجت منه جزئيا"
        } else {
            "حُلت ${p.name} بقرار السلطان"
        }
    }

    private fun dissolveCore(s: GameState, p: PowerState, rng: Random): Boolean {
        if (p.key == "janissaries") {
            val readiness = janissaryReadiness(s)
            val backlash = rng.nextDouble() >= readiness.successChance
            if (backlash) {
                s.stab.shift("political", -9.0)
                s.stab.shift("military", -8.0)
                s.legitimacy = (s.legitimacy - 10).coerceIn(0, 100)
                s.army = (s.army - 8).coerceIn(0, 100)
                p.influence = clampD(p.influence * 0.8, 0.0, 100.0)
                p.satisfaction = clampD(p.satisfaction - 15.0, 0.0, 100.0)
                return true
            }
            p.dissolved = true
            p.influence = clampD(p.influence * 0.1, 0.0, 100.0)
            s.projects.removeAll { it.power == p.key }
            if (!s.completed.contains("modern_army")) {
                s.army = (s.army - 10).coerceIn(0, 100)
            }
            val armyCut = (s.budget.lineValue("army") * 0.08).roundToDouble()
            if (armyCut > 0.0) s.budget.change("army", -armyCut)
            s.legitimacy = (s.legitimacy + 4).coerceIn(0, 100)
            s.stab.shift("political", 3.0)
            recordWorldReform(s, "الواقعة الخيرية: حل الانكشارية نهائيا")
            MilestoneRules.evaluate(s)
            return false
        }

        val backlash = p.danger > 70.0 && rng.nextDouble() < 0.5
        if (backlash) {
            s.stab.shift("political", -6.0)
            s.stab.shift("military", -5.0)
            s.legitimacy = (s.legitimacy - 6).coerceIn(0, 100)
            s.army = (s.army - 3).coerceIn(0, 100)
            p.influence = clampD(p.influence * 0.6, 0.0, 100.0)
            p.satisfaction = clampD(p.satisfaction - 10.0, 0.0, 100.0)
            return true
        }
        p.dissolved = true
        p.influence = clampD(p.influence * 0.1, 0.0, 100.0)
        s.projects.removeAll { it.power == p.key }
        val pName = powerDef(p.key)?.name ?: p.name

        when (p.key) {
            "tax_farmers" -> {
                s.econ.corruption = clampD(s.econ.corruption - 8.0, 0.0, 100.0)
                s.stab.shift("economic", -3.0)
                s.stab.shift("political", -2.0)
                s.pending.add(
                    PendingEffect(
                        dueRound = s.round + 2 * s.k,
                        effects = Effects(),
                        lines = listOf(LineChange("land", (s.budget.totalRevenue * 0.025).roundToDouble())),
                        source = "إلغاء الالتزام: تحسن جباية الضرائب الزراعية",
                    ),
                )
            }
            "mamluks" -> {
                val egypt = s.econ.provinces["egypt"]
                if (egypt != null && !egypt.isLost) {
                    egypt.mood = clampD(egypt.mood - 6.0, 0.0, 100.0)
                    egypt.autonomy = clampD(egypt.autonomy - 10.0, 0.0, 100.0)
                }
                s.stab.shift("regional", -3.0)
                s.stab.shift("political", -1.0)
            }
            "ulema" -> {
                s.stab.shift("religious", -10.0)
                s.legitimacy = (s.legitimacy - 8).coerceIn(0, 100)
                s.stab.shift("social", -2.0)
            }
            else -> {
                s.stab.shift("political", -2.0)
                s.stab.shift("regional", -1.0)
            }
        }

        recordWorldReform(s, "تم حل $pName نهائيا")
        return false
    }

    fun dissolveRisk(p: PowerState, s: GameState? = null): String {
        if (p.key == "janissaries" && s != null) {
            val chance = janissaryReadiness(s).successChance
            if (chance >= 0.70) return "جاهز للحل"
            if (chance >= 0.40) return "مخاطرة متوسطة"
            return "تمرد مرجح"
        }
        return if (p.danger > 70.0) "خطر مرتفع" else "خطر منخفض"
    }

    fun averageDanger(s: GameState): Double = StabilityRules.averageDanger(s)
}
