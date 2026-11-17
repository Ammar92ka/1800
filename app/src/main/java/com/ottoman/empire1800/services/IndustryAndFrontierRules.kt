package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.economicSystemDef
import com.ottoman.empire1800.data.economicSystemDefs
import com.ottoman.empire1800.data.effectiveSubProvinceResources
import com.ottoman.empire1800.data.factoryDef
import com.ottoman.empire1800.data.factoryDefs
import com.ottoman.empire1800.data.frontierRegionDef
import com.ottoman.empire1800.data.frontierRegionDefs
import com.ottoman.empire1800.data.subProvinceDef
import com.ottoman.empire1800.data.subProvinceDefs
import com.ottoman.empire1800.data.subProvincesForParent
import com.ottoman.empire1800.models.Budget
import com.ottoman.empire1800.models.EconomyState
import com.ottoman.empire1800.models.FactoryInstance
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.SubProvinceState
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.countryDef
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class CanBuildResult(val ok: Boolean, val reason: String = "")

object IndustryRules {
    const val ECON_SYSTEM_COOLDOWN_HALVES: Int = 8
    const val SUBSIDY_ANNUAL_SHARE: Double = 0.002
    const val MAX_FACTORY_LEVEL: Int = 3

    fun isSubProvinceControlled(econ: EconomyState, subKey: String): Boolean {
        val def = subProvinceDef(subKey) ?: return false
        val core = econ.provinces[def.parentKey]
        if (core != null) return !core.isLost
        val fr = econ.frontierRegions[def.parentKey]
        return fr != null && fr.controlled && !fr.isLost
    }

    fun literacyMultiplier(literacy: Double): Double =
        0.85 + 0.01 * clampD(literacy, 0.0, 80.0)

    fun totalFactoryLevels(econ: EconomyState, type: String? = null): Int {
        var total = 0
        for (sp in econ.subProvinces.values) {
            if (!isSubProvinceControlled(econ, sp.key)) continue
            for (fac in sp.factories) {
                if (type == null || fac.type == type) total += fac.level
            }
        }
        return total
    }

    fun hasCoalPower(econ: EconomyState): Boolean = totalFactoryLevels(econ, "coal_mine") > 0

    fun paperMillAdminDiscount(econ: EconomyState): Double =
        min(0.25, totalFactoryLevels(econ, "paper_mill") * 0.05)

    fun armsPowerBonus(econ: EconomyState): Double =
        min(0.15, totalFactoryLevels(econ, "arms_factory") * 0.025)

    fun armsCostDiscount(econ: EconomyState): Double =
        min(0.20, totalFactoryLevels(econ, "arms_factory") * 0.035)

    fun canBuildInSubProvince(
        econ: EconomyState,
        subKey: String,
        type: String,
        year: Int,
    ): CanBuildResult {
        val sDef = subProvinceDef(subKey)
        val fDef = factoryDef(type)
        if (sDef == null || fDef == null) {
            return CanBuildResult(false, "سنجق أو مصنع غير معروف")
        }
        if (!isSubProvinceControlled(econ, subKey)) {
            return CanBuildResult(false, "هذا السنجق خارج سيطرة الدولة حاليا")
        }
        val res = effectiveSubProvinceResources(sDef, year, econ.oilDiscovered)
        if (fDef.requiresResources.isNotEmpty() && fDef.requiresResources.none { res.contains(it) }) {
            return CanBuildResult(false, "يتطلب ${fDef.name} موارد مناسبة غير متوفرة في ${sDef.name}")
        }
        val sp = econ.subProvinces[subKey]
        val existing = sp?.factories?.firstOrNull { it.type == type }
        if (existing != null) {
            if (existing.level >= MAX_FACTORY_LEVEL) {
                return CanBuildResult(false, "وصل ${fDef.name} في ${sDef.name} للمستوى الأقصى ($MAX_FACTORY_LEVEL)")
            }
            return CanBuildResult(true)
        }
        val usedSlots = sp?.factories?.size ?: 0
        if (usedSlots >= sDef.slots) {
            return CanBuildResult(false, "استُنفدت خانات المصانع في ${sDef.name} ($usedSlots/${sDef.slots})")
        }
        return CanBuildResult(true)
    }

