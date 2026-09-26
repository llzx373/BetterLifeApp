// 全局离线横幅:无网络时钉在内容顶部,说明影响范围而不是只报错(文案规范:错误即恢复路径)。
//
// 颜色用 errorContainer:DESIGN_SYSTEM 把「功能不可用的错误态」定在这组色上
// (聊天页错误气泡同例);无 Key 那种「可用但降级」的提示才用 secondaryContainer,不要混。
// 纯展示、无点击处理,按 DESIGN_SYSTEM §8 无障碍条款不需要触控目标,但仍给了 48dp
// 最小高度(Spacing.space12),保证大字体档下也能放下整行文案。
package com.betterlife.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.betterlife.app.R
import com.betterlife.app.ui.theme.Spacing

@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    MotionEntrance(visible = visible, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.space12)
                    .padding(horizontal = Spacing.space4, vertical = Spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null)
                Text(
                    text = stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
