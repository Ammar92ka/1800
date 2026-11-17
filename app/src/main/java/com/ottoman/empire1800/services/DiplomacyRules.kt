package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.CountryState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.PendingEffect
import com.ottoman.empire1800.models.SpyReport
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.countryDef

data class SpyCostPreview(
    val recruitCost: Double,
    val annualUpkeep: Double,
    val upkeepDelta: Double,
)

/** الدبلوماسية: العلاقات، السفارات، المعاهدات، وسقف الاقتراض الخارجي. */
object DiplomacyRules {
    const val EMBASSY_OPEN_SHARE: Double = 0.02
    const val EMBASSY_UPKEEP_SHARE: Double = 0.01
    const val GIFT_SHARE: Double = 0.010
    const val TREATY_SHARE: Double = 0.012
    const val ALLIANCE_SHARE: Double = 0.020
    const val TREATY_CUSTOMS_GAIN: Double = 0.015
    const val TREATY_COOLDOWN: Int = 20

    val spyUpkeep: List<Double> = listOf(0.0, 0.012, 0.030)
    val domesticUpkeep: List<Double> = listOf(0.0, 0.015, 0.035)
    val spyRecruit: List<Double> = listOf(0.0, 0.010, 0.030)
    val domesticRecruit: List<Double> = listOf(0.0, 0.008, 0.024)
    const val SPY_RECRUIT_SHARE: Double = 0.010
    const val DOMESTIC_SPECIAL_OP_SHARE: Double = 0.015
    const val FOREIGN_SPECIAL_OP_SHARE: Double = 0.028
    const val SPECIAL_OP_SHARE: Double = 0.028
    val allianceRivals: Map<String, List<String>> = mapOf(
        "britain" to listOf("france"),
        "france" to listOf("britain", "germany"),
        "russia" to listOf("austria", "persia"),
        "austria" to listOf("russia"),
        "germany" to listOf("france"),
        "persia" to listOf("russia"),
    )

    fun spyCostPreview(
        s: GameState,
        fromLvl: Int,
        toLvl: Int,
        isDomestic: Boolean,
    ): SpyCostPreview {
        val upkeepTable = if (isDomestic) domesticUpkeep else spyUpkeep
        val recruitTable = if (isDomestic) domesticRecruit else spyRecruit
        val from = fromLvl.coerceIn(0, 2)
        val to = toLvl.coerceIn(0, 2)
        val recruitCost = if (to > from) costShare(s, recruitTable[to] - recruitTable[from]) else 0.0
        val annualUpkeep = costShare(s, upkeepTable[to])
        val upkeepDelta = costShare(s, upkeepTable[to] - upkeepTable[from])
        return SpyCostPreview(recruitCost = recruitCost, annualUpkeep = annualUpkeep, upkeepDelta = upkeepDelta)
    }

    private const val CREDIT_REF: Double = 48.0

    fun creditFactor(s: GameState): Double {
        val lenders = listOf("britain", "france", "austria")
        val sum = lenders.sumOf { s.countries[it]?.relation ?: CREDIT_REF }
        val avg = sum / lenders.size
        return clampD(1.0 + (avg - CREDIT_REF) / 100.0, 0.6, 1.25)
    }

    fun relationLabel(r: Double): String = when {
        r < 25.0 -> "عدائية"
        r < 40.0 -> "متوترة"
        r < 60.0 -> "محايدة"
        r < 80.0 -> "ودية"
        else -> "حليفة"
    }

