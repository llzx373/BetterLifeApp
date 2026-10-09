// 主页壳:起始路由来自 SettingsViewModel(唯一读过 onboardingDone 的地方),
// onboardingDone 的语义是「看过引导」而非「填完档案」(N1:可「先随便看看」跳过,
// 空档案直入今日页,档案由每日一问渐进补齐)。
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.betterlife.app.MainActivity
import com.betterlife.app.R
import com.betterlife.app.data.AppSettings
import com.betterlife.app.ui.chat.ChatScreen
import com.betterlife.app.ui.common.OfflineBanner
import com.betterlife.app.ui.completed.CompletedScreen
import com.betterlife.app.ui.dismissed.DismissedScreen
import com.betterlife.app.ui.favorites.FavoritesScreen
import com.betterlife.app.ui.library.ArticleListScreen
import com.betterlife.app.ui.library.ArticleScreen
import com.betterlife.app.ui.library.EntryDetailScreen
import com.betterlife.app.ui.library.LibraryScreen
import com.betterlife.app.ui.library.SectionScreen
import com.betterlife.app.ui.mine.MineScreen
import com.betterlife.app.ui.onboarding.OnboardingScreen
import com.betterlife.app.ui.settings.SettingsAdvancedScreen
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
// tab 路由用 data object 就够了;带参的(Section/Entry/Chat/Library 的 lens 筛选)是 data class,参数即路由参数。

@Serializable
data object TodayRoute

@Serializable
data object TodoRoute

@Serializable
data class LibraryRoute(val lens: String? = null)

@Serializable
data class SectionRoute(val sectionN: Int)

@Serializable
data class EntryRoute(val entryId: String)

@Serializable
data class ArticleRoute(val key: String)

@Serializable
data object ArticleListRoute

@Serializable
data object MineRoute

@Serializable
data class ChatRoute(val entryId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data object SettingsAdvancedRoute

@Serializable
data object FavoritesRoute

@Serializable
data object DismissedRoute

@Serializable
data object CompletedRoute

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
 *
 * N7 长辈模式:tab 简化为「今日 + AI 问答」两个;条目库/统计/待办收进「我的」页,
 * 「我的」由今日页问候区右侧的人形入口进入。
 */
internal fun tabsFor(seniorMode: Boolean): List<TabSpec> =
    if (seniorMode) {
        listOf(
            TabSpec(TodayRoute::class, R.string.nav_today, Icons.Filled.Home) {
                it.navigate(TodayRoute) { tabOptions() }
            },
            TabSpec(
                ChatRoute::class,
                R.string.title_chat,
                Icons.AutoMirrored.Filled.Send,
            ) { it.navigate(ChatRoute()) { tabOptions() } },
        )
    } else {
        listOf(
            TabSpec(TodayRoute::class, R.string.nav_today, Icons.Filled.Home) {
                it.navigate(TodayRoute) { tabOptions() }
            },
            TabSpec(TodoRoute::class, R.string.nav_todo, Icons.Filled.Done) {
                it.navigate(TodoRoute) { tabOptions() }
            },
            TabSpec(
                LibraryRoute::class,
                R.string.nav_library,
                Icons.AutoMirrored.Filled.List,
            ) { it.navigate(LibraryRoute()) { tabOptions() } },
            TabSpec(MineRoute::class, R.string.nav_mine, Icons.Filled.Person) {
                it.navigate(MineRoute) { tabOptions() }
            },
        )
    }

@Composable
fun AppNav(
    navTarget: String? = null,
    onNavTargetConsumed: () -> Unit = {},
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
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
                navTarget = navTarget,
                onNavTargetConsumed = onNavTargetConsumed,
                settings = state.settings,
                onSeniorAskAnswered = { enable ->
                    if (enable) vm.setSeniorMode(true)
                    vm.markSeniorModeAsked()
                },
            )
        }
    }
}

