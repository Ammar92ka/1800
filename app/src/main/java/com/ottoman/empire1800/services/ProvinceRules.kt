package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.clampD
import kotlin.math.max

/** الولايات: ميل الانفصال، الحكم الذاتي، التمرد، والاستقلال أو الفقدان. */
object ProvinceRules {
    const val AUTONOMOUS_AT: Double = 70.0
    const val LOYAL_BELOW: Double = 50.0
    const val SECESSION_AT: Double = 95.0

    const val SUPPRESS_SHARE: Double = 0.020
    const val PRIVILEGES_SHARE: Double = 0.015
    const val DISMEMBERED_BELOW: Double = 0.40

    fun lost(s: GameState): List<ProvinceState> =
        s.econ.provinces.values.filter { it.isLost }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val rng = rngFor(s, 21)
        val atWar = s.wars.isNotEmpty()
        val threatened = s.countries.values.any { it.relation < 30.0 && it.military > s.army }
        val governorsMerged = s.powers["governors"]?.merged == true
        val tribesMerged = s.powers["tribes"]?.merged == true
        for (p in s.econ.provinces.values) {
            if (p.isLost) continue
            val d = p.def
            var f = 0.5 + (50.0 - p.mood) / 50.0 + (55.0 - s.legitimacy) / 100.0
            if (atWar) f += 0.3
            if (threatened && (p.key == "rumelia" || p.key == "egypt" || p.key == "syria")) f += 0.2
            if (s.army > 60) f -= 0.15
            if (governorsMerged) f -= 0.15
            if (tribesMerged && (p.key == "syria" || p.key == "iraq" || p.key == "hijaz")) f -= 0.20
            if (p.key == "egypt") {
                val m = s.powers["mamluks"]
                if (m != null && !m.dissolved) {
                    f += (m.influence - 50.0) / 200.0
                } else if (m != null && m.dissolved) {
                    f -= if (m.merged) 0.25 else 0.35
                }
            }
            var gain = d.sep * max(-0.3, f)
            if (gain > 0.0) gain *= s.diff.events
            p.autonomy = clampD(p.autonomy + gain * s.f, 0.0, 100.0)

            if (p.status == ProvinceState.LOYAL && p.autonomy >= AUTONOMOUS_AT) {
                p.status = ProvinceState.AUTONOMOUS_STATUS
                notes.add("أعلنت ${p.name} حكما ذاتيا وصارت تدفع جزءا من إيرادها فقط")
            } else if (p.status == ProvinceState.AUTONOMOUS_STATUS && p.autonomy < LOYAL_BELOW) {
                p.status = ProvinceState.LOYAL
                notes.add("عادت ${p.name} إلى الولاء الكامل")
            }

            if (!inGrace(s) && p.autonomy >= SECESSION_AT) {
                if (rng.nextDouble() < 0.30 * s.diff.events * s.f) {
                    val crushed = s.army >= 55 && s.wars.isEmpty()
                    if (crushed) {
                        p.autonomy = 70.0
                        p.mood = clampD(p.mood - 10.0, 0.0, 100.0)
                        s.treasury = max(0.0, s.treasury - costShare(s, 0.05))
                        s.stab.shift("political", -2.0)
                        s.army = (s.army - 2).coerceIn(0, 100)
                        notes.add("قمع جيشنا محاولة انفصال ${p.name} بكلفة باهظة")
                    } else {
                        notes.add(lose(s, p, how = "independence"))
                    }
                }
            }
        }
        return notes
    }

    fun lose(s: GameState, p: ProvinceState, how: String): String {
        p.status = ProvinceState.INDEPENDENT
        p.autonomy = 100.0
        IndustryRules.clearSubProvinceFactoriesForParent(s.econ, p.key)
        s.demog.population = clampD(s.demog.population * (1.0 - p.def.weight * 0.9), 1.0, 500.0)
        s.legitimacy = (s.legitimacy - 6).coerceIn(0, 100)
        s.foreign = (s.foreign - 3).coerceIn(0, 100)
        s.stab.shift("regional", -4.0)
        s.stab.shift("political", -3.0)
        return if (how == "cession") {
            "تنازلت الدولة عن ${p.name} بمعاهدة الصلح"
        } else {
            "انفصلت ${p.name} واستقلت عن الدولة"
        }
    }

    fun suppress(s: GameState, key: String): CourtResult {
        val core = s.econ.provinces[key]
        val fr = if (core == null) s.econ.frontierRegions[key] else null
        if (core == null && fr == null) return CourtResult(false, "ولاية غير معروفة")
        if (fr != null && !fr.controlled) return CourtResult(false, "هذا الإقليم خارج سيطرة الدولة")
        val name = core?.name ?: fr!!.name
        val isLost = core?.isLost ?: fr!!.isLost
        if (isLost) return CourtResult(false, "$name مفقودة")
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, SUPPRESS_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")

        s.treasury -= cost
        s.courtActions -= 1
        if (core != null) {
            core.autonomy = clampD(core.autonomy - 15.0, 0.0, 100.0)
            core.mood = clampD(core.mood - 5.0, 0.0, 100.0)
        } else if (fr != null) {
            fr.autonomy = clampD(fr.autonomy - 15.0, 0.0, 100.0)
            fr.mood = clampD(fr.mood - 5.0, 0.0, 100.0)
        }
        s.army = (s.army - 1).coerceIn(0, 100)
        var dipNote = ""
        if (key == "rumelia") {
            s.countries["russia"]?.let { it.relation = clampD(it.relation - 4.0, 0.0, 100.0) }
            s.countries["austria"]?.let { it.relation = clampD(it.relation - 4.0, 0.0, 100.0) }
            dipNote = " وتوترت العلاقات مع روسيا والنمسا (-4)"
        }
        s.courtLog.add("قمع الاضطراب في $name")
        recordRoyalDecree(s, "قمع الاضطراب في $name")
        return CourtResult(true, "خف ميل $name إلى الانفصال لكن غضب أهلها$dipNote")
    }

    fun privileges(s: GameState, key: String): CourtResult {
        val core = s.econ.provinces[key]
        val fr = if (core == null) s.econ.frontierRegions[key] else null
        if (core == null && fr == null) return CourtResult(false, "ولاية غير معروفة")
        if (fr != null && !fr.controlled) return CourtResult(false, "هذا الإقليم خارج سيطرة الدولة")
        val name = core?.name ?: fr!!.name
        val isLost = core?.isLost ?: fr!!.isLost
        if (isLost) return CourtResult(false, "$name مفقودة")
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, PRIVILEGES_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي")

        s.treasury -= cost
        s.courtActions -= 1
        if (core != null) {
            core.autonomy = clampD(core.autonomy - 8.0, 0.0, 100.0)
            core.mood = clampD(core.mood + 8.0, 0.0, 100.0)
        } else if (fr != null) {
            fr.autonomy = clampD(fr.autonomy - 8.0, 0.0, 100.0)
            fr.mood = clampD(fr.mood + 8.0, 0.0, 100.0)
        }
        var nudged = 0
        for (other in s.econ.provinces.values) {
            if (other.key != key && !other.isLost && (other.autonomy >= 30.0 || other.mood < 50.0)) {
                other.autonomy = clampD(other.autonomy + 2.0, 0.0, 100.0)
                nudged += 1
            }
        }
        s.courtLog.add("امتيازات لـ$name")
        val nudgeNote = if (nudged > 0) "؛ وارتفع تطلع $nudged ولايات مضطربة للامتيازات (+2 انفصال)" else ""
        return CourtResult(true, "ارتاحت $name لامتيازاتك$nudgeNote")
    }
}
