package com.ottoman.empire1800.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.data.ProjectDef
import com.ottoman.empire1800.data.economicSystemDef
import com.ottoman.empire1800.data.economicSystemDefs
import com.ottoman.empire1800.data.effectiveAbout
import com.ottoman.empire1800.data.effectiveSubProvinceResources
import com.ottoman.empire1800.data.factoryDef
import com.ottoman.empire1800.data.factoryDefs
import com.ottoman.empire1800.data.frontierRegionDef
import com.ottoman.empire1800.data.frontierRegionDefs
import com.ottoman.empire1800.data.projectCatalog
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.data.resourceLabels
import com.ottoman.empire1800.data.subProvincesForParent
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.ActiveProject
import com.ottoman.empire1800.models.CountryState
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.PowerProject
import com.ottoman.empire1800.models.PowerState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.SpyReport
import com.ottoman.empire1800.models.StabilityState
import com.ottoman.empire1800.models.War
import com.ottoman.empire1800.models.countryDef
import com.ottoman.empire1800.models.grandVizierDef
import com.ottoman.empire1800.models.grandVizierDefs
import com.ottoman.empire1800.services.CourtResult
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.DiplomacyRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.GrandVizierRules
import com.ottoman.empire1800.services.IndustryRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.services.costShare
import com.ottoman.empire1800.services.periodPlural
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import com.ottoman.empire1800.ui.util.formatNumber
import java.util.Locale
import kotlin.math.roundToInt

private data class CourtTabDef(val title: String, val icon: ImageVector)

private val CourtTabs = listOf(
    CourtTabDef("الاستقرار والقوى", Icons.Filled.Balance),
    CourtTabDef("الدبلوماسية والحروب", Icons.Filled.Public),
    CourtTabDef("الاستخبارات", Icons.Filled.Visibility),
    CourtTabDef("الاقتصاد والدين", Icons.Filled.AccountBalanceWallet),
    CourtTabDef("الولايات", Icons.Filled.Map),
    CourtTabDef("المشاريع", Icons.Filled.Construction),
)

private val SpyLevels = listOf(0 to "لا", 1 to "محدودة", 2 to "واسعة")

private data class ConfirmAction(val text: String, val onConfirm: () -> Unit)

