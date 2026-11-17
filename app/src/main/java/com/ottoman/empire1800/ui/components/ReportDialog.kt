package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.HalfYearReport
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import com.ottoman.empire1800.ui.util.formatMoneySigned
import com.ottoman.empire1800.ui.util.formatSigned

@Composable
private fun ReportRow(
    label: String,
    value: Double,
    signed: Boolean = true,
    color: Color? = null,
) {
    val text = if (signed) formatMoneySigned(value) else formatMoney(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.Muted, modifier = Modifier.weight(1f))
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = color ?: AppColors.White,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    report: HalfYearReport,
    isReplay: Boolean = false,
    onContinue: () -> Unit,
) {
    val net = report.net
    val changes = report.indicatorChanges.entries.filter { it.value != 0 }

    AlertDialog(
        onDismissRequest = { if (isReplay) onContinue() },
        containerColor = AppColors.Card,
        title = {
            Column {
                Text("تقرير ${report.period}", color = AppColors.Gold)
                if (isReplay) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "سجل الفترة السابقة",
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ReportRow("الإيراد المحصل", report.revenue)
                ReportRow("النفقات", -report.spending)
                ReportRow("فوائد الدين", -report.interest)
                if (report.warSpend > 0.0) ReportRow("نفقات الحرب", -report.warSpend)
                if (report.dadPaid > 0.0) ReportRow("إدارة الديون العامة", -report.dadPaid)
                if (report.projectSpend > 0.0) ReportRow("قسط المشروع القومي", -report.projectSpend)
                if (report.oneTimeCosts > 0.0) ReportRow("مصروفات القرارات", -report.oneTimeCosts)
                if (report.loans > 0.0) ReportRow("قروض حصلت عليها", report.loans)
                if (report.repaid > 0.0) ReportRow("سداد من الدين", -report.repaid)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = AppColors.CardBorder,
                )
                ReportRow(
                    label = "صافي الميزانية",
                    value = net,
                    color = if (net < 0) AppColors.Bad else AppColors.Good,
                )
                ReportRow("الخزينة الآن", report.treasuryAfter, signed = false)
                ReportRow("الدين الآن", report.debtAfter, signed = false)

                if (report.emergencyBorrowed > 0.0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = AppColors.Bad,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نفدت الخزينة فاقترضت الدولة ${formatMoney(report.emergencyBorrowed)} قرش بعمولة، وتأخرت رواتب الجند والموظفين.",
                            lineHeight = 22.sp,
                            color = AppColors.White,
                        )
                    }
                }

                if (changes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("تغير المؤشرات", color = AppColors.Gold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((k, v) in changes) {
                            Pill(
                                text = "$k ${formatSigned(v)}",
                                good = v > 0,
                                up = v > 0,
                            )
                        }
                    }
                }

                if (report.courtLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("إجراءات الديوان", color = AppColors.Gold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    for (n in report.courtLog) {
                        Text(
                            text = "• $n",
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                if (report.events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("أحداث ${GameEngine.periodNoun(report.ppy)}", color = AppColors.Gold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    for (n in report.events) {
                        Text(
                            text = "• $n",
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                if (report.dueNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("آثار ظهرت الآن", color = AppColors.Gold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    for (n in report.dueNotes) {
                        Text(
                            text = "• $n",
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text(
                    when {
                        isReplay -> "إغلاق التقرير"
                        report.ending == null -> "تابع"
                        else -> "النتيجة"
                    },
                )
            }
        },
    )
}
