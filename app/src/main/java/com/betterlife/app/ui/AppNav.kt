// 主页壳:起始路由来自 SettingsViewModel(唯一读过 onboardingDone 的地方),
// 导航用 NavigationSuiteScaffold —— compact 出 ShortNavigationBar,
// medium/expanded 自动换成 WideNavigationRail,大屏适配不再需要手写宽度分支。
//
// 路由一律走类型安全形式(Kotlin Serialization):`entry/{entryId}` 这种字符串
// 路由散在多个文件里手写,重构时必错。
package com.betterlife.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.betterlife.app.BetterLifeApp
import com.betterlife.app.R
import com.betterlife.app.ui.chat.ChatScreen
import com.betterlife.app.ui.common.OfflineBanner
import com.betterlife.app.ui.dismissed.DismissedScreen
import com.betterlife.app.ui.favorites.FavoritesScreen
import com.betterlife.app.ui.library.EntryDetailScreen
import com.betterlife.app.ui.library.LibraryScreen
import com.betterlife.app.ui.library.SectionScreen
import com.betterlife.app.ui.mine.MineScreen
import com.betterlife.app.ui.onboarding.OnboardingScreen
import com.betterlife.app.ui.settings.SettingsScreen
import com.betterlife.app.ui.stats.StatsScreen
import com.betterlife.app.ui.theme.LocalMotionLevel
import com.betterlife.app.ui.theme.MotionLevel
import com.betterlife.app.ui.theme.motionEffectsSpec
import com.betterlife.app.ui.theme.motionSpatialSpec
import com.betterlife.app.ui.timer.TimerScreen
import com.betterlife.app.ui.today.TodayScreen
import com.betterlife.app.ui.todo.TodoScreen
import com.betterlife.app.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// ---- 路由 ----
// 四个 tab 用 data object 就够了;带参的(Section/Entry/Chat)是 data class,参数即路由参数。

@Serializable
data object TodayRoute

@Serializable
data object TodoRoute

@Serializable
data object LibraryRoute

@Serializable
data class SectionRoute(val sectionN: Int)

@Serializable
data class EntryRoute(val entryId: String)

@Serializable
data object MineRoute

@Serializable
data class ChatRoute(val entryId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data object FavoritesRoute

@Serializable
data object DismissedRoute

@Serializable
data object StatsRoute

@Serializable
data class TimerRoute(val taskId: Long)

@Serializable
data object OnboardingRoute

internal data class TabSpec(
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
    val go: (NavHostController) -> Unit,
)

/**
 * 每个 tab 各自写死具体路由类型,不把它们退化成 `Any` ——
 * 类型安全路由的序列化信息来自路由对象的**静态类型**,退化成 Any 会丢掉这层保障。
 */
internal val tabs = listOf(
    TabSpec(TodayRoute::class, R.string.nav_today, Icons.Filled.Home) { it.navigate(TodayRoute) { tabOptions() } },
    TabSpec(TodoRoute::class, R.string.nav_todo, Icons.Filled.Done) { it.navigate(TodoRoute) { tabOptions() } },
    TabSpec(
        LibraryRoute::class,
        R.string.nav_library,
        Icons.AutoMirrored.Filled.List,
    ) { it.navigate(LibraryRoute) { tabOptions() } },
    TabSpec(MineRoute::class, R.string.nav_mine, Icons.Filled.Person) { it.navigate(MineRoute) { tabOptions() } },
)

@Composable
fun AppNav(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // DataStore 还没读出来时不要先渲染 today 再跳走,那会闪一下。
    // loading → 主界面走 Crossfade:OFF 档 spec 是 snap,瞬时切换没有中间帧。
    Crossfade(
        targetState = state.loaded,
        animationSpec = motionEffectsSpec(),
        label = "appLoading",
    ) { loaded ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AppScaffold(
                startDestination = if (state.settings.onboardingDone) TodayRoute else OnboardingRoute,
            )
        }
    }
}