private data class DissolveOutcome(
    val powerName: String,
    val ok: Boolean,
    val message: String,
    val detail: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtScreen(
    state: GameState,
    revision: Long,
    initialTab: Int = 0,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onBack: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val rev = revision

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, CourtTabs.lastIndex)) }
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var dissolveOutcome by remember { mutableStateOf<DissolveOutcome?>(null) }
    val durations = remember { mutableStateMapOf<String, Int>() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onConsumeSnackbar()
        }
    }

    val unit = if (state.ppy == 4) "أرباع" else "أنصاف"
    val noActions = state.courtActions <= 0
    fun costText(share: Double) = "${formatMoney(costShare(state, share))} قرش"

    Scaffold(
        containerColor = AppColors.Navy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("الديوان • الإجراءات ${state.courtActions}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Navy,
                        titleContentColor = AppColors.White,
                        navigationIconContentColor = AppColors.White,
                    ),
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AppColors.Navy,
                    contentColor = AppColors.Gold,
                    edgePadding = 8.dp,
                ) {
                    CourtTabs.forEachIndexed { idx, tab ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(tab.title) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                0 -> StabilityAndPowersTab(
                    state = state,
                    unit = unit,
                    noActions = noActions,
                    costText = ::costText,
                    onRunAction = onRunAction,
                    onRequestConfirm = { text, block -> pendingConfirm = ConfirmAction(text, block) },
                    onShowDissolveOutcome = { dissolveOutcome = it },
                )
                1 -> DiplomacyAndWarsTab(
                    state = state,
                    unit = unit,
                    noActions = noActions,
                    costText = ::costText,
                    onRunAction = onRunAction,
                    onRequestConfirm = { text, block -> pendingConfirm = ConfirmAction(text, block) },
                )
                2 -> SpiesTab(
                    state = state,
                    noActions = noActions,
                    costText = ::costText,
                    onRunAction = onRunAction,
                )
                3 -> EconomyAndDebtTab(
                    state = state,
                    noActions = noActions,
                    costText = ::costText,
                    onRunAction = onRunAction,
                )
                4 -> ProvincesTab(
                    state = state,
                    noActions = noActions,
                    costText = ::costText,
                    onRunAction = onRunAction,
                )
                5 -> ProjectsTab(
                    state = state,
                    noActions = noActions,
                    durations = durations,
                    onRunAction = onRunAction,
                    onRequestConfirm = { text, block -> pendingConfirm = ConfirmAction(text, block) },
                )
            }
        }
    }

    pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = AppColors.Card,
            text = { Text(confirm.text, lineHeight = 24.sp, color = AppColors.White) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirm = null
                        confirm.onConfirm()
                    },
                ) {
                    Text("نفّذ")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) {
                    Text("تراجع")
                }
            },
        )
    }

    dissolveOutcome?.let { out ->
        AlertDialog(
            onDismissRequest = { dissolveOutcome = null },
            containerColor = AppColors.Card,
            title = {
                Text(
                    text = if (out.ok) "نجح حل ${out.powerName}" else "فشلت محاولة حل ${out.powerName}",
                    color = if (out.ok) AppColors.Good else AppColors.Bad,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(out.message, color = AppColors.White, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    Text(out.detail, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            },
            confirmButton = {
                Button(onClick = { dissolveOutcome = null }) {
                    Text("فهمت")
                }
            },
        )
    }
}

// ───────────── عناصر مشتركة ─────────────

@Composable
private fun CourtCard(
    border: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, border ?: AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun CourtBar(
    label: String,
    value: Double,
    invert: Boolean = false,
    text: String? = null,
) {
    val v = value.coerceIn(0.0, 100.0)
    val bad = if (invert) v >= 60.0 else v <= 25.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AppColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.width(74.dp),
        )
        LinearProgressIndicator(
            progress = { (v / 100.0).toFloat() },
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (bad) AppColors.Bad else AppColors.Gold,
            trackColor = AppColors.CardBorder,
        )
        Text(
            text = text ?: v.roundToInt().toString(),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.White,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        text = t,
        color = AppColors.Gold,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SectionHint(t: String) {
    Text(
        text = t,
        color = AppColors.Muted,
        lineHeight = 22.sp,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

// ───────────── تبويب 1: الاستقرار والقوى ─────────────

@Composable
private fun StabilityAndPowersTab(
    state: GameState,
    unit: String,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
    onShowDissolveOutcome: (DissolveOutcome) -> Unit,
) {
    val powersList = remember(state.powers) { state.powers.values.toList() }
    val activeVizier = grandVizierDef(state.grandVizier)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "الاستقرار الكلي متوسط ستة أبعاد. إذا هبطت ثلاثة أبعاد إلى الحضيض انهار النظام ولو بدا المتوسط مقبولا. " +
                    "لديك ${state.courtActions} إجراء في هذه الفترة (تعيين الصدر الأعظم والترضية والمشاريع والحل والهدايا والمعاهدات).",
            )
            CourtCard(border = AppColors.Gold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("الصدر الأعظم: ${activeVizier.name}")
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = "تبديل بـ 1 إجراء (${costText(GrandVizierRules.CHANGE_SHARE)})",
                    color = AppColors.Muted,
                    fontSize = 12.5.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                for (v in grandVizierDefs) {
                    val selected = state.grandVizier == v.key
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) AppColors.Navy else AppColors.Card,
                        border = BorderStroke(1.dp, if (selected) AppColors.Gold else AppColors.CardBorder),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = v.name,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Text("الحالي", color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    OutlinedButton(
                                        enabled = !noActions,
                                        onClick = { onRunAction { GrandVizierRules.appoint(it, v.key) } },
                                    ) {
                                        Text("تعيين")
                                    }
                                }
                            }
                            Text(v.bonus, color = AppColors.Good, fontSize = 12.sp, lineHeight = 18.sp)
                            Text(v.tradeoff, color = AppColors.Bad, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
            CourtCard(border = AppColors.CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("فرمانات السلطان")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${state.decrees.size}/8",
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.decrees.isEmpty()) {
                    Text(
                        text = "لم تصدر فرمانات كبرى بعد؛ ستبقى التعيينات والتحالفات وحملات الفساد والقمع وإعانة الحبوب محفوظة هنا عبر الجولات.",
                        color = AppColors.Muted,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.decrees.asReversed().forEach { decree ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.Navy,
                                border = BorderStroke(1.dp, AppColors.CardBorder),
                            ) {
                                Text(
                                    text = decree,
                                    color = AppColors.White,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
            CourtCard {
                SectionTitle("الاستقرار الكلي ${state.stability} / 100")
                for (k in StabilityState.keys) {
                    CourtBar(StabilityState.labels[k] ?: k, state.stab[k])
                }
            }
        }
        items(powersList.size) { idx ->
            PowerCard(
                state = state,
                power = powersList[idx],
                unit = unit,
                noActions = noActions,
                costText = costText,
                onRunAction = onRunAction,
                onRequestConfirm = onRequestConfirm,
                onShowDissolveOutcome = onShowDissolveOutcome,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerCard(
    state: GameState,
    power: PowerState,
    unit: String,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
    onShowDissolveOutcome: (DissolveOutcome) -> Unit,
) {
    if (power.dissolved) {
        CourtCard {
            Text(
                text = if (power.merged) {
                    "${power.name} (مندمجة في الدولة): أصبحت جزءا من مؤسسات السلطنة."
                } else {
                    "${power.name}: حُلت نهائيا."
                },
                color = AppColors.Muted,
            )
        }
        return
    }
    val project: PowerProject? = state.projects.firstOrNull { it.power == power.key }
    val danger = power.danger
    val busy = project != null
    val isContained = power.containedUntil > state.round
    val statusSuffix = buildList {
        if (power.reformed) add("مُصلَحة")
        if (isContained) add("محتواة")
    }.joinToString(" • ")

    CourtCard(border = if (danger >= 60.0) AppColors.Bad else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (statusSuffix.isEmpty()) power.name else "${power.name} ($statusSuffix)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
                modifier = Modifier.weight(1f),
            )
            if (danger >= 60.0) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = AppColors.Bad,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(power.def.about, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(6.dp))
        CourtBar("النفوذ", power.influence)
        CourtBar("الرضا", power.satisfaction)
        CourtBar("الخطر", danger, invert = true)
        val chancePct = (PowerRules.dissolveSuccessChance(state, power) * 100.0).roundToInt()
        if (power.key == "janissaries") {
            val readiness = PowerRules.janissaryReadiness(state)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "جاهزية الحل ($chancePct%): " +
                    "${if (readiness.hasUlema) "✓" else "✗"} العلماء≥55 • " +
                    "${if (readiness.hasMilitary) "✓" else "✗"} جيش≥55/حديث • " +
                    "${if (readiness.lowInfluence) "✓" else "✗"} نفوذ≤65",
                color = if (chancePct >= 70) AppColors.Good else AppColors.Gold,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${if (danger > 70.0) "الخطر >70 يرفع احتمال التمرد" else "الخطر ≤70 يضمن نجاح الحل"} • فرصة النجاح $chancePct%",
                color = if (chancePct >= 100) AppColors.Good else AppColors.Bad,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        if (project != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "مشروع جار: ${PowerProject.kindLabels[project.kind]} (باقٍ ${project.remaining} من $unit السنة)",
                color = AppColors.Gold,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val isJan = power.key == "janissaries"
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions,
                        onClick = { onRunAction { PowerRules.appease(it, power.key) } },
                    ) {
                        Text("ترضية ${costText(PowerRules.APPEASE_SHARE)}", fontSize = 12.sp)
                    }
                    Text(
                        text = "+8 رضا" + if (isJan) " • تذمر التجار/العلماء (-2)" else "",
                        color = AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions && !busy,
                        onClick = { onRunAction { PowerRules.startProject(it, power.key, "reform") } },
                    ) {
                        Text("إصلاح ${costText(PowerRules.REFORM_SHARE)}", fontSize = 12.sp)
                    }
                    Text(
                        text = "يقلل الخطر الدائم (+2 استقرار وشرعية)",
                        color = AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions && !busy,
                        onClick = { onRunAction { PowerRules.startProject(it, power.key, "contain") } },
                    ) {
                        Text("احتواء ${costText(PowerRules.CONTAIN_SHARE)}", fontSize = 12.sp)
                    }
                    Text(
                        text = "-15 نفوذ ووقف المؤامرات مؤقتا",
                        color = AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions && !busy,
                        onClick = { onRunAction { PowerRules.startProject(it, power.key, "merge") } },
                    ) {
                        Text("دمج ${costText(PowerRules.MERGE_SHARE)}", fontSize = 12.sp)
                    }
                    Text(
                        text = "دمج نهائي بمؤسسات الدولة",
                        color = AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions,
                    onClick = {
                        val riskLabel = PowerRules.dissolveRisk(power, state)
                        val msg = if (power.key == "janissaries") {
                            "حل الانكشارية الآن ($riskLabel — فرصة النجاح $chancePct%). قد ينجح بإنهاء الفيلق أو ينفجر تمرد دموي إذا لم تكتمل الجاهزية. هل تحاول حل ${power.name}؟"
                        } else if (danger > 70.0) {
                            "خطر ${power.name} مرتفع: قد ترتد محاولة حلها بتمرد وخسارة هيبة ($riskLabel — فرصة النجاح $chancePct%). هل تحل ${power.name}؟"
                        } else {
                            "هل تحل ${power.name} نهائيا؟ ($riskLabel — فرصة النجاح $chancePct%)"
                        }
                        onRequestConfirm(msg) {
                            var captured: CourtResult? = null
                            onRunAction {
                                val r = PowerRules.dissolve(it, power.key)
                                captured = r
                                r
                            }
                            captured?.detail?.let { d ->
                                onShowDissolveOutcome(
                                    DissolveOutcome(
                                        powerName = power.name,
                                        ok = captured!!.ok,
                                        message = captured!!.message,
                                        detail = d,
                                    ),
                                )
                            }
                        }
                    },
                ) {
                    Text("حل ${costText(PowerRules.DISSOLVE_SHARE)} • نجاح $chancePct%", fontSize = 12.sp)
                }
                Text(
                    text = if (isJan) {
                        "الواقعة الخيرية: +4 شرعية و-8% نفقات الجيش، أو تمرد (-10 شرعية) عند فشل الجاهزية"
                    } else {
                        "ينهي نفوذ القوة نهائيا مع آثار خاصة بها (1 إجراء)"
                    },
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

// ───────────── تبويب 2: الدبلوماسية والحروب ─────────────

@Composable
private fun DiplomacyAndWarsTab(
    state: GameState,
    unit: String,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
) {
    val credit = DiplomacyRules.creditFactor(state)
    val countries = remember(state.countries) { state.countries.values.toList() }
    val activeAllies = countries.filter { it.alliance && WarRules.warWith(state, it.key) == null }
    val allyBonusPct = (activeAllies.size * 12).coerceAtMost(30)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "علاقات الدائنين (بريطانيا وفرنسا والنمسا) تحدد سقف قروضك: المعامل الآن ${String.format(Locale.US, "%.2f", credit)}. " +
                    "السفارة تحفظ العلاقة وتفتح لك أعين التجسس، وكلفتها السنوية تضاف إلى بند الإدارة.",
            )
            if (activeAllies.isNotEmpty()) {
                CourtCard(border = AppColors.Good) {
                    Text(
                        text = "تحالفات دفاعية قائمة: ${activeAllies.joinToString("، ") { it.name }} (+$allyBonusPct% قوة قتالية في الحروب)",
                        color = AppColors.Good,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.wars.isNotEmpty()) {
                SectionTitle("حروب جارية")
                for (w in state.wars) {
                    WarCard(
                        state = state,
                        war = w,
                        unit = unit,
                        noActions = noActions,
                        costText = costText,
                        onRunAction = onRunAction,
                        onRequestConfirm = onRequestConfirm,
                    )
                }
            }
        }
        items(countries.size) { idx ->
            CountryCard(
                state = state,
                country = countries[idx],
                noActions = noActions,
                costText = costText,
                onRunAction = onRunAction,
            )
        }
    }
}

@Composable
private fun WarCard(
    state: GameState,
    war: War,
    unit: String,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
) {
    val c = state.countries[war.enemy]
    val our = WarRules.ourPower(state)
    val enemy = if (c == null) 0.0 else WarRules.enemyPower(c)
    CourtCard(border = AppColors.Bad) {
        Text(
            text = "الحرب مع ${war.enemyName} (${if (war.byUs) "بدأناها" else "بدأوها"})",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.White,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CourtBar("الموقف", (war.score + 100.0) / 2.0, text = war.score.roundToInt().toString())
        Text(
            text = "${WarRules.describe(war)} • منذ ${war.halves} من $unit السنة • قوتنا ${our.roundToInt()} مقابل ${enemy.roundToInt()} • كلفة الفترة ${formatMoney(WarRules.costs(state))} قرش",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            enabled = !noActions,
            onClick = {
                onRequestConfirm(
                    "شروط الصلح تتبع موقفك الآن (${WarRules.describe(war)}): قد تدفع تعويضا أو تتنازل عن أرض إن كنت خاسرا. هل تطلب الصلح؟",
                ) { onRunAction { WarRules.seekPeace(it, war.enemy) } }
            },
        ) {
            Text("طلب الصلح ${costText(WarRules.PEACE_SHARE)}")
        }
        Text(
            text = "إنهاء الحرب بشروط الموقف الحالي (+15 للتفاوض)",
            color = AppColors.Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CountryCard(
    state: GameState,
    country: CountryState,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val econR = ForeignRules.econRatio(state, country)
    val milR = ForeignRules.milRatio(state, country)
    val isAtWar = WarRules.warWith(state, country.key) != null
    val canSignAlliance = !country.alliance && !isAtWar && country.relation >= 80.0 && !noActions
    val rivals = DiplomacyRules.allianceRivals[country.key].orEmpty().map { countryDef(it)?.name ?: it }
    val treatyRivalNote = when (country.key) {
        "britain" -> "، -5 فرنسا"
        "france" -> "، -5 بريطانيا"
        else -> ""
    }
    val statusTags = buildList {
        add(if (country.embassy) "لنا سفارة دائمة" else "لا سفارة دائمة")
        if (country.alliance) add("حلف دفاعي")
    }.joinToString(" • ")

    CourtCard(border = if (country.alliance) AppColors.Good else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = country.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${DiplomacyRules.relationLabel(country.relation)} (${country.relation.roundToInt()})",
                color = when {
                    country.relation >= 60.0 -> AppColors.Good
                    country.relation <= 35.0 -> AppColors.Bad
                    else -> AppColors.Gold
                },
            )
        }
        CourtBar("العلاقة", country.relation)
        CourtBar("اقتصاده", econR / 3.0 * 100.0, text = "${String.format(Locale.US, "%.1f", econR)}×")
        CourtBar("جيشه", milR / 3.0 * 100.0, invert = true, text = "${String.format(Locale.US, "%.1f", milR)}×")
        Text(
            text = "${ForeignRules.strengthLabel(milR)} عسكريا، و${ForeignRules.strengthLabel(econR)} اقتصاديا (بالنسبة لنا).",
            color = AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Text(
            text = statusTags,
            color = if (country.alliance) AppColors.Good else AppColors.Muted,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions,
                        onClick = { onRunAction { DiplomacyRules.gift(it, country.key) } },
                    ) {
                        Text("هدايا ${costText(DiplomacyRules.GIFT_SHARE)}", fontSize = 12.sp)
                    }
                    Text("+6 علاقة (1 إجراء)", color = AppColors.Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions,
                        onClick = { onRunAction { DiplomacyRules.tradeTreaty(it, country.key) } },
                    ) {
                        Text("معاهدة تجارية ${costText(DiplomacyRules.TREATY_SHARE)}", fontSize = 12.sp)
                    }
                    Text(
                        text = "+8 علاقة وعائد جمركي بعد سنة$treatyRivalNote",
                        color = AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (country.embassy) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onRunAction { DiplomacyRules.closeEmbassy(it, country.key) } },
                        ) {
                            Text("إغلاق السفارة (0 قرش)", fontSize = 12.sp)
                        }
                        Text(
                            text = "-4 علاقة وتوفر صيانة الإدارة",
                            color = AppColors.Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onRunAction { DiplomacyRules.openEmbassy(it, country.key) } },
                        ) {
                            Text("افتتاح سفارة ${costText(DiplomacyRules.EMBASSY_OPEN_SHARE)}", fontSize = 12.sp)
                        }
                        Text(
                            text = "تحسن تدريجي • صيانة ${costText(DiplomacyRules.EMBASSY_UPKEEP_SHARE)}/سنة",
                            color = AppColors.Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSignAlliance,
                        onClick = { onRunAction { DiplomacyRules.signAlliance(it, country.key) } },
                    ) {
                        Text(
                            text = if (country.alliance) "حلف دفاعي قائم" else "حلف دفاعي ${costText(DiplomacyRules.ALLIANCE_SHARE)}",
                            fontSize = 12.sp,
                        )
                    }
                    val rivalTxt = if (rivals.isNotEmpty()) "، -8 ${rivals.joinToString(" و")}" else ""
                    val gateTxt = if (!country.alliance && country.relation < 80.0) " (يتطلب علاقة ≥80)" else ""
                    Text(
                        text = if (country.alliance) {
                            "يمنح +12% قوة قتالية و+4 زخم حرب"
                        } else {
                            "+12% قوة حرب$rivalTxt$gateTxt"
                        },
                        color = if (country.alliance) AppColors.Good else AppColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

// ───────────── تبويب 3: الاستخبارات ─────────────

@Composable
private fun SpiesTab(
    state: GameState,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val countries = remember(state.countries) { state.countries.values.toList() }
    val reports = state.intel.asReversed()
    val domCur = DiplomacyRules.spyCostPreview(state, state.domesticSpy, state.domesticSpy, isDomestic = true)
    val domOpCost = costShare(state, DiplomacyRules.DOMESTIC_SPECIAL_OP_SHARE)
    val foreignOpCost = costShare(state, DiplomacyRules.FOREIGN_SPECIAL_OP_SHARE)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "الجواسيس يجلبون تقارير بدرجة موثوقية، وقد يكذب التقرير. تنكشف حقيقته في الفترة التالية فتعرف من تثق به. " +
                    "تختلف كلفة الاستخبارات الداخلية عن الشبكات الخارجية؛ التجنيد لمرة واحدة والصيانة تضاف لبند الإدارة سنويا.",
            )
            CourtCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("الاستخبارات الداخلية")
                    Text(
                        text = "الصيانة: ${formatMoney(domCur.annualUpkeep)} قرش/سنة",
                        color = AppColors.Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "تكشف مؤامرات القوى قبل وقوعها وتقدم تقارير عن أخطرها.",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpyLevels.forEach { (lvl, label) ->
                    val prev = DiplomacyRules.spyCostPreview(state, state.domesticSpy, lvl, isDomestic = true)
                    val active = state.domesticSpy == lvl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Button(
                                modifier = Modifier.width(88.dp),
                                onClick = { onRunAction { DiplomacyRules.setDomesticSpy(it, lvl) } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.width(88.dp),
                                onClick = { onRunAction { DiplomacyRules.setDomesticSpy(it, lvl) } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                        Text(
                            modifier = Modifier.weight(1f),
                            text = if (lvl == 0) {
                                "إيقاف الشبكة • 0 قرش تجنيد • صيانة 0 قرش/سنة"
                            } else {
                                "تجنيد: ${formatMoney(prev.recruitCost)} قرش • صيانة: ${formatMoney(prev.annualUpkeep)} قرش/سنة"
                            },
                            color = AppColors.Muted,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.domesticSpy >= 1 && !noActions,
                    onClick = { onRunAction { DiplomacyRules.domesticSpecialOp(it) } },
                ) {
                    Text("مهمة استخباراتية خاصة (${costText(DiplomacyRules.DOMESTIC_SPECIAL_OP_SHARE)})")
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "تكلف 1.5% من الإيراد (${formatMoney(domOpCost)} قرش) + 1 إجراء ديوان • " +
                        when (state.domesticSpy) {
                            1 -> "-8 نفوذ لأخطر قوة (مخاطرة 35% لـ -2 شرعية)"
                            2 -> "-8 نفوذ لأخطر قوة دون انكشاف"
                            else -> "فعّل «محدودة» على الأقل للمهام الخاصة (-8 نفوذ)"
                        },
                    color = AppColors.Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        items(countries.size) { idx ->
            val c = countries[idx]
            val curCost = DiplomacyRules.spyCostPreview(state, c.spy, c.spy, isDomestic = false)
            CourtCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("التجسس على ${c.name}")
                    Text(
                        text = "الصيانة: ${formatMoney(curCost.annualUpkeep)} قرش/سنة",
                        color = AppColors.Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "جودة المعلومات: ${c.intelQuality} من 3 (السفارة تضيف درجة).",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpyLevels.forEach { (lvl, label) ->
                    val prev = DiplomacyRules.spyCostPreview(state, c.spy, lvl, isDomestic = false)
                    val active = c.spy == lvl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Button(
                                modifier = Modifier.width(88.dp),
                                onClick = { onRunAction { DiplomacyRules.setSpyLevel(it, c.key, lvl) } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.width(88.dp),
                                onClick = { onRunAction { DiplomacyRules.setSpyLevel(it, c.key, lvl) } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                        Text(
                            modifier = Modifier.weight(1f),
                            text = if (lvl == 0) {
                                "إيقاف الشبكة • 0 قرش تجنيد • صيانة 0 قرش/سنة"
                            } else {
                                "تجنيد: ${formatMoney(prev.recruitCost)} قرش • صيانة: ${formatMoney(prev.annualUpkeep)} قرش/سنة"
                            },
                            color = AppColors.Muted,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                if (c.spy >= 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !noActions,
                        onClick = { onRunAction { DiplomacyRules.foreignSpecialOp(it, c.key) } },
                    ) {
                        Text("مهمة استخباراتية خاصة (${costText(DiplomacyRules.FOREIGN_SPECIAL_OP_SHARE)})")
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "تكلف 2.8% من الإيراد (${formatMoney(foreignOpCost)} قرش) + 1 إجراء ديوان • " +
                            if (c.spy == 1) {
                                "استطلاع مؤكد (+5 حرب إذا نشطة) • مخاطرة 35% (-5 علاقة)"
                            } else {
                                "استطلاع مؤكد (+5 حرب إذا نشطة) دون انكشاف"
                            },
                        color = AppColors.Muted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        item {
            SectionTitle("التقارير")
            if (reports.isEmpty()) {
                Text("لا تقارير بعد.", color = AppColors.Muted)
            } else {
                for (r in reports) {
                    ReportTile(r)
                }
            }
        }
    }
}

@Composable
private fun ReportTile(r: SpyReport) {
    val (status, color) = when {
        !r.revealed -> "غير مؤكد" to AppColors.Muted
        r.truth -> "تبين أنه صحيح" to AppColors.Good
        else -> "تبين أنه كاذب" to AppColors.Bad
    }
    CourtCard {
        Text(r.claim, lineHeight = 22.sp, color = AppColors.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("الموثوقية ${r.reliability}% • $status", color = color, fontSize = 13.sp)
    }
}

// ───────────── تبويب 4: الاقتصاد والدين ─────────────

@Composable
private fun EconomyAndDebtTab(
    state: GameState,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val e = state.econ
    val d = state.demog
    val austerityCooldown = EconomyRules.austerityCooldownRemaining(state)
    val unit = GameEngine.periodUnit(state.ppy)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "هذه أرقام تحسبها اللعبة بقواعد ثابتة، وتتأثر بها الجباية والاستقرار. العجز الطويل يغذي التضخم، " +
                    "ورفع الضرائب يغضب الولايات، والجفاف يرفع أسعار الحبوب.",
            )
            CourtCard {
                SectionTitle("الأسعار والمال")
                CourtBar("الحبوب", e.grain / 2.6, invert = true, text = e.grain.roundToInt().toString())
                CourtBar("التضخم", e.inflation * 10.0, invert = true, text = "${String.format(Locale.US, "%.1f", e.inflation)}%")
                CourtBar("الفساد", e.corruption, invert = true)
                Spacer(modifier = Modifier.height(4.dp))
                Text("فترات عجز متتالية: ${e.deficitStreak}", color = AppColors.Muted, fontSize = 13.sp)
                Text(
                    "مضاعف الجباية من الولايات والفساد: ${String.format(Locale.US, "%.3f", e.revenueMultiplier)}",
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                )
            }
            CourtCard(border = AppColors.Gold) {
                SectionTitle("إجراءات اقتصادية عاجلة (إجراءات متبقية: ${state.courtActions})")
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions && e.grain > 120.0,
                    onClick = { onRunAction { EconomyRules.grainSubsidy(it) } },
                ) {
                    Text("إعانة الخبز والحبوب ${costText(EconomyRules.GRAIN_SUBSIDY_SHARE)}")
                }
                Text(
                    text = if (e.grain > 120.0) {
                        "يخفض الحبوب (-25)، يرفع مزاج الولايات (+4) والاستقرار الاجتماعي (+2)"
                    } else {
                        "يُتاح فقط عند ارتفاع أسعار الحبوب فوق 120"
                    },
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions,
                    onClick = { onRunAction { EconomyRules.antiCorruption(it) } },
                ) {
                    Text("حملة مكافحة الفساد ${costText(EconomyRules.ANTI_CORRUPTION_SHARE)}")
                }
                Text(
                    text = "يخفض الفساد (-6) ويغضب الملتزمين (-8) والولاة (-4)",
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions && austerityCooldown == 0,
                    onClick = { onRunAction { EconomyRules.palaceAusterity(it) } },
                ) {
                    Text(
                        if (austerityCooldown > 0) {
                            "تقشف البلاط (متاح بعد $austerityCooldown $unit)"
                        } else {
                            "تقشف البلاط (-5% نفقات دائمة)"
                        },
                    )
                }
                Text(
                    text = if (austerityCooldown > 0) {
                        "0 قرش · متاح بعد $austerityCooldown $unit (-5% نفقات القصر الدائمة، -2 شرعية)"
                    } else {
                        "0 قرش · يخفض نفقات القصر الدائمة (-5% من الإيراد) مقابل -2 شرعية"
                    },
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                )
            }
            IndustryAndEconomicSystemCard(state = state, noActions = noActions, onRunAction = onRunAction)
            DebtCard(state)
            CourtCard {
                SectionTitle("السكان والتعليم (تقديري)")
                Text("السكان: ${String.format(Locale.US, "%.1f", d.population)} مليون", color = AppColors.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("القراءة والكتابة: ${String.format(Locale.US, "%.1f", d.literacy)}%", color = AppColors.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("المدارس الابتدائية: ${formatNumber(d.schools)}", color = AppColors.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("الجنود: نحو ${formatNumber(state.army * 4000)}", color = AppColors.White)
            }
        }
    }
}

@Composable
private fun IndustryAndEconomicSystemCard(
    state: GameState,
    noActions: Boolean,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val activeSys = economicSystemDef(state.econ.economicSystem)
    val econSysCooldown = IndustryRules.econSystemCooldownRemaining(state)
    val totalFactories = IndustryRules.totalFactoryLevels(state.econ)
    val annualFactoryRev = IndustryRules.annualFactoryRevenue(state.econ, state.budget, state.year, state.demog.literacy)
    val paperDiscPct = (IndustryRules.paperMillAdminDiscount(state.econ) * 100.0).roundToInt()
    val armsPowerPct = String.format(Locale.US, "%.1f", IndustryRules.armsPowerBonus(state.econ) * 100.0)
    val coalActive = IndustryRules.hasCoalPower(state.econ)
    val litMult = String.format(Locale.US, "%.2f", IndustryRules.literacyMultiplier(state.demog.literacy))

    CourtCard(border = AppColors.Gold.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("النظام الاقتصادي والصناعة")
            Text(activeSys.name, color = AppColors.Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndustryKpiTile("مستويات المصانع", "$totalFactories مستوى", AppColors.White, Modifier.weight(1f))
            IndustryKpiTile("العائد الصناعي السنوي", "${formatMoney(annualFactoryRev)} قرش", AppColors.Good, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndustryKpiTile("خصم الورق للإدارة", "-$paperDiscPct% (سقف 25%)", AppColors.Good, Modifier.weight(1f))
            IndustryKpiTile(
                "طاقة الفحم الحجري",
                if (coalActive) "نشطة (+25%)" else "غير مفعلة",
                if (coalActive) AppColors.Good else AppColors.Muted,
                Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndustryKpiTile("كفاءة القراءة", "×$litMult", AppColors.Gold, Modifier.weight(1f))
            IndustryKpiTile("دعم مصانع السلاح", "+$armsPowerPct% قوة", AppColors.Good, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("التحول بين المذاهب الاقتصادية (1 إجراء ديوان)", color = AppColors.Muted, fontSize = 11.5.sp)
            Text(
                text = if (econSysCooldown > 0) "انتظر $econSysCooldown ${periodPlural(state.ppy)}" else "التغيير متاح الآن",
                color = AppColors.Gold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (sys in economicSystemDefs) {
                val isActive = state.econ.economicSystem == sys.key
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) AppColors.SelectedBg else AppColors.DeepBg,
                    border = BorderStroke(
                        width = if (isActive) 1.5.dp else 1.dp,
                        color = if (isActive) AppColors.Gold else AppColors.CardBorder,
                    ),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sys.name, color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            if (isActive) {
                                Text("مطبق", color = AppColors.Gold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            } else {
                                OutlinedButton(
                                    enabled = !noActions && econSysCooldown == 0,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    onClick = { onRunAction { IndustryRules.setEconomicSystem(it, sys.key) } },
                                ) {
                                    Text("اعتماد", fontSize = 11.5.sp)
                                }
                            }
                        }
                        Text(sys.subtitle, color = AppColors.Muted, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(sys.bonus, color = AppColors.Good, fontSize = 11.5.sp, lineHeight = 16.sp)
                        Text(sys.tradeoff, color = AppColors.Bad, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndustryKpiTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppColors.DeepBg,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, color = AppColors.Muted, fontSize = 10.5.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DebtCard(state: GameState) {
    val rev = state.budget.totalRevenue
    val rating = DebtRules.creditRating(state)
    val status = when {
        state.dadActive -> "إدارة الديون العامة تحصّل ${(DebtRules.DAD_SHARE * 100).roundToInt()}% من الإيراد للدائنين."
        DebtRules.inMoratorium(state) -> "الفوائد الخارجية معلقة بعد إعلان العجز عن السداد."
        state.defaultRound >= 0 -> "سبق أن عجزت الدولة عن السداد، والدائنون يذكرون ذلك."
        else -> ""
    }

    CourtCard {
        SectionTitle("الدين والتصنيف الائتماني")
        Text("الدين الداخلي: ${formatMoney(DebtRules.internalDebt(state))} قرش (فائدة 12% سنويا)")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "الدين الخارجي: ${formatMoney(state.foreignDebt)} قرش" +
                if (state.foreignDebt > 0.0) {
                    " (فائدة ${String.format(Locale.US, "%.1f", DebtRules.foreignRate(state) * 100.0)}% سنويا)"
                } else {
                    ""
                },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("الدين الكلي ${String.format(Locale.US, "%.2f", state.debt / (if (rev <= 0.0) 1.0 else rev))}× الإيراد السنوي")
        Spacer(modifier = Modifier.height(6.dp))
        CourtBar("التصنيف", rating)
        for (fct in DebtRules.ratingFactors(state)) {
            if (kotlin.math.abs(fct.value) >= 0.5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                ) {
                    Text(fct.label, color = AppColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = "${if (fct.value > 0) "+" else ""}${fct.value.roundToInt()}",
                        color = if (fct.value > 0) AppColors.Good else AppColors.Bad,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(status, color = AppColors.Gold, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "القروض الأجنبية تبدأ من 1854، وإن تراكم الدين الأجنبي مع اقتراض طارئ متتال عجزت الدولة عن السداد.",
            color = AppColors.Muted,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
        )
    }
}

// ───────────── تبويب 5: الولايات ─────────────

@Composable
private fun ProvincesTab(
    state: GameState,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    var openSubsParent by remember { mutableStateOf<String?>("anatolia") }
    val provinces = state.econ.provinces.values.toList()
    val frontiers = frontierRegionDefs.mapNotNull { def -> state.econ.frontierRegions[def.key] }
    val loyalCount = provinces.count { it.status == ProvinceState.LOYAL }
    val autoCount = provinces.count { it.status == ProvinceState.AUTONOMOUS_STATUS }
    val lostCount = provinces.count { it.status == ProvinceState.INDEPENDENT }
    val controlledFrontierCount = frontiers.count { it.controlled && !it.isLost }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "كل ولاية لها مزاج وميل إلى الانفصال. إذا بلغ الحكم الذاتي 70 صارت شبه مستقلة (تدفع 40% من إيرادها)، وعند 95 قد تنفصل. " +
                    "الجيش القوي يقمع، والرضا والشرعية العاليان يبطئان الانفصال. إذا هبط وزن الولايات الفعالة دون 40% تفككت الإمبراطورية " +
                    "(الآن ${(state.econ.activeWeight * 100).roundToInt()}%).",
            )
            CourtCard(border = AppColors.Gold.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("نظرة عامة على الولايات الست")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("موالية $loyalCount", color = AppColors.Good, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("ذاتي $autoCount", color = AppColors.Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("مفقودة $lostCount", color = AppColors.Bad, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        if (controlledFrontierCount > 0) {
                            Text("ثغور $controlledFrontierCount/4", color = AppColors.Gold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                provinces.chunked(2).forEachIndexed { rowIdx, pair ->
                    if (rowIdx > 0) Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (p in pair) {
                            val (statusLabel, accentColor, bgColor) = when (p.status) {
                                ProvinceState.LOYAL -> Triple("موالية", AppColors.Good, AppColors.GoodBg)
                                ProvinceState.AUTONOMOUS_STATUS -> Triple("حكم ذاتي", AppColors.Gold, AppColors.SelectedBg)
                                else -> Triple("مفقودة", AppColors.Bad, AppColors.BadBg)
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = bgColor,
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = p.name,
                                            color = AppColors.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = statusLabel,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "المزاج ${p.mood.roundToInt()}",
                                            color = AppColors.Muted,
                                            fontSize = 11.sp,
                                        )
                                        Text(
                                            text = "الانفصال ${p.autonomy.roundToInt()}%",
                                            color = if (p.autonomy >= 70.0) AppColors.Bad else AppColors.Muted,
                                            fontWeight = if (p.autonomy >= 70.0) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        items(provinces.size) { idx ->
            val p = provinces[idx]
            ProvinceCard(
                state = state,
                p = p,
                subsOpen = openSubsParent == p.key,
                onToggleSubs = { openSubsParent = if (openSubsParent == p.key) null else p.key },
                noActions = noActions,
                costText = costText,
                onRunAction = onRunAction,
            )
        }
        item {
            CourtCard(border = AppColors.Gold.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("الثغور والأقاليم القابلة للفتح")
                    Text(
                        text = "$controlledFrontierCount/4 تابعة للسلطنة",
                        color = AppColors.Gold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "تُضم هذه الأقاليم بانتصار عسكري حاسم (+60 نقاط حرب) ضد القوى المعنية أو عبر القرارات التاريخية، وتمنح إيرادا وموارد وسناجق إضافية.",
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (def in frontierRegionDefs) {
                        val fr = state.econ.frontierRegions[def.key] ?: continue
                        val statusLabel = if (fr.controlled) {
                            ProvinceState.statusLabels[fr.status] ?: fr.status
                        } else {
                            "خارج السيطرة"
                        }
                        val statusColor = when {
                            !fr.controlled -> AppColors.Muted
                            fr.isLost -> AppColors.Bad
                            fr.autonomy >= 70.0 -> AppColors.Bad
                            else -> AppColors.Good
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = AppColors.DeepBg,
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(def.name, color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                    Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                if (!fr.controlled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(def.about, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val enemiesStr = def.conquerEnemies.joinToString(" أو ") { countryDef(it)?.name ?: it }
                                    val reqStr = def.requiresRegion?.let { " (بعد استعادة ${frontierRegionDef(it)?.name ?: it})" } ?: ""
                                    Text(
                                        text = "الأعداء المستهدفون: $enemiesStr$reqStr",
                                        color = AppColors.Gold,
                                        fontSize = 11.5.sp,
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    CourtBar("المزاج", fr.mood)
                                    CourtBar("الانفصال", fr.autonomy, invert = true)
                                    if (!fr.isLost) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = !noActions,
                                                onClick = { onRunAction { ProvinceRules.privileges(it, def.key) } },
                                            ) {
                                                Text("امتيازات ${costText(ProvinceRules.PRIVILEGES_SHARE)}", fontSize = 11.5.sp)
                                            }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = !noActions,
                                                onClick = { onRunAction { ProvinceRules.suppress(it, def.key) } },
                                            ) {
                                                Text("قمع ${costText(ProvinceRules.SUPPRESS_SHARE)}", fontSize = 11.5.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val frOpen = openSubsParent == def.key
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { openSubsParent = if (frOpen) null else def.key },
                                    ) {
                                        Text(if (frOpen) "إخفاء سناجق ومصانع ${def.name}" else "عرض سناجق ومصانع ${def.name}", fontSize = 12.sp)
                                    }
                                    if (frOpen) {
                                        SubProvincesBlock(
                                            state = state,
                                            parentKey = def.key,
                                            lost = fr.isLost,
                                            noActions = noActions,
                                            onRunAction = onRunAction,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProvinceCard(
    state: GameState,
    p: ProvinceState,
    subsOpen: Boolean,
    onToggleSubs: () -> Unit,
    noActions: Boolean,
    costText: (Double) -> String,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val subsCount = subProvincesForParent(p.key).sumOf { spDef ->
        state.econ.subProvinces[spDef.key]?.factories?.sumOf { it.level } ?: 0
    }
    if (p.isLost) {
        CourtCard {
            Text("${p.name}: مفقودة (${(p.def.weight * 100).roundToInt()}% من قاعدة الإيراد).", color = AppColors.Muted)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onToggleSubs) {
                Text(if (subsOpen) "إخفاء السناجق" else "عرض السناجق", fontSize = 12.sp)
            }
            if (subsOpen) {
                SubProvincesBlock(state = state, parentKey = p.key, lost = true, noActions = noActions, onRunAction = onRunAction)
            }
        }
        return
    }
    val risky = p.autonomy >= 70.0
    CourtCard(border = if (risky) AppColors.Bad else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = p.name + if (subsCount > 0) " ($subsCount مصانع)" else "",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ProvinceState.statusLabels[p.status] ?: p.status,
                color = if (risky) AppColors.Bad else AppColors.Gold,
            )
        }
        Text("وزنها في الإيراد ${(p.def.weight * 100).roundToInt()}%", color = AppColors.Muted, fontSize = 13.sp)
        CourtBar("المزاج", p.mood)
        CourtBar("الانفصال", p.autonomy, invert = true)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions,
                    onClick = { onRunAction { ProvinceRules.suppress(it, p.key) } },
                ) {
                    Text("قمع ${costText(ProvinceRules.SUPPRESS_SHARE)}", fontSize = 12.sp)
                }
                Text(
                    text = "-15 انفصال، -5 مزاج، -1 جيش" + if (p.key == "rumelia") "، -4 روسيا والنمسا" else "",
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !noActions,
                    onClick = { onRunAction { ProvinceRules.privileges(it, p.key) } },
                ) {
                    Text("امتيازات ${costText(ProvinceRules.PRIVILEGES_SHARE)}", fontSize = 12.sp)
                }
                Text(
                    text = "+8 مزاج، -8 انفصال؛ +2 انفصال لولايات مضطربة",
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleSubs,
        ) {
            Text(
                text = if (subsOpen) "إخفاء السناجق والموارد والمصانع" else "السناجق والموارد والمصانع (${subProvincesForParent(p.key).size} مناطق)",
                fontSize = 12.sp,
                color = AppColors.Gold,
            )
        }
        if (subsOpen) {
            SubProvincesBlock(state = state, parentKey = p.key, lost = false, noActions = noActions, onRunAction = onRunAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubProvincesBlock(
    state: GameState,
    parentKey: String,
    lost: Boolean,
    noActions: Boolean,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    var buildingSubKey by remember { mutableStateOf<String?>(null) }
    val subs = subProvincesForParent(parentKey)
    val year = state.year
    val sys = economicSystemDef(state.econ.economicSystem)
    if (subs.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("السناجق والموارد والمصانع", color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (sys.canStateBuild) "1 إجراء للبناء/الترقية" else "السوق الحر (استثمار خاص)",
                color = AppColors.Muted,
                fontSize = 11.sp,
            )
        }

        for (spDef in subs) {
            val spState = state.econ.subProvinces[spDef.key]
            val facs = spState?.factories ?: emptyList()
            val resources = effectiveSubProvinceResources(spDef, year, state.econ.oilDiscovered)
            val canAddMore = !lost && facs.size < spDef.slots
            val showPicker = buildingSubKey == spDef.key && canAddMore

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = AppColors.DeepBg,
                border = BorderStroke(1.dp, AppColors.CardBorder),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(spDef.name, color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("خانات: ${facs.size}/${spDef.slots}", color = AppColors.Muted, fontSize = 11.5.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (rk in resources) {
                            val isOil = rk == "oil"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOil) AppColors.SelectedBg else AppColors.Card,
                                border = BorderStroke(1.dp, if (isOil) AppColors.Gold else AppColors.CardBorder),
                            ) {
                                Text(
                                    text = resourceLabels[rk] ?: rk,
                                    color = if (isOil) AppColors.Gold else AppColors.White,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (facs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (fac in facs) {
                                val fDef = factoryDef(fac.type) ?: continue
                                val annualYield = IndustryRules.annualFactoryYield(
                                    state.econ,
                                    state.budget,
                                    year,
                                    state.demog.literacy,
                                    spDef.key,
                                    fac,
                                )
                                val upgradeCost = IndustryRules.factoryBuildCost(state, spDef.key, fac.type)
                                val isMax = fac.level >= IndustryRules.MAX_FACTORY_LEVEL

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppColors.Card,
                                    border = BorderStroke(1.dp, AppColors.CardBorder),
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "${fDef.name} (م${fac.level})",
                                                color = AppColors.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                            )
                                            Text(
                                                text = "+${formatMoney(annualYield)}/سنة",
                                                color = AppColors.Good,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        if (!lost) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (sys.canStateBuild && !isMax) {
                                                    OutlinedButton(
                                                        enabled = !noActions,
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        onClick = { onRunAction { IndustryRules.buildOrUpgradeFactory(it, spDef.key, fac.type) } },
                                                    ) {
                                                        Text("ترقية (${formatMoney(upgradeCost)})", fontSize = 11.sp)
                                                    }
                                                }
                                                if (sys.canSubsidize) {
                                                    OutlinedButton(
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        onClick = { onRunAction { IndustryRules.toggleSubsidy(it, spDef.key, fac.type) } },
                                                    ) {
                                                        Text(
                                                            text = if (fac.subsidized) "مدعوم" else "دعم",
                                                            fontSize = 11.sp,
                                                            color = if (fac.subsidized) AppColors.Good else AppColors.Muted,
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    onClick = { onRunAction { IndustryRules.demolishFactory(it, spDef.key, fac.type) } },
                                                ) {
                                                    Text("تفكيك", fontSize = 11.sp, color = AppColors.Bad)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (canAddMore && sys.canStateBuild) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { buildingSubKey = if (showPicker) null else spDef.key },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(if (showPicker) "إخفاء قائمة المصانع" else "+ بناء مصنع في السنجق", fontSize = 11.5.sp)
                        }
                        if (showPicker) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (fd in factoryDefs.filter { fd -> facs.none { it.type == fd.key } }) {
                                    val chk = IndustryRules.canBuildInSubProvince(state.econ, spDef.key, fd.key, year)
                                    val cost = IndustryRules.factoryBuildCost(state, spDef.key, fd.key)
                                    val bonusMatch = fd.bonusResources.any { resources.contains(it) }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = AppColors.Card,
                                        border = BorderStroke(1.dp, AppColors.CardBorder),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(fd.name, color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                OutlinedButton(
                                                    enabled = chk.ok && !noActions,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    onClick = {
                                                        onRunAction { IndustryRules.buildOrUpgradeFactory(it, spDef.key, fd.key) }
                                                        buildingSubKey = null
                                                    },
                                                ) {
                                                    Text("بناء (${formatMoney(cost)})", fontSize = 11.sp)
                                                }
                                            }
                                            Text(
                                                text = if (!chk.ok) {
                                                    chk.reason
                                                } else {
                                                    (if (bonusMatch) "★ +30% مورد مناسب · " else "") + fd.shortDesc
                                                },
                                                color = if (chk.ok) AppColors.Muted else AppColors.Bad,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────── تبويب 6: المشاريع القومية ─────────────

@Composable
private fun ProjectsTab(
    state: GameState,
    noActions: Boolean,
    durations: MutableMap<String, Int>,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
) {
    val active = state.project
    val remainingCatalog = projectCatalog.filter { !state.completed.contains(it.key) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionHint(
                "مشروع واحد فقط في كل وقت، يمول بقسط من الخزينة كل فترة. قصّر المدة يغلو عليك حتى 25%. " +
                    "الفوائد تظهر عند الاكتمال، وللمشروع صيانة سنوية دائمة. إذا نقص المال تعثر المشروع ولم يتقدم. " +
                    "يستهلك البدء إجراء من الديوان.",
            )
            if (active != null) {
                ActiveProjectCard(
                    state = state,
                    project = active,
                    onRunAction = onRunAction,
                    onRequestConfirm = onRequestConfirm,
                )
            } else {
                SuggestionsCard(state)
            }
            if (state.completed.isNotEmpty()) {
                CourtCard {
                    SectionTitle("مشاريع منجزة")
                    for (k in state.completed) {
                        Text(
                            text = "✓ ${projectDef(k)?.name ?: k}",
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            SectionTitle("المشاريع المتاحة")
        }
        items(remainingCatalog.size) { idx ->
            CatalogProjectCard(
                state = state,
                def = remainingCatalog[idx],
                noActions = noActions,
                durations = durations,
                onRunAction = onRunAction,
            )
        }
    }
}

@Composable
private fun SuggestionsCard(state: GameState) {
    val list = ProjectRules.suggestions(state)
    if (list.isEmpty()) return
    CourtCard(border = AppColors.Gold) {
        SectionTitle("مقترحات الديوان")
        for (x in list) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(x.def.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.White)
                Text(x.reason, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        Text("اختر من القائمة أدناه وحدد المدة ثم ابدأ.", color = AppColors.Muted, fontSize = 12.5.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveProjectCard(
    state: GameState,
    project: ActiveProject,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
    onRequestConfirm: (String, () -> Unit) -> Unit,
) {
    val d = projectDef(project.key)
    val years = String.format(Locale.US, "%.1f", project.halves.toDouble() / state.ppy)
    val stateSuffix = when {
        project.paused -> " • موقوف مؤقتا"
        project.stalled > 0 -> " • متعثر لنقص التمويل"
        else -> ""
    }
    CourtCard(border = AppColors.Gold) {
        SectionTitle("الجاري: ${d?.name ?: project.key}")
        CourtBar("الاكتمال", project.progress * 100.0, text = "${(project.progress * 100.0).roundToInt()}%")
        Text(
            text = "باقٍ ${project.remaining} من ${project.halves} فترة ($years سنة) • القسط ${formatMoney(project.installment)} قرش$stateSuffix",
            color = if (project.stalled > 0) AppColors.Bad else AppColors.Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        if (d != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("عند الاكتمال: ${d.benefits}", fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onRunAction { ProjectRules.setPaused(it, !project.paused) } },
            ) {
                Text(if (project.paused) "استئناف المشروع" else "إيقاف مؤقت")
            }
            OutlinedButton(
                onClick = {
                    onRequestConfirm("إلغاء المشروع يضيع كل ما أنفق عليه. هل تلغيه؟") {
                        onRunAction { ProjectRules.cancel(it) }
                    }
                },
            ) {
                Text("إلغاء المشروع")
            }
        }
    }
}

@Composable
private fun CatalogProjectCard(
    state: GameState,
    def: ProjectDef,
    noActions: Boolean,
    durations: MutableMap<String, Int>,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val open = state.year >= def.fromYear
    val isActive = state.project?.key == def.key
    val minP = ProjectRules.minPeriods(state, def)
    val maxP = ProjectRules.maxPeriods(state, def)
    val halves = (durations[def.key] ?: ((minP + maxP) / 2)).coerceIn(minP, maxP)
    val payback = ProjectRules.paybackYears(state, def, halves)
    val total = ProjectRules.totalCost(state, def, halves)
    val inst = ProjectRules.installment(state, def, halves)
    val canStart = open && state.project == null && !noActions && state.treasury >= inst

    CourtCard {
        Text(def.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.White)
        Text(def.effectiveAbout(state), color = AppColors.Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("الفوائد: ${def.benefits}", fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.White)
        if (!open) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("متاح من سنة ${def.fromYear}", color = AppColors.Bad, fontSize = 13.sp)
        } else if (!isActive) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "المدة: $halves فترة (${String.format(Locale.US, "%.1f", halves.toDouble() / state.ppy)} سنة)",
                color = AppColors.White,
            )
            if (maxP > minP) {
                Slider(
                    value = halves.toFloat(),
                    valueRange = minP.toFloat()..maxP.toFloat(),
                    steps = (maxP - minP - 1).coerceAtLeast(0),
                    onValueChange = { durations[def.key] = it.roundToInt().coerceIn(minP, maxP) },
                )
            }
            Text(
                text = "الكلفة الكلية ${formatMoney(total)} قرش، بقسط ${formatMoney(inst)} كل فترة" +
                    if (payback == null) "" else " • يسترد كلفته في نحو ${payback.roundToInt()} سنة",
                color = AppColors.Muted,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                enabled = canStart,
                onClick = { onRunAction { ProjectRules.start(it, def.key, halves) } },
            ) {
                Text("بدء المشروع")
            }
        }
    }
}
