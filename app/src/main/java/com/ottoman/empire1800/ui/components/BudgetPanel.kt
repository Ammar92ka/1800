package com.ottoman.empire1800.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.revenueLines
import com.ottoman.empire1800.models.spendingLines
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import com.ottoman.empire1800.ui.util.formatMoneySigned
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun BudgetRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AppColors.Muted,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            color = color ?: AppColors.White,
        )
    }
}

@Composable
private fun BudgetTitle(text: String) {
    Text(
        text = text,
        color = AppColors.Gold,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
fun BudgetPanel(state: GameState) {
    var expanded by remember { mutableStateOf(false) }

    val b = state.budget
    val efficiency = EconomyRules.collectionEfficiency(state.stability)
    val f = state.f
    val unit = if (state.ppy == 4) "ربع السنة" else "نصف السنة"
    val revenueHalf =
        EconomyRules.halfYearRevenue(
            b,
            state.stability,
            econ = state.econ,
            f = f,
            year = state.year,
            literacy = state.demog.literacy,
        ) * state.diff.revenue
    val spendingHalf = EconomyRules.halfYearSpending(b, f = f, grandVizier = state.grandVizier, econ = state.econ)
    val interestHalf = DebtRules.halfYearInterest(state)
    val warHalf = WarRules.costs(state)
    val proj = state.project
    val projHalf = if (proj != null && !proj.paused) proj.installment else 0.0
    val net = revenueHalf - spendingHalf - interestHalf - warHalf - projHalf

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = AppColors.Gold,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الميزانية والآثار القادمة",
                        color = AppColors.White,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "صافي $unit المتوقع: ${formatMoneySigned(net)} قرش",
                        color = if (net < 0) AppColors.Bad else AppColors.Good,
                        fontSize = 12.5.sp,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.Muted,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                ) {
                    BudgetTitle("الإيراد السنوي (${formatMoney(b.totalRevenue)} قرش)")
                    for (l in revenueLines) {
                        BudgetRow(l.label, formatMoney(b.lineValue(l.key)))
                    }
                    BudgetRow("كفاءة الجباية الحالية", "${(efficiency * 100).roundToInt()}%")

                    BudgetTitle("النفقات السنوية (${formatMoney(b.totalSpending)} قرش)")
                    for (l in spendingLines) {
                        BudgetRow(l.label, formatMoney(b.lineValue(l.key)))
                    }

                    BudgetTitle("الدين")
                    BudgetRow(
                        label = "فائدة $unit (${(EconomyRules.INTEREST_RATE * 100).roundToInt()}% سنويا على الداخلي)",
                        value = formatMoney(interestHalf),
                        color = AppColors.Bad,
                    )
                    if (warHalf > 0.0) {
                        BudgetRow("نفقات الحرب في $unit", formatMoney(warHalf), color = AppColors.Bad)
                    }
                    if (projHalf > 0.0) {
                        BudgetRow("قسط المشروع القومي", formatMoney(projHalf))
                    }
                    BudgetRow(
                        label = "نسبة الدين إلى الإيراد السنوي",
                        value = String.format(Locale.US, "%.2f", EconomyRules.debtRatio(state.debt, b)),
                    )

                    BudgetTitle("آثار قرارات سابقة قادمة")
                    if (state.pending.isEmpty()) {
                        Text("لا توجد.", color = AppColors.Muted, fontSize = 13.sp)
                    } else {
                        for (p in state.pending) {
                            Text(
                                text = "• ${GameEngine.periodLabel(p.dueRound, state.ppy)}: ${p.source}",
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = AppColors.White,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