    fun applyRelations(s: GameState, deltas: Map<String, Int>) {
        deltas.forEach { (k, d) ->
            val c = s.countries[k]
            if (c != null) c.relation = clampD(c.relation + d, 0.0, 100.0)
        }
    }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val f = s.f
        for (c in s.countries.values) {
            if (c.embassy) {
                if (c.relation < 60.0) c.relation = clampD(c.relation + 0.3 * f, 0.0, 100.0)
            } else if (c.relation > 45.0) {
                c.relation = clampD(c.relation - 1.0 * f, 45.0, 100.0)
            } else if (c.relation < 45.0) {
                c.relation = clampD(c.relation + 1.0 * f, 0.0, 45.0)
            }
        }
        if (s.army < 30) {
            s.countries["russia"]?.let { it.relation = clampD(it.relation - 1.0 * f, 0.0, 100.0) }
            s.countries["austria"]?.let { it.relation = clampD(it.relation - 0.5 * f, 0.0, 100.0) }
        }
        if (s.foreign > 65) {
            for (c in s.countries.values) {
                c.relation = clampD(c.relation + 0.3 * f, 0.0, 100.0)
            }
        }
        for (c in s.countries.values) {
            if (c.alliance && c.relation < 45.0) {
                c.alliance = false
                s.world = s.world.withAlliances(s.world.alliances.filter { !it.contains(c.name) })
                recordRoyalDecree(s, "فسخ الحلف الدفاعي مع ${c.name}")
                notes.add("انهار الحلف الدفاعي مع ${c.name} لتدهور العلاقات")
            }
            if (c.relation < 20.0) notes.add("علاقاتنا بـ${c.name} في أسوأ حالاتها وخطر الحرب قائم")
        }
        return notes
    }

    fun openEmbassy(s: GameState, key: String): CourtResult {
        val c = s.countries[key] ?: return CourtResult(false, "دولة غير معروفة")
        if (c.embassy) return CourtResult(false, "لنا سفارة في ${c.name} أصلا")
        val cost = costShare(s, EMBASSY_OPEN_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")
        s.treasury -= cost
        s.budget.change("admin", costShare(s, EMBASSY_UPKEEP_SHARE))
        c.embassy = true
        s.courtLog.add("افتتاح سفارة دائمة في ${c.name}")
        return CourtResult(true, "افتتحت سفارة في ${c.name}")
    }

    fun closeEmbassy(s: GameState, key: String): CourtResult {
        val c = s.countries[key] ?: return CourtResult(false, "دولة غير معروفة")
        if (!c.embassy) return CourtResult(false, "لا سفارة لنا في ${c.name}")
        s.budget.change("admin", -costShare(s, EMBASSY_UPKEEP_SHARE))
        c.embassy = false
        c.relation = clampD(c.relation - 4.0, 0.0, 100.0)
        s.courtLog.add("إغلاق السفارة في ${c.name}")
        return CourtResult(true, "أغلقت السفارة في ${c.name} وتضررت العلاقة قليلا")
    }

    fun setSpyLevel(s: GameState, key: String, level: Int): CourtResult {
        val c = s.countries[key] ?: return CourtResult(false, "دولة غير معروفة")
        val to = level.coerceIn(0, 2)
        if (to == c.spy) return CourtResult(true, "لا تغيير")
        if (to > c.spy) {
            val cost = costShare(s, spyRecruit[to] - spyRecruit[c.spy])
            if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لتجنيد العملاء")
            s.treasury -= cost
        }
        s.budget.change("admin", costShare(s, spyUpkeep[to] - spyUpkeep[c.spy]))
        s.courtLog.add("شبكة التجسس على ${c.name}: مستوى $to")
        c.spy = to
        return CourtResult(true, "شبكة التجسس على ${c.name} صارت مستوى $to")
    }

    fun setDomesticSpy(s: GameState, level: Int): CourtResult {
        val to = level.coerceIn(0, 2)
        if (to == s.domesticSpy) return CourtResult(true, "لا تغيير")
        if (to > s.domesticSpy) {
            val cost = costShare(s, domesticRecruit[to] - domesticRecruit[s.domesticSpy])
            if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لتجنيد العملاء")
            s.treasury -= cost
        }
        s.budget.change("admin", costShare(s, domesticUpkeep[to] - domesticUpkeep[s.domesticSpy]))
        s.courtLog.add("الاستخبارات الداخلية: مستوى $to")
        s.domesticSpy = to
        return CourtResult(true, "الاستخبارات الداخلية صارت مستوى $to")
    }

    private fun checkAction(s: GameState, c: CountryState?, cost: Double): CourtResult {
        if (c == null) return CourtResult(false, "دولة غير معروفة")
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")
        return CourtResult(true, "")
    }

    fun gift(s: GameState, key: String): CourtResult {
        val c = s.countries[key]
        val cost = costShare(s, GIFT_SHARE)
        val chk = checkAction(s, c, cost)
        if (!chk.ok) return chk
        s.treasury -= cost
        s.courtActions -= 1
        c!!.relation = clampD(c.relation + 6.0, 0.0, 100.0)
        s.courtLog.add("هدايا دبلوماسية إلى ${c.name}")
        return CourtResult(true, "تحسنت علاقتنا بـ${c.name}")
    }

    fun tradeTreaty(s: GameState, key: String): CourtResult {
        val c = s.countries[key]
        val cost = costShare(s, TREATY_SHARE)
        val chk = checkAction(s, c, cost)
        if (!chk.ok) return chk
        if (c!!.relation < 40.0) return CourtResult(false, "علاقتنا بـ${c.name} أسوأ من أن تقبل معاهدة")
        if (s.round - c.lastTreaty < TREATY_COOLDOWN * s.k) {
            return CourtResult(false, "معاهدتنا مع ${c.name} ما زالت سارية")
        }
        s.treasury -= cost
        s.courtActions -= 1
        c.lastTreaty = s.round
        c.relation = clampD(c.relation + 8.0, 0.0, 100.0)
        if (key == "britain") {
            s.countries["france"]?.let { it.relation = clampD(it.relation - 5.0, 0.0, 100.0) }
        } else if (key == "france") {
            s.countries["britain"]?.let { it.relation = clampD(it.relation - 5.0, 0.0, 100.0) }
        }
        s.pending.add(
            PendingEffect(
                dueRound = s.round + 2 * s.k,
                lines = listOf(
                    LineChange(
                        "customs",
                        costShare(s, TREATY_CUSTOMS_GAIN) * if (ProjectRules.hasPerk(s, "trade")) 1.5 else 1.0,
                    ),
                ),
                source = "معاهدة تجارية مع ${c.name}",
            ),
        )
        s.courtLog.add("معاهدة تجارية مع ${c.name}")
        return CourtResult(true, "وُقعت معاهدة تجارية مع ${c.name}؛ تظهر عوائدها بعد سنة")
    }

    fun signAlliance(s: GameState, key: String): CourtResult {
        val c = s.countries[key] ?: return CourtResult(false, "دولة غير معروفة")
        if (WarRules.warWith(s, key) != null) {
            return CourtResult(false, "لا يمكن توقيع حلف دفاعي أثناء الحرب مع ${c.name}")
        }
        if (c.alliance) {
            return CourtResult(false, "لنا حلف دفاعي قائم مع ${c.name} بالفعل")
        }
        if (c.relation < 80.0) {
            return CourtResult(false, "يتطلب الحلف الدفاعي علاقة لا تقل عن 80 مع ${c.name}")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, ALLIANCE_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لتكاليف الحلف الدفاعي")

        s.treasury -= cost
        s.courtActions -= 1
        c.alliance = true

        val rivals = allianceRivals[key].orEmpty()
        val rivalNames = mutableListOf<String>()
        for (rKey in rivals) {
            val rCountry = s.countries[rKey]
            if (rCountry != null) {
                rCountry.relation = clampD(rCountry.relation - 8.0, 0.0, 100.0)
                rivalNames.add(countryDef(rKey)?.name ?: rKey)
            }
        }

        val entry = "حلف دفاعي مع ${c.name}"
        val nextAlliances = (listOf(entry) + s.world.alliances.filter { it != entry }).take(6)
        s.world = s.world.withAlliances(nextAlliances)
        s.courtLog.add("توقيع حلف دفاعي مع ${c.name}")
        recordRoyalDecree(s, "توقيع حلف دفاعي مع ${c.name}")
        val rivalNote = if (rivalNames.isNotEmpty()) " وتوترت علاقة ${rivalNames.joinToString(" و")} (-8)" else ""
        return CourtResult(
            ok = true,
            message = "وُقع حلف دفاعي مع ${c.name} (+12% قوة قتالية في الحروب)$rivalNote",
        )
    }

    fun domesticSpecialOp(s: GameState): CourtResult {
        if (s.domesticSpy < 1) {
            return CourtResult(false, "فعّل الاستخبارات الداخلية (مستوى 1 على الأقل) أولا")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val active = s.powers.values.filter { !it.dissolved }.sortedByDescending { it.danger }
        if (active.isEmpty()) return CourtResult(false, "لا توجد قوى فاعلة تحتاج عملية سرية")
        val cost = costShare(s, DOMESTIC_SPECIAL_OP_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي للعملية الاستخباراتية")
        s.treasury -= cost
        s.courtActions -= 1

        val top = active.first()
        top.influence = clampD(top.influence - 8.0, 0.0, 100.0)
        val discovered = s.domesticSpy == 1 && rngFor(s, 911 + (stableHash(top.key) % 97)).nextDouble() < 0.35
        if (discovered) {
            s.legitimacy = (s.legitimacy - 2).coerceIn(0, 100)
        }
        s.intel.add(
            SpyReport(
                round = s.round,
                target = "domestic",
                claim = "عملية سرية لتفكيك خلايا ${top.name} (-8 نفوذ)",
                reliability = 100,
                truth = true,
                revealed = true,
            ),
        )
        if (s.intel.size > SpyRules.MAX_KEPT) {
            s.intel.subList(0, s.intel.size - SpyRules.MAX_KEPT).clear()
        }
        s.courtLog.add("عملية استخبارات داخلية ضد ${top.name}")
        return CourtResult(
            ok = true,
            message = if (discovered) {
                "تم اختراق شبكات ${top.name} (-8 نفوذ)، لكن العملية انكشفت جزئيا (-2 شرعية)"
            } else {
                "نجحت العملية السرية ضد ${top.name} وتراجع نفوذها (-8) مع توثيق التقرير"
            },
        )
    }

    fun foreignSpecialOp(s: GameState, key: String): CourtResult {
        val c = s.countries[key] ?: return CourtResult(false, "دولة غير معروفة")
        if (c.spy < 1) {
            return CourtResult(false, "فعّل شبكة التجسس على ${c.name} (مستوى 1 على الأقل) أولا")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, FOREIGN_SPECIAL_OP_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي للعملية الاستخباراتية")
        s.treasury -= cost
        s.courtActions -= 1

        val hostile = c.relation < 35.0
        val claim = if (hostile) {
            "استطلاع مؤكد: ${c.name} تحشد قواتها وتجهز لحرب ضدنا"
        } else {
            "استطلاع مؤكد: ${c.name} لا تحشد لحرب قريبة وتميل للمهادنة"
        }
        s.intel.add(
            SpyReport(
                round = s.round,
                target = key,
                claim = claim,
                reliability = 100,
                truth = true,
                revealed = true,
            ),
        )
        if (s.intel.size > SpyRules.MAX_KEPT) {
            s.intel.subList(0, s.intel.size - SpyRules.MAX_KEPT).clear()
        }

        val war = WarRules.warWith(s, key)
        if (war != null) {
            war.score = clampD(war.score + 5.0, -100.0, 100.0)
        }
        val discovered = c.spy == 1 && rngFor(s, 923 + (stableHash(key) % 97)).nextDouble() < 0.35
        if (discovered) {
            c.relation = clampD(c.relation - 5.0, 0.0, 100.0)
        }
        s.courtLog.add("مهمة استخبارات خاصة في ${c.name}")
        val warNote = if (war != null) " وتعززت نقاط الحرب (+5)" else ""
        val riskNote = if (discovered) " — لكن العميل انكشف (-5 علاقة)" else ""
        return CourtResult(
            ok = true,
            message = "وصل تقرير مؤكد من ${c.name}$warNote$riskNote",
        )
    }
}
