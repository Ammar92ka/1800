package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.CountryState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.War
import com.ottoman.empire1800.models.clampD
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** نظام الحروب: تحسبه اللعبة بالأرقام (قوة جيشنا وقوة العدو ونقاط الحرب). */
object WarRules {
    const val MAX_WARS: Int = 2
    const val EXHAUSTION_HALVES: Int = 14
    const val PEACE_SHARE: Double = 0.005

    val frontProvince: Map<String, String> = mapOf(
        "russia" to "rumelia",
        "austria" to "rumelia",
        "germany" to "rumelia",
        "britain" to "egypt",
        "france" to "egypt",
        "persia" to "iraq",
    )

    fun warWith(s: GameState, enemy: String): War? = s.wars.firstOrNull { it.enemy == enemy }

    fun ourPower(s: GameState): Double {
        var tech = 1.0 + 0.004 * (s.education - 15)
        tech += (ProjectRules.perkCount(s, "tech") * 0.08).coerceIn(0.0, 0.2)
        val logistics = 1.0 + (ProjectRules.perkCount(s, "logistics") * 0.05).coerceIn(0.0, 0.2)
        val morale = 0.8 + 0.4 * s.stability / 100.0
        val supply = if (s.treasury > 0.0) 1.0 else 0.85
        val vizierMult = if (s.grandVizier == "military") 1.10 else 1.0
        val allyCount = s.countries.values.count { it.alliance && warWith(s, it.key) == null }
        val allianceMult = 1.0 + clampD(allyCount * 0.12, 0.0, 0.30)
        val armsMult = 1.0 + IndustryRules.armsPowerBonus(s.econ)
        val p = s.army * tech * logistics * morale * supply * vizierMult * allianceMult * armsMult
        return max(1.0, p)
    }

    fun enemyPower(c: CountryState): Double =
        c.military * (0.8 + 0.2 * (c.economy / 100.0).coerceIn(0.3, 3.0))

    fun ratio(s: GameState, c: CountryState): Double {
        val our = ourPower(s)
        return our / (our + enemyPower(c))
    }

    fun costs(s: GameState): Double {
        var total = 0.0
        val discount = 1.0 - IndustryRules.armsCostDiscount(s.econ)
        for (w in s.wars) {
            val c = s.countries[w.enemy] ?: continue
            val r = enemyPower(c) / ourPower(s)
            total += s.budget.totalRevenue * 0.04 * r.coerceIn(0.5, 2.0) * s.f * discount
        }
        return total
    }

    fun declare(s: GameState, enemy: String, byUs: Boolean): String {
        val c = s.countries[enemy]
        if (c == null || warWith(s, enemy) != null || s.wars.size >= MAX_WARS) return ""
        s.wars.add(War(enemy, s.round, byUs = byUs))
        c.relation = clampD(c.relation - 10.0, 0.0, 100.0)
        if (c.alliance) {
            c.alliance = false
            s.world = s.world.withAlliances(s.world.alliances.filter { !it.contains(c.name) })
            recordRoyalDecree(s, "فسخ الحلف الدفاعي مع ${c.name} لاندلاع الحرب")
        }
        return if (byUs) "أعلنت الدولة الحرب على ${c.name}" else "أعلنت ${c.name} الحرب علينا"
    }

    fun describe(w: War): String = when {
        w.score >= 30.0 -> "تميل لصالحنا"
        w.score <= -30.0 -> "تميل ضدنا"
        else -> "متكافئة"
    }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val rng = rngFor(s, 31)
        val f = s.f
        val k = s.k

        for (w in s.wars.toList()) {
            val c = s.countries[w.enemy]
            if (c == null) {
                s.wars.remove(w)
                continue
            }
            w.halves += 1
            val r = ratio(s, c)
            var allyBonus = 0.0
            for (o in s.countries.values) {
                if (o.key != w.enemy && warWith(s, o.key) == null) {
                    if (o.alliance) allyBonus += 4.0
                    else if (o.relation >= 75.0) allyBonus += 2.0
                }
            }
            val defenseBonus = if (w.byUs) 0.0 else 3.0 + (if (ProjectRules.hasPerk(s, "defense")) 2.0 else 0.0)
            val delta = ((r - 0.5) * 30.0 +
                defenseBonus -
                w.halves * f * 0.3 +
                allyBonus +
                (rng.nextDouble() * 12.0 - 6.0)) * f
            w.score = clampD(w.score + delta, -100.0, 100.0)

            val loss = (0.5 + 1.5 * (1.0 - r)) * f
            val whole = floor(loss).toInt()
            val extra = if (rng.nextDouble() < (loss - whole)) 1 else 0
            s.army = (s.army - whole - extra).coerceIn(0, 100)
            s.demog.population = clampD(s.demog.population * (1.0 - 0.0015 * (1.0 - r) * f), 1.0, 500.0)
            s.stab.shift("social", -0.5 * f)
            c.relation = clampD(c.relation - 1.0 * f, 0.0, 100.0)
            if (w.halves % (4 * k) == 0) {
                if (w.score >= 40.0) s.legitimacy = (s.legitimacy + 1).coerceIn(0, 100)
                if (w.score <= -30.0) s.legitimacy = (s.legitimacy - 1).coerceIn(0, 100)
            }
            if (w.halves % (2 * k) == 0) {
                if (w.score >= 30.0) s.foreign = (s.foreign + 1).coerceIn(0, 100)
                if (w.score <= -30.0) s.foreign = (s.foreign - 1).coerceIn(0, 100)
            }
            notes.add("الحرب مع ${c.name}: ${describe(w)} (نقاط ${w.score.roundToInt()})")

            if (w.score >= 60.0) {
                notes.add(conclude(s, w, w.score))
            } else if (w.score <= -60.0) {
                notes.add(conclude(s, w, w.score))
            } else if (w.halves >= EXHAUSTION_HALVES * k) {
                notes.add(conclude(s, w, 0.0))
            }
        }