    fun factoryBuildCost(s: GameState, subKey: String, type: String): Double {
        val fDef = factoryDef(type) ?: return 0.0
        val sp = s.econ.subProvinces[subKey]
        val existing = sp?.factories?.firstOrNull { it.type == type }
        val nextLevel = min(MAX_FACTORY_LEVEL, (existing?.level ?: 0) + 1)
        val levelMult = 1.0 + (nextLevel - 1) * 0.45
        val sysMult = economicSystemDef(s.econ.economicSystem).buildCostMult
        return costShare(s, fDef.costShare * levelMult * sysMult)
    }

    fun econSystemCooldownRemaining(s: GameState): Int {
        val last = s.econ.lastEconSystemRound
        if (last < 0) return 0
        val rem = ECON_SYSTEM_COOLDOWN_HALVES * s.k - (s.round - last)
        return if (rem > 0) rem else 0
    }

    fun setEconomicSystem(s: GameState, key: String): CourtResult {
        val def = economicSystemDefs.firstOrNull { it.key == key }
            ?: return CourtResult(false, "نظام اقتصادي غير معروف")
        if (s.econ.economicSystem == key) {
            return CourtResult(false, "نظام \"${def.name}\" مطبق بالفعل")
        }
        val rem = econSystemCooldownRemaining(s)
        if (rem > 0) {
            return CourtResult(false, "لا يمكن تغيير النظام الاقتصادي قبل مرور $rem ${periodPlural(s.ppy)}")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        s.courtActions -= 1
        s.econ.economicSystem = key
        s.econ.lastEconSystemRound = s.round

        if (key != "interventionism") {
            for (sp in s.econ.subProvinces.values) {
                for (fac in sp.factories) fac.subsidized = false
            }
        }

        fun adjPower(pKey: String, delta: Double) {
            s.powers[pKey]?.let {
                if (!it.dissolved) it.satisfaction = clampD(it.satisfaction + delta, 0.0, 100.0)
            }
        }
        fun adjCountry(cKey: String, delta: Double) {
            s.countries[cKey]?.let { it.relation = clampD(it.relation + delta, 0.0, 100.0) }
        }

        when (key) {
            "laissez_faire" -> {
                adjPower("merchants", 6.0)
                adjPower("tax_farmers", -3.0)
                adjPower("janissaries", -3.0)
            }
            "protectionist" -> {
                adjPower("merchants", 3.0)
                adjCountry("britain", -4.0)
                adjCountry("france", -4.0)
            }
            "interventionism" -> {
                adjPower("merchants", 2.0)
                adjPower("governors", 2.0)
            }
            else -> {
                adjPower("governors", 3.0)
                adjPower("merchants", -3.0)
            }
        }

        s.courtLog.add("تحول النظام الاقتصادي إلى: ${def.name}")
        recordRoyalDecree(s, "اعتماد نظام ${def.name}")
        return CourtResult(true, "اعتمدت الدولة نظام ${def.name} (${def.bonus})")
    }

    fun buildOrUpgradeFactory(s: GameState, subKey: String, type: String): CourtResult {
        val sys = economicSystemDef(s.econ.economicSystem)
        if (!sys.canStateBuild) {
            return CourtResult(
                false,
                "السوق الحر يمنع الدولة من بناء أو ترقية المصانع مباشرة؛ التجار يتولون الاستثمار أو غيّر النظام الاقتصادي",
            )
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val chk = canBuildInSubProvince(s.econ, subKey, type, s.year)
        if (!chk.ok) return CourtResult(false, chk.reason.ifEmpty { "تعذر البناء" })
        val cost = factoryBuildCost(s, subKey, type)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لبناء المصنع")

        val sp = s.econ.subProvinces.getOrPut(subKey) { SubProvinceState(subKey) }
        val fDef = factoryDef(type)!!
        val sDef = subProvinceDef(subKey)!!
        val existing = sp.factories.firstOrNull { it.type == type }

        s.treasury -= cost
        s.courtActions -= 1

        if (existing != null) {
            existing.level += 1
            s.courtLog.add("توسعة ${fDef.name} في ${sDef.name} (مستوى ${existing.level})")
            return CourtResult(true, "تمت ترقية ${fDef.name} في ${sDef.name} إلى المستوى ${existing.level}")
        }
        sp.factories.add(FactoryInstance(type = type, level = 1, subsidized = false))
        s.courtLog.add("إنشاء ${fDef.name} في ${sDef.name}")
        return CourtResult(true, "أُنشئ ${fDef.name} (المستوى 1) في ${sDef.name}")
    }

    fun demolishFactory(s: GameState, subKey: String, type: String): CourtResult {
        if (!isSubProvinceControlled(s.econ, subKey)) {
            return CourtResult(false, "هذا السنجق خارج السيطرة")
        }
        val sp = s.econ.subProvinces[subKey]
        val idx = sp?.factories?.indexOfFirst { it.type == type } ?: -1
        if (sp == null || idx < 0) return CourtResult(false, "لا يوجد هذا المصنع في السنجق")
        val fac = sp.factories[idx]
        val fDef = factoryDef(type)!!
        val sDef = subProvinceDef(subKey)!!
        val scrap = costShare(s, fDef.costShare * 0.2 * fac.level)
        sp.factories.removeAt(idx)
        s.treasury += scrap
        s.courtLog.add("تفكيك ${fDef.name} في ${sDef.name}")
        val scrapFmt = String.format(Locale.US, "%,d", scrap.roundToInt())
        return CourtResult(true, "فُكك ${fDef.name} في ${sDef.name} وأُعيد $scrapFmt قرش للخزينة")
    }

    fun toggleSubsidy(s: GameState, subKey: String, type: String): CourtResult {
        if (s.econ.economicSystem != "interventionism") {
            return CourtResult(false, "دعم المصانع متاح فقط في نظام الاقتصاد المختلط والتدخل")
        }
        if (!isSubProvinceControlled(s.econ, subKey)) {
            return CourtResult(false, "هذا السنجق خارج السيطرة")
        }
        val fac = s.econ.subProvinces[subKey]?.factories?.firstOrNull { it.type == type }
            ?: return CourtResult(false, "المصنع غير موجود")
        fac.subsidized = !fac.subsidized
        val fDef = factoryDef(type)!!
        return CourtResult(
            true,
            if (fac.subsidized) "فُعّل الدعم المالي لـ${fDef.name}" else "أُلغي الدعم المالي عن ${fDef.name}",
        )
    }

    fun annualFactoryYield(
        econ: EconomyState,
        b: Budget,
        year: Int,
        literacy: Double,
        subKey: String,
        fac: FactoryInstance,
    ): Double {
        val sDef = subProvinceDef(subKey) ?: return 0.0
        val fDef = factoryDef(fac.type) ?: return 0.0
        val res = effectiveSubProvinceResources(sDef, year, econ.oilDiscovered)
        val resMatch = if (fDef.bonusResources.any { res.contains(it) }) 1.30 else 1.0
        val coalMult = if (hasCoalPower(econ)) 1.25 else 1.0
        val litMult = literacyMultiplier(literacy)
        val sysMult = economicSystemDef(econ.economicSystem).yieldMult
        return b.totalRevenue * fDef.baseYieldShare * fac.level * resMatch * coalMult * litMult * sysMult
    }

    fun annualFactoryRevenue(
        econ: EconomyState,
        b: Budget,
        year: Int = 1800,
        literacy: Double = 4.0,
    ): Double {
        var sum = 0.0
        for (sp in econ.subProvinces.values) {
            if (!isSubProvinceControlled(econ, sp.key)) continue
            for (fac in sp.factories) {
                sum += annualFactoryYield(econ, b, year, literacy, sp.key, fac)
            }
        }
        return sum
    }

    fun annualSubsidyCost(econ: EconomyState, b: Budget): Double {
        if (econ.economicSystem != "interventionism") return 0.0
        val rev = b.totalRevenue
        var total = 0.0
        for (sp in econ.subProvinces.values) {
            if (!isSubProvinceControlled(econ, sp.key)) continue
            for (fac in sp.factories) {
                if (fac.subsidized) total += rev * SUBSIDY_ANNUAL_SHARE * fac.level
            }
        }
        return total
    }

    fun clearSubProvinceFactoriesForParent(econ: EconomyState, parentKey: String) {
        for (def in subProvincesForParent(parentKey)) {
            econ.subProvinces[def.key]?.factories?.clear()
        }
    }

    private data class Candidate(val subKey: String, val type: String, val isUpgrade: Boolean, val score: Int)

    private fun privateAutoInvest(s: GameState, rng: Random, reasonPrefix: String): String? {
        val year = s.year
        val candidates = mutableListOf<Candidate>()
        for (spDef in subProvinceDefs) {
            if (!isSubProvinceControlled(s.econ, spDef.key)) continue
            val res = effectiveSubProvinceResources(spDef, year, s.econ.oilDiscovered)
            for (fDef in factoryDefs) {
                val chk = canBuildInSubProvince(s.econ, spDef.key, fDef.key, year)
                if (!chk.ok) continue
                val sp = s.econ.subProvinces[spDef.key]
                val existing = sp?.factories?.firstOrNull { it.type == fDef.key }
                val bonusMatch = if (fDef.bonusResources.any { res.contains(it) }) 3 else 0
                val baseScore = (if (existing != null) 2 else 1) +
                    bonusMatch +
                    (if (fDef.key == "textile" || fDef.key == "trade_depot") 1 else 0)
                candidates.add(Candidate(spDef.key, fDef.key, existing != null, baseScore))
            }
        }
        if (candidates.isEmpty()) return null
        candidates.sortByDescending { it.score }
        val topSlice = candidates.take(min(4, candidates.size))
        val pick = topSlice[rng.nextInt(topSlice.size)]
        val sp = s.econ.subProvinces.getOrPut(pick.subKey) { SubProvinceState(pick.subKey) }
        val fDef = factoryDef(pick.type)!!
        val sDef = subProvinceDef(pick.subKey)!!
        val existing = sp.factories.firstOrNull { it.type == pick.type }
        if (existing != null) {
            existing.level += 1
            return "$reasonPrefix: وسّع التجار ${fDef.name} في ${sDef.name} (مستوى ${existing.level})"
        }
        sp.factories.add(FactoryInstance(type = pick.type, level = 1, subsidized = false))
        return "$reasonPrefix: افتتح التجار ${fDef.name} في ${sDef.name}"
    }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val f = s.f
        val year = s.year

        // 1. Oil discoveries in Mosul (1890+) & Baku/Caucasus (1875+)
        for (def in subProvinceDefs) {
            if (
                def.oilFromYear != null &&
                year >= def.oilFromYear &&
                !s.econ.oilDiscovered.contains(def.key) &&
                isSubProvinceControlled(s.econ, def.key)
            ) {
                s.econ.oilDiscovered.add(def.key)
                s.econ.provinces[def.parentKey]?.let { core ->
                    if (!core.isLost) core.mood = clampD(core.mood + 4.0, 0.0, 100.0)
                }
                s.econ.frontierRegions[def.parentKey]?.let { fr ->
                    if (fr.controlled && !fr.isLost) fr.mood = clampD(fr.mood + 4.0, 0.0, 100.0)
                }
                notes.add("اكتشاف النفط في ${def.name} ($year)! تضاعفت قيمة مناجم المنطقة.")
                recordRoyalDecree(s, "اكتشاف النفط في ${def.name}")
            }
        }

        // 2. Paper Mill literacy growth
        val totalPaper = totalFactoryLevels(s.econ, "paper_mill")
        if (totalPaper > 0) {
            s.demog.literacy = clampD(s.demog.literacy + 0.015 * totalPaper * f, 1.0, 80.0)
        }

        // 3. Food Mill grain moderation & local mood boost
        val totalFood = totalFactoryLevels(s.econ, "food_mill")
        if (totalFood > 0 && s.econ.grain > 105.0) {
            s.econ.grain = clampD(s.econ.grain - min(8.0, 1.2 * totalFood) * f, 60.0, 260.0)
        }
        for (sp in s.econ.subProvinces.values) {
            if (!isSubProvinceControlled(s.econ, sp.key)) continue
            val foodLvls = sp.factories.filter { it.type == "food_mill" }.sumOf { it.level }
            if (foodLvls <= 0) continue
            val parentKey = subProvinceDef(sp.key)?.parentKey ?: continue
            s.econ.provinces[parentKey]?.let { core ->
                if (!core.isLost) core.mood = clampD(core.mood + 0.35 * foodLvls * f, 0.0, 100.0)
            }
            s.econ.frontierRegions[parentKey]?.let { fr ->
                if (fr.controlled && !fr.isLost) fr.mood = clampD(fr.mood + 0.35 * foodLvls * f, 0.0, 100.0)
            }
        }

        // 4. Economic system dynamics
        when (s.econ.economicSystem) {
            "laissez_faire" -> {
                s.powers["merchants"]?.let { merchants ->
                    if (!merchants.dissolved) {
                        merchants.satisfaction = clampD(merchants.satisfaction + 0.8 * f, 0.0, 100.0)
                    }
                }
                val rngBuild = rngFor(s, 841)
                if (!inGrace(s) && rngBuild.nextDouble() < 0.45 * f) {
                    privateAutoInvest(s, rngBuild, "استثمار السوق الحر")?.let { notes.add(it) }
                }
                val hardTimes = s.econ.grain >= 140.0 || s.stability < 42 || s.econ.inflation >= 8.0
                if (hardTimes && !hasCoalPower(s.econ) && rngFor(s, 842).nextDouble() < 0.12 * f) {
                    for (sp in s.econ.subProvinces.values) {
                        if (!isSubProvinceControlled(s.econ, sp.key)) continue
                        val sDef = subProvinceDef(sp.key) ?: continue
                        val res = effectiveSubProvinceResources(sDef, year, s.econ.oilDiscovered)
                        val idx = sp.factories.indexOfFirst { fac ->
                            val fd = factoryDef(fac.type)
                            fac.level == 1 && !fac.subsidized && fd != null && fd.bonusResources.none { res.contains(it) }
                        }
                        if (idx >= 0) {
                            val removed = sp.factories.removeAt(idx)
                            val fName = factoryDef(removed.type)?.name ?: removed.type
                            notes.add("أُغلقت ورشة $fName في ${sDef.name} تحت ضغط الأزمة وغياب الحماية")
                            break
                        }
                    }
                }
            }
            "interventionism" -> {
                val merchants = s.powers["merchants"]
                val rngCo = rngFor(s, 843)
                if (!inGrace(s) && merchants != null && !merchants.dissolved && merchants.satisfaction >= 60.0 && rngCo.nextDouble() < 0.20 * f) {
                    privateAutoInvest(s, rngCo, "استثمار مشترك")?.let { notes.add(it) }
                }
            }
            "protectionist" -> {
                s.countries["britain"]?.let { it.relation = clampD(it.relation - 1.0 * f, 0.0, 100.0) }
                s.countries["france"]?.let { it.relation = clampD(it.relation - 1.0 * f, 0.0, 100.0) }
            }
        }

        return notes
    }
}

object FrontierRules {
    fun conquer(s: GameState, key: String, reason: String = ""): String {
        val def = frontierRegionDef(key) ?: return ""
        val fr = s.econ.frontierRegions[key] ?: return ""
        if (fr.controlled) return ""
        fr.controlled = true
        fr.status = ProvinceState.LOYAL
        fr.mood = def.mood
        fr.autonomy = def.autonomy
        fr.harvest = 1.0
        fr.harvestRounds = 0

        s.foreign = (s.foreign + 3).coerceIn(0, 100)
        s.legitimacy = (s.legitimacy + 2).coerceIn(0, 100)
        s.demog.population = clampD(s.demog.population * (1.0 + def.weight * 0.75), 1.0, 500.0)

        val year = s.year
        for (spDef in subProvincesForParent(def.key)) {
            if (spDef.oilFromYear != null && year >= spDef.oilFromYear && !s.econ.oilDiscovered.contains(spDef.key)) {
                s.econ.oilDiscovered.add(spDef.key)
            }
        }
        val suffix = if (reason.isNotEmpty()) " ($reason)" else ""
        recordRoyalDecree(s, "ضم إقليم ${def.name} إلى الدولة$suffix")
        s.courtLog.add("فتح وضم إقليم ${def.name}")
        return "؛ وتم فتح وضم إقليم ${def.name} إلى أملاك الدولة!"
    }

