package com.artha.kirana.ui.pnl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.TextMuted
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer

@Composable
fun ProfitChart(daily: List<DailyRevenue>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(daily) {
        if (daily.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries { series(daily.map { it.amount }) }
            }
        }
    }

    Card(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = fill(Mint)),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberAxisLabelComponent(color = TextMuted),
                    guideline = rememberAxisGuidelineComponent(fill = fill(Line)),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberAxisLabelComponent(color = TextMuted),
                    guideline = null,
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier,
        )
    }
}
