package com.ottoman.empire1800.ui.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** عزل الأرقام عن اتجاه النص حتى تظهر علامة السالب في مكانها الصحيح. */
fun ltr(s: String): String = "\u2066$s\u2069"

/** رقم بفواصل الآلاف، مثل 12,000. */
fun formatNumber(v: Number): String {
    val r = v.toDouble().roundToLong()
    val digits = abs(r).toString()
    val buf = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) buf.append(',')
        buf.append(digits[i])
    }
    return ltr(if (r < 0) "-$buf" else buf.toString())
}

/** رقم بإشارة صريحة، مثل +800 أو -1,200. */
fun formatSigned(v: Number): String {
    val r = v.toDouble().roundToLong()
    if (r == 0L) return ltr("0")
    val digits = formatNumber(abs(r))
        .replace("\u2066", "")
        .replace("\u2069", "")
    return ltr(if (r > 0) "+$digits" else "-$digits")
}

private fun trimDecimals(v: Double): String {
    var t = String.format(Locale.US, "%.2f", v)
    if (t.contains('.')) {
        t = t.replace(Regex("0+$"), "").replace(Regex("\\.$"), "")
    }
    return t
}

/** مبلغ بالقروش بصيغة مختصرة: 2.5 مليون، 800 ألف. */
fun formatMoney(v: Number): String {
    val d = v.toDouble()
    val a = abs(d)
    val body = when {
        a >= 1e9 -> "${trimDecimals(a / 1e9)} مليار"
        a >= 1e6 -> "${trimDecimals(a / 1e6)} مليون"
        a >= 1e3 -> "${trimDecimals(a / 1e3)} ألف"
        else -> a.roundToLong().toString()
    }
    return if (d < 0) ltr("-") + body else body
}

/** مبلغ مختصر بإشارة صريحة. */
fun formatMoneySigned(v: Number): String {
    val d = v.toDouble()
    if (d.roundToLong() == 0L) return "0"
    val body = formatMoney(abs(d))
    return if (d > 0) "+$body" else "-$body"
}
