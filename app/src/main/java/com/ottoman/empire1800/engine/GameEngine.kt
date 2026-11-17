package com.ottoman.empire1800.engine

import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.HistoryEntry
import com.ottoman.empire1800.models.PendingEffect
import com.ottoman.empire1800.models.StatPoint
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.services.ClimateRules
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.DeepEconomyRules
import com.ottoman.empire1800.services.DiplomacyRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.FrontierRules
import com.ottoman.empire1800.services.IndustryRules
import com.ottoman.empire1800.services.MilestoneRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.SpyRules
import com.ottoman.empire1800.services.StabilityRules
import com.ottoman.empire1800.services.WarRules
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class EndingType {
    DEPOSED,
    COLLAPSE,
    BANKRUPTCY,
    CONQUEST,
    SURVIVED,
    DISMEMBERED,
}

data class Ending(
    val type: EndingType,
    val title: String,
    val description: String,
) {
    val isVictory: Boolean get() = type == EndingType.SURVIVED

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("title", title)
        put("description", description)
    }

    companion object {
        fun fromJson(raw: Any?): Ending? {
            val obj = raw as? JSONObject ?: return null
            val typeStr = obj.opt("type") as? String ?: return null
            val type = EndingType.entries.firstOrNull { it.name.equals(typeStr, ignoreCase = true) } ?: return null
            val title = obj.opt("title") as? String ?: ""
            val desc = obj.opt("description") as? String ?: ""
            return Ending(type = type, title = title, description = desc)
        }
    }
}

data class HalfYearReport(
    val round: Int,
    val period: String,
    val revenue: Double,
    val spending: Double,
    val interest: Double,
    val oneTimeCosts: Double,
    val loans: Double,
    val emergencyBorrowed: Double,
    val dueNotes: List<String>,
    val indicatorChanges: Map<String, Int>,
    val treasuryAfter: Double,
    val debtAfter: Double,
    val ending: Ending? = null,
    val repaid: Double = 0.0,
    val events: List<String> = emptyList(),
    val courtLog: List<String> = emptyList(),
    val projectSpend: Double = 0.0,
    val warSpend: Double = 0.0,
    val dadPaid: Double = 0.0,
    val ppy: Int = 2,
) {
    val net: Double get() = revenue - spending - interest - warSpend - dadPaid - projectSpend

    fun toJson(): JSONObject = JSONObject().apply {
        put("round", round)
        put("period", period)
        put("revenue", revenue)
        put("spending", spending)
        put("interest", interest)
        put("oneTimeCosts", oneTimeCosts)
        put("loans", loans)
        put("emergencyBorrowed", emergencyBorrowed)
        put("dueNotes", JSONArray(dueNotes))
        put("indicatorChanges", JSONObject().apply { indicatorChanges.forEach { (k, v) -> put(k, v) } })
        put("treasuryAfter", treasuryAfter)
        put("debtAfter", debtAfter)
        put("ending", ending?.toJson() ?: JSONObject.NULL)
        put("repaid", repaid)
        put("events", JSONArray(events))
        put("courtLog", JSONArray(courtLog))
        put("projectSpend", projectSpend)
        put("warSpend", warSpend)
        put("dadPaid", dadPaid)
        put("ppy", ppy)
    }

    fun toPromptText(): String {
        fun m(v: Double): String = "${String.format(Locale.US, "%.2f", v / 1e6)}م"
        val changes = indicatorChanges.entries
            .filter { it.value != 0 }
            .joinToString("، ") { "${it.key} ${if (it.value > 0) "+" else ""}${it.value}" }
        val buf = StringBuilder()
        buf.append("تقرير $period: إيراد ${m(revenue)}، نفقات ${m(spending)}، فوائد ${m(interest)}")
        buf.append("، صافي ${m(net)}، قرارات ${m(oneTimeCosts)}")
        if (warSpend > 0.0) buf.append("، نفقات حرب ${m(warSpend)}")
        if (projectSpend > 0.0) buf.append("، مشروع قومي ${m(projectSpend)}")
        if (dadPaid > 0.0) buf.append("، إدارة الديون ${m(dadPaid)}")
        if (loans > 0.0) buf.append("، قروض ${m(loans)}")
        if (repaid > 0.0) buf.append("، سداد دين ${m(repaid)}")
        if (emergencyBorrowed > 0.0) buf.append("، اقتراض طارئ ${m(emergencyBorrowed)}")
        buf.append(".")
        if (changes.isNotEmpty()) buf.append(" المؤشرات: $changes.")
        if (courtLog.isNotEmpty()) buf.append(" إجراءات السلطان: ${courtLog.joinToString("؛ ")}.")
        if (events.isNotEmpty()) buf.append(" أحداث: ${events.take(8).joinToString("؛ ")}.")
        if (dueNotes.isNotEmpty()) buf.append(" آثار ظهرت الآن: ${dueNotes.joinToString("؛ ")}.")
        return buf.toString()
    }

    companion object {
        fun fromJson(raw: Any?): HalfYearReport? {
            val obj = raw as? JSONObject ?: return null
            val period = obj.opt("period") as? String ?: ""
            if (period.isEmpty()) return null
            fun d(k: String): Double {
                val v = obj.opt(k)
                return if (v is Number && v.toDouble().isFinite()) v.toDouble() else 0.0
            }
            fun strList(k: String): List<String> {
                val arr = obj.optJSONArray(k) ?: return emptyList()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val s = arr.opt(i) as? String
                    if (!s.isNullOrBlank()) out.add(s)
                }
                return out
            }
            val indMap = linkedMapOf<String, Int>()
            val indObj = obj.optJSONObject("indicatorChanges")
            if (indObj != null) {
                val keys = indObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val v = indObj.opt(key)
                    if (v is Number && v.toDouble().isFinite()) indMap[key] = v.toDouble().roundToInt()
                }
            }
            val rVal = obj.opt("round")
            val ppyVal = obj.opt("ppy")
            return HalfYearReport(
                round = if (rVal is Number) rVal.toInt().coerceAtLeast(0) else 0,
                period = period,
                revenue = d("revenue"),
                spending = d("spending"),
                interest = d("interest"),
                oneTimeCosts = d("oneTimeCosts"),
                loans = d("loans"),
                emergencyBorrowed = d("emergencyBorrowed"),
                dueNotes = strList("dueNotes"),
                indicatorChanges = indMap,
                treasuryAfter = d("treasuryAfter"),
                debtAfter = d("debtAfter"),
                ending = Ending.fromJson(obj.opt("ending")),
                repaid = d("repaid"),
                events = strList("events"),
                courtLog = strList("courtLog"),
                projectSpend = d("projectSpend"),
                warSpend = d("warSpend"),
                dadPaid = d("dadPaid"),
                ppy = if (ppyVal is Number && ppyVal.toInt() == 4) 4 else 2,
            )
        }
    }
}

