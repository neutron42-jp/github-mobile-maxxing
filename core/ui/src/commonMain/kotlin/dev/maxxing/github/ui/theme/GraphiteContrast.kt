package dev.maxxing.github.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.maxxing.github.model.DiffLineKind

/**
 * Graphite-only color resolution for review and diff surfaces.
 *
 * Graphite's primaryContainer is dark in light mode and light in dark mode, so translucent
 * primaryContainer fills lose contrast once onSurface/onSurfaceVariant text is drawn on top.
 * These helpers replace only those fills with the neutral surface family and derive the
 * remaining accents from the applied scheme. Legacy callers keep the existing opacity-based
 * values, so a non-Graphite palette is never affected.
 *
 * Layer order for the diff rows is background -> inline changed-word band -> foreground text.
 * The band uses the same opaque accent as the +/- symbol, and its text uses surface.
 */
object GraphiteContrast {

    /** State green used on Graphite's neutral surfaces (light / dark). */
    internal val SuccessLight = Color(0xFF166534)
    internal val SuccessDark = Color(0xFF86EFAC)

    /** State amber used on Graphite's neutral surfaces (light / dark). */
    internal val WarningLight = Color(0xFF854D0E)
    internal val WarningDark = Color(0xFFFCD34D)

    /** Existing accents kept for every non-Graphite palette. */
    internal val LegacySuccess = Color(0xFF16A34A)
    internal val LegacyWarning = Color(0xFFD97706)
    internal val AddedDiffGreen = Color(0xFF22C55E)

    /** Graphite is light when the applied scheme's surface is light. */
    private fun isLightSurface(colorScheme: ColorScheme): Boolean =
        colorScheme.surface.luminance() > 0.5f

    private fun successForeground(colorScheme: ColorScheme): Color =
        if (isLightSurface(colorScheme)) SuccessLight else SuccessDark

    private fun warningForeground(colorScheme: ColorScheme): Color =
        if (isLightSurface(colorScheme)) WarningLight else WarningDark

    /**
     * Foreground for a success (viewed/resolved/added) or warning (changed) marker.
     * Returns the pre-Graphite constants when [graphitePalette] is false.
     */
    fun statusAccentColor(
        colorScheme: ColorScheme,
        graphitePalette: Boolean,
        isWarning: Boolean = false
    ): Color = when {
        graphitePalette && isWarning -> warningForeground(colorScheme)
        graphitePalette -> successForeground(colorScheme)
        isWarning -> LegacyWarning
        else -> LegacySuccess
    }

    /**
     * Opaque fill for a review file row. Graphite selection uses the Highest surface and a
     * primary border; every other palette keeps the translucent primaryContainer fill.
     */
    data class ReviewFileRowColors(
        val container: Color,
        val border: Color?
    )

    fun reviewFileRowColors(
        colorScheme: ColorScheme,
        graphitePalette: Boolean,
        selected: Boolean
    ): ReviewFileRowColors = when {
        !selected -> ReviewFileRowColors(colorScheme.surfaceContainerLow, border = null)
        graphitePalette -> ReviewFileRowColors(colorScheme.surfaceContainerHighest, colorScheme.primary)
        else -> ReviewFileRowColors(colorScheme.primaryContainer.copy(alpha = 0.6f), border = null)
    }

    /** Opaque fill for the all-files-viewed completion card. */
    fun reviewCompletionCardContainer(colorScheme: ColorScheme, graphitePalette: Boolean): Color =
        if (graphitePalette) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.primaryContainer.copy(alpha = 0.72f)
        }

    /** Opaque fill for the Files-tab review start/resume card. */
    fun reviewStartCardContainer(colorScheme: ColorScheme, graphitePalette: Boolean): Color =
        if (graphitePalette) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.primaryContainer.copy(alpha = 0.55f)
        }

    /** Branch chip fill and content color. A null content color keeps the legacy inherited color. */
    data class BranchChipColors(
        val container: Color,
        val content: Color?
    )

    fun branchChipColors(colorScheme: ColorScheme, graphitePalette: Boolean): BranchChipColors =
        if (graphitePalette) {
            BranchChipColors(
                container = colorScheme.surfaceContainerHighest,
                content = colorScheme.onSurface
            )
        } else {
            BranchChipColors(
                container = colorScheme.primaryContainer.copy(alpha = 0.6f),
                content = null
            )
        }

    /** The +N addition counter uses the Graphite state green on High, and the existing green otherwise. */
    fun additionsCounterColor(colorScheme: ColorScheme, graphitePalette: Boolean): Color =
        if (graphitePalette) successForeground(colorScheme) else AddedDiffGreen

    /** Resolved colors for one shared diff row, including its inline changed-word span. */
    data class DiffLineColors(
        val background: Color,
        val lineNumber: Color,
        val prefix: Color,
        val content: Color,
        val inlineSpanBackground: Color?,
        val inlineSpanForeground: Color,
        val selectionBorder: Color?
    )

    fun diffLineColors(
        colorScheme: ColorScheme,
        kind: DiffLineKind,
        selected: Boolean,
        graphitePalette: Boolean
    ): DiffLineColors = if (graphitePalette) {
        val background = when (kind) {
            DiffLineKind.ADDED ->
                AddedDiffGreen.copy(alpha = 0.14f).compositeOver(colorScheme.surface)
            DiffLineKind.REMOVED ->
                colorScheme.error.copy(alpha = 0.14f).compositeOver(colorScheme.surface)
            DiffLineKind.META -> colorScheme.surfaceContainerHighest
            DiffLineKind.CONTEXT -> colorScheme.surfaceContainerLow
        }
        val accent = when (kind) {
            DiffLineKind.ADDED -> successForeground(colorScheme)
            DiffLineKind.REMOVED -> colorScheme.error
            else -> colorScheme.onSurfaceVariant
        }
        DiffLineColors(
            background = background,
            lineNumber = colorScheme.onSurfaceVariant,
            prefix = accent,
            content = colorScheme.onSurface,
            inlineSpanBackground = when (kind) {
                DiffLineKind.ADDED, DiffLineKind.REMOVED -> accent
                else -> null
            },
            inlineSpanForeground = colorScheme.surface,
            selectionBorder = if (selected) colorScheme.primary else null
        )
    } else {
        val background = when {
            selected -> colorScheme.primaryContainer.copy(alpha = 0.45f)
            kind == DiffLineKind.ADDED -> AddedDiffGreen.copy(alpha = 0.14f)
            kind == DiffLineKind.REMOVED -> colorScheme.error.copy(alpha = 0.14f)
            kind == DiffLineKind.META -> colorScheme.surfaceContainerHighest
            else -> colorScheme.surfaceContainerLow
        }
        DiffLineColors(
            background = background,
            lineNumber = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            prefix = when (kind) {
                DiffLineKind.ADDED -> LegacySuccess
                DiffLineKind.REMOVED -> colorScheme.error
                else -> colorScheme.onSurfaceVariant
            },
            content = colorScheme.onSurface,
            inlineSpanBackground = when (kind) {
                DiffLineKind.ADDED -> AddedDiffGreen.copy(alpha = 0.36f)
                DiffLineKind.REMOVED -> colorScheme.error.copy(alpha = 0.34f)
                else -> null
            },
            inlineSpanForeground = colorScheme.onSurface,
            selectionBorder = null
        )
    }
}
