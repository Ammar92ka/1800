package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.ProjectDef
import com.ottoman.empire1800.data.projectCatalog
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.models.ActiveProject
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD
import kotlin.math.roundToDouble
import kotlin.math.roundToInt

/** نتيجة تمويل نصف سنة من المشروع القومي. */
data class ProjectStep(val spent: Double, val notes: List<String>)

/** اقتراح من الديوان: مشروع مناسب للوضع الحالي مع سببه. */
data class ProjectSuggestion(val def: ProjectDef, val reason: String, val score: Double)

/** المشاريع القومية: واحد في كل وقت، تمويل بأقساط نصف سنوية، وفوائد تظهر عند الاكتمال. */
object ProjectRules {
    const val RUSH_PREMIUM: Double = 0.25

    fun has(s: GameState, key: String): Boolean = s.completed.contains(key)

    fun hasPerk(s: GameState, perk: String): Boolean =
        s.completed.any { projectDef(it)?.perk == perk }

    fun perkCount(s: GameState, perk: String): Int =
        s.completed.count { projectDef(it)?.perk == perk }

    fun isAvailable(s: GameState, d: ProjectDef): Boolean =
        s.year >= d.fromYear && !s.completed.contains(d.key)

    fun available(s: GameState): List<ProjectDef> =
        projectCatalog.filter { isAvailable(s, it) }

    fun minPeriods(s: GameState, d: ProjectDef): Int = d.minHalves * s.k
    fun maxPeriods(s: GameState, d: ProjectDef): Int = d.maxHalves * s.k

    fun clampHalves(s: GameState, d: ProjectDef, periods: Int): Int =
        periods.coerceIn(minPeriods(s, d), maxPeriods(s, d))

    fun totalCost(s: GameState, d: ProjectDef, halves: Int): Double {
        val h = clampHalves(s, d, halves)
        val span = maxPeriods(s, d) - minPeriods(s, d)
        val premium = if (span <= 0) 1.0 else 1.0 + RUSH_PREMIUM * (maxPeriods(s, d) - h) / span
        val vizierDiscount = if (s.grandVizier == "economist") 0.90 else 1.0
        return (s.budget.totalRevenue * d.cost * premium * vizierDiscount).roundToDouble()
    }

    fun installment(s: GameState, d: ProjectDef, halves: Int): Double =
        (totalCost(s, d, halves) / clampHalves(s, d, halves)).roundToDouble()

    fun paybackYears(s: GameState, d: ProjectDef, periods: Int): Double? {
        val rev = s.budget.totalRevenue
        val gain = d.lines.values.sum() * rev - d.upkeep * rev
        if (gain <= 0.0) return null
        return totalCost(s, d, periods) / gain
    }

    fun suggestions(s: GameState, limit: Int = 3): List<ProjectSuggestion> {
        val e = s.econ
        val out = mutableListOf<ProjectSuggestion>()
        val activePowers = s.powers.values.filter { !it.dissolved }
        val avgDanger = activePowers.sumOf { it.danger } / activePowers.size.coerceIn(1, 99)
        val restless = e.provinces.values.any { !it.isLost && it.autonomy >= 45.0 }
        for (d in available(s)) {
            var score = 0.0
            val why = mutableListOf<String>()
            val mid = ((d.minHalves + d.maxHalves) / 2) * s.k
            val payback = paybackYears(s, d, mid)
            if (payback != null) {
                score += (30.0 / payback).coerceIn(0.0, 4.0)
                if (e.deficitStreak >= 2 * s.k || s.treasury < s.budget.totalRevenue / 8.0) {
                    score += 2.0
                    why.add("الميزانية مثقلة والمشروع يزيد الإيراد (استرداد نحو ${payback.roundToInt()} سنة)")
                }
            }
            if ((e.grain > 125.0 || e.provinces.values.any { it.harvest < 1.0 }) &&
                (d.perk == "drought" || d.perk == "granary")
            ) {
                score += 4.0
                why.add("الغلاء والجفاف يضربان المحصول")
            }
            if (s.wars.isNotEmpty() && (d.perk == "tech" || d.perk == "logistics" || d.perk == "defense" || d.key == "arsenal")) {
                score += 4.0
                why.add("نحن في حرب والمشروع يقوي جيشنا")
            }
            if (s.army < 35 && d.effects.army > 0) {
                score += 3.0
                why.add("جيشنا ضعيف")
            }
            if (s.education < 25 && (d.effects.education >= 3 || d.schools > 0.0)) {
                score += 3.0
                why.add("التعليم متدن")
            }
            if (e.corruption > 55.0 && d.corruption < 0.0) {
                score += 3.0
                why.add("الفساد يأكل الجباية")
            }
            if (e.inflation > 6.0 && d.inflation < 0.0) {
                score += 4.0
                why.add("التضخم مرتفع")
            }
            if (restless && (d.mood > 0.0 || d.provinceMood.isNotEmpty() || d.perk == "logistics")) {
                score += 2.5
                why.add("الولايات قلقة وتحتاج ربطا وخدمات")
            }
            if (avgDanger > 50.0 && d.perk == "security") {
                score += 3.0
                why.add("خطر القوى الداخلية مرتفع")
            }
            if (DebtRules.creditRating(s) < 55.0 && d.perk == "credit") {
                score += 3.0
                why.add("تصنيفنا الائتماني ضعيف")
            }
            if (d.key == "modern_army" && s.powers["janissaries"]?.dissolved == true) {
                score += 5.0
                why.add("سد الفراغ العسكري بعد حل الانكشارية بجيش نظامي حديث")
            }
            val inst = installment(s, d, mid)
            if (inst > s.budget.totalRevenue / 2.0 * s.f * 0.35) {
                score -= 2.0
            }
            if (why.isEmpty()) continue
            out.add(ProjectSuggestion(d, why.take(2).joinToString(" • "), score))
        }
        out.sortByDescending { it.score }
        return out.take(limit)
    }

