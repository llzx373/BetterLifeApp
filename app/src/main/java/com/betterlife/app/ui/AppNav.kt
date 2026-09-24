// 主页壳:按 onboardingDone 决定起始路由,底部四个 tab + 全部页面路由
package com.betterlife.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.ui.chat.ChatScreen
import com.betterlife.app.ui.library.EntryDetailScreen
import com.betterlife.app.ui.library.LibraryScreen
import com.betterlife.app.ui.library.SectionScreen
import com.betterlife.app.ui.mine.MineScreen
import com.betterlife.app.ui.onboarding.OnboardingScreen
import com.betterlife.app.ui.settings.SettingsScreen
import com.betterlife.app.ui.today.TodayScreen
import com.betterlife.app.ui.todo.TodoScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val TODAY = "today"
    const val TODO = "todo"
    const val LIBRARY = "library"
    const val SECTION = "library/{sectionN}"
    const val ENTRY = "entry/{entryId}"
    const val MINE = "mine"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    fun section(n: Int) = "library/$n"
    fun entry(id: String) = "entry/$id"
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Routes.TODAY, "今日", Icons.Filled.Home),
    TabSpec(Routes.TODO, "待办", Icons.Filled.Done),
    TabSpec(Routes.LIBRARY, "条目库", Icons.AutoMirrored.Filled.List),
    TabSpec(Routes.MINE, "我的", Icons.Filled.Person),
)

@Composable
fun AppNav() {
    val context = LocalContext.current
    // SettingsViewModel 的初始值与默认值不可区分,这里直接读一次 DataStore 决定起始页,避免闪屏
    val startRoute by produceState<String?>(initialValue = null) {
        val app = context.applicationContext as BetterLifeApp
        val done = withContext(Dispatchers.IO) { app.container.settingsStore.current().onboardingDone }
        value = if (done) Routes.TODAY else Routes.ONBOARDING
    }

    when (val start = startRoute) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> AppScaffold(start)
    }
}

@Composable
private fun AppScaffold(startRoute: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTab(navController, tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenEntry = { id -> navController.navigate(Routes.entry(id)) },
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                    onEditProfile = { navController.navigate(Routes.ONBOARDING) },
                    onOpenLibrary = { navigateTab(navController, Routes.LIBRARY) },
                )
            }
            composable(Routes.TODO) {
                TodoScreen(onOpenEntry = { id -> navController.navigate(Routes.entry(id)) })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenSection = { n -> navController.navigate(Routes.section(n)) },
                    onOpenEntry = { id -> navController.navigate(Routes.entry(id)) },
                )
            }
            composable(
                route = Routes.SECTION,
                arguments = listOf(navArgument("sectionN") { type = NavType.IntType }),
            ) { entry ->
                SectionScreen(
                    sectionN = entry.arguments?.getInt("sectionN") ?: 0,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(Routes.entry(id)) },
                )
            }
            composable(
                route = Routes.ENTRY,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
            ) { entry ->
                EntryDetailScreen(
                    entryId = entry.arguments?.getString("entryId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MINE) {
                MineScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onEditProfile = { navController.navigate(Routes.ONBOARDING) },
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Routes.ONBOARDING) },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
        }
    }
}

private fun navigateTab(navController: NavHostController, route: String) {
    // 固定锚定 today:onboarding 作为起始页时在完成后会被弹出回退栈,不能作为 popUpTo 锚点
    navController.navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
