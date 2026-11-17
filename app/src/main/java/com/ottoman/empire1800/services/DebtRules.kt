package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD
import kotlin.math.max
import kotlin.math.roundToInt

/** عامل واحد في التصنيف الائتماني. */
data class RatingFactor(val label: String, val value: Double)

/** الدين الأقرب للواقع: داخلي وخارجي، تصنيف ائتماني، عجز عن السداد، وإدارة ديون عامة. */
object DebtRules {
    const val FOREIGN_FROM: Int = 1854
    const val FOREIGN_SHARE: Double = 0.7
    const val DAD_SHARE: Double = 0.20
    const val MORATORIUM_HALVES: Int = 4
    const val DEFAULT_MEMORY: Int = 40

    private val lenders: List<String> = listOf("britain", "france", "austria", "germany")

    fun internalDebt(s: GameState): Double = max(0.0, s.debt - s.foreignDebt)

    fun ratingFactors(s: GameState): List<RatingFactor> {
        val rev = s.budget.totalRevenue
        val ratio = if (rev > 0.0) s.debt / rev else 10.0
        var rel = 0.0
        var n = 0
        for (k in listOf("britain", "france", "austria")) {
            rel += s.countries[k]?.relation ?: 48.0
            n++
        }
        val avg = if (n == 0) 48.0 else rel / n
        val k = s.k
        val deficit = s.econ.deficitStreak.toDouble() / k
        val recentDefault = s.defaultRound >= 0 && s.round - s.defaultRound < DEFAULT_MEMORY * k
        return listOf(
            RatingFactor("نسبة الدين إلى الإيراد", -12.0 * (ratio - 0.33)),
            RatingFactor("علاقاتنا بالدائنين", 0.4 * (avg - 48.0)),
            RatingFactor("عجز الميزانية المتواصل", -(1.5 * (deficit - 2.0)).coerceIn(0.0, 10.0)),
            RatingFactor("التضخم", -(1.5 * (s.econ.inflation - 4.0)).coerceIn(0.0, 8.0)),
            RatingFactor("الحروب الجارية", -(6.0 * s.wars.size).coerceIn(0.0, 12.0)),
            RatingFactor("استقرار الحكم", 0.1 * (s.stability - 55)),
            RatingFactor("مصرف عثماني حديث", if (ProjectRules.hasPerk(s, "credit")) 10.0 else 0.0),
            RatingFactor("إدارة الديون العامة", if (s.dadActive) 5.0 else 0.0),
            RatingFactor("صدر أعظم اقتصادي", if (s.grandVizier == "economist") 5.0 else 0.0),
            RatingFactor("سمعة العجز السابق", if (recentDefault) -30.0 else 0.0),
        )
    }

    fun creditRating(s: GameState): Double =
        clampD(75.0 + ratingFactors(s).sumOf { it.value }, 5.0, 95.0)

    fun foreignRate(s: GameState): Double = 0.05 + 0.10 * (1.0 - creditRating(s) / 100.0)

    fun inMoratorium(s: GameState): Boolean =
        s.defaultRound >= 0 && s.round - s.defaultRound < MORATORIUM_HALVES * s.k

    fun halfYearInterest(s: GameState): Double {
        val internal = EconomyRules.halfYearInterest(
            internalDebt(s),
            inflation = s.econ.inflation,
            f = s.f,
        )
        val foreign = if (inMoratorium(s)) 0.0 else s.foreignDebt * foreignRate(s) / 2.0 * s.f
        return internal + foreign
    }

    fun addLoan(s: GameState, amount: Double) {
        if (amount <= 0.0 || !amount.isFinite()) return
        s.debt += amount
        if (s.year >= FOREIGN_FROM && !inMoratorium(s)) {
            s.foreignDebt += amount * FOREIGN_SHARE
            if (s.foreignDebt > 0.0) s.hadForeignDebt = true
        }
    }

    fun addForeign(s: GameState, amount: Double) {
        if (amount <= 0.0 || !amount.isFinite()) return
        s.debt += amount
        s.foreignDebt += amount
        if (s.foreignDebt > 0.0) s.hadForeignDebt = true
    }

    fun repay(s: GameState, amount: Double): Double {
        if (amount <= 0.0) return 0.0
        var paid = amount
        if (paid > s.debt) paid = s.debt
        if (paid > s.treasury) paid = s.treasury
        if (paid <= 0.0) return 0.0
        val internal = internalDebt(s)
        val foreignPaid = if (paid > internal) paid - internal else 0.0
        s.treasury -= paid
        s.debt -= paid
        s.foreignDebt = (s.foreignDebt - foreignPaid).coerceIn(0.0, s.debt)
        MilestoneRules.evaluate(s)
        return paid
    }

    fun dadPayment(s: GameState, halfRevenue: Double): Double {
        if (!s.dadActive || s.foreignDebt <= 0.0) return 0.0
        var pay = halfRevenue * DAD_SHARE
        if (pay > s.foreignDebt) pay = s.foreignDebt
        if (pay <= 0.0) return 0.0
        s.foreignDebt -= pay
        s.debt = max(0.0, s.debt - pay)
        MilestoneRules.evaluate(s)
        return pay
    }

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()
        val rev = s.budget.totalRevenue
        val k = s.k
        val canDefault = s.defaultRound < 0 || s.round - s.defaultRound > DEFAULT_MEMORY * k
        if (canDefault && s.foreignDebt > 0.5 * rev && s.emergencyStreak >= 3 * k) {
            s.defaultRound = s.round
            s.dadActive = false
            for (lender in lenders) {
                val c = s.countries[lender]
                if (c != null) c.relation = clampD(c.relation - 15.0, 0.0, 100.0)
            }
            s.legitimacy = (s.legitimacy - 4).coerceIn(0, 100)
            s.stab.shift("political", -3.0)
            notes.add("أعلنت الدولة عجزها عن سداد الديون الخارجية وعلقت الفوائد، فغضب الدائنون")
        }
        if (s.defaultRound >= 0 &&
            !s.dadActive &&
            s.round - s.defaultRound >= MORATORIUM_HALVES * k &&
            s.round - s.defaultRound <= DEFAULT_MEMORY * k &&
            s.foreignDebt > 0.2 * rev
        ) {
            s.dadActive = true
            s.legitimacy = (s.legitimacy - 2).coerceIn(0, 100)
            for (lender in lenders) {
                val c = s.countries[lender]
                if (c != null) c.relation = clampD(c.relation + 10.0, 0.0, 100.0)
            }
            notes.add("أنشئت إدارة للديون العامة تحصّل ${(DAD_SHARE * 100).roundToInt()}% من الإيراد للدائنين")
        }
        if (s.dadActive && s.foreignDebt <= 0.2 * rev) {
            s.dadActive = false
            notes.add("أتمت إدارة الديون العامة ما عليها وأنهت إشرافها على الإيرادات")
        }
        return notes
    }
}
