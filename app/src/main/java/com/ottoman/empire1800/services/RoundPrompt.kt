package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.anchorsFor
import com.ottoman.empire1800.data.economicSystemDef
import com.ottoman.empire1800.data.frontierRegionDef
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.data.subProvinceDef
import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.Budget
import com.ottoman.empire1800.models.BudgetLine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.PowerProject
import com.ottoman.empire1800.models.StabilityState
import com.ottoman.empire1800.models.WorldMemory
import com.ottoman.empire1800.models.countryDefs
import com.ottoman.empire1800.models.grandVizierDef
import com.ottoman.empire1800.models.powerDef
import com.ottoman.empire1800.models.powerDefs
import com.ottoman.empire1800.models.revenueLines
import com.ottoman.empire1800.models.spendingLines
import java.util.Locale
import kotlin.math.roundToInt

private fun n(v: Number): String = v.toDouble().roundToInt().toString()

private fun linesText(lines: List<BudgetLine>, b: Budget): String =
    lines.joinToString("، ") { "${it.key}=${n(b.lineValue(it.key))}" }

private fun clip(s: String, maxLen: Int): String =
    if (s.length <= maxLen) s else "${s.substring(0, maxLen - 1)}…"

private const val HISTORY_ENTRIES = 9
private const val DETAILED_ENTRIES = 3

private const val SCHEMA_EXAMPLE =
    """{"headline":"عنوان","briefing":"ملخص","chronicle":"ملاحظة","world":{"wars":[],"alliances":[],"reforms":[],"promises":[]},""" +
        """"decisions":[{"category":"internal","title":"..","place":"..","scene":"..","dilemma":"..","options":[{"text":"..","result":"..","reaction":"..","note":"..",""" +
        """"cost":0,"loan":0,"now":{"legitimacy":0,"stability":0,"army":0,"foreign":0,"education":0},"later":{"legitimacy":0,"stability":0,"army":0,"foreign":0,"education":0},""" +
        """"delay":0,"line_changes":[{"line":"army","delta":0}],"rel":{"britain":0},"pow":{"ulema":0},"dissolve":"","war":"","peace":"","project":"","conquer":""}]}]}"""

