package dev.maxxing.github.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Users
import dev.maxxing.github.model.RepoInsightsData
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.parseHexColor
import dev.maxxing.github.ui.i18n.appStrings

@Composable
internal fun RepoInsightsTabContent(
    insights: RepoInsightsData?,
    isLoading: Boolean,
    errorMsg: String?,
    onRefresh: () -> Unit,
    onOpenUser: (String) -> Unit
) {
    val strings = appStrings()
    when {
        isLoading && insights == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MaxxingLoadingState(message = strings.insightsLoading)
            }
        }

        errorMsg != null && insights == null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onRefresh) { Text(strings.retry) }
            }
        }

        else -> {
            val data = insights ?: RepoInsightsData()
            val recentWeeks = data.weeklyActivity.takeLast(16)
            val maxWeekCommits = recentWeeks.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
            val commitsLast4w = data.weeklyActivity.takeLast(4).sumOf { it.total }
            val commitsLastYear = data.weeklyActivity.sumOf { it.total }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "insights_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.insightsHeader,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onRefresh,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Lucide.RefreshCw, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(strings.refresh, maxLines = 1, softWrap = false)
                        }
                    }
                }

                if (data.statsComputing) {
                    item(key = "insights_computing") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Lucide.Activity,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.insightsComputingNotice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item(key = "insights_kpi") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InsightKpiTile(
                            label = strings.insightsLast4wCommits,
                            value = commitsLast4w.toString(),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        InsightKpiTile(
                            label = strings.insights52wCommits,
                            value = commitsLastYear.toString(),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        InsightKpiTile(
                            label = strings.insightsLanguages,
                            value = data.languages.size.toString(),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }

                item(key = "insights_languages") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Lucide.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(strings.insightsLanguages, style = MaterialTheme.typography.titleMedium)
                                if (data.totalLanguageBytes > 0L) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = formatByteSize(data.totalLanguageBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (data.languages.isEmpty()) {
                                Text(
                                    text = strings.insightsNoLanguages,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    data.languages.forEach { lang ->
                                        val weight = lang.percentage.coerceAtLeast(1f)
                                        Box(
                                            modifier = Modifier
                                                .weight(weight)
                                                .fillMaxHeight()
                                                .background(parseHexColor(lang.colorHex))
                                        )
                                    }
                                }

                                data.languages.forEachIndexed { idx, lang ->
                                    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(parseHexColor(lang.colorHex))
                                        )
                                        Text(
                                            text = lang.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatByteSize(lang.bytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${lang.percentage}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "insights_pulse") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Lucide.GitCommitHorizontal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(strings.insightsWeeklyPulse, style = MaterialTheme.typography.titleMedium)
                            }

                            if (recentWeeks.isEmpty()) {
                                Text(
                                    text = if (data.statsComputing) {
                                        strings.insightsComputingPulse
                                    } else {
                                        strings.insightsNoWeeklyPulse
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(92.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    recentWeeks.forEach { week ->
                                        val fraction = (week.total.toFloat() / maxWeekCommits.toFloat())
                                            .coerceIn(if (week.total > 0) 0.10f else 0.04f, 1f)
                                        Column(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            verticalArrangement = Arrangement.Bottom,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(fraction)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(
                                                        if (week.total > 0) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = strings.insightsPulseSummary(maxWeekCommits, recentWeeks.sumOf { it.total }),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "insights_contributors") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Lucide.Users,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(strings.insightsTopContributors, style = MaterialTheme.typography.titleMedium)
                            }

                            if (data.contributors.isEmpty()) {
                                Text(
                                    text = strings.insightsNoContributors,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                data.contributors.forEachIndexed { idx, contributor ->
                                    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenUser(contributor.login) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        UserAvatar(contributor.avatarUrl, contributor.login, sizeDp = 30)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${contributor.login}",
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (contributor.additions > 0 || contributor.deletions > 0) {
                                                Text(
                                                    text = "+${contributor.additions} / -${contributor.deletions}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (contributor.totalCommits > 0) {
                                            Text(
                                                text = strings.insightsCommitCount(contributor.totalCommits),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> {
        val tenths = ((bytes / 1_048_576.0) * 10.0).toInt()
        "${tenths / 10}.${tenths % 10} MB"
    }
    bytes >= 1_024L -> "${bytes / 1_024L} KB"
    else -> "$bytes B"
}