/** محرك اللعبة. */
class GameEngine {

    fun evaluate(s: GameState): Ending? {
        if (s.legitimacy <= LEGITIMACY_LOSS_AT) {
            return Ending(
                EndingType.DEPOSED,
                "خُلعت عن العرش",
                "فقدت ثقة العلماء والجند وكبار رجال الدولة، فاجتمعوا على خلعك وأعلنوا سلطانا جديدا.",
            )
        }
        if (s.stability <= STABILITY_LOSS_AT || s.stab.countBelow(BROKEN_DIMENSION) >= 3) {
            return Ending(
                EndingType.COLLAPSE,
                "انهيار النظام",
                "عمّت الفوضى والثورات الولايات والعاصمة، وانفرط عقد الدولة قبل أن تستعيد زمامها.",
            )
        }
        if (EconomyRules.debtRatio(s.debt, s.budget) >= EconomyRules.BANKRUPTCY_RATIO) {
            return Ending(
                EndingType.BANKRUPTCY,
                "إفلاس الدولة",
                "عجزت الخزينة عن خدمة الديون، فتدخل الدائنون الأجانب في مالية الدولة وصارت قراراتها رهينة لهم.",
            )
        }
        if (s.army <= 8 && s.foreign <= 12) {
            return Ending(
                EndingType.CONQUEST,
                "تقاسم الدول الكبرى للأراضي",
                "ضعف الجيش وغابت المكانة الدولية، فاقتطعت القوى الكبرى الولايات واحدة بعد أخرى.",
            )
        }
        if (s.econ.activeWeight < ProvinceRules.DISMEMBERED_BELOW) {
            return Ending(
                EndingType.DISMEMBERED,
                "تفككت الإمبراطورية",
                "انفصلت الولايات واحدة بعد أخرى حتى لم يبق للسلطان إلا العاصمة وما حولها، وتحولت الدولة إلى دويلة.",
            )
        }
        if (s.round >= totalRoundsFor(s.ppy)) {
            val avg = (s.legitimacy + s.stability + s.army + s.foreign + s.education) / 5.0
            val ratio = EconomyRules.debtRatio(s.debt, s.budget)
            if (avg >= 70.0 && ratio < 1.5) {
                return Ending(
                    EndingType.SURVIVED,
                    "إمبراطورية مزدهرة",
                    "بلغت عام $END_YEAR والدولة قوية ومحدثة، وصارت لها كلمة بين الكبار.",
                )
            }
            if (avg >= 50.0 && ratio < 3.0) {
                return Ending(
                    EndingType.SURVIVED,
                    "دولة متماسكة",
                    "بلغت عام $END_YEAR والدولة ما زالت متماسكة رغم الأزمات، وأمامها طريق إصلاح طويل.",
                )
            }
            return Ending(
                EndingType.SURVIVED,
                "نجوت بالكاد",
                "بلغت عام $END_YEAR والدولة مثقلة بالديون والضعف، لكنها ما زالت قائمة.",
            )
        }
        return null
    }

