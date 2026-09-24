// 设计 token 的截图基线：口径色板与中文排版阶梯。
//
// 这两张基线同时是无障碍验收的证据（DESIGN_SYSTEM §3.2 / §11）：
// 口径必须色 + 图标成对出现、明度要拉开，排版要看 200% 缩放下会不会挤成一团。
package com.betterlife.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.data.EntryKeys
import com.betterlife.app.ui.FourFoldPreview
import com.betterlife.app.ui.PreviewSurface
import com.betterlife.app.ui.common.lensGroupTitle

@PreviewTest
@FourFoldPreview
@Composable
fun LensPalette() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
            LensRow(EntryKeys.LENS_MORTALITY)
            LensRow(EntryKeys.LENS_MONEY)
            LensRow(EntryKeys.LENS_TIME)
            LensRow(EntryKeys.LENS_FREEDOM)
        }
    }
}

@PreviewTest
@FourFoldPreview
@Composable
fun TypographyLadder() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            LadderLine("屏幕标题", MaterialTheme.typography.headlineSmall)
            LadderLine("区块标题", MaterialTheme.typography.titleMedium)
            LadderLine("卡片标题", MaterialTheme.typography.titleSmall)
            LadderLine("正文：一天做一件小事就够了", MaterialTheme.typography.bodyMedium)
            LadderLine("次要说明：通用口径，不替代医生", MaterialTheme.typography.bodySmall)
            LadderLine("徽标 极高", MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LensRow(lens: String) {
    val color = LocalLensColors.current.forLens(lens) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Icon(imageVector = lensIcon(lens) ?: return@Row, contentDescription = null, tint = color)
        Text(
            text = lensGroupTitle(lens),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun LadderLine(text: String, style: TextStyle) {
    Text(text = text, style = style)
}
