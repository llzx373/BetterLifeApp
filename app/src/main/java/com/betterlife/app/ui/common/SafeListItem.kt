// material3 ListItem 的本地替身,API 与槽位语义保持一致,可直接替换调用点。
//
// 为什么存在:compose-ui 1.12 引入的 RectManager 有回归(issuetracker 549552303 /
// 557063500)。M3 ListItem 的测量策略会读 supportingContent 的 FirstBaseline/LastBaseline
// 来判断 supporting 是否多行;LazyList 预取或越界测量离屏条目时,这次基线读取会强制
// 放置一棵从未注册进 RectList 的子树,直接抛 "LayoutNode N not found in RectList" 闪退
// (今日页推荐区、章节列表、收藏列表下滑均可复现)。
// 1.12.1 与 1.13.0-alpha03 都还没带修复;material3 1.5.0-alpha 线又依赖 ui ≥ 1.12 的
// 新 API(graphicsLayer LayerOutsets),降版本会 NoSuchMethodError,只能换掉触发点。
//
// 实现逐行复刻 material3 1.5.0-alpha29 的 InteractiveListItem 非交互路径(颜色、
// 槽位文字样式、12dp 槽间距、56/72/88dp 最小高度、垂直对齐断点),唯一差异:
// isSupportingMultiline 改用「实测高度 > 30sp」的启发式,不读基线。
// 官方修复发布并升级后,这个文件应整体删除、调用点改回 ListItem。
package com.betterlife.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle

private val SlotSpacing = 12.dp

/** LocalMinimumInteractiveComponentSize 未设置时是 Dp.Unspecified,按 0 处理 */
@Composable
private fun minimumInteractiveSizeOrZero(): Dp {
    val value = LocalMinimumInteractiveComponentSize.current
    return if (value == Dp.Unspecified) 0.dp else value
}

/** 槽位文字样式对齐 ListTokens:label/bodyLarge、supporting/bodyMedium、overline/labelSmall */
@Composable
fun SafeListItem(
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    verticalAlignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    content: @Composable () -> Unit,
) {
    val containerColor = colors.containerColor(enabled = true, selected = false, dragged = false)
    val contentColor = colors.contentColor(enabled = true, selected = false, dragged = false)
    val leadingColor = colors.leadingContentColor(enabled = true, selected = false, dragged = false)
    val trailingColor = colors.trailingContentColor(enabled = true, selected = false, dragged = false)
    val overlineColor = colors.overlineContentColor(enabled = true, selected = false, dragged = false)
    val supportingColor =
        colors.supportingContentColor(enabled = true, selected = false, dragged = false)

    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        SafeListItemLayout(
            modifier = modifier
                .semantics(mergeDescendants = true) {}
                // 与 InteractiveListItem 相同的修饰链:graphicsLayer 会强制开图层,
                // 影响边缘抗锯齿混合;省掉它截图基线会有 1bit 级色差
                .graphicsLayer { shape = RectangleShape; clip = false }
                .background(containerColor, RectangleShape)
                .clip(RectangleShape)
                .padding(contentPadding),
            verticalAlignment = verticalAlignment,
            verticalPadding =
                contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding(),
            leading = {
                Slot(leadingContent, leadingColor, MaterialTheme.typography.labelLarge) { contentSlot ->
                    // 与 M3 相同:内边距计入触控目标,放宽最小交互尺寸,避免重复放大
                    val mics =
                        (minimumInteractiveSizeOrZero() - (startPadding + SlotSpacing))
                            .coerceAtLeast(0.dp)
                    Box(Modifier.padding(end = SlotSpacing)) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides mics,
                            content = contentSlot,
                        )
                    }
                }
            },
            trailing = {
                Slot(trailingContent, trailingColor, MaterialTheme.typography.labelSmall) { contentSlot ->
                    val mics =
                        (minimumInteractiveSizeOrZero() - (endPadding + SlotSpacing))
                            .coerceAtLeast(0.dp)
                    Box(Modifier.padding(start = SlotSpacing)) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides mics,
                            content = contentSlot,
                        )
                    }
                }
            },
            overline = {
                Slot(overlineContent, overlineColor, MaterialTheme.typography.labelSmall) { Box { it() } }
            },
            supporting = {
                Slot(supportingContent, supportingColor, MaterialTheme.typography.bodyMedium) { Box { it() } }
            },
            content = {
                Slot(content, contentColor, MaterialTheme.typography.bodyLarge) { Box { it() } }
            },
        )
    }
}

