package com.ottoman.empire1800.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.StabilityState
import com.ottoman.empire1800.models.milestoneDefs
import com.ottoman.empire1800.services.CrisisAlert
import com.ottoman.empire1800.services.CrisisRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.ui.components.BudgetPanel
import com.ottoman.empire1800.ui.components.OptionTile
import com.ottoman.empire1800.ui.components.StatCard
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import java.util.Locale
import kotlin.math.roundToInt

private val Letters = listOf("أ", "ب", "ج")

private fun categoryIcon(c: DecisionCategory): ImageVector = when (c) {
    DecisionCategory.INTERNAL -> Icons.Filled.AccountBalance
    DecisionCategory.ECONOMY -> Icons.Filled.Payments
    DecisionCategory.FOREIGN -> Icons.Filled.Public
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    revision: Long,
    loadingPlan: Boolean,
    resolving: Boolean,
    attempt: Int,
    problem: String?,
    error: String?,
    onRetryPlan: () -> Unit,
    onPickOption: (Int, Int) -> Unit,
    onSetRepay: (Double, Boolean) -> Unit,
    onResolve: () -> Unit,
    onOpenCourt: (Int) -> Unit,
    onOpenLastReport: () -> Unit,
    onOpenMilestones: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val plan = state.plan
    val totalRounds = GameEngine.totalRoundsFor(state.ppy)
    val campaignProgress = (state.round.toDouble() / totalRounds).coerceIn(0.0, 1.0).toFloat()

    // Ensure Compose observes revision updates for mutable GameState changes
    @Suppress("UNUSED_VARIABLE")
    val rev = revision

    Scaffold(
        containerColor = AppColors.Navy,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(GameEngine.periodLabel(state.round, state.ppy)) },
                    actions = {
                        IconButton(onClick = onOpenCharts) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "الرسوم البيانية")
                        }
                        IconButton(onClick = { onOpenCourt(0) }, enabled = plan != null) {
                            Icon(Icons.Filled.Groups, contentDescription = "الديوان")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "الذكاء الاصطناعي")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Navy,
                        titleContentColor = AppColors.White,
                        actionIconContentColor = AppColors.White,
                    ),
                )
                LinearProgressIndicator(
                    progress = { campaignProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AppColors.Gold,
                    trackColor = AppColors.CardBorder,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loadingPlan || (resolving && plan == null) -> {
                    LoadingPlanView(state = state, attempt = attempt, problem = problem)
                }
                error != null || plan == null -> {
                    ErrorPlanView(
                        error = error ?: "",
                        onRetry = onRetryPlan,
                        onOpenSettings = onOpenSettings,
                    )
                }
                else -> {
                    RoundContent(
                        state = state,
                        plan = plan,
                        resolving = resolving,
                        onPickOption = onPickOption,
                        onSetRepay = onSetRepay,
                        onResolve = onResolve,
                        onOpenCourt = onOpenCourt,
                        onOpenLastReport = onOpenLastReport,
                        onOpenMilestones = onOpenMilestones,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPlanView(state: GameState, attempt: Int, problem: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppColors.Gold)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "الديوان يُعدّ تقارير ${GameEngine.periodLabel(state.round, state.ppy)}...",
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                color = AppColors.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "الذكاء الاصطناعي يكتب الأحداث والقرارات. قد يستغرق هذا نصف دقيقة.",
                textAlign = TextAlign.Center,
                color = AppColors.Muted,
                lineHeight = 22.sp,
            )
            if (attempt > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (problem == null) {
                        "محاولة $attempt من 3 (الخدمة مشغولة)"
                    } else {
                        "محاولة $attempt من 3 (كان الرد السابق غير صالح)"
                    },
                    color = AppColors.Gold,
                )
            }
        }
    }
}

@Composable
private fun ErrorPlanView(
    error: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AppColors.Bad,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                color = AppColors.White,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إعادة المحاولة")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إعدادات الذكاء الاصطناعي")
            }
        }
    }
}

