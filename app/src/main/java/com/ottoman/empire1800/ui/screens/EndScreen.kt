package com.ottoman.empire1800.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.Ending
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.milestoneDefs
import com.ottoman.empire1800.ui.components.StatCard
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndScreen(
    ending: Ending,
    state: GameState,
    onNewGame: () -> Unit,
    onHome: () -> Unit,
) {
    val win = ending.isVictory
    val color = if (win) AppColors.Gold else AppColors.Bad
    val lastRound = if (state.round > 0) state.round - 1 else 0
    val recent = state.history.asReversed().take(30)

    Scaffold(
        containerColor = AppColors.Navy,
        topBar = {
            TopAppBar(
                title = { Text("نهاية الحملة") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Navy,
                    titleContentColor = AppColors.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (win) Icons.Filled.EmojiEvents else Icons.Filled.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = color,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ending.title,
                        textAlign = TextAlign.Center,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (win) {
                            "حكمت حتى ${GameEngine.END_YEAR}"
                        } else {
                            "انتهى حكمك في ${GameEngine.periodLabel(lastRound, state.ppy)}"
                        },
                        textAlign = TextAlign.Center,
                        color = AppColors.Muted,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = ending.description,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                        fontSize = 16.sp,
                        color = AppColors.White,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text("ملخص الحملة", fontWeight = FontWeight.Bold, color = AppColors.Gold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        icon = Icons.Filled.AccountBalanceWallet,
                        label = "الخزينة",
                        value = "${formatMoney(state.treasury)} قرش",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        label = "الدين",
                        value = "${formatMoney(state.debt)} قرش",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        icon = Icons.Filled.WorkspacePremium,
                        label = "الشرعية",
                        value = "${state.legitimacy} / 100",
                        progress = state.legitimacy / 100.0,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.Balance,
                        label = "الاستقرار",
                        value = "${state.stability} / 100",
                        progress = state.stability / 100.0,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        icon = Icons.Filled.Shield,
                        label = "الجيش",
                        value = "${state.army} / 100",
                        progress = state.army / 100.0,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.Public,
                        label = "المكانة الدولية",
                        value = "${state.foreign} / 100",
                        progress = state.foreign / 100.0,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                StatCard(
                    icon = Icons.Filled.School,
                    label = "التعليم والتحديث",
                    value = "${state.education} / 100",
                    progress = state.education / 100.0,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                val unlockedSet = state.milestones.toSet()
                val unlockedCount = milestoneDefs.count { unlockedSet.contains(it.id) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Card,
                    border = BorderStroke(1.dp, AppColors.CardBorder),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = AppColors.Gold,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الإنجازات السلطانية",
                                    color = AppColors.Gold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Text(
                                text = "$unlockedCount/${milestoneDefs.size}",
                                color = AppColors.Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        milestoneDefs.chunked(2).forEachIndexed { rowIdx, pair ->
                            if (rowIdx > 0) Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (m in pair) {
                                    val done = unlockedSet.contains(m.id)
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (done) AppColors.GoodBg else AppColors.Navy,
                                        border = BorderStroke(
                                            1.dp,
                                            if (done) AppColors.Good.copy(alpha = 0.45f) else AppColors.CardBorder,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = if (done) AppColors.Good else AppColors.Muted,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = m.title,
                                                color = if (done) AppColors.White else AppColors.Muted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f),
                                            )
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
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "آخر قراراتك (${recent.size} من ${state.history.size})",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Gold,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(recent) { h ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(text = h.choice, color = AppColors.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${GameEngine.periodLabel(h.round, state.ppy)} - ${h.category}: ${h.title}",
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp),
                        color = AppColors.CardBorder.copy(alpha = 0.5f),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("لعبة جديدة")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("القائمة الرئيسية")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