fun buildRoundPrompt(s: GameState, problem: String? = null): String {
    val revenue = s.budget.totalRevenue
    val ratio = EconomyRules.debtRatio(s.debt, s.budget)
    val year = s.year
    val f = s.f
    val pace = if (s.ppy == 4) "ربع سنوية (كل 3 أشهر)" else "نصف سنوية (كل 6 أشهر)"
    val unit = if (s.ppy == 4) "أرباع" else "أنصاف"
    val buf = StringBuilder()

    // القسم الثابت
    buf.appendLine(
        "أنت راوٍ ومحكّم تاريخي للعبة الدولة العثمانية 1800-${GameEngine.END_YEAR}؛ اللاعب هو السلطان نفسه. " +
            "اكتب جولة $pace: briefing ثم ثلاثة قرارات (internal وeconomy وforeign) لكل منها ثلاثة خيارات مختلفة جوهريا، ولكل خيار ثمن وخطر.",
    )
    buf.appendLine("القواعد:")
    buf.appendLine(
        "1) التاريخ الحقيقي هو الخلفية، والتاريخ البديل يبنى على قرارات اللاعب ونتائجها فقط (آخر قراراته وتقرير الميزانية والأحداث أدناه). لا مفارقات تاريخية، وللتنفيذ وقت ولكل قرار ردود فعل.",
    )
    buf.appendLine(
        "2) المناخ والمؤامرات وتقارير الاستخبارات وعلاقات الدول تحسبها اللعبة بأرقام: استثمرها سرديا ولا تخترع أرقامها. تقارير الاستخبارات قد تكون كاذبة فعاملها كشائعات.",
    )
    val allLinesDesc = (revenueLines + spendingLines).joinToString("، ") { "${it.key}=${it.label}" }
    buf.appendLine(
        "3) حدود الأرقام (يخفض الزائد تلقائيا): cost حتى ${n(revenue * Calibrator.MAX_COST_SHARE * f)}، loan حتى ${n(revenue * Calibrator.MAX_LOAN_SHARE * DiplomacyRules.creditFactor(s) * f)}، " +
            "line_changes حتى ${Calibrator.MAX_LINE_CHANGES} بنود وكل تغيير سنوي بين ±${n(revenue * Calibrator.MAX_LINE_SHARE * f)} (مفاتيح: $allLinesDesc؛ الإيراد الجديد يتأخر ${s.k} فترات على الأقل - 6 أشهر). " +
            "now بين ±${Calibrator.capNow(f)}، later بين ±${Calibrator.capLater(f)}، delay من 0 إلى ${Calibrator.capDelay(f)} فترات ($unit سنوية)، ومجموع المكاسب الموجبة في الخيار ≤ ${Calibrator.capGain(f)}.",
    )
    val countriesList = countryDefs.joinToString("، ") { "${it.key}=${it.name}" }
    val activePowerDefs = powerDefs.filter { s.powers[it.key]?.dissolved != true }
    val powersList = activePowerDefs.joinToString("، ") { "${it.key}=${it.name}" }
    buf.appendLine(
        "4) rel: تغيير علاقة دول ($countriesList) حتى ±${Calibrator.capRelation(f)}. " +
            "pow: تغيير رضا القوى الفاعلة فقط ($powersList) حتى ±${Calibrator.capPowerMood(f)}. " +
            "اذكر فقط ما يتأثر فعلا (حتى ${Calibrator.MAX_MAP_KEYS} لكل منهما). dissolve: مفتاح قوة فاعلة يقرر الخيار نفسه حلها نهائيا (نادر). " +
            "war: دولة يعلن الخيار الحرب عليها (أو تعلنها علينا). peace: دولة يطلب الخيار الصلح معها. " +
            "project: مفتاح مشروع قومي من «المشاريع المتاحة» أدناه يبدأه الخيار (واحد فقط في كل وقت).",
    )
    buf.appendLine(
        "4أ) الحروب والمشاريع والولايات والديون تحسبها اللعبة بالأرقام (نقاط الحرب، أقساط المشروع، الانفصال، الفوائد): لا تقرر نتيجة معركة ولا تخترع أرقامها، اكتب سردها وقراراتها فقط.",
    )
    buf.appendLine(
        "5) العربية المبسطة الموجزة: scene≤60 كلمة، dilemma≤30، text≤28، result≤35، reaction≤22، note (الأساس التاريخي)≤18، briefing≤90.",
    )
    buf.appendLine(
        "6) world: حدّث القوائم كاملة وقصيرة (≤${WorldMemory.MAX_ITEMS} عناصر لكل قائمة و≤12 كلمة للعنصر): الحروب الجارية، التحالفات والمعاهدات، الإصلاحات الجارية، الوعود. chronicle ملاحظة ≤40 كلمة لما لا يتسع فيها.",
    )
    buf.appendLine("الرد: كائن JSON واحد فقط، بلا نص خارجه ولا أسوار كود، بهذا الشكل (الحقول الاختيارية تحذف إن كانت فارغة):")
    buf.appendLine(SCHEMA_EXAMPLE)
    buf.appendLine()

    // القسم المتغير
    val vizier = grandVizierDef(s.grandVizier)
    buf.appendLine("الفترة: ${GameEngine.periodLabel(s.round, s.ppy)} (الجولة ${s.round + 1} من ${GameEngine.totalRoundsFor(s.ppy)}). الصدر الأعظم: ${vizier.name} (${vizier.promptHint}).")
    buf.appendLine(
        "الحالة (قروش، 1 قرش = 40 بارة): خزينة ${n(s.treasury)}، دين ${n(s.debt)} (${String.format(Locale.US, "%.2f", ratio)}× الإيراد السنوي).",
    )
    val stabDetails = StabilityState.keys.joinToString("، ") { "${StabilityState.labels[it]} ${s.stab[it].roundToInt()}" }
    buf.appendLine(
        "المؤشرات/100: شرعية ${s.legitimacy}، استقرار ${s.stability} [$stabDetails]، " +
            "جيش ${s.army} (≈${n(s.army * 4000)} جندي)، مكانة دولية ${s.foreign}، تعليم ${s.education}.",
    )
    buf.appendLine("إيراد سنوي ${n(revenue)}: ${linesText(revenueLines, s.budget)}")
    buf.appendLine("نفقات سنوية ${n(s.budget.totalSpending)}: ${linesText(spendingLines, s.budget)}")
    val provincesSummary = s.econ.provinces.values.joinToString("، ") { p ->
        if (p.isLost) "${p.name} مفقودة"
        else "${p.name} ${n(p.mood)}/${n(p.autonomy)}${if (p.status == "autonomous") " شبه مستقلة" else ""}"
    }
    val econSys = economicSystemDef(s.econ.economicSystem)
    val totalFactories = IndustryRules.totalFactoryLevels(s.econ)
    val paperDiscPct = (IndustryRules.paperMillAdminDiscount(s.econ) * 100.0).roundToInt()
    val coalStr = if (IndustryRules.hasCoalPower(s.econ)) "، طاقة الفحم نشطة (+25%)" else ""
    val oilNames = s.econ.oilDiscovered.mapNotNull { subProvinceDef(it)?.name }
    val oilStr = if (oilNames.isNotEmpty()) "، نفط مكتشف: ${oilNames.joinToString(" و")}" else ""
    val controlledFrontiers = s.econ.frontierRegions.values
        .filter { it.controlled && !it.isLost }
        .map { frontierRegionDef(it.key)?.name ?: it.key }
    val availFrontiers = s.econ.frontierRegions.values
        .filter { !it.controlled }
        .map { "${it.key}=${frontierRegionDef(it.key)?.name ?: it.key}" }
    buf.appendLine(
        "اقتصاد: نظام ${econSys.name}، صناعة $totalFactories مستوى مصانع${if (paperDiscPct > 0) " (توفير الورق -$paperDiscPct%)" else ""}$coalStr$oilStr. " +
            "حبوب ${n(s.econ.grain)} (100 معتاد)، تضخم ${String.format(Locale.US, "%.1f", s.econ.inflation)}%، فساد ${n(s.econ.corruption)}، " +
            "$unit سنوات عجز متتالية ${s.econ.deficitStreak}. الولايات (مزاج/ميل للانفصال): $provincesSummary.",
    )
    buf.appendLine(
        "الثغور: مفتوحة [${controlledFrontiers.joinToString("، ").ifEmpty { "لا شيء" }}]، متاحة للفتح عبر conquer أو النصر الحاسم [${availFrontiers.joinToString("، ").ifEmpty { "مكتملة" }}].",
    )
    buf.appendLine(
        "سكان ${String.format(Locale.US, "%.1f", s.demog.population)} مليون، قراءة وكتابة ${String.format(Locale.US, "%.1f", s.demog.literacy)}%، مدارس ابتدائية ${n(s.demog.schools)}.",
    )

    val activePowers = s.powers.values.filter { !it.dissolved }
    val powersText = if (activePowers.isEmpty()) {
        "لا قوى فاعلة"
    } else {
        activePowers.joinToString("؛ ") { p ->
            val tag = if (p.reformed) " (مصلحة)" else ""
            "${p.name}$tag نفوذ ${n(p.influence)} رضا ${n(p.satisfaction)} خطر ${n(p.danger)}"
        }
    }
    buf.appendLine("قوى الدولة الفاعلة: $powersText.")
    val dissolvedPowers = s.powers.values.filter { it.dissolved }
    if (dissolvedPowers.isNotEmpty()) {
        val disText = dissolvedPowers.joinToString("، ") { p ->
            "${p.key}=${p.name} (${if (p.merged) "مندمجة في الدولة" else "محلولة نهائيا"})"
        }
        buf.appendLine(
            "DISSOLVED POWERS (قوى منحلة/مندمجة نهائيا - ممنوع تماما ذكرها كقوى فاعلة أو كتابة معضلات/خيارات لترضيتها أو حلها أو وضع مفاتيحها في pow أو dissolve): $disText.",
        )
    }

    val countriesText = s.countries.values.joinToString("؛ ") { c ->
        val tags = buildList {
            if (c.embassy) add("سفارة")
            if (c.alliance) add("حلف دفاعي")
            if (c.spy > 0) add("تجسس ${c.spy}")
        }
        val eco = String.format(Locale.US, "%.1f", ForeignRules.econRatio(s, c))
        val mil = String.format(Locale.US, "%.1f", ForeignRules.milRatio(s, c))
        "${c.name} ${n(c.relation)}${if (tags.isEmpty()) "" else " (${tags.joinToString("، ")})"} اقتصاد $eco× جيش $mil×"
    }
    buf.appendLine("الدول (علاقة/100، وقوتها نسبة إلى قوتنا): $countriesText.")

    if (s.wars.isNotEmpty()) {
        val warsText = s.wars.joinToString("؛ ") { w ->
            "${w.enemyName} (${WarRules.describe(w)}، نقاط ${n(w.score)}، منذ ${w.halves} فترات، ${if (w.byUs) "بدأناها" else "بدأوها"})"
        }
        buf.appendLine("حروب جارية: $warsText.")
    }
    if (s.domesticSpy > 0) buf.appendLine("الاستخبارات الداخلية: مستوى ${s.domesticSpy}.")

    val prj = s.project
    if (prj != null) {
        val d = projectDef(prj.key)
        buf.appendLine(
            "مشروع قومي جار: ${d?.name ?: prj.key} اكتمل ${(prj.progress * 100).roundToInt()}% (باقٍ ${prj.remaining} فترات${if (prj.stalled > 0) "، متعثر للتمويل" else ""}).",
        )
    } else {
        val open = ProjectRules.available(s)
        if (open.isNotEmpty()) {
            buf.appendLine("المشاريع المتاحة للبدء (project): ${open.joinToString("، ") { "${it.key}=${it.name}" }}.")
        }
    }
    if (s.completed.isNotEmpty()) {
        buf.appendLine("مشاريع منجزة: ${s.completed.joinToString("، ") { projectDef(it)?.name ?: it }}.")
    }
    if (s.foreignDebt > 0.0 || s.defaultRound >= 0) {
        buf.appendLine(
            "الدين الخارجي ${n(s.foreignDebt)} من ${n(s.debt)}، تصنيف ائتماني ${n(DebtRules.creditRating(s))}/100" +
                "${if (s.defaultRound >= 0) "، سبق العجز عن السداد" else ""}${if (s.dadActive) "، إدارة الديون العامة تحصّل جزءا من الإيراد" else ""}.",
        )
    }
    if (s.projects.isNotEmpty()) {
        val pp = s.projects.joinToString("؛ ") {
            "${PowerProject.kindLabels[it.kind]} ${powerDef(it.power)?.name ?: it.power} (${it.remaining} فترات)"
        }
        buf.appendLine("مشاريع على القوى: $pp.")
    }

    val rumors = s.intel.filter { !it.revealed }
    if (rumors.isNotEmpty()) {
        buf.appendLine("تقارير استخبارات غير مؤكدة: ${rumors.joinToString("؛ ") { "${it.claim} (${it.reliability}%)" }}.")
    }

    if (s.pending.isNotEmpty()) {
        val pend = s.pending.joinToString("؛ ") {
            "${GameEngine.periodLabel(it.dueRound, s.ppy)}: ${clip(it.source, 60)}"
        }
        buf.appendLine("آثار مؤجلة قادمة: $pend.")
    }

    if (!s.world.isEmpty) {
        fun l(label: String, xs: List<String>): String = if (xs.isEmpty()) "" else "$label: ${xs.joinToString("؛ ")}. "
        val mem = "${l("حروب", s.world.wars)}${l("تحالفات", s.world.alliances)}${l("إصلاحات", s.world.reforms)}${l("وعود", s.world.promises)}".trim()
        buf.appendLine("ذاكرة العالم: $mem")
    }
    val formalAllies = s.countries.values.filter { it.alliance }.map { it.name }
    if (formalAllies.isNotEmpty()) {
        buf.appendLine("تحالفات دفاعية قائمة: ${formalAllies.joinToString("، ")}.")
    }
    if (s.decrees.isNotEmpty()) {
        buf.appendLine("فرمانات السلطان الأخيرة (تستمر عبر الجولات): ${s.decrees.joinToString("؛ ")}.")
    }
    if (s.chronicle.trim().isNotEmpty()) buf.appendLine("ملاحظة: ${clip(s.chronicle.trim(), Calibrator.MAX_CHRONICLE)}")
    if (s.lastReport.trim().isNotEmpty()) buf.appendLine(s.lastReport.trim())

    if (s.history.isEmpty()) {
        buf.appendLine("لا قرارات سابقة: هذه بداية الحملة، ابدأ من الوضع الحقيقي مطلع 1800.")
    } else {
        val from = if (s.history.size > HISTORY_ENTRIES) s.history.size - HISTORY_ENTRIES else 0
        val recent = s.history.subList(from, s.history.size)
        buf.appendLine("قرارات اللاعب الأخيرة (الأحدث آخرا):")
        for (i in recent.indices) {
            val h = recent[i]
            val detailed = i >= recent.size - DETAILED_ENTRIES
            buf.appendLine(
                "- ${GameEngine.periodLabel(h.round, s.ppy)} | ${h.category} | ${h.title}: ${clip(h.choice, if (detailed) 170 else 80)}",
            )
        }
    }

    val anchors = anchorsFor(year, s)
    if (anchors.isNotEmpty()) buf.appendLine("مرجع التاريخ القريب (قد ينحرف بقرارات اللاعب): ${anchors.joinToString(" ")}")

    if (!problem.isNullOrBlank()) {
        buf.appendLine("تنبيه: ردك السابق رُفض لأن: ${problem.trim()} أعد JSON كاملا وصحيحا.")
    } else {
        buf.appendLine("أعد JSON فقط.")
    }
    return buf.toString()
}