@Composable
private fun HeroCard(state: GameState) {
    val total = GameEngine.totalRoundsFor(state.ppy)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.year}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Gold,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = GameEngine.periodLabel(state.round, state.ppy),
                    color = AppColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(AppColors.CardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${GameEngine.paceName(state.ppy)} • ${state.diff.name}",
                        fontSize = 12.5.sp,
                        color = AppColors.White,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (state.round.toDouble() / total).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AppColors.Gold,
                trackColor = AppColors.CardBorder,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("1800", color = AppColors.Muted, fontSize = 12.sp)
                Text("الجولة ${state.round + 1} من $total", color = AppColors.Muted, fontSize = 12.sp)
                Text("${GameEngine.END_YEAR}", color = AppColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatGroup(title: String, rows: List<List<@Composable (Modifier) -> Unit>>) {
    if (rows.isEmpty()) return
    Column {
        Text(
            text = title,
            color = AppColors.Gold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        rows.forEachIndexed { idx, row ->
            if (idx > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (item in row) {
                    item(Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatsSection(s: GameState) {
    val ratio = EconomyRules.debtRatio(s.debt, s.budget)
    val weakest = s.stab.weakest
    Column {
        StatGroup(
            title = "الدولة",
            rows = listOf(
                listOf(
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.AccountBalanceWallet,
                            label = "الخزينة",
                            value = "${formatMoney(s.treasury)} قرش",
                            warn = s.treasury < s.budget.totalSpending / 8.0,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        StatCard(
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            label = "الدين",
                            value = "${formatMoney(s.debt)} قرش",
                            hint = "${String.format(Locale.US, "%.1f", ratio)} من الإيراد السنوي",
                            warn = ratio >= 3.0,
                            modifier = mod,
                        )
                    },
                ),
                listOf(
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.WorkspacePremium,
                            label = "الشرعية",
                            value = "${s.legitimacy} / 100",
                            progress = s.legitimacy / 100.0,
                            warn = s.legitimacy <= 25,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.Balance,
                            label = "الاستقرار",
                            value = "${s.stability} / 100",
                            progress = s.stability / 100.0,
                            hint = "الأضعف: ${StabilityState.labels[weakest] ?: weakest} ${s.stab[weakest].roundToInt()}",
                            warn = s.stability <= 22 || s.stab.countBelow(15.0) >= 2,
                            modifier = mod,
                        )
                    },
                ),
            ),
        )

        StatGroup(
            title = "القوة",
            rows = listOf(
                listOf(
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.Shield,
                            label = "الجيش",
                            value = "${s.army} / 100",
                            progress = s.army / 100.0,
                            warn = s.army <= 15,
                            modifier = mod,
                        )
                    },
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.Public,
                            label = "المكانة الدولية",
                            value = "${s.foreign} / 100",
                            progress = s.foreign / 100.0,
                            warn = s.foreign <= 20,
                            modifier = mod,
                        )
                    },
                ),
                listOf(
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.School,
                            label = "التعليم والتحديث",
                            value = "${s.education} / 100",
                            progress = s.education / 100.0,
                            modifier = mod,
                        )
                    },
                ),
            ),
        )

        StatGroup(
            title = "المجتمع والأسعار",
            rows = listOf(
                listOf(
                    { mod ->
                        StatCard(
                            icon = Icons.Filled.Groups,
                            label = "السكان",
                            value = "${String.format(Locale.US, "%.1f", s.demog.population)} مليون",
                            hint = "قراءة وكتابة ${String.format(Locale.US, "%.1f", s.demog.literacy)}%",
                            modifier = mod,
                        )
                    },
                    { mod ->
                        StatCard(
                            icon = Icons.AutoMirrored.Filled.ShowChart,
                            label = "الأسعار",
                            value = "تضخم ${String.format(Locale.US, "%.1f", s.econ.inflation)}%",
                            hint = "الحبوب ${s.econ.grain.roundToInt()}",
                            warn = s.econ.inflation >= 8.0 || s.econ.grain >= 150.0,
                            modifier = mod,
                        )
                    },
                ),
            ),
        )

        val proj = s.project
        if (proj != null || s.wars.isNotEmpty()) {
            val rowItems = buildList<@Composable (Modifier) -> Unit> {
                if (proj != null) {
                    add { mod ->
                        val unit = if (s.ppy == 4) "أرباع" else "أنصاف"
                        val suffix = if (proj.paused) " • موقوف" else if (proj.stalled > 0) " • متعثر" else ""
                        StatCard(
                            icon = Icons.Filled.Construction,
                            label = "المشروع القومي",
                            value = projectDef(proj.key)?.name ?: proj.key,
                            hint = "اكتمل ${(proj.progress * 100).roundToInt()}% • باقٍ ${proj.remaining} $unit$suffix",
                            progress = proj.progress,
                            warn = proj.stalled > 0,
                            modifier = mod,
                        )
                    }
                }
                if (s.wars.isNotEmpty()) {
                    add { mod ->
                        StatCard(
                            icon = Icons.Filled.LocalFireDepartment,
                            label = "الحرب",
                            value = s.wars.joinToString(" و") { it.enemyName },
                            hint = s.wars.joinToString(" • ") { WarRules.describe(it) },
                            warn = s.wars.any { it.score <= -30.0 },
                            modifier = mod,
                        )
                    }
                }
            }
            StatGroup(title = "جارٍ الآن", rows = listOf(rowItems))
        }
    }
}

