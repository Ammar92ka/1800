package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.ui.theme.AppColors

@Composable
fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    progress: Double? = null,
    warn: Boolean = false,
) {
    val accent = if (warn) AppColors.Bad else AppColors.Gold
    val description = buildString {
        append("$label: $value")
        if (hint != null) append("، $hint")
        if (warn) append("، تحذير")
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, if (warn) AppColors.Bad else AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (warn) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AppColors.Bad,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = AppColors.CardBorder,
                )
            }
            if (hint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