@Composable
private fun AppScaffold(startDestination: Any) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedTab = tabs.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }

    // 非 tab 页(详情/设置/聊天/引导)收起导航组件,让内容占满。
    // 初始值跟着起始路由走,避免引导页先闪一下导航栏再看它收回去。
    val navState = rememberNavigationSuiteScaffoldState(
        initialValue = if (tabs.any { it.routeClass == startDestination::class }) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        },
    )
    LaunchedEffect(selectedTab) {
        if (selectedTab == null) navState.hide() else navState.show()
    }

    // 初始值 true:启动时 NetworkMonitor 的第一帧快照还没到,先按在线处理,避免横幅闪一下。
    val networkMonitor = (LocalContext.current.applicationContext as BetterLifeApp).container.networkMonitor
    val online by networkMonitor.online.collectAsStateWithLifecycle(initialValue = true)

    NavigationSuiteScaffold(
        state = navState,
        navigationItems = {
            tabs.forEach { tab ->
                val label = stringResource(tab.labelRes)
                NavigationSuiteItem(
                    selected = selectedTab == tab,
                    onClick = { tab.go(navController) },
                    icon = { Icon(tab.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 横幅在状态栏之下、NavHost 之上;不可见时 MotionEntrance 不组合内容,不占位
            OfflineBanner(visible = !online, modifier = Modifier.statusBarsPadding())
            // 转场 spec 必须先在 Composable 作用域取出来 —— NavHost 的转场 lambda 不是
            // Composable 上下文。OFF 档给 None:连 snap() 都可能留一帧 alpha=0 的中间态
            val motionOff = LocalMotionLevel.current == MotionLevel.OFF
            val navFade = motionEffectsSpec<Float>()
            val navSlide = motionSpatialSpec<IntOffset>()
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    when {
                        motionOff -> EnterTransition.None
                        // tab 之间切换只淡入;推进二级页时新页从右侧滑入,给出方向感
                        initialState.destination.isTabDestination() &&
                            targetState.destination.isTabDestination() -> fadeIn(animationSpec = navFade)
                        else -> fadeIn(animationSpec = navFade) +
                            slideInHorizontally(animationSpec = navSlide) { it / 4 }
                    }
                },
                exitTransition = {
                    if (motionOff) ExitTransition.None else fadeOut(animationSpec = navFade)
                },
                popEnterTransition = {
                    if (motionOff) EnterTransition.None else fadeIn(animationSpec = navFade)
                },
                popExitTransition = {
                    if (motionOff) ExitTransition.None else fadeOut(animationSpec = navFade)
                },
            ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onOpenChat = { navController.navigate(ChatRoute()) },
                    onEditProfile = { navController.navigate(OnboardingRoute) },
                    onOpenLibrary = { navController.navigate(LibraryRoute) { tabOptions() } },
                    onStartTimer = { id -> navController.navigate(TimerRoute(id)) },
                )
            }
            composable<TodoRoute> {
                TodoScreen(
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onStartTimer = { id -> navController.navigate(TimerRoute(id)) },
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(
                    onOpenSection = { n -> navController.navigate(SectionRoute(n)) },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onOpenChat = { id -> navController.navigate(ChatRoute(id)) },
                )
            }
            composable<SectionRoute> { entry ->
                SectionScreen(
                    sectionN = entry.toRoute<SectionRoute>().sectionN,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                )
            }
            composable<EntryRoute> { entry ->
                EntryDetailScreen(
                    entryId = entry.toRoute<EntryRoute>().entryId,
                    onBack = { navController.popBackStack() },
                    onExplain = { id -> navController.navigate(ChatRoute(id)) },
                )
            }
            composable<MineRoute> {
                MineScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onEditProfile = { navController.navigate(OnboardingRoute) },
                    onOpenChat = { navController.navigate(ChatRoute()) },
                    onOpenFavorites = { navController.navigate(FavoritesRoute) },
                    onOpenDismissed = { navController.navigate(DismissedRoute) },
                    onOpenStats = { navController.navigate(StatsRoute) },
                )
            }
            composable<StatsRoute> {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                )
            }
            composable<DismissedRoute> {
                DismissedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                )
            }
            composable<TimerRoute> { entry ->
                TimerScreen(
                    taskId = entry.toRoute<TimerRoute>().taskId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ChatRoute> { entry ->
                ChatScreen(
                    entryId = entry.toRoute<ChatRoute>().entryId,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(OnboardingRoute) },
                )
            }
            composable<OnboardingRoute> {
                OnboardingScreen(onFinished = {
                    navController.navigate(TodayRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                })
            }
            }
        }
    }
}

/** tab 之间切换的导航选项:保留各自栈内状态,不重复入栈 */
private fun NavOptionsBuilder.tabOptions() {
    // 固定锚定 today:onboarding 作为起始页时在完成后会被弹出回退栈,不能作为 popUpTo 锚点
    popUpTo(TodayRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 目的地是否属于四个 tab 之一(含其栈内层级),用于区分 tab 切换与二级页推进 */
private fun NavDestination?.isTabDestination(): Boolean =
    this?.hierarchy?.any { dest -> tabs.any { tab -> dest.hasRoute(tab.routeClass) } } == true
