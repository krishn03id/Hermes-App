package id.krishn03.hermes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.krishn03.hermes.data.UsageStat

// A distinct, pleasant palette for pie slices (cycled if there are many models).
private val SliceColors = listOf(
    Color(0xFF6C5CE7), // purple
    Color(0xFFD97757), // clay
    Color(0xFF00B894), // green
    Color(0xFF0984E3), // blue
    Color(0xFFE17055), // coral
    Color(0xFFFDCB6E), // amber
    Color(0xFFE84393), // pink
    Color(0xFF00CEC9), // teal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    usage: List<UsageStat>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    // Weight slices by messages sent per model.
    val total = usage.sumOf { it.messages }.coerceAtLeast(1)
    val slices = usage
        .sortedByDescending { it.messages }
        .mapIndexed { i, stat ->
            SliceData(
                label = stat.model,
                sub = "${stat.provider.label} · ${stat.messages} msg · ${formatChars(stat.charsReceived)}",
                fraction = stat.messages.toFloat() / total,
                color = SliceColors[i % SliceColors.size],
            )
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (usage.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Delete, contentDescription = "Reset usage")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (usage.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No usage yet.\nSend a few messages and your per-model breakdown will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PieChart(
                        slices = slices,
                        modifier = Modifier
                            .size(200.dp)
                            .aspectRatio(1f),
                        centerLabel = "$total",
                        centerSub = "messages",
                    )
                }
            }
            item {
                Text(
                    "By model",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(slices) { slice -> LegendRow(slice) }
        }
    }
}

private data class SliceData(
    val label: String,
    val sub: String,
    val fraction: Float,
    val color: Color,
)

@Composable
private fun PieChart(
    slices: List<SliceData>,
    modifier: Modifier = Modifier,
    centerLabel: String,
    centerSub: String,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.18f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                startAngle += sweep
            }
        }
        // Donut center label.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                centerSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(slice: SliceData) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = slice.color, shape = CircleShape, modifier = Modifier.size(14.dp)) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    slice.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    slice.sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${(slice.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = slice.color,
            )
        }
    }
}

private fun formatChars(chars: Long): String = when {
    chars >= 1_000_000 -> "%.1fM chars".format(chars / 1_000_000.0)
    chars >= 1_000 -> "%.1fk chars".format(chars / 1_000.0)
    else -> "$chars chars"
}
