package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.ui.theme.AppColors
import kotlin.math.roundToLong

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    series: List<ChartSeries>,
    firstYear: Int,
    lastYear: Int,
    height: Dp = 190.dp,
    format: ((Double) -> String)? = null,
) {
    val all = series.flatMap { it.values }
    if (all.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "لا بيانات بعد: العب جولة واحدة على الأقل.",
                color = AppColors.Muted,
            )
        }
        return
    }

    val lo = all.minOrNull() ?: 0.0
    var hi = all.maxOrNull() ?: 1.0
    if (hi - lo < 1e-9) hi = lo + 1.0
    val fmt = format ?: { v -> v.roundToLong().toString() }

    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (s in series) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(s.color, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = s.label,
                        fontSize = 12.5.sp,
                        color = AppColors.Muted,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
            ) {
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(
                        color = AppColors.CardBorder,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                for (s in series) {
                    val vals = s.values
                    if (vals.isEmpty()) continue
                    val path = Path()
                    val n = vals.size
                    for (i in vals.indices) {
                        val x = if (n == 1) size.width / 2f else size.width * i / (n - 1).toFloat()
                        val y = (size.height - ((vals[i] - lo) / (hi - lo)) * size.height).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = s.color,
                        style = Stroke(width = 2.2.dp.toPx()),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("$firstYear", fontSize = 12.sp, color = AppColors.Muted)
                Text("أدنى ${fmt(lo)} • أعلى ${fmt(hi)}", fontSize = 12.sp, color = AppColors.Muted)
                Text("$lastYear", fontSize = 12.sp, color = AppColors.Muted)
            }
        }
    }
}
