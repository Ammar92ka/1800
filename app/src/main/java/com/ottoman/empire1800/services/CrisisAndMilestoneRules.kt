package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.MilestoneDef
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.countryDef
import com.ottoman.empire1800.models.milestoneDefs
import java.util.Locale
import kotlin.math.roundToInt

data class CrisisAlert(
    val id: String,
    val title: String,
    val detail: String,
    val courtTabIndex: Int,
)

object CrisisRules {
    fun evaluate(s: GameState): List<CrisisAlert> {
        val alerts = mutableListOf<CrisisAlert>()

        val riskyPowers = s.powers.values
            .filter { !it.dissolved && it.danger >= 60.0 }
            .sortedByDescending { it.danger }
        if (riskyPowers.isNotEmpty()) {
            val top = riskyPowers.first()
            alerts.add(
                CrisisAlert(
                    id = "power_danger",
                    title = "خطر داخلي: ${top.name} (${top.danger.roundToInt()}/100)",
                    detail = if (riskyPowers.size > 1) {
                        "${riskyPowers.size} قوى داخلية تتجاوز حد الخطر (≥60)"
                    } else {
                        "احتمال مؤامرة أو تمرد وشيك ما لم تُحتوَ أو تُرضَ"
                    },
                    courtTabIndex = 0, // الاستقرار والقوى
                ),
            )
        }

        val riskyProvinces = s.econ.provinces.values
            .filter { it.status != ProvinceState.INDEPENDENT && it.autonomy >= 70.0 }
            .sortedByDescending { it.autonomy }
        if (riskyProvinces.isNotEmpty()) {
            val top = riskyProvinces.first()
            alerts.add(
                CrisisAlert(
                    id = "province_autonomy",
                    title = "تمرد ولايات: ${top.name} (${top.autonomy.roundToInt()}%)",
                    detail = if (riskyProvinces.size > 1) {
                        "${riskyProvinces.size} ولايات بلغت الحكم الذاتي وتهدد بالانفصال"
                    } else {
                        "بلغ الانفصال ≥70؛ تُفقد الولاية إذا تجاوزت 95"
                    },
                    courtTabIndex = 4, // الولايات
                ),
            )
        }

        if (s.econ.grain >= 150.0 || s.econ.inflation >= 8.0) {
            val parts = buildList {
                if (s.econ.grain >= 150.0) add("الحبوب ${s.econ.grain.roundToInt()}")
                if (s.econ.inflation >= 8.0) add("التضخم ${String.format(Locale.US, "%.1f", s.econ.inflation)}%")
            }
            alerts.add(
                CrisisAlert(
                    id = "economic_crisis",
                    title = "أزمة معيشية: ${parts.joinToString(" · ")}",
                    detail = "يضغط الغلاء والتضخم على الاستقرار ومزاج الولايات",
                    courtTabIndex = 3, // الاقتصاد والدين
                ),
            )
        }

        val losingWars = s.wars.filter { it.score <= -20.0 }.sortedBy { it.score }
        if (losingWars.isNotEmpty()) {
            val worst = losingWars.first()
            val enemyName = countryDef(worst.enemy)?.name ?: worst.enemy
            alerts.add(
                CrisisAlert(
                    id = "losing_war",
                    title = "جبهة متراجعة أمام $enemyName (${worst.score.roundToInt()})",
                    detail = "استمرار التراجع يهدد بهزيمة ثقيلة وفقدان ولايات حدودية",
                    courtTabIndex = 1, // الدبلوماسية والحروب
                ),
            )
        }

        return alerts
    }
}

object MilestoneRules {
    fun isMet(s: GameState, id: String): Boolean {
        val year = s.year
        return when (id) {
            "abolish_janissaries" -> {
                val jan = s.powers["janissaries"]
                jan != null && jan.dissolved && !jan.merged
            }
            "build_railway" -> s.completed.contains("railway") || s.completed.contains("hijaz_railway")
            "survive_50_years" -> year >= 1850
            "clear_foreign_debt" -> s.hadForeignDebt && s.foreignDebt <= 0.0
            "win_major_war" -> s.wonMajorWar
            "preserve_empire_1875" -> year >= 1875 && s.econ.provinces.values.all { !it.isLost }
            else -> false
        }
    }

    fun evaluate(s: GameState): List<String> {
        if (s.foreignDebt > 0.0 || s.dadActive || s.defaultRound >= 0) {
            s.hadForeignDebt = true
        }
        val unlockedNow = mutableListOf<String>()
        for (def: MilestoneDef in milestoneDefs) {
            if (!s.milestones.contains(def.id) && isMet(s, def.id)) {
                s.milestones.add(def.id)
                unlockedNow.add("إنجاز سلطاني جديد: ${def.title}")
            }
        }
        return unlockedNow
    }
}
