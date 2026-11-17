package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.CountryState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.countryDef

/** اقتصادات الدول وقواها العسكرية: تنمو بمعدلات تاريخية تقريبية مع تقلب عشوائي حتمي. */
object ForeignRules {
    fun ottomanEconomy(s: GameState): Double {
        val pi = if (s.econ.priceIndex <= 0.0) 1.0 else s.econ.priceIndex
        val factoryRev = IndustryRules.annualFactoryRevenue(s.econ, s.budget, s.year, s.demog.literacy)
        val real = (s.budget.totalRevenue + factoryRev) / pi
        return 100.0 * real / 18_000_000.0 * s.econ.revenueMultiplier
    }

    fun ottomanMilitary(s: GameState): Double = s.army.toDouble()

    fun econRatio(s: GameState, c: CountryState): Double {
        val ours = ottomanEconomy(s)
        return c.economy / if (ours < 1.0) 1.0 else ours
    }

    fun milRatio(s: GameState, c: CountryState): Double {
        val ours = ottomanMilitary(s)
        return c.military / if (ours < 1.0) 1.0 else ours
    }

    fun strengthLabel(ratio: Double): String = when {
        ratio < 0.8 -> "أضعف منا"
        ratio < 1.25 -> "مقارب لنا"
        ratio < 2.0 -> "أقوى منا"
        else -> "أقوى منا بكثير"
    }

    fun annualMilGrowth(key: String, year: Int): Double {
        val base = countryDef(key)?.milGrowth ?: 0.0
        var extra = 0.0
        when (key) {
            "france" -> {
                if (year <= 1814) extra = 0.02
                else if (year <= 1829) extra = -0.06
            }
            "russia" -> {
                if (year in 1854..1856) extra = -0.04
                else if (year in 1857..1870) extra = -0.01
            }
            "austria" -> if (year >= 1866) extra = -0.01
            "germany" -> if (year >= 1860) extra = 0.025
        }
        return base + extra
    }

    fun annualEcoGrowth(key: String, year: Int): Double {
        val base = countryDef(key)?.ecoGrowth ?: 0.0
        var extra = 0.0
        if (key == "germany" && year >= 1850) extra = 0.012
        if (key == "austria" && year >= 1900) extra = 0.005
        return base + extra
    }

    fun step(s: GameState): List<String> {
        val year = s.year
        val f = s.f
        var i = 0
        for (c in s.countries.values) {
            i++
            val rng = rngFor(s, 700 + i)
            val noiseE = (rng.nextDouble() - 0.5) * 0.006
            val noiseM = (rng.nextDouble() - 0.5) * 0.010
            c.economy = clampD(c.economy * (1.0 + (annualEcoGrowth(c.key, year) / 2.0 + noiseE) * f), 20.0, 5000.0)
            c.military = clampD(c.military * (1.0 + (annualMilGrowth(c.key, year) / 2.0 + noiseM) * f), 8.0, 300.0)
        }
        return emptyList()
    }
}
