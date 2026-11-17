package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.EconomyState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.SpyReport
import com.ottoman.empire1800.models.clampD
import kotlin.math.roundToInt
import kotlin.random.Random

/** المناخ والكوارث: جفاف وفيضان وطاعون ووفرة. */
object ClimateRules {
    fun step(s: GameState): List<String> {
        val e = s.econ
        val notes = mutableListOf<String>()
        for (p in e.provinces.values) {
            if (p.harvestRounds > 0) {
                p.harvestRounds -= 1
                if (p.harvestRounds <= 0) p.harvest = 1.0
            } else {
                p.harvest = 1.0
            }
        }
        e.grain += (100.0 - e.grain) * 0.2 * s.f
        if (!inGrace(s)) {
            val rng = rngFor(s, 3)
            val m = s.diff.events * s.f
            val shock = if (ProjectRules.hasPerk(s, "granary")) 0.6 else 1.0
            val r = rng.nextDouble()
            if (r < 0.10 * m) {
                val a = pick(rng, e)
                val hit = mutableListOf(a)
                if (rng.nextDouble() < 0.4) {
                    val b = pick(rng, e)
                    if (b.key != a.key) hit.add(b)
                }
                val resist = ProjectRules.hasPerk(s, "drought")
                for (p in hit) {
                    p.harvest = if (resist) 0.8 else 0.65
                    p.harvestRounds = s.k
                }
                e.grain += (if (resist) 20.0 else 35.0) * shock
                notes.add("جفاف في ${hit.joinToString(" و") { it.name }} يضرب المحصول ويرفع الأسعار")
            } else if (r < 0.15 * m) {
                val p = pick(rng, e)
                p.harvest = 0.8
                p.harvestRounds = s.k
                p.mood = clampD(p.mood - 2.0, 0.0, 100.0)
                notes.add("فيضانات في ${p.name} تتلف الحقول")
            } else if (r < 0.19 * m) {
                val p = pick(rng, e)
                val resist = ProjectRules.hasPerk(s, "plague")
                p.mood = clampD(p.mood - (if (resist) 2.0 else 4.0), 0.0, 100.0)
                s.demog.population = clampD(s.demog.population * (if (resist) 0.997 else 0.994), 1.0, 500.0)
                s.stab.shift("social", if (resist) -1.0 else -2.0)
                notes.add("طاعون في ${p.name} يحصد الناس ويشل التجارة")
            } else if (r < 0.27 * m) {
                for (p in e.provinces.values) {
                    p.harvest = 1.1
                    p.harvestRounds = s.k
                }
                e.grain -= 15.0
                notes.add("موسم وفير يخفض أسعار الحبوب")
            } else if (r < 0.30 * m) {
                val p = pick(rng, e)
                p.harvest = 0.8
                p.harvestRounds = s.k
                e.grain += 15.0 * shock
                notes.add("جراد وصقيع في ${p.name} ينقصان الغلة")
            }
        }
        e.grain = clampD(e.grain, 60.0, 260.0)
        return notes
    }

    private fun pick(rng: Random, e: EconomyState): ProvinceState {
        val live = e.provinces.values.filter { !it.isLost }
        if (live.isEmpty()) return e.provinces.values.first()
        val total = live.sumOf { it.def.weight }
        var x = rng.nextDouble() * total
        for (p in live) {
            x -= p.def.weight
            if (x <= 0.0) return p
        }
        return live.last()
    }
}

/** الاستخبارات: تقارير عن الدول وعن القوى الداخلية، بدرجات موثوقية. */
object SpyRules {
    private val foreignChance = listOf(0.0, 0.5, 0.7, 0.9)
    private val foreignAccuracy = listOf(0.0, 0.55, 0.75, 0.9)
    private val domesticChance = listOf(0.0, 0.6, 0.85)
    private val domesticAccuracy = listOf(0.0, 0.6, 0.85)
    const val MAX_KEPT: Int = 8

    fun step(s: GameState): List<String> {
        val notes = mutableListOf<String>()

        for (r in s.intel) {
            if (!r.revealed && r.round < s.round) {
                r.revealed = true
                notes.add("تبين أن ${if (r.truth) "صحة" else "كذب"} تقرير الاستخبارات: ${r.claim}")
            }
        }

        var i = 0
        for (c in s.countries.values) {
            i++
            val q = c.intelQuality.coerceIn(0, 3)
            if (q == 0) continue
            val rng = rngFor(s, 400 + i)
            if (rng.nextDouble() >= foreignChance[q] * s.f) continue
            val hostile = c.relation < 35.0
            val truthful = rng.nextDouble() < foreignAccuracy[q]
            val claimsHostile = if (truthful) hostile else !hostile
            val claim = if (claimsHostile) {
                "${c.name} تحشد قواتها وتجهز لحرب ضدنا"
            } else {
                "${c.name} لا تنوي الحرب علينا وتميل للمهادنة"
            }
            val relPct = (foreignAccuracy[q] * 100).roundToInt()
            s.intel.add(
                SpyReport(
                    round = s.round,
                    target = c.key,
                    claim = claim,
                    reliability = relPct,
                    truth = truthful,
                ),
            )
            notes.add("تقرير استخبارات (موثوقية $relPct%): $claim")
        }

        val lvl = s.domesticSpy.coerceIn(0, 2)
        if (lvl > 0) {
            val active = s.powers.values.filter { !it.dissolved }.sortedByDescending { it.danger }
            if (active.isNotEmpty()) {
                val rng = rngFor(s, 450)
                if (rng.nextDouble() < domesticChance[lvl] * s.f) {
                    val top = active.first()
                    val real = top.danger >= 55.0
                    val truthful = rng.nextDouble() < domesticAccuracy[lvl]
                    val claimsPlot = if (truthful) real else !real
                    val claim = if (claimsPlot) {
                        "${top.name} تدبر شيئا ضد العرش"
                    } else {
                        "${top.name} هادئة ولا تنوي شيئا"
                    }
                    val relPct = (domesticAccuracy[lvl] * 100).roundToInt()
                    s.intel.add(
                        SpyReport(
                            round = s.round,
                            target = "domestic",
                            claim = claim,
                            reliability = relPct,
                            truth = truthful,
                        ),
                    )
                    notes.add("تقرير استخبارات داخلي (موثوقية $relPct%): $claim")
                }
            }
        }

        if (s.intel.size > MAX_KEPT) {
            val excess = s.intel.size - MAX_KEPT
            s.intel.subList(0, excess).clear()
        }
        return notes
    }
}