@Composable
private fun Slot(
    slot: @Composable (() -> Unit)?,
    color: Color,
    textStyle: TextStyle,
    wrap: @Composable (@Composable () -> Unit) -> Unit,
) {
    if (slot != null) {
        wrap {
            CompositionLocalProvider(
                LocalContentColor provides color,
                LocalTextStyle provides textStyle,
                content = slot,
            )
        }
    }
}

@Composable
private fun SafeListItemLayout(
    modifier: Modifier,
    verticalAlignment: Alignment.Vertical,
    verticalPadding: Dp,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    overline: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val measurePolicy =
        remember(verticalAlignment, verticalPadding) {
            SafeListItemMeasurePolicy(verticalAlignment, verticalPadding)
        }
    Layout(
        modifier = modifier,
        contents = listOf(leading, trailing, overline, supporting, content),
        measurePolicy = measurePolicy,
    )
}

/** 行类型:决定最小高度与垂直内边距 */
private enum class RowType {
    OneLine,
    TwoLine,
    ThreeLine,
}

private fun rowType(hasOverline: Boolean, hasSupporting: Boolean, supportingMultiline: Boolean): RowType =
    when {
        (hasOverline && hasSupporting) || supportingMultiline -> RowType.ThreeLine
        hasOverline || hasSupporting -> RowType.TwoLine
        else -> RowType.OneLine
    }

private fun Int.subtractSafely(other: Int): Int =
    if (this == Constraints.Infinity) this else (this - other).coerceAtLeast(0)

private val Placeable?.widthOrZero get() = this?.width ?: 0
private val Placeable?.heightOrZero get() = this?.height ?: 0