    private fun ind(v: Int): Int = v.coerceIn(0, 100)

    private fun applyEffects(s: GameState, e: Effects) {
        val k = s.diff.losses
        fun adj(v: Int): Int = if (v < 0) (v * k).roundToInt() else v
        s.legitimacy = ind(s.legitimacy + adj(e.legitimacy))
        if (e.stability != 0) s.stab.shiftAll(adj(e.stability).toDouble())
        s.army = ind(s.army + adj(e.army))
        s.foreign = ind(s.foreign + adj(e.foreign))
        s.education = ind(s.education + adj(e.education))
    }

    private fun changeLine(s: GameState, line: String, delta: Double) {
        DeepEconomyRules.onLineChange(s, line, delta)
        s.budget.change(line, delta)
    }

    private fun snapshot(s: GameState): LinkedHashMap<String, Int> = linkedMapOf(
        "الشرعية" to s.legitimacy,
        "الاستقرار" to s.stability,
        "الجيش" to s.army,
        "المكانة الدولية" to s.foreign,
        "التعليم" to s.education,
    )

    fun repayDebt(s: GameState, amount: Double): Double = DebtRules.repay(s, amount)

    fun resolveRound(s: GameState): HalfYearReport {
        val plan = s.plan
        if (plan == null || !s.hasAllPicks) {
            throw IllegalStateException("الجولة غير جاهزة للتنفيذ")
        }
        val before = snapshot(s)
        val finishedRound = s.round
        val courtLog = s.courtLog.toList()
        val events = mutableListOf<String>()
        var costs = 0.0
        var loans = 0.0

        // 1) قرارات اللاعب
        for (i in 0 until 3) {
            val decision = plan.decisions[i]
            val option = decision.options[s.picks[i]!!]
            costs += option.cost
            loans += option.loan
            s.treasury -= option.cost
            s.treasury += option.loan
            DebtRules.addLoan(s, option.loan)
            applyEffects(s, option.now)
            DiplomacyRules.applyRelations(s, option.relations)
            option.powerMood.forEach { (k, d) ->
                val p = s.powers[k]
                if (p != null && !p.dissolved) p.satisfaction = clampD(p.satisfaction + d, 0.0, 100.0)
            }
            if (option.dissolve.isNotEmpty()) {
                val msg = PowerRules.dissolveFromDecision(s, option.dissolve)
                if (msg.isNotEmpty()) events.add(msg)
            }
            if (option.war.isNotEmpty()) {
                val msg = WarRules.declare(s, option.war, byUs = true)
                if (msg.isNotEmpty()) events.add(msg)
            }
            if (option.peace.isNotEmpty()) {
                val msg = WarRules.peaceFromDecision(s, option.peace)
                if (msg.isNotEmpty()) events.add(msg)
            }
            if (option.project.isNotEmpty()) {
                val d = projectDef(option.project)
                if (d != null) {
                    val r = ProjectRules.start(
                        s,
                        option.project,
                        ((d.minHalves + d.maxHalves) / 2) * s.k,
                        viaDecision = true,
                    )
                    if (r.ok) events.add(r.message)
                }
            }
            if (option.conquer.isNotEmpty()) {
                val msg = FrontierRules.conquer(s, option.conquer, "قرار سلطاني")
                if (msg.isNotEmpty()) events.add(msg.removePrefix("؛ "))
            }
            if (!option.later.isZero || option.lineChanges.isNotEmpty()) {
                if (option.delay <= 0) {
                    applyEffects(s, option.later)
                    for (c in option.lineChanges) {
                        changeLine(s, c.line, c.delta)
                    }
                } else {
                    s.pending.add(
                        PendingEffect(
                            dueRound = finishedRound + option.delay,
                            effects = option.later,
                            lines = option.lineChanges,
                            source = "${decision.title}: ${shortText(option.text)}",
                        ),
                    )
                }
            }
            s.history.add(
                HistoryEntry(
                    round = finishedRound,
                    category = decision.category.label,
                    title = decision.title,
                    choice = option.text,
                    result = option.result,
                ),
            )
        }

        // 2) مناخ نصف السنة
        events.addAll(ClimateRules.step(s))

        // 3) ميزانية نصف السنة
        val revenue = EconomyRules.halfYearRevenue(
            s.budget,
            s.stability,
            econ = s.econ,
            f = s.f,
            year = s.year,
            literacy = s.demog.literacy,
        ) * s.diff.revenue
        val spending = EconomyRules.halfYearSpending(
            s.budget,
            f = s.f,
            grandVizier = s.grandVizier,
            econ = s.econ,
        )
        val interest = DebtRules.halfYearInterest(s)
        val warSpend = WarRules.costs(s)
        val dadPaid = DebtRules.dadPayment(s, revenue)
        val net = revenue - spending - interest - warSpend - dadPaid
        s.treasury += net

        val project = ProjectRules.fund(s)
        events.addAll(project.notes)

        // 4) اقتراض طارئ عند نفاد الخزينة
        var emergency = 0.0
        if (s.treasury < 0.0) {
            s.emergencyStreak += 1
            emergency = -s.treasury * (1.0 + EconomyRules.EMERGENCY_LOAN_FEE)
            s.debt += emergency
            s.treasury = 0.0
            s.stab.shiftAll(-1.0 * s.f)
            if (s.emergencyStreak % s.k == 0) {
                s.legitimacy = ind(s.legitimacy - 1)
                s.army = ind(s.army - 2)
            }
        } else {
            s.emergencyStreak = 0
        }

        // 5) تسديد دين اختاره اللاعب
        val repaid = repayDebt(s, s.repay)
        s.repay = 0.0

        // 6) الأنظمة العميقة
        val ratio = EconomyRules.debtRatio(s.debt, s.budget)
        events.addAll(DeepEconomyRules.step(s, net = net))
        events.addAll(StabilityRules.step(s, debtRatio = ratio, emergency = emergency > 0.0))
        events.addAll(PowerRules.step(s, emergency = emergency > 0.0))
        events.addAll(DiplomacyRules.step(s))
        ForeignRules.step(s)
        events.addAll(WarRules.step(s))
        events.addAll(ProvinceRules.step(s))
        events.addAll(FrontierRules.step(s))
        events.addAll(IndustryRules.step(s))
        events.addAll(DebtRules.step(s))
        events.addAll(SpyRules.step(s))

        // 7) انجراف الجيش والتعليم بحسب الإنفاق
        val totalRevenue = s.budget.totalRevenue
        if (totalRevenue > 0.0 && s.tick) {
            val armyBonus = if (s.grandVizier == "military") 1 else 0
            val eduBonus = when (s.grandVizier) {
                "reformer" -> 1
                "traditional" -> -1
                else -> 0
            }
            s.army = ind(s.army + EconomyRules.armyDrift(s.budget.lineValue("army") / totalRevenue) + armyBonus)
            s.education = ind(s.education + EconomyRules.educationDrift(s.budget.lineValue("civil") / totalRevenue) + eduBonus)
            val halfIdx = if (s.ppy == 2) s.round else s.round / 2
            if (s.grandVizier == "traditional" && halfIdx % 2 == 1) {
                s.legitimacy = ind(s.legitimacy + 1)
            }
        }

        // 8) نمو اسمي ثم دخول نصف السنة التالي وظهور الآثار المؤجلة
        DeepEconomyRules.applyNominalGrowth(s)
        s.round += 1
        val due = mutableListOf<String>()
        val stillPending = mutableListOf<PendingEffect>()
        for (p in s.pending) {
            if (p.dueRound <= s.round) {
                applyEffects(s, p.effects)
                for (c in p.lines) {
                    changeLine(s, c.line, c.delta)
                }
                due.add(p.source)
            } else {
                stillPending.add(p)
            }
        }
        s.pending = stillPending

        s.series.add(
            StatPoint(
                round = s.round,
                treasury = s.treasury,
                debt = s.debt,
                revenue = s.budget.totalRevenue,
                legitimacy = s.legitimacy,
                stability = s.stability,
                army = s.army,
                foreign = s.foreign,
                education = s.education,
                population = s.demog.population,
                literacy = s.demog.literacy,
                inflation = s.econ.inflation,
            ),
        )
        if (s.series.size > 900) {
            s.series.subList(0, s.series.size - 900).clear()
        }

        // 9) تنظيف الجولة المنتهية وفتح نصف سنة جديد
        s.plan = null
        s.picks = mutableListOf(null, null, null)
        s.courtActions = if (s.ppy == 4) 1 else 2
        s.courtLog = mutableListOf()
        events.addAll(MilestoneRules.evaluate(s))
        s.events = events.take(16).toMutableList()

        val after = snapshot(s)
        val changes = linkedMapOf<String, Int>()
        for (k in before.keys) {
            changes[k] = (after[k] ?: 0) - (before[k] ?: 0)
        }

        val report = HalfYearReport(
            round = finishedRound,
            period = periodLabel(finishedRound, s.ppy),
            revenue = revenue,
            spending = spending,
            interest = interest,
            oneTimeCosts = costs,
            loans = loans,
            emergencyBorrowed = emergency,
            dueNotes = due,
            indicatorChanges = changes,
            treasuryAfter = s.treasury,
            debtAfter = s.debt,
            ending = evaluate(s),
            repaid = repaid,
            events = events,
            courtLog = courtLog,
            projectSpend = project.spent,
            warSpend = warSpend,
            dadPaid = dadPaid,
            ppy = s.ppy,
        )
        s.lastReport = report.toPromptText()
        s.lastFullReport = report
        return report
    }