        if (!inGrace(s)) {
            var i = 0
            for (c in s.countries.values) {
                i++
                if (s.wars.size >= MAX_WARS || warWith(s, c.key) != null || c.relation >= 22.0) continue
                val strength = (enemyPower(c) / ourPower(s)).coerceIn(0.6, 2.0)
                val p = (0.015 + (22.0 - c.relation) * 0.004) * strength * s.diff.events * f
                if (rngFor(s, 800 + i).nextDouble() < p) {
                    val msg = declare(s, c.key, byUs = false)
                    if (msg.isNotEmpty()) notes.add(msg)
                }
            }
        }
        return notes
    }

    fun seekPeace(s: GameState, enemy: String): CourtResult {
        val w = warWith(s, enemy) ?: return CourtResult(false, "لا حرب مع هذه الدولة")
        if (w.halves < 2 * s.k) {
            return CourtResult(false, "لا يقبل العدو التفاوض في الشهور الأولى")
        }
        val cost = costShare(s, PEACE_SHARE)
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")
        s.treasury -= cost
        s.courtActions -= 1
        s.courtLog.add("طلب الصلح من ${w.enemyName}")
        val text = conclude(s, w, w.score + 15.0)
        return CourtResult(true, text)
    }

    fun peaceFromDecision(s: GameState, enemy: String): String {
        val w = warWith(s, enemy) ?: return ""
        return conclude(s, w, w.score + 15.0)
    }

    private fun target(s: GameState, enemy: String): ProvinceState? {
        val key = frontProvince[enemy]
        val p = if (key == null) null else s.econ.provinces[key]
        if (p != null && !p.isLost) return p
        var best: ProvinceState? = null
        for (q in s.econ.provinces.values) {
            if (q.isLost || q.key == "anatolia") continue
            if (best == null || q.def.sep > best.def.sep) best = q
        }
        return best
    }

    fun conclude(s: GameState, w: War, x: Double): String {
        val c = s.countries[w.enemy]!!
        val rev = s.budget.totalRevenue
        val name = c.name
        val text: String
        if (x >= 60.0) {
            s.treasury += rev * 0.15
            s.foreign = (s.foreign + 4).coerceIn(0, 100)
            s.legitimacy = (s.legitimacy + 4).coerceIn(0, 100)
            c.relation = 25.0
            c.military = clampD(c.military * 0.9, 8.0, 300.0)
            s.wonMajorWar = true
            MilestoneRules.evaluate(s)
            val conquerNote = FrontierRules.onDecisiveVictory(s, w.enemy)
            text = "انتصرنا على $name وفرضنا عليها تعويضا وصلحا مشرفا$conquerNote"
        } else if (x >= 20.0) {
            s.treasury += rev * 0.05
            s.foreign = (s.foreign + 2).coerceIn(0, 100)
            c.relation = 30.0
            s.wonMajorWar = true
            MilestoneRules.evaluate(s)
            text = "صلح مع $name بشروط لصالحنا"
        } else if (x > -20.0) {
            c.relation = 30.0
            text = "صلح مع $name بلا غالب ولا مغلوب"
        } else if (x > -60.0) {
            DebtRules.addForeign(s, rev * 0.12)
            s.foreign = (s.foreign - 3).coerceIn(0, 100)
            s.legitimacy = (s.legitimacy - 3).coerceIn(0, 100)
            val p = target(s, w.enemy)
            if (p != null) p.autonomy = clampD(p.autonomy + 25.0, 0.0, 100.0)
            c.relation = 30.0
            text = "صلح مع $name بشروط قاسية: تعويض حرب وامتيازات في الحدود"
        } else {
            DebtRules.addForeign(s, rev * 0.25)
            s.foreign = (s.foreign - 6).coerceIn(0, 100)
            s.legitimacy = (s.legitimacy - 6).coerceIn(0, 100)
            s.stab.shift("political", -4.0)
            s.army = (s.army - 6).coerceIn(0, 100)
            val p = target(s, w.enemy)
            val lost = if (p == null) "" else "؛ ${ProvinceRules.lose(s, p, how = "cession")}"
            c.relation = 30.0
            text = "هزيمة أمام $name وصلح مذل: تعويض ثقيل$lost"
        }
        s.wars.remove(w)
        s.countries[w.enemy]?.let { it.military = clampD(it.military, 8.0, 300.0) }
        return text
    }

    fun worstRatio(s: GameState): Double {
        var worst = 1.0
        for (c in s.countries.values) {
            val r = ForeignRules.milRatio(s, c)
            if (r > worst) worst = r
        }
        return worst
    }
}
