package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.economicSystemDef
import com.ottoman.empire1800.models.Budget
import com.ottoman.empire1800.models.EconomyState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToDouble

/** قواعد الاقتصاد (بالقروش). */
object EconomyRules {
    const val INTEREST_RATE: Double = 0.12
    const val EMERGENCY_LOAN_FEE: Double = 0.10
    const val NOMINAL_GROWTH_PER_HALF_YEAR: Double = 0.01
    const val COLLECTION_BASE: Double = 0.85
    const val COLLECTION_SPAN: Double = 0.15
    const val BANKRUPTCY_RATIO: Double = 6.0
    const val GRAIN_SUBSIDY_SHARE: Double = 0.025
    const val ANTI_CORRUPTION_SHARE: Double = 0.020
    const val AUSTERITY_PALACE_SHARE: Double = 0.050
    const val AUSTERITY_COOLDOWN_HALVES: Int = 8

    fun collectionEfficiency(stability: Int): Double =
        COLLECTION_BASE + COLLECTION_SPAN * (stability.coerceIn(0, 100) / 100.0)

    fun halfYearRevenue(
        b: Budget,
        stability: Int,
        econ: EconomyState? = null,
        f: Double = 1.0,
        year: Int = 1800,
        literacy: Double = 4.0,
    ): Double {
        val sys = economicSystemDef(econ?.economicSystem ?: "state_capitalism")
        val customsExtra = b.lineValue("customs") * (sys.tariffMult - 1.0)
        val factoryAnnual = if (econ != null) IndustryRules.annualFactoryRevenue(econ, b, year, literacy) else 0.0
        return ((b.totalRevenue + customsExtra + factoryAnnual) / 2.0) * f * collectionEfficiency(stability) * (econ?.revenueMultiplier ?: 1.0)
    }

    fun halfYearSpending(
        b: Budget,
        f: Double = 1.0,
        grandVizier: String = "traditional",
        econ: EconomyState? = null,
    ): Double {
        val paperDiscount = if (econ != null) IndustryRules.paperMillAdminDiscount(econ) else 0.0
        val adminSavedAnnual = b.lineValue("admin") * paperDiscount
        val subsidyAnnual = if (econ != null) IndustryRules.annualSubsidyCost(econ, b) else 0.0
        val base = ((b.totalSpending - adminSavedAnnual + subsidyAnnual) / 2.0) * f
        val militaryExtra = if (grandVizier == "military") (b.lineValue("army") / 2.0) * f * 0.06 else 0.0
        return max(0.0, base + militaryExtra)
    }

    fun halfYearInterest(debt: Double, inflation: Double = 2.0, f: Double = 1.0): Double {
        val premium = if (inflation > 2.0) 0.003 * (inflation - 2.0) else 0.0
        return debt * (INTEREST_RATE + premium) / 2.0 * f
    }

    fun debtRatio(debt: Double, b: Budget): Double {
        val revenue = b.totalRevenue
        return if (revenue <= 0.0) Double.POSITIVE_INFINITY else debt / revenue
    }

    fun debtUnrest(ratio: Double): Int = when {
        ratio >= 5.0 -> 3
        ratio >= 3.0 -> 2
        ratio >= 1.5 -> 1
        else -> 0
    }

    fun armyDrift(armyShare: Double): Int = when {
        armyShare < 0.30 -> -2
        armyShare < 0.38 -> -1
        armyShare > 0.50 -> 1
        else -> 0
    }

    fun educationDrift(civilShare: Double): Int = when {
        civilShare >= 0.10 -> 1
        civilShare < 0.03 -> -1
        else -> 0
    }

    fun austerityCooldownRemaining(s: GameState): Int {
        if (s.lastAusterityRound < 0) return 0
        val rem = AUSTERITY_COOLDOWN_HALVES * s.k - (s.round - s.lastAusterityRound)
        return if (rem > 0) rem else 0
    }

    fun grainSubsidy(s: GameState): CourtResult {
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        if (s.econ.grain <= 120.0) {
            return CourtResult(false, "أسعار الحبوب معتدلة الآن؛ تُتاح إعانة الخبز إذا تجاوز السعر 120")
        }
        val cost = costShare(s, GRAIN_SUBSIDY_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لإعانة الخبز والحبوب")
        s.treasury -= cost
        s.courtActions -= 1
        s.econ.grain = clampD(s.econ.grain - 25.0, 60.0, 260.0)
        for (p in s.econ.provinces.values) {
            if (!p.isLost) p.mood = clampD(p.mood + 4.0, 0.0, 100.0)
        }
        s.stab.shift("social", 2.0)
        s.courtLog.add("إعانة الخبز والحبوب في الأسواق")
        recordRoyalDecree(s, "إعانة الخبز والحبوب في الأسواق")
        return CourtResult(true, "تراجعت أسعار الحبوب (-25) وتحسن مزاج الولايات والاستقرار الاجتماعي (+2)")
    }

    fun antiCorruption(s: GameState): CourtResult {
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = costShare(s, ANTI_CORRUPTION_SHARE)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لحملة مكافحة الفساد")
        s.treasury -= cost
        s.courtActions -= 1
        s.econ.corruption = clampD(s.econ.corruption - 6.0, 5.0, 95.0)
        s.powers["tax_farmers"]?.let {
            if (!it.dissolved) it.satisfaction = clampD(it.satisfaction - 8.0, 0.0, 100.0)
        }
        s.powers["governors"]?.let {
            if (!it.dissolved) it.satisfaction = clampD(it.satisfaction - 4.0, 0.0, 100.0)
        }
        s.courtLog.add("حملة تفتيش ومكافحة للفساد")
        recordRoyalDecree(s, "حملة مكافحة الفساد")
        return CourtResult(true, "انخفض الفساد (-6) مع غضب الملتزمين (-8) والولاة (-4)")
    }

    fun palaceAusterity(s: GameState): CourtResult {
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val rem = austerityCooldownRemaining(s)
        if (rem > 0) {
            return CourtResult(false, "تقشف البلاط قيد الانتظار (باقٍ $rem ${periodUnit(s.ppy)})")
        }
        val currentPalace = s.budget.lineValue("palace")
        val targetCut = (s.budget.totalRevenue * AUSTERITY_PALACE_SHARE).roundToDouble()
        val cut = min(currentPalace, targetCut)
        if (cut <= 0.0) return CourtResult(false, "نفقات البلاط في حدها الأدنى بالفعل")
        s.budget.change("palace", -cut)
        s.legitimacy = (s.legitimacy - 2).coerceIn(0, 100)
        s.courtActions -= 1
        s.lastAusterityRound = s.round
        s.courtLog.add("تقشف البلاط وتقليص مصاريف القصر")
        recordRoyalDecree(s, "تقشف البلاط وتقليص مصاريف القصر")
        return CourtResult(true, "تراجعت نفقات القصر الدائمة بنسبة 5% من الإيراد مقابل -2 شرعية")
    }
}