@Composable
private fun DecisionCard(
    decision: Decision,
    picked: Int?,
    ppy: Int,
    onPick: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Navy,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon(decision.category),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AppColors.Gold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = decision.category.label,
                    color = AppColors.Gold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (picked != null) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AppColors.Good,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = decision.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
            )
            if (decision.place.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(decision.place, color = AppColors.Muted, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(decision.scene, lineHeight = 26.sp, fontSize = 15.5.sp, color = AppColors.White)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = decision.dilemma,
                lineHeight = 25.sp,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.White,
            )
            Spacer(modifier = Modifier.height(12.dp))
            decision.options.forEachIndexed { k, opt ->
                OptionTile(
                    letter = Letters.getOrElse(k) { "${k + 1}" },
                    option = opt,
                    isSelected = picked == k,
                    ppy = ppy,
                    onTap = { onPick(k) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ConfirmBar(
    state: GameState,
    plan: RoundPlan,
    resolving: Boolean,
    onSetRepay: (Double, Boolean) -> Unit,
    onResolve: () -> Unit,
) {
    val picksKey = state.picks.joinToString(",") { it?.toString() ?: "_" }
    val projection = remember(state.round, state.treasury, state.debt, state.ppy, picksKey) {
        GameEngine.estimateRoundTreasury(state)
    }
    val cost = projection.selectedCosts
    val loan = projection.selectedLoans
    val chosen = state.picks.count { it != null }
    val spendable = state.treasury - cost
    val maxRepay = if (spendable <= 0.0) 0.0 else minOf(spendable, state.debt)
    val clampedRepay = state.repay.coerceIn(0.0, maxRepay)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (projection.willBorrowEmergency) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Bad.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AppColors.Bad.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = AppColors.Bad,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تحذير قبل التنفيذ: الخيارات المختارة مع مصروفات الفترة ستستنفد الخزينة بعجز قدره ${formatMoney(projection.emergencyShortfall)} قرش، وتُفعّل اقتراضا طارئا بنحو ${formatMoney(projection.estimatedEmergencyLoan)} قرش بعمولة 10% وتراجع الاستقرار.",
                        color = AppColors.White,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        } else if (projection.willStallProject && state.project != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = AppColors.Gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تنبيه: الرصيد المتبقي بعد المصروفات لن يكفي لقسط المشروع القومي (${formatMoney(state.project!!.installment)} قرش)، وقد يتعثر تمويله هذه الفترة.",
                        color = AppColors.White,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
        if (maxRepay > 0.0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Card,
                border = BorderStroke(1.dp, AppColors.CardBorder),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (clampedRepay <= 0.0) {
                            "سداد من الدين (اختياري): لا شيء"
                        } else {
                            "سداد من الدين: ${formatMoney(clampedRepay)} قرش"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.White,
                    )
                    Text(
                        text = "يُسدد من فائض الخزينة في نهاية هذه الفترة، ويوفر فائدة الدين.",
                        color = AppColors.Muted,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                    )
                    Slider(
                        value = clampedRepay.toFloat(),
                        valueRange = 0f..maxRepay.toFloat(),
                        steps = 19,
                        onValueChange = { onSetRepay(it.toDouble(), false) },
                        onValueChangeFinished = { onSetRepay(state.repay.coerceIn(0.0, maxRepay), true) },
                    )
                }
            }
        }

        Text(
            text = if (chosen < 3) {
                "اخترت $chosen من 3 قرارات."
            } else {
                "المصروفات الفورية: ${formatMoney(cost)} قرش" +
                    if (loan > 0.0) "، القروض: ${formatMoney(loan)} قرش" else ""
            },
            textAlign = TextAlign.Center,
            color = AppColors.Muted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
        )

        Button(
            onClick = onResolve,
            enabled = state.hasAllPicks && !resolving,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Icon(Icons.Filled.Gavel, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("نفّذ قرارات هذه الجولة")
        }
    }
}

@Composable
private fun CrisisBanner(
    crises: List<CrisisAlert>,
    onOpenCourt: (Int) -> Unit,
) {
    if (crises.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.BadBg,
        border = BorderStroke(1.dp, AppColors.Bad.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = AppColors.Bad,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تنبيه أزمة عاجلة (${crises.size})",
                        color = AppColors.Bad,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                    )
                }
                Button(
                    onClick = { onOpenCourt(crises.first().courtTabIndex) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Gold,
                        contentColor = AppColors.Navy,
                    ),
                ) {
                    Text("الانتقال للديوان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            for (c in crises) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCourt(c.courtTabIndex) },
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.Navy.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, AppColors.Bad.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = c.title,
                                color = AppColors.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                            )
                            Text(
                                text = c.detail,
                                color = AppColors.Muted,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "معالجة",
                            color = AppColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(state: GameState) {
    if (state.history.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("الكل") }
    val categories = listOf("الكل", "داخلي", "اقتصاد", "خارجي")

    val filtered = remember(state.history.size, state.ppy, searchQuery, categoryFilter) {
        val q = searchQuery.trim().lowercase()
        state.history.asReversed().filter { e ->
            val catOk = when (categoryFilter) {
                "الكل" -> true
                else -> e.category.contains(categoryFilter)
            }
            if (!catOk) return@filter false
            if (q.isEmpty()) return@filter true
            val pLabel = GameEngine.periodLabel(e.round, state.ppy)
            e.title.lowercase().contains(q) ||
                e.choice.lowercase().contains(q) ||
                e.result.lowercase().contains(q) ||
                e.category.lowercase().contains(q) ||
                pLabel.lowercase().contains(q)
        }
    }
    val visible = filtered.take(50)

    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "سجل القرارات (${state.history.size})",
                color = AppColors.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppColors.Muted,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("ابحث في القرارات أو الفترات...", color = AppColors.Muted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Gold) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "مسح البحث", tint = AppColors.Muted)
                            }
                        }
                    } else {
                        null
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.White,
                        unfocusedTextColor = AppColors.White,
                        focusedBorderColor = AppColors.Gold,
                        unfocusedBorderColor = AppColors.CardBorder,
                    ),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = categoryFilter == label,
                            onClick = { categoryFilter = label },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = categories.size),
                        ) {
                            Text(label, fontSize = 11.5.sp)
                        }
                    }
                }
                if (searchQuery.isNotBlank() || categoryFilter != "الكل") {
                    Text(
                        text = "المعروض ${filtered.size} من أصل ${state.history.size}",
                        color = AppColors.Muted,
                        fontSize = 11.5.sp,
                    )
                }
                if (visible.isEmpty()) {
                    Text(
                        text = "لا توجد قرارات تطابق بحثك.",
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    for (e in visible) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(e.choice, color = AppColors.White, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            if (e.result.isNotEmpty()) {
                                Text(e.result, color = AppColors.Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                            }
                            Text(
                                text = "${GameEngine.periodLabel(e.round, state.ppy)} - ${e.category}: ${e.title}",
                                color = AppColors.Gold.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 6.dp),
                                color = AppColors.CardBorder.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundContent(
    state: GameState,
    plan: RoundPlan,
    resolving: Boolean,
    onPickOption: (Int, Int) -> Unit,
    onSetRepay: (Double, Boolean) -> Unit,
    onResolve: () -> Unit,
    onOpenCourt: (Int) -> Unit,
    onOpenLastReport: () -> Unit,
    onOpenMilestones: () -> Unit,
) {
    val crises = CrisisRules.evaluate(state)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            HeroCard(state)
            Spacer(modifier = Modifier.height(4.dp))
            StatsSection(state)
            CrisisBanner(crises = crises, onOpenCourt = onOpenCourt)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.lastFullReport != null) {
                    OutlinedButton(
                        onClick = onOpenLastReport,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تقرير الفترة السابقة", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenMilestones,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الإنجازات (${state.milestones.size}/${milestoneDefs.size})", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            BudgetPanel(state)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onOpenCourt(0) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الديوان: الاستقرار والقوى والسفارات والجواسيس (إجراءات ${state.courtActions})")
            }
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppColors.Card,
                border = BorderStroke(1.dp, AppColors.Gold),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (plan.headline.isNotEmpty()) {
                        Text(
                            text = plan.headline,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Gold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = plan.briefing,
                        lineHeight = 27.sp,
                        fontSize = 15.5.sp,
                        color = AppColors.White,
                    )
                }
            }
        }

        items(
            count = plan.decisions.size,
            key = { index -> "${state.round}_${plan.decisions[index].category.id}_$index" },
        ) { index ->
            DecisionCard(
                decision = plan.decisions[index],
                picked = state.picks[index],
                ppy = state.ppy,
                onPick = { opt -> onPickOption(index, opt) },
            )
        }

        item {
            ConfirmBar(
                state = state,
                plan = plan,
                resolving = resolving,
                onSetRepay = onSetRepay,
                onResolve = onResolve,
            )
            HistorySection(state)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
