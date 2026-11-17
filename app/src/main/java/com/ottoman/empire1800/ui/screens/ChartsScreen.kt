package com.ottoman.empire1800.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.StatPoint
import com.ottoman.empire1800.ui.components.ChartSeries
import com.ottoman.empire1800.ui.components.LineChart
import com.ottoman.empire1800.ui.theme.AppColors
import java.util.Locale

private val ChartRed = Color(0xFFE57373)
private val ChartGreen = Color(0xFF81C784)
private val ChartBlue = Color(0xFF64B5F6)
private val ChartPurple = Color(0xFFBA68C8)
private val ChartOrange = Color(0xFFFFB74D)

private fun snapshotPoints(state: GameState): List<StatPoint> {
    val pts = state.series.toMutableList()
    if (pts.isEmpty() || pts.last().round != state.round) {
        pts.add(
            StatPoint(
                round = state.round,
                treasury = state.treasury,
                debt = state.debt,
                revenue = state.budget.totalRevenue,
                legitimacy = state.legitimacy,
                stability = state.stability,
                army = state.army,
                foreign = state.foreign,
                education = state.education,
                population = state.demog.population,
                literacy = state.demog.literacy,
                inflation = state.econ.inflation,
            ),
        )
    }
    return pts
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = AppColors.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    state: GameState,
    onBack: () -> Unit,
) {
    val pts = snapshotPoints(state)
    val first = GameEngine.yearOf(pts.first().round, state.ppy)
    val last = GameEngine.yearOf(pts.last().round, state.ppy)
    val millions: (Double) -> String = { v ->
        "${String.format(Locale.US, "%.1f", v / 1e6)} م"
    }
    val oneDec: (Double) -> String = { v ->
        String.format(Locale.US, "%.1f", v)
    }

    Scaffold(
        containerColor = AppColors.Navy,
        topBar = {
            TopAppBar(
                title = { Text("رسوم بيانية") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Navy,
                    titleContentColor = AppColors.White,
                    navigationIconContentColor = AppColors.White,
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
                ChartCard("الخزينة والدين") {
                    LineChart(
                        series = listOf(
                            ChartSeries("الخزينة", ChartGreen, pts.map { it.treasury }),
                            ChartSeries("الدين", ChartRed, pts.map { it.debt }),
                        ),
                        firstYear = first,
                        lastYear = last,
                        format = millions,
                    )
                }
            }
            item {
                ChartCard("الإيراد السنوي") {
                    LineChart(
                        series = listOf(
                            ChartSeries("الإيراد", ChartBlue, pts.map { it.revenue }),
                        ),
                        firstYear = first,
                        lastYear = last,
                        format = millions,
                    )
                }
            }
            item {
                ChartCard("الشرعية والاستقرار") {
                    LineChart(
                        series = listOf(
                            ChartSeries("الشرعية", AppColors.Gold, pts.map { it.legitimacy.toDouble() }),
                            ChartSeries("الاستقرار", ChartGreen, pts.map { it.stability.toDouble() }),
                        ),
                        firstYear = first,
                        lastYear = last,
                    )
                }
            }
            item {
                ChartCard("الجيش والمكانة والتعليم") {
                    LineChart(
                        series = listOf(
                            ChartSeries("الجيش", ChartRed, pts.map { it.army.toDouble() }),
                            ChartSeries("المكانة الدولية", ChartBlue, pts.map { it.foreign.toDouble() }),
                            ChartSeries("التعليم", ChartPurple, pts.map { it.education.toDouble() }),
                        ),
                        firstYear = first,
                        lastYear = last,
                    )
                }
            }
            item {
                ChartCard("السكان (بالملايين)") {
                    LineChart(
                        series = listOf(
                            ChartSeries("السكان", ChartOrange, pts.map { it.population }),
                        ),
                        firstYear = first,
                        lastYear = last,
                        format = oneDec,
                    )
                }
            }
            item {
                ChartCard("القراءة والكتابة والتضخم") {
                    LineChart(
                        series = listOf(
                            ChartSeries("القراءة والكتابة %", ChartPurple, pts.map { it.literacy }),
                            ChartSeries("التضخم %", ChartRed, pts.map { it.inflation }),
                        ),
                        firstYear = first,
                        lastYear = last,
                        format = oneDec,
                    )
                }
            }
        }
    }
}
