package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.budgetLineLabel
import com.ottoman.empire1800.models.isRevenueLine
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import com.ottoman.empire1800.ui.util.formatMoneySigned

private fun monthsLabel(periods: Int, ppy: Int = 2): String {
    val monthsPerPeriod = 12 / (if (ppy == 4) 4 else 2)
    val n = periods * monthsPerPeriod
    return if (n in 3..10) "$n أشهر" else "$n شهرا"
}

@Composable
private fun LabeledBlock(title: String, body: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            color = AppColors.Gold,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = AppColors.White,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionTile(
    letter: String,
    option: DecisionOption,
    isSelected: Boolean,
    onTap: () -> Unit,
    ppy: Int = 2,
) {
    val hasLater = !option.later.isZero || option.lineChanges.isNotEmpty()
    val hasNow = option.cost > 0.0 || option.loan > 0.0 || !option.now.isZero

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "الخيار $letter: ${option.text}"
            }
            .clickable(role = Role.Button, onClick = onTap),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppColors.SelectedBg else AppColors.Card,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AppColors.Gold else AppColors.CardBorder,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (isSelected) AppColors.Gold else AppColors.CardBorder,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) AppColors.Navy else AppColors.White,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = option.text,
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                    color = AppColors.White,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.Gold,
                    )
                }
            }

            if (hasNow) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (option.cost > 0.0) {
                        Pill(
                            text = "مصروف ${formatMoney(option.cost)} قرش",
                            icon = Icons.Filled.Payments,
                            good = false,
                        )
                    }
                    if (option.loan > 0.0) {
                        Pill(
                            text = "قرض ${formatMoney(option.loan)} قرش",
                            icon = Icons.Filled.AccountBalance,
                        )
                    }
                }
                if (!option.now.isZero) {
                    Spacer(modifier = Modifier.height(6.dp))
                    EffectChips(effects = option.now)
                }
            }

            if (hasLater) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (option.delay <= 0) "آثار ممتدة:" else "بعد ${monthsLabel(option.delay, ppy)}:",
                    color = AppColors.Muted,
                    fontSize = 12.5.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!option.later.isZero) {
                    EffectChips(effects = option.later)
                }
                if (option.lineChanges.isNotEmpty()) {
                    if (!option.later.isZero) Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (c in option.lineChanges) {
                            val revenue = isRevenueLine(c.line)
                            val up = c.delta > 0.0
                            Pill(
                                text = "${budgetLineLabel(c.line)} ${formatMoneySigned(c.delta)} سنويا",
                                up = up,
                                good = if (revenue) up else !up,
                            )
                        }
                    }
                }
            }

            if (isSelected) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = AppColors.CardBorder,
                )
                LabeledBlock("النتيجة المتوقعة", option.result)
                if (option.reaction.isNotEmpty()) {
                    LabeledBlock("ردود الفعل", option.reaction)
                }
                if (option.note.isNotEmpty()) {
                    LabeledBlock("الأساس التاريخي", option.note)
                }
                if (option.calibrated) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.Muted,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "عُدّلت بعض أرقام هذا الخيار لتوافق حجم ميزانية الدولة في هذه المرحلة.",
                            color = AppColors.Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}
