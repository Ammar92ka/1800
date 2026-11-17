package com.ottoman.empire1800.data

import com.ottoman.empire1800.models.GameState

/** مرجع مختصر للتاريخ الحقيقي (1800-1910) يرسل للذكاء الاصطناعي للمعايرة. */
data class HistoryAnchor(
    val from: Int,
    val to: Int,
    val text: String,
    val skipIfDissolved: String? = null,
)

val historyAnchorList: List<HistoryAnchor> = listOf(
    HistoryAnchor(1798, 1801, "الحملة الفرنسية على مصر ثم جلاء الفرنسيين بمساعدة بريطانيا (1798-1801)."),
    HistoryAnchor(1804, 1813, "بدء الثورة الصربية 1804."),
    HistoryAnchor(1806, 1812, "الحرب العثمانية الروسية 1806-1812 وانتهاؤها بصلح بوخارست."),
    HistoryAnchor(1805, 1805, "تولي محمد علي باشا ولاية مصر 1805."),
    HistoryAnchor(1807, 1808, "خلع سليم الثالث وسلطنة مصطفى الرابع 1807، ثم سلطنة محمود الثاني وحادثة علمدار مصطفى باشا 1808."),
    HistoryAnchor(1821, 1829, "الثورة اليونانية 1821-1829 وانتهاؤها باستقلال اليونان (نافارين 1827)."),
    HistoryAnchor(1826, 1826, "إلغاء الانكشارية (الواقعة الخيرية) وبناء جيش جديد 1826.", skipIfDissolved = "janissaries"),
    HistoryAnchor(1831, 1841, "حروب محمد علي مع الدولة 1831-1833 و1839-1841 والتدخل الأوروبي."),
    HistoryAnchor(1839, 1856, "خط كلخانة 1839 وبدء التنظيمات في عهد عبد المجيد الأول، وخط همايون 1856."),
    HistoryAnchor(1853, 1858, "حرب القرم 1853-1856 وأول قروض خارجية 1854، وقانون الأراضي 1858."),
    HistoryAnchor(1861, 1876, "عهد عبد العزيز 1861-1876، وافتتاح قناة السويس 1869."),
    HistoryAnchor(1875, 1876, "توقف الدولة عن سداد الديون 1875، وخلع عبد العزيز 1876 والدستور الأول وبداية عهد عبد الحميد الثاني."),
    HistoryAnchor(1877, 1881, "الحرب الروسية العثمانية 1877-1878 ومؤتمر برلين، وإنشاء إدارة الديون العامة 1881."),
    HistoryAnchor(1882, 1897, "الاحتلال البريطاني لمصر 1882، والحرب اليونانية العثمانية 1897."),
    HistoryAnchor(1908, 1909, "الثورة الدستورية (الاتحاد والترقي) 1908 وخلع عبد الحميد الثاني 1909."),
)

val historyAnchors: List<String> get() = historyAnchorList.map { it.text }

fun anchorsFor(year: Int, s: GameState? = null): List<String> =
    historyAnchorList
        .filter { a ->
            a.to >= year - 1 &&
                a.from <= year + 5 &&
                (a.skipIfDissolved == null || s?.powers?.get(a.skipIfDissolved)?.dissolved != true)
        }
        .map { it.text }