private class SafeListItemMeasurePolicy(
    val verticalAlignment: Alignment.Vertical,
    val verticalPadding: Dp,
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (leadingMeasurable, trailingMeasurable, overlineMeasurable, supportingMeasurable, contentMeasurable) =
            measurables

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var constraintOffsetX = 0
        var constraintOffsetY = 0

        val leadingPlaceable = leadingMeasurable.firstOrNull()?.measure(looseConstraints)
        constraintOffsetX += leadingPlaceable.widthOrZero

        val trailingPlaceable =
            trailingMeasurable.firstOrNull()?.measure(looseConstraints.offset(horizontal = -constraintOffsetX))
        constraintOffsetX += trailingPlaceable.widthOrZero

        val overlinePlaceable =
            overlineMeasurable.firstOrNull()?.measure(looseConstraints.offset(horizontal = -constraintOffsetX))
        constraintOffsetY += overlinePlaceable.heightOrZero

        val contentPlaceable =
            contentMeasurable
                .firstOrNull()
                ?.measure(
                    looseConstraints.offset(horizontal = -constraintOffsetX, vertical = -constraintOffsetY),
                )
        constraintOffsetY += contentPlaceable.heightOrZero

        val supportingPlaceable =
            supportingMeasurable
                .firstOrNull()
                ?.measure(
                    looseConstraints.offset(horizontal = -constraintOffsetX, vertical = -constraintOffsetY),
                )

        // 与 M3 的分歧点:不读 supportingPlaceable[FirstBaseline]/[LastBaseline]
        // (离屏测量时这一读就是闪退点),改用 M3 自己在 intrinsic 路径用的高度启发式
        val supportingMultiline =
            supportingPlaceable != null && supportingPlaceable.height > 30.sp.roundToPx()

        val type =
            rowType(
                hasOverline = overlinePlaceable != null,
                hasSupporting = supportingPlaceable != null,
                supportingMultiline = supportingMultiline,
            )

        val width =
            calculateWidth(
                leadingWidth = leadingPlaceable.widthOrZero,
                trailingWidth = trailingPlaceable.widthOrZero,
                overlineWidth = overlinePlaceable.widthOrZero,
                supportingWidth = supportingPlaceable.widthOrZero,
                contentWidth = contentPlaceable.widthOrZero,
                constraints = constraints,
            )
        val height =
            calculateHeight(
                leadingHeight = leadingPlaceable.heightOrZero,
                trailingHeight = trailingPlaceable.heightOrZero,
                overlineHeight = overlinePlaceable.heightOrZero,
                supportingHeight = supportingPlaceable.heightOrZero,
                contentHeight = contentPlaceable.heightOrZero,
                constraints = constraints,
                rowType = type,
            )

        return layout(width, height) {
            leadingPlaceable?.placeRelative(x = 0, y = verticalAlignment.align(leadingPlaceable.height, height))

            val mainContentX = leadingPlaceable.widthOrZero
            val mainContentTotalHeight =
                contentPlaceable.heightOrZero + overlinePlaceable.heightOrZero +
                    supportingPlaceable.heightOrZero
            var currentY = verticalAlignment.align(mainContentTotalHeight, height)

            overlinePlaceable?.placeRelative(mainContentX, currentY)
            currentY += overlinePlaceable.heightOrZero
            contentPlaceable?.placeRelative(mainContentX, currentY)
            currentY += contentPlaceable.heightOrZero
            supportingPlaceable?.placeRelative(mainContentX, currentY)

            trailingPlaceable?.placeRelative(
                x = width - trailingPlaceable.width,
                y = verticalAlignment.align(trailingPlaceable.height, height),
            )
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::maxIntrinsicHeight)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::maxIntrinsicWidth)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::minIntrinsicHeight)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::minIntrinsicWidth)

    private fun calculateIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
        intrinsicMeasure: IntrinsicMeasurable.(height: Int) -> Int,
    ): Int {
        val (leading, trailing, overline, supporting, content) = measurables
        return calculateWidth(
            leadingWidth = leading.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            trailingWidth = trailing.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            overlineWidth = overline.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            supportingWidth = supporting.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            contentWidth = content.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            constraints = Constraints(),
        )
    }

    private fun Density.calculateIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
        intrinsicMeasure: IntrinsicMeasurable.(width: Int) -> Int,
    ): Int {
        val (leading, trailing, overline, supporting, content) = measurables

        var remainingWidth = width
        val leadingHeight =
            leading.firstOrNull()?.let {
                val h = it.intrinsicMeasure(remainingWidth)
                remainingWidth = remainingWidth.subtractSafely(it.maxIntrinsicWidth(Constraints.Infinity))
                h
            } ?: 0
        val trailingHeight =
            trailing.firstOrNull()?.let {
                val h = it.intrinsicMeasure(remainingWidth)
                remainingWidth = remainingWidth.subtractSafely(it.maxIntrinsicWidth(Constraints.Infinity))
                h
            } ?: 0
        val overlineHeight = overline.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val supportingHeight = supporting.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val contentHeight = content.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0

        val type =
            rowType(
                hasOverline = overlineHeight > 0,
                hasSupporting = supportingHeight > 0,
                supportingMultiline = supportingHeight > 30.sp.roundToPx(),
            )

        return calculateHeight(
            leadingHeight = leadingHeight,
            trailingHeight = trailingHeight,
            overlineHeight = overlineHeight,
            supportingHeight = supportingHeight,
            contentHeight = contentHeight,
            constraints = Constraints(),
            rowType = type,
        )
    }

    private fun calculateWidth(
        leadingWidth: Int,
        trailingWidth: Int,
        overlineWidth: Int,
        supportingWidth: Int,
        contentWidth: Int,
        constraints: Constraints,
    ): Int {
        if (constraints.hasBoundedWidth) return constraints.maxWidth
        val mainContentWidth = maxOf(contentWidth, overlineWidth, supportingWidth)
        return leadingWidth + mainContentWidth + trailingWidth
    }

    private fun Density.calculateHeight(
        leadingHeight: Int,
        trailingHeight: Int,
        overlineHeight: Int,
        supportingHeight: Int,
        contentHeight: Int,
        constraints: Constraints,
        rowType: RowType,
    ): Int {
        val mainContentHeight = contentHeight + overlineHeight + supportingHeight
        val calculatedHeight = maxOf(leadingHeight, mainContentHeight, trailingHeight)

        // M3 ListTokens:56 / 72 / 88dp
        val defaultMinHeight =
            when (rowType) {
                RowType.OneLine -> 56.dp
                RowType.TwoLine -> 72.dp
                RowType.ThreeLine -> 88.dp
            }
        val defaultMinHeightNoPadding = (defaultMinHeight - verticalPadding).coerceAtLeast(0.dp)
        // 同 Modifier.defaultMinSize 语义:调用方已给最小高度时不覆盖
        val minHeight =
            if (constraints.minHeight == 0) {
                defaultMinHeightNoPadding.roundToPx().coerceIn(0, constraints.maxHeight)
            } else {
                constraints.minHeight
            }

        return calculatedHeight.coerceIn(minHeight, constraints.maxHeight)
    }
}
