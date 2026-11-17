package com.ottoman.empire1800.models

import org.json.JSONObject
import kotlin.math.max

/** بند في الميزانية (إيراد أو نفقة) بمفتاح ثابت يستخدمه الذكاء الاصطناعي واسم عربي للعرض. */
data class BudgetLine(val key: String, val label: String)

val revenueLines: List<BudgetLine> = listOf(
    BudgetLine("customs", "الجمارك والمكوس"),
    BudgetLine("land", "الضرائب الزراعية والالتزام والمالكانة"),
    BudgetLine("provinces", "أموال الولايات (مصر وغيرها)"),
    BudgetLine("other", "الجزية والاحتكارات وضرائب أخرى"),
)

val spendingLines: List<BudgetLine> = listOf(
    BudgetLine("army", "الجيش والبحرية"),
    BudgetLine("palace", "القصر والبلاط"),
    BudgetLine("admin", "الإدارة والبيروقراطية"),
    BudgetLine("civil", "التعليم والصحة والأشغال"),
)

fun isRevenueLine(key: String): Boolean = revenueLines.any { it.key == key }
fun isSpendingLine(key: String): Boolean = spendingLines.any { it.key == key }
fun isBudgetLine(key: String): Boolean = isRevenueLine(key) || isSpendingLine(key)

fun budgetLineLabel(key: String): String {
    for (l in revenueLines + spendingLines) {
        if (l.key == key) return l.label
    }
    return key
}

/** ميزانية الدولة السنوية بالقروش. 1 قرش = 40 بارة. */
class Budget(
    revenue: Map<String, Double>,
    spending: Map<String, Double>,
) {
    val revenue: MutableMap<String, Double> = revenue.toMutableMap()
    val spending: MutableMap<String, Double> = spending.toMutableMap()

    val totalRevenue: Double get() = revenue.values.sum()
    val totalSpending: Double get() = spending.values.sum()

    fun lineValue(key: String): Double = revenue[key] ?: spending[key] ?: 0.0

    /** يغير بندا بقيمة سنوية، ولا يسمح بنزوله تحت الصفر. المفتاح غير المعروف يُتجاهل. */
    fun change(key: String, delta: Double) {
        if (!delta.isFinite()) return
        if (revenue.containsKey(key)) {
            revenue[key] = max(0.0, (revenue[key] ?: 0.0) + delta)
        } else if (spending.containsKey(key)) {
            spending[key] = max(0.0, (spending[key] ?: 0.0) + delta)
        }
    }

    /** نمو اسمي (تضخم) لكل البنود. */
    fun grow(rate: Double) {
        for (k in revenue.keys.toList()) {
            revenue[k] = (revenue[k] ?: 0.0) * (1.0 + rate)
        }
        for (k in spending.keys.toList()) {
            spending[k] = (spending[k] ?: 0.0) * (1.0 + rate)
        }
    }

    fun copy(): Budget = Budget(revenue = revenue, spending = spending)

    fun toJson(): JSONObject = JSONObject().apply {
        put("revenue", JSONObject().apply {
            revenue.forEach { (k, v) -> put(k, v) }
        })
        put("spending", JSONObject().apply {
            spending.forEach { (k, v) -> put(k, v) }
        })
    }

    companion object {
        fun initial1800(): Budget = Budget(
            revenue = linkedMapOf(
                "customs" to 2_000_000.0,
                "land" to 9_000_000.0,
                "provinces" to 3_000_000.0,
                "other" to 4_000_000.0,
            ),
            spending = linkedMapOf(
                "army" to 8_000_000.0,
                "palace" to 3_500_000.0,
                "admin" to 4_500_000.0,
                "civil" to 1_000_000.0,
            ),
        )

        fun fromJson(raw: Any?): Budget {
            val base = initial1800()
            val obj = raw as? JSONObject ?: return base

            fun read(rawMap: Any?, fallback: Map<String, Double>): MutableMap<String, Double> {
                val out = fallback.toMutableMap()
                val mapObj = rawMap as? JSONObject ?: return out
                for (k in fallback.keys) {
                    val v = mapObj.opt(k)
                    if (v is Number) {
                        val d = v.toDouble()
                        if (d.isFinite()) out[k] = d.coerceIn(0.0, 1e15)
                    }
                }
                return out
            }

            return Budget(
                revenue = read(obj.opt("revenue"), base.revenue),
                spending = read(obj.opt("spending"), base.spending),
            )
        }
    }
}