    private fun shortText(text: String): String =
        if (text.length <= 60) text else "${text.substring(0, 60)}..."

    companion object {
        const val START_YEAR: Int = 1800
        const val END_YEAR: Int = 1910
        private const val LEGITIMACY_LOSS_AT: Int = 15
        private const val STABILITY_LOSS_AT: Int = 12
        private const val BROKEN_DIMENSION: Double = 8.0

        val totalRounds: Int get() = (END_YEAR - START_YEAR + 1) * 2

        fun totalRoundsFor(ppy: Int): Int =
            (END_YEAR - START_YEAR + 1) * if (ppy == 4) 4 else 2

        fun yearOf(round: Int, ppy: Int = 2): Int =
            START_YEAR + round / if (ppy == 4) 4 else 2

        private val halves = listOf("كانون الثاني - حزيران", "تموز - كانون الأول")
        private val quarters = listOf(
            "كانون الثاني - آذار",
            "نيسان - حزيران",
            "تموز - أيلول",
            "تشرين الأول - كانون الأول",
        )

        fun halfLabel(round: Int, ppy: Int = 2): String =
            if (ppy == 4) quarters[round % 4] else halves[round % 2]

        fun periodLabel(round: Int, ppy: Int = 2): String =
            "${halfLabel(round, ppy)} ${yearOf(round, ppy)}"

        fun paceName(ppy: Int): String = if (ppy == 4) "كل 3 أشهر" else "كل 6 أشهر"
        fun periodNoun(ppy: Int): String = if (ppy == 4) "ربع السنة" else "نصف السنة"
        fun periodUnit(ppy: Int): String = if (ppy == 4) "ربع سنة" else "نصف سنة"
        fun periodPlural(ppy: Int): String = if (ppy == 4) "أرباع" else "أنصاف"

        fun estimateRoundTreasury(s: GameState): TreasuryProjection {
            val f = s.f
            val projectedBudget = s.budget.copy()
            var selectedCosts = 0.0
            var selectedLoans = 0.0
            var stabShift = 0
            val plan = s.plan
            if (plan != null) {
                for (i in 0 until 3) {
                    val pickIdx = s.picks.getOrNull(i) ?: continue
                    val opt = plan.decisions.getOrNull(i)?.options?.getOrNull(pickIdx) ?: continue
                    selectedCosts += opt.cost
                    selectedLoans += opt.loan
                    fun adjStab(v: Int): Int = if (v < 0) (v * s.diff.losses).roundToInt() else v
                    stabShift += adjStab(opt.now.stability)
                    if (opt.delay <= 0) {
                        stabShift += adjStab(opt.later.stability)
                        for (c in opt.lineChanges) {
                            projectedBudget.change(c.line, c.delta)
                        }
                    }
                }
            }

            val projectedStab = (s.stability + stabShift).coerceIn(0, 100)
            val revenue = EconomyRules.halfYearRevenue(
                projectedBudget,
                projectedStab,
                econ = s.econ,
                f = f,
                year = s.year,
                literacy = s.demog.literacy,
            ) * s.diff.revenue
            val spending = EconomyRules.halfYearSpending(
                projectedBudget,
                f = f,
                grandVizier = s.grandVizier,
                econ = s.econ,
            )

            val inMoratorium = DebtRules.inMoratorium(s)
            val foreignAdded =
                if (s.year >= DebtRules.FOREIGN_FROM && !inMoratorium) selectedLoans * DebtRules.FOREIGN_SHARE else 0.0
            val debtAfterLoans = s.debt + selectedLoans
            val foreignAfterLoans = minOf(debtAfterLoans, s.foreignDebt + foreignAdded)
            val internalAfterLoans = maxOf(0.0, debtAfterLoans - foreignAfterLoans)
            val interest = EconomyRules.halfYearInterest(internalAfterLoans, inflation = s.econ.inflation, f = f) +
                (if (inMoratorium) 0.0 else (foreignAfterLoans * DebtRules.foreignRate(s) / 2.0) * f)

            val warSpend = WarRules.costs(s)
            val dadPaid = if (s.dadActive && foreignAfterLoans > 0.0) minOf(revenue * DebtRules.DAD_SHARE, foreignAfterLoans) else 0.0
            val projectedNet = revenue - spending - interest - warSpend - dadPaid
            val projectedTreasuryAfter = s.treasury - selectedCosts + selectedLoans + projectedNet
            val emergencyShortfall = if (projectedTreasuryAfter < 0.0) -projectedTreasuryAfter else 0.0
            val estimatedEmergencyLoan =
                if (emergencyShortfall > 0.0) emergencyShortfall * (1.0 + EconomyRules.EMERGENCY_LOAN_FEE) else 0.0
            val willBorrowEmergency = projectedTreasuryAfter < 0.0
            val prj = s.project
            val willStallProject = prj != null && !prj.paused && projectedTreasuryAfter >= 0.0 && projectedTreasuryAfter < prj.installment

            return TreasuryProjection(
                selectedCosts = selectedCosts,
                selectedLoans = selectedLoans,
                revenue = revenue,
                spending = spending,
                interest = interest,
                warSpend = warSpend,
                dadPaid = dadPaid,
                projectedNet = projectedNet,
                projectedTreasuryAfter = projectedTreasuryAfter,
                emergencyShortfall = emergencyShortfall,
                estimatedEmergencyLoan = estimatedEmergencyLoan,
                willBorrowEmergency = willBorrowEmergency,
                willStallProject = willStallProject,
            )
        }
    }
}

data class TreasuryProjection(
    val selectedCosts: Double,
    val selectedLoans: Double,
    val revenue: Double,
    val spending: Double,
    val interest: Double,
    val warSpend: Double,
    val dadPaid: Double,
    val projectedNet: Double,
    val projectedTreasuryAfter: Double,
    val emergencyShortfall: Double,
    val estimatedEmergencyLoan: Double,
    val willBorrowEmergency: Boolean,
    val willStallProject: Boolean,
)