@Composable
private fun AppScaffold(
    startDestination: Any,
    navTarget: String?,
    onNavTargetConsumed: () -> Unit,
    settings: AppSettings,
    onSeniorAskAnswered: (Boolean) -> Unit,
) {
    val seniorMode = settings.seniorMode
    val tabs = remember(seniorMode) { tabsFor(seniorMode) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedTab = tabs.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }

    // 非 tab 页(详情/设置/聊天/引导)收起导航组件,让内容占满。
    // 长辈模式例外:条目库/待办/我的不再是 tab,但它们自身没有返回键,
    // 导航栏是唯一的回家路径,必须保持可见。
    // 初始值跟着起始路由走,避免引导页先闪一下导航栏再看它收回去。
    val navVisible = selectedTab != null ||
        (seniorMode && currentDestination.isSeniorNavVisibleDestination())
    val navState = rememberNavigationSuiteScaffoldState(
        initialValue = if (
            tabs.any { it.routeClass == startDestination::class }
        ) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        },
    )
    LaunchedEffect(navVisible) {
        if (navVisible) navState.show() else navState.hide()
    }

    // N7:onboarding 首次完成后的一次性长辈模式询问(可跳过,只问一次)
    var showSeniorAsk by rememberSaveable { mutableStateOf(false) }

    // N2c/N4/N5:通知深链——点通知直达目标页(挽回→今日页,周报→统计页,每日一条→条目详情);消费一次即回调置空,重组不会反复跳
    LaunchedEffect(navTarget) {
        when {
            navTarget == MainActivity.ROUTE_TODO -> navController.navigate(TodoRoute) { tabOptions() }
            navTarget == MainActivity.ROUTE_TODAY -> navController.navigate(TodayRoute) { tabOptions() }
            // 桌面快捷方式「问 AI」:聊天页非 tab(长辈模式才是),普通 navigate 压栈,返回回到来处
            navTarget == MainActivity.ROUTE_CHAT -> navController.navigate(ChatRoute())
            // N4 周报深链：统计页不是 tab，直接 navigate（同 MinePage 的 onOpenStats）
            navTarget == MainActivity.ROUTE_STATS -> navController.navigate(StatsRoute)
            // N5 每日一条深链："entry/<entryId>" → 条目详情页
            navTarget?.startsWith(MainActivity.ROUTE_ENTRY_PREFIX) == true ->
                navController.navigate(EntryRoute(navTarget.removePrefix(MainActivity.ROUTE_ENTRY_PREFIX)))
        }
        if (navTarget != null) onNavTargetConsumed()
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
                        initialState.destination.isTabDestination(tabs) &&
                            targetState.destination.isTabDestination(tabs) -> fadeIn(animationSpec = navFade)
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
                    onOpenLibrary = { navController.navigate(LibraryRoute()) { tabOptions() } },
                    onOpenMine = { navController.navigate(MineRoute) },
                    onStartTimer = { id -> navController.navigate(TimerRoute(id)) },
                )
            }
            composable<TodoRoute> {
                TodoScreen(
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onStartTimer = { id -> navController.navigate(TimerRoute(id)) },
                )
            }
            composable<LibraryRoute> { entry ->
                LibraryScreen(
                    // 统计页完成度行带口径进来:进库即带好该 lens 筛选
                    initialLens = entry.toRoute<LibraryRoute>().lens,
                    onOpenSection = { n -> navController.navigate(SectionRoute(n)) },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onOpenChat = { id -> navController.navigate(ChatRoute(id)) },
                    onOpenArticle = { key -> navController.navigate(ArticleRoute(key)) },
                    onOpenArticleList = { navController.navigate(ArticleListRoute) },
                )
            }
            composable<SectionRoute> { entry ->
                SectionScreen(
                    sectionN = entry.toRoute<SectionRoute>().sectionN,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    onOpenArticle = { key -> navController.navigate(ArticleRoute(key)) },
                )
            }
            composable<EntryRoute> { entry ->
                EntryDetailScreen(
                    entryId = entry.toRoute<EntryRoute>().entryId,
                    onBack = { navController.popBackStack() },
                    onExplain = { id -> navController.navigate(ChatRoute(id)) },
                    onOpenArticle = { key -> navController.navigate(ArticleRoute(key)) },
                )
            }
            composable<ArticleRoute> { entry ->
                ArticleScreen(
                    articleKey = entry.toRoute<ArticleRoute>().key,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                    // 长文互链(正文里的 docs 链接)压栈即可,返回回到上一篇
                    onOpenArticle = { key -> navController.navigate(ArticleRoute(key)) },
                )
            }
            composable<ArticleListRoute> {
                ArticleListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { key -> navController.navigate(ArticleRoute(key)) },
                )
            }
            composable<MineRoute> {
                MineScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onEditProfile = { navController.navigate(OnboardingRoute) },
                    onOpenChat = { navController.navigate(ChatRoute()) },
                    onOpenFavorites = { navController.navigate(FavoritesRoute) },
                    onOpenDismissed = { navController.navigate(DismissedRoute) },
                    onOpenCompleted = { navController.navigate(CompletedRoute) },
                    onOpenStats = { navController.navigate(StatsRoute) },
                    // N7:长辈模式下条目库/待办不再是 tab,入口收进「我的」页(二级页语义,不带 tabOptions)
                    onOpenLibrary = { navController.navigate(LibraryRoute()) },
                    onOpenTodo = { navController.navigate(TodoRoute) },
                )
            }
            composable<StatsRoute> {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    // 完成度口径行 → 条目库带该 lens 筛选;连签行 → 待办页(二级页语义,同「我的」)
                    onOpenLens = { lens -> navController.navigate(LibraryRoute(lens)) },
                    onOpenTodo = { navController.navigate(TodoRoute) },
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
            composable<CompletedRoute> {
                CompletedScreen(
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
                    // banner 指向的是配 API Key,Key 在高级设置二级页,直达而非落一级设置
                    onOpenSettings = { navController.navigate(SettingsAdvancedRoute) },
                    onOpenEntry = { id -> navController.navigate(EntryRoute(id)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(OnboardingRoute) },
                    onOpenAdvanced = { navController.navigate(SettingsAdvancedRoute) },
                )
            }
            composable<SettingsAdvancedRoute> {
                SettingsAdvancedScreen(onBack = { navController.popBackStack() })
            }
            composable<OnboardingRoute> {
                // 进入本页时 onboardingDone 还没被本次保存改写:首次完成为 false,
                // 从「我的」进编辑档案为 true —— 只有首次完成才触发长辈模式询问
                val firstRun = remember { !settings.onboardingDone }
                OnboardingScreen(
                    onFinished = {
                        if (firstRun && !settings.seniorModeAsked && !settings.seniorMode) {
                            showSeniorAsk = true
                        }
                        navController.navigate(TodayRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    },
                    // 编辑模式顶栏的返回键:不保存,原路退回(「我的」/今日页)
                    onBack = { navController.popBackStack() },
                )
            }
            }
        }
    }

    // N7 一次性询问:开启/暂不/点外部关闭都只问这一次(标记由 onSeniorAskAnswered 落盘)
    if (showSeniorAsk) {
        AlertDialog(
            onDismissRequest = {
                showSeniorAsk = false
                onSeniorAskAnswered(false)
            },
            title = { Text(stringResource(R.string.senior_ask_title)) },
            text = { Text(stringResource(R.string.senior_ask_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSeniorAsk = false
                    onSeniorAskAnswered(true)
                }) { Text(stringResource(R.string.senior_ask_enable)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSeniorAsk = false
                    onSeniorAskAnswered(false)
                }) { Text(stringResource(R.string.senior_ask_skip)) }
            },
        )
    }
}

/** tab 之间切换的导航选项:保留各自栈内状态,不重复入栈 */
private fun NavOptionsBuilder.tabOptions() {
    // 固定锚定 today:onboarding 作为起始页时在完成后会被弹出回退栈,不能作为 popUpTo 锚点
    popUpTo(TodayRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 目的地是否属于当前 tab 集合之一(含其栈内层级),用于区分 tab 切换与二级页推进 */
private fun NavDestination?.isTabDestination(tabs: List<TabSpec>): Boolean =
    this?.hierarchy?.any { dest -> tabs.any { tab -> dest.hasRoute(tab.routeClass) } } == true

/**
 * N7:长辈模式下不再是 tab、但自身没有返回键的页面(从「我的」进入),
 * 导航栏必须保持可见 —— 它是这些页面唯一的回家路径。
 */
private fun NavDestination?.isSeniorNavVisibleDestination(): Boolean =
    this?.hierarchy?.any {
        it.hasRoute<MineRoute>() || it.hasRoute<LibraryRoute>() || it.hasRoute<TodoRoute>()
    } == true
