package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.milestoneDefs
import com.ottoman.empire1800.ui.theme.AppColors

@Composable
fun MilestonesDialog(
    state: GameState,
    onDismiss: () -> Unit,
) {
    val unlockedSet = state.milestones.toSet()
    val unlockedCount = milestoneDefs.count { unlockedSet.contains(it.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = AppColors.Gold,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "الإنجازات السلطانية",
                        color = AppColors.Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "محقق $unlockedCount من ${milestoneDefs.size}",
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (m in milestoneDefs) {
                    val done = unlockedSet.contains(m.id)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (done) AppColors.GoodBg else AppColors.Navy,
                        border = BorderStroke(
                            1.dp,
                            if (done) AppColors.Good.copy(alpha = 0.45f) else AppColors.CardBorder,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (done) AppColors.Good else AppColors.Muted,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = m.title,
                                        color = AppColors.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (done) "محقق" else "مغلق",
                                        color = if (done) AppColors.Good else AppColors.Muted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = m.description,
                                    color = AppColors.White.copy(alpha = 0.9f),
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                )
                                if (!done) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "شرط الفتح: ${m.hint}",
                                        color = AppColors.Muted,
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
    )
}
