// 导航壳的宽度自适应基线。
//
// B1 的核心主张是「compact 出底部栏、medium/expanded 自动换 WideNavigationRail」,
// 这句话不该只写在文档里 —— 它得有一张图能证明。这里用两种宽度各渲一次。
//
// 注意整屏预览必须给固定尺寸,而且这次尺寸本身就是要验证的变量。
package com.betterlife.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.betterlife.app.ui.theme.BetterLifeTheme

/** 412dp = 手机竖直;900dp = 平板/展开态,跨过 840dp 这条线 */
private const val PHONE_WIDTH = 412
private const val TABLET_WIDTH = 900

@PreviewTest
@Preview(name = "compact-light", widthDp = PHONE_WIDTH, heightDp = 915)
@Preview(
    name = "compact-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = PHONE_WIDTH,
    heightDp = 915,
)
@Preview(name = "expanded-light", widthDp = TABLET_WIDTH, heightDp = 700)
@Preview(
    name = "expanded-dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = TABLET_WIDTH,
    heightDp = 700,
)
@Composable
fun NavigationShellByWidth() {
    BetterLifeTheme {
        NavigationSuiteScaffold(
            navigationItems = {
                tabs.forEach { tab ->
                    val label = stringResource(tab.labelRes)
                    NavigationSuiteItem(
                        selected = tab.routeClass == TodayRoute::class,
                        onClick = {},
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            },
        ) {
            // 占位内容:这里要看的是导航组件占哪条边、内容区还剩多少,不是内容本身
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "内容区",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
