package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import kotlin.math.roundToDouble
import kotlin.random.Random

/** أول سنتين (4 أنصاف) بلا أحداث عشوائية: يتعلم اللاعب الأنظمة قبل أن تضربه المفاجآت. */
const val GRACE_ROUNDS: Int = 4

fun inGrace(s: GameState): Boolean = s.round < GRACE_ROUNDS * s.k

/** مولد أرقام عشوائي حتمي: نفس الحفظ ونفس الجولة يعطيان نفس الأحداث دائما. */
fun rngFor(s: GameState, salt: Int): Random {
    val seed = (s.seed.toLong() * 7919L + s.round.toLong() * 104729L + salt.toLong() * 31L) and 0x7fffffffL
    return Random(seed.toInt())
}

/** تكلفة الإجراءات تقاس بنسبة من الإيراد السنوي حتى تكبر مع الدولة. */
fun costShare(s: GameState, share: Double): Double = (s.budget.totalRevenue * share).roundToDouble()

/** نتيجة إجراء من الديوان. */
data class CourtResult(val ok: Boolean, val message: String, val detail: String? = null)

/** تجزئة ثابتة للنص. */
fun stableHash(s: String): Int {
    var h = 7L
    for (ch in s) {
        h = (h * 31L + ch.code.toLong()) and 0x7fffffffL
    }
    return h.toInt()
}

/** السنة الميلادية لفترة معينة (0 = الفترة الأولى من 1800). */
fun yearOfRound(round: Int, ppy: Int = 2): Int = 1800 + round / if (ppy == 4) 4 else 2

fun periodNoun(ppy: Int): String = if (ppy == 4) "ربع السنة" else "نصف السنة"
fun periodUnit(ppy: Int): String = if (ppy == 4) "ربع سنة" else "نصف سنة"
fun periodPlural(ppy: Int): String = if (ppy == 4) "أرباع" else "أنصاف"
fun exhaustedCourtMsg(s: GameState): String =
    if (s.ppy == 4) "استنفدت إجراءات الديوان لربع السنة هذا" else "استنفدت إجراءات الديوان لنصف السنة هذا"

private val periodHalves = listOf("كانون الثاني - حزيران", "تموز - كانون الأول")
private val periodQuarters = listOf(
    "كانون الثاني - آذار",
    "نيسان - حزيران",
    "تموز - أيلول",
    "تشرين الأول - كانون الأول",
)

fun formatPeriodTag(round: Int, ppy: Int = 2): String {
    val year = yearOfRound(round, ppy)
    val label = if (ppy == 4) periodQuarters[round % 4] else periodHalves[round % 2]
    return "$label $year"
}

fun recordRoyalDecree(s: GameState, entry: String) {
    val clean = entry.trim().take(120)
    if (clean.isEmpty()) return
    val stamped = "${formatPeriodTag(s.round, s.ppy)}: $clean"
    s.decrees.add(stamped)
    if (s.decrees.size > 8) {
        s.decrees.subList(0, s.decrees.size - 8).clear()
    }
}