    fun start(s: GameState, key: String, halves: Int, viaDecision: Boolean = false): CourtResult {
        val d = projectDef(key) ?: return CourtResult(false, "مشروع غير معروف")
        if (s.project != null) {
            return CourtResult(false, "لديك مشروع جار؛ أتمه أو ألغه قبل بدء غيره")
        }
        if (s.completed.contains(key)) return CourtResult(false, "${d.name} منجز أصلا")
        if (s.year < d.fromYear) {
            return CourtResult(false, "${d.name} غير ممكن قبل ${d.fromYear}")
        }
        val h = clampHalves(s, d, halves)
        val inst = installment(s, d, h)
        if (!viaDecision) {
            if (s.courtActions <= 0) {
                return CourtResult(false, exhaustedCourtMsg(s))
            }
            if (s.treasury < inst) {
                return CourtResult(false, "الخزينة لا تكفي للقسط الأول")
            }
            s.courtActions -= 1
            s.courtLog.add("بدء مشروع ${d.name}")
        }
        s.project = ActiveProject(key, h, inst, s.round)
        val yearsText = String.format(java.util.Locale.US, "%.1f", h.toDouble() / s.ppy)
        return CourtResult(true, "بدأ مشروع ${d.name}: $yearsText سنة بأقساط كل فترة")
    }

    fun cancel(s: GameState): CourtResult {
        val p = s.project ?: return CourtResult(false, "لا مشروع جار")
        val d = projectDef(p.key)
        s.project = null
        s.courtLog.add("إلغاء مشروع ${d?.name ?: p.key}")
        return CourtResult(true, "أُلغي المشروع وضاع ما أُنفق عليه")
    }

    fun setPaused(s: GameState, paused: Boolean): CourtResult {
        val p = s.project ?: return CourtResult(false, "لا مشروع جار")
        p.paused = paused
        if (paused) p.stalled = 0
        return CourtResult(true, if (paused) "أُوقف المشروع مؤقتا" else "استؤنف المشروع")
    }

    fun fund(s: GameState): ProjectStep {
        val p = s.project ?: return ProjectStep(0.0, emptyList())
        val d = projectDef(p.key)
        if (d == null) {
            s.project = null
            return ProjectStep(0.0, emptyList())
        }
        val notes = mutableListOf<String>()
        var spent = 0.0
        if (p.paused) return ProjectStep(0.0, emptyList())
        if (s.treasury >= p.installment) {
            s.treasury -= p.installment
            spent = p.installment
            p.funded += 1
            p.stalled = 0
            if (p.funded >= p.halves) notes.add(complete(s, d))
        } else {
            p.stalled += 1
            notes.add("تعثر مشروع ${d.name} لنقص التمويل (${periodUnit(s.ppy)} رقم ${p.stalled})")
        }
        return ProjectStep(spent, notes)
    }

    private fun complete(s: GameState, d: ProjectDef): String {
        val rev = s.budget.totalRevenue
        d.lines.forEach { (line, pct) -> s.budget.change(line, rev * pct) }
        if (d.upkeep > 0.0) s.budget.change(d.upkeepLine, rev * d.upkeep)

        val e = d.effects
        s.legitimacy = (s.legitimacy + e.legitimacy).coerceIn(0, 100)
        s.army = (s.army + e.army).coerceIn(0, 100)
        s.foreign = (s.foreign + e.foreign).coerceIn(0, 100)
        s.education = (s.education + e.education).coerceIn(0, 100)
        if (e.stability != 0) s.stab.shiftAll(e.stability.toDouble())

        val econ = s.econ
        if (d.mood != 0.0) {
            for (p in econ.provinces.values) {
                if (!p.isLost) p.mood = clampD(p.mood + d.mood, 0.0, 100.0)
            }
        }
        d.provinceMood.forEach { (k, v) ->
            val p = econ.provinces[k]
            if (p != null && !p.isLost) p.mood = clampD(p.mood + v, 0.0, 100.0)
        }
        econ.corruption = clampD(econ.corruption + d.corruption, 5.0, 95.0)
        econ.inflation = clampD(econ.inflation + d.inflation, 1.0, 12.0)
        s.demog.schools = clampD(s.demog.schools + d.schools, 0.0, 1e7)
        s.demog.literacy = clampD(s.demog.literacy + d.literacy, 1.0, 80.0)
        d.powers.forEach { (k, v) ->
            val p = s.powers[k]
            if (p != null && !p.dissolved) p.satisfaction = clampD(p.satisfaction + v, 0.0, 100.0)
        }

        s.completed.add(d.key)
        s.project = null
        return "اكتمل مشروع ${d.name}: ${d.benefits}"
    }
}