    fun onDecisiveVictory(s: GameState, enemy: String): String {
        for (def in frontierRegionDefs) {
            val fr = s.econ.frontierRegions[def.key] ?: continue
            if (fr.controlled) continue
            if (!def.conquerEnemies.contains(enemy)) continue
            if (def.requiresRegion != null && s.econ.frontierRegions[def.requiresRegion]?.controlled != true) continue
            val eName = countryDef(enemy)?.name ?: enemy
            return conquer(s, def.key, "انتصار حاسم على $eName")
        }
        return ""
    }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val f = s.f
        val diff = s.diff
        val rng = rngFor(s, 851)
        val atWar = s.wars.isNotEmpty()
        val stabTotal = s.stability

        for (fr in s.econ.frontierRegions.values) {
            if (!fr.controlled || fr.isLost) continue
            val def = fr.def

            var dMood = 0.05 * (stabTotal - 55) + 0.03 * (def.mood - fr.mood)
            if (s.econ.grain > 130.0) dMood -= (s.econ.grain - 130.0) / 40.0
            else if (s.econ.grain < 90.0) dMood += 0.5
            fr.mood = clampD(fr.mood + dMood * f, 0.0, 100.0)

            var factor = 0.4 + (50.0 - fr.mood) / 50.0 + (55.0 - s.legitimacy) / 100.0
            if (atWar) factor += 0.25
            if (s.army > 60) factor -= 0.15
            var gain = def.sep * max(-0.3, factor)
            if (gain > 0.0) gain *= diff.events
            fr.autonomy = clampD(fr.autonomy + gain * f, 0.0, 100.0)

            if (fr.status == ProvinceState.LOYAL && fr.autonomy >= ProvinceRules.AUTONOMOUS_AT) {
                fr.status = ProvinceState.AUTONOMOUS_STATUS
                notes.add("أعلن إقليم ${def.name} حكما ذاتيا وصار يدفع جزءا من إيراده فقط")
            } else if (fr.status == ProvinceState.AUTONOMOUS_STATUS && fr.autonomy < ProvinceRules.LOYAL_BELOW) {
                fr.status = ProvinceState.LOYAL
                notes.add("عاد إقليم ${def.name} إلى الولاء الكامل")
            }

            if (!inGrace(s) && fr.autonomy >= ProvinceRules.SECESSION_AT && rng.nextDouble() < 0.25 * diff.events * f) {
                fr.status = ProvinceState.INDEPENDENT
                fr.autonomy = 100.0
                IndustryRules.clearSubProvinceFactoriesForParent(s.econ, fr.key)
                s.legitimacy = (s.legitimacy - 3).coerceIn(0, 100)
                s.stab.shift("regional", -2.0)
                notes.add("انفصل إقليم ${def.name} عن السيادة العثمانية بعد تصاعد التمرد!")
            }
        }
        return notes
    }
}
