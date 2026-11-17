package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatSigned

@Composable
fun Pill(
    text: String,
    good: Boolean? = null,
    up: Boolean? = null,
    icon: ImageVector? = null,
) {
    val color = when (good) {
        null -> AppColors.Muted
        true -> AppColors.Good
        false -> AppColors.Bad
    }
    val bg = when (good) {
        null -> AppColors.CardBorder
        true -> AppColors.GoodBg
        false -> AppColors.BadBg
    }
    val iconData = icon ?: when (up) {
        null -> null
        true -> Icons.Filled.ArrowUpward
        false -> Icons.Filled.ArrowDownward
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconData != null) {
            Icon(
                imageVector = iconData,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = AppColors.White,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectChips(
    effects: Effects,
    prefix: String = "",
) {
    val items = buildList {
        fun addIfNonZero(label: String, v: Int) {
            if (v != 0) add(label to v)
        }
        addIfNonZero("الشرعية", effects.legitimacy)
        addIfNonZero("الاستقرار", effects.stability)
        addIfNonZero("الجيش", effects.army)
        addIfNonZero("المكانة الدولية", effects.foreign)
        addIfNonZero("التعليم", effects.education)
    }
    if (items.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((label, value) in items) {
            Pill(
                text = "$prefix$label ${formatSigned(value)}",
                good = value > 0,
                up = value > 0,
            )
        }
    }
}
