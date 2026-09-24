# BetterLifeApp 设计系统与改造方案

配套阅读:`docs/DESIGN.md`(架构与推荐语义)。本文只谈**看得见、摸得着、动起来**的部分:色彩、字体、形状、间距、动效、组件、状态、无障碍、大屏适配。

**事实基线(核对于 2026-09-24)**

| 项 | 现状 / 目标 | 说明 |
|---|---|---|
| `androidx.compose.material3` 稳定版 | **1.4.0** | 基线 M3,不含任何 Expressive 组件 |
| `androidx.compose.material3` alpha | **1.5.0-alpha29**(2026-09-23) | Expressive 组件全在这条线上 |
| Expressive 的传递依赖 | **要求 `compileSdk 37`** | Compose 库已把 compileSdk 提到 37.1 |
| 本项目现状 | compileSdk **34** / AGP **8.5.2** / Kotlin **2.0.20** / BOM **2024.09.00** | 差 3 代 |

> **这是本文最重要的结论**:M3 Expressive 不是"换个主题包",它和"升级工具链"是同一件事。compileSdk 34 → 37、AGP 8.5.2 → 8.13+/9.x、Kotlin 2.0.20 → 2.2+,三者必须一起动。所以 §8 的 P0 阶段把工具链升级单独隔离,先做完再谈视觉。

---

## 1. 定位与设计原则

**Register = Product(仪器,不是营销物料)**。用户每天打开它打卡、翻条目。设计的价值来自**一致性、速度、一眼可读**,不是惊喜。

**这个产品真正的"物件"是什么**

不是"卡片列表",是**条目**。一条条目 = 一句建议 × 一组成本(钱/时间/毅力) × 一个收益档 × 一个证据等级 × 一个口径。

**产品全名叫「高性价比人生助手」,但现在的 UI 从没把"性价比"画出来过** —— 只有一排文字徽标。这是整个改造最大的机会点,也是 §3.3 和 §5.2 存在的理由。

**四条不可违背的产品规则(来自 `docs/DESIGN.md` §1,设计上必须体现)**

1. **口径之间不排序**。保命 / 守住钱 / 省精力 / 别踩线 是四个平行世界,视觉上必须可区分、不能混排。
2. **待核实不进推荐**,只在条目库出现并标注。
3. **争议可见但必须标争议**。
4. **不贩卖焦虑**。这是健康类 App 最容易犯的错:用红色 + 倒计时 + "你还有 X 天没打卡"来制造压力。本 App 的基调是"今天做一件小事就够了",完成态要比未完成态**更安静**,而不是更响亮。

---

## 2. 现状诊断(逐条对应文件)

| # | 问题 | 位置 | 后果 |
|---|---|---|---|
| D1 | **全 App 都是同款 Card**。今日页把问候、任务卡、口径分组、推荐卡全塞进 `LazyColumn`,除了分组标题外全是同一个 `surfaceVariant` 容器 | `TodayScreen.kt` 全文 | 眯眼测试失败:看不出哪 3 件事最重要。卡片在这里是"没做布局决策"的产物 |
| D2 | **完成态比未完成态更显眼**。未打卡=`surfaceVariant`,已打卡=`primaryContainer` | `TodayScreen.kt:170-173`、`TodoScreen.kt:118-121` | 视觉逻辑倒置。已完成的任务应该退到背景,不该抢注意力 |
| D3 | **口径没有任何视觉区分**。保命/守钱/省精力/别踩线 全是 `colorScheme.primary` 的小标题 | `TodayScreen.kt:152-156`、`EntryComponents.kt:22-28` | 产品的核心分类法在视觉上等于不存在 |
| D4 | **性价比只有文字**。`RatioBadge` 就是一个 `Surface` + 12sp 字 | `EntryComponents.kt:52-75` | 601 条建议里的排序依据,用户看不到 |
| D5 | **动态取色默认开**,品牌绿在绝大多数设备上直接消失 | `Theme.kt:81-84` | 换一台手机就不是同一个 App 了 |
| D6 | **形状不在 M3 尺度上**。`small=8 / medium=16 / large=24`,而 M3 尺度是 4/8/12/16/28 | `Theme.kt:70-74` | `medium=16` 让所有 Card 都比规范更圆,叠上嵌套卡片后显得"软塌塌" |
| D7 | **间距随手写**。同文件里 6/8/10/12/16dp 混用 | 全项目 | 没有节奏 |
| D8 | **中文字体的行高是拉丁字体的**。M3 默认 `bodyMedium = 14sp/20sp`(1.43) | `Theme.kt`(未自定义 Typography) | 中文在这个行高下会挤。中文需要比拉丁更松的 leading |
| D9 | **只有 loading 一个状态**。`TodayViewModel.UiState` 只有 `loading: Boolean` | `TodayViewModel.kt:33-36` | 没有 error / 空 / 离线 / 刷新中 / 全部完成 四种状态的容身之处 |
| D10 | **嵌套点击区域**。任务卡整体可点(进详情),里面又塞了一个 IconButton(打卡) | `TodayScreen.kt:166-229` | 拇指误触;想打卡却进了详情页 |
| D11 | **打卡零反馈**。只有一个 `scaleIn()` 的勾 | `TodayScreen.kt:210-217` | 这是全 App 最高频的动作,值得一次触觉 + 一次形变 |
| D12 | **"思考中…"什么都没说** | `ChatScreen.kt:136-147` | Loading copy 应该说明正在做什么 |
| D13 | **关于页是一整段免责声明** | `SettingsScreen.kt:236-244` | 长文在手机上是墙,不是内容 |

---

## 3. 设计 Token

新建 `app/src/main/java/com/betterlife/app/ui/theme/`,拆成 `Color.kt` / `Type.kt` / `Shape.kt` / `Spacing.kt` / `Motion.kt` / `Lens.kt`。

### 3.1 色彩

**品牌绿保留,但取消"动态取色默认开"。**

决策:设置页提供**三选一**(不是开关),默认"品牌绿"。

```
主题:  ○ 品牌绿 (默认)   ○ 跟随系统壁纸 (Material You)   ○ 深色优先
```

理由:D5。健康类 App 的品牌识别依赖固定色;Material You 是加分项,不该是默认。动态取色仍然保留,在 Android 12+ 作为选项提供,并且:**选 Material You 时,口径色(§3.2)不跟随壁纸**,否则四个语义色会被壁纸洗掉,失去区分度。

品牌色不改(#2E7D32 系),但补齐完整的 tonal palette(13 阶),现在的 `Color.kt` 是手工挑的十几对,缺 `surfaceContainer*` 系列 —— 那正是 M3 表达性组件(工具栏、分段列表)要用的色槽。

**需要补的色槽**(当前 `Theme.kt` 完全没有):

```
surfaceContainerLowest / surfaceContainerLow / surfaceContainer
surfaceContainerHigh / surfaceContainerHighest
surfaceDim / surfaceBright
inverseSurface / inverseOnSurface / inversePrimary
surfaceTint
outlineVariant
```

> 注意:`MaterialExpressiveTheme` 的新组件大量依赖 `surfaceContainer*`。缺这些色槽时,组件会落到默认的灰紫,和品牌绿打架。

### 3.2 口径色(Lens Colors)—— 本项目的自定义语义色

M3 只有 primary/secondary/tertiary/error 四个角色,不够表达四个平行口径。定义一个 M3 之外的扩展,用 `CompositionLocal` 下发:

```kotlin
@Immutable
data class LensColors(
    val life: Color,   // 保命   (死亡率)
    val money: Color,  // 守住钱 (金钱)
    val time: Color,   // 省精力 (时间)
    val line: Color,   // 别踩线 (自由)
)
val LocalLensColors = staticCompositionLocalOf { lightLensColors }
```

| 口径 | 色相 | 语义理由 |
|---|---|---|
| 保命(死亡率) | error 家族(暗红) | 唯一允许用危险的语义,因为它就是危险 |
| 守住钱(金钱) | 琥珀/暖橙(自建) | 钱的直觉色,且和红色拉开明度 |
| 省精力(时间) | 蓝(自建) | 冷静、非情绪化 |
| 别踩线(自由) | 紫(自建) | 法律/边界的中性色,不恐吓 |

**三条硬约束:**

1. **明度必须拉开**。保命最暗、守钱中、省精力最亮 —— 这样在红绿色盲(氘代)模拟下,保命和守钱不会糊成一团。上线前跑一次模拟。
2. **绝不只靠颜色**。每个口径必须同时带图标:保命=盾、守钱=¥、省精力=时钟、别踩线=警示三角。这是无障碍底线,也让色盲用户可用。
3. **对比度 ≥ 4.5:1**(文字) / 3:1(图形),对 `surface` 和 `surfaceContainer*` 两种底都验。

### 3.3 性价比可视化(新组件:`CostMeter`)

替换 `EntryComponents.kt` 里的 `RatioBadge` + `CostChips`。目标是用**一个 96×20dp 的图形**表达"这条建议花多少、赚多少"。

```
成本侧(左,3 格,满=2)              收益侧(右)
钱   时间  毅力                     影响程度
▮▮   ▮▯   ▮▯                       ▮▮▮  性价比 极高
多   中   些                        大
```

- 每格 4×8dp,圆角 2dp。3 格 × 3 项 = 成本分 `cs`(0~6),正好对应 `docs/DESIGN.md` §1 的算法。
- 成本格用 `outlineVariant`(低) / `onSurfaceVariant`(高) —— **不用颜色**,因为它是量,不是语义。
- 收益侧用口径色或 `primary`。
- `ratio` 档位用**形状**区分:极高=填充胶囊,高=描边胶囊,一般=纯文字。这样色盲用户也能分辨。
- 整个组件加 `Modifier.semantics { contentDescription = "成本 钱多 时间中 毅力些,收益 大,性价比 极高" }`。

这个组件是 D4 的解药,也是产品名第一次在界面上兑现。

### 3.4 字体(中文优先)

**不引入任何拉丁 webfont。** 中文正文必须走系统字体(Roboto → Noto Sans CJK 回退链),自备字体只会导致汉字回退到系统、中西文行高打架。

只定义 5 个用得到的档位,其余继承默认:

| 用法 | 档位 | 现状 → 目标 |
|---|---|---|
| 屏幕标题 | `headlineSmall` | 24/32 → **24/34** |
| 区块标题 | `titleMedium` | 16/24 → **16/26** |
| 卡片标题 | `titleSmall` | 14/20 → **15/24** |
| 正文 | `bodyMedium` | 14/20 → **15/26** |
| 次要说明 | `bodySmall` | 12/16 → **13/20** |
| 徽标/标签 | `labelSmall` | 11/16 → **12/17** |

核心是**行高**:中文方块字没有拉丁的 x-height 与升降部,同样 leading 下视觉密度高得多。全局行高按 **1.6~1.75** 给,而不是 M3 默认的 1.43。

另外:
- 连续天数等数字用 **tabular figures**(`FontFeatureSettings = "tnum"`),否则天数跳动时数字会横移。
- `SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)`(`TodayScreen.kt:191`)改为跟随系统 locale,日期格式交给 `DateTimeFormatter.ofLocalizedDate`,不要硬编码中文。
- 支持系统字体缩放 **至 200%**(§7.4)。

### 3.5 形状

回到 M3 尺度,并让**形状承担层级**:

```
extraSmall = 4dp    // 列表行、徽标
small      = 8dp    // 输入框、小按钮
medium     = 12dp   // 次级卡片
large      = 16dp   // 主卡片(今日任务)
extraLarge = 28dp   // 底部弹层、FloatingToolbar
```

现状 `medium=16` 全部卡片更圆 —— 改到 12 后,主卡片显式指定 `large`。**层级来自对比**:主卡片 16 / 次级 12 / 列表 4,而不是所有东西都一样圆。

### 3.6 间距

建立唯一阶梯,**禁止字面量**:

```
space1 =  4dp   // 图标与文字、徽标内边距
space2 =  8dp   // 徽标之间
space3 = 12dp   // 卡片内边距
space4 = 16dp   // 屏幕边距、卡片之间
space6 = 24dp   // 区块之间
space8 = 32dp   // 大步留白
space12= 48dp   // 空状态、页面底部避让 FAB
```

用一个 `object Spacing` 暴露,配合 `LocalSpacing` 也可,但当前规模下 object 常量足够。D7 的解药。

### 3.7 动效

见 §6。

---

## 4. M3 Expressive 采纳策略

### 4.1 稳定度分级(2026-09-24)

按 AndroidX release notes 实际毕业时间排列:

**已在 1.5.0 alpha 线内毕业(不再需要 `@OptIn`)**

| 组件 / API | 毕业版本 |
|---|---|
| `MotionScheme` | alpha15 (2026-02-25) |
| `materialExpressTheme` / `expressiveLightColorScheme` | alpha18 (2026-04-22) |
| `WavyProgressIndicator` | alpha18 |
| `ToggleButton` / `FilledTonalToggleButton` / `OutlinedToggleButton` | alpha19 (2026-05-06) |
| 表达性 Menu / FAB Menu | alpha19 |
| `SplitButton` | alpha20 (2026-05-19) |
| `ButtonGroup` / `FloatingToolbar` | alpha22 (2026-06-17) |
| 表达性 `TopAppBar` / `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` / `FlexibleBottomAppBar` | alpha23 (2026-07-01) |
| 表达性 ListItem(含非交互变体) | alpha23 |
| `BottomAppBar` | alpha26 (2026-08-12) |

**仍是实验性,不要押注**

| 组件 | 状态 |
|---|---|
| `MaterialShapes` + 形变 | alpha19 的毕业被**回滚** |
| `LoadingIndicator` / `ContainedLoadingIndicator` | alpha19 毕业同样被**回滚** |
| 表达性 `TimePicker` | alpha23 新增,alpha27/28 还在调设计 |
| `Scrim`、独立 `StaticSheet` | 仍实验 |

> ⚠️ **命名坑**:release notes 里写的是 `materialExpressTheme`,而 API reference / KMP 文档写的是 `MaterialExpressiveTheme`。**锁定 alpha 后以你实际依赖版本的 API reference 为准**,不要照抄本文。
>
> ⚠️ **暗色坑**:截至 alpha 线,`expressiveLightColorScheme()` 已稳定,但**未见对应的 `expressiveDarkColorScheme()`**。暗色需确认当前 alpha 是否提供,否则仍用 `darkColorScheme()`。

### 4.2 采纳矩阵

| 组件 | 用在哪 | 现在动吗 |
|---|---|---|
| `MaterialExpressiveTheme` + `MotionScheme.expressive()` | App 根 | ✅ P1 |
| `ButtonGroup` + `ToggleButton` | 档案多选、口径切换 | ✅ P2 |
| `SplitButton` | 今日任务打卡(打卡 + 换一条) | ✅ P2 |
| `FloatingToolbar` | 条目详情底部动作 | ✅ P2 |
| `WavyProgressIndicator` | 引导页进度 | ✅ P2 |
| 表达性 ListItem | 设置页、条目库目录 | ✅ P2 |
| 表达性 `TopAppBar` | 滚动折叠的标题 | ✅ P3 |
| `NavigationSuiteScaffold` + `ShortNavigationBar` / `WideNavigationRail` | 底部导航(自适应) | ✅ P3 |
| `PullToRefreshBox` | 今日页 | ✅ P3 |
| Horizontal Carousel(多浏览) | 条目库精选 | ⏸ 观察 |
| `LoadingIndicator` | AI 思考中 | ⏸ 等稳定,先用 `WavyProgressIndicator` 替代 |
| `MaterialShapes` 形变 | 打卡成功 | ⏸ 等稳定,先用 `AnimatedContent` + scale |
| 表达性 `TimePicker` | 提醒时间 | ⏸ 等稳定 |

**原则:能进 P2 的都是已毕业 API。实验性的一律推迟,并在代码里留 TODO 注释标注"待 X 组件毕业"。**

---

## 5. 逐屏改造方案

每屏写清:**工作模式 → 现状问题 → 改造动作 → 验收**。

### 5.1 今日页(`TodayScreen.kt`,292 行)

**模式:Monitor + Operate。** 用户来这里看"今天做什么",然后打卡走人。停留时间应该 < 30 秒。

**问题**:D1(全同款卡片)、D2(完成态更响)、D3(口径无色)、D4、D10(嵌套点击)、D11(零反馈)。

**改造**:

1. **建立三级视觉层级**,拆掉统一的 Card 外壳:
   - **问候区**:纯文字,无容器。问候语 + 日期 + 右侧一个 `StreakPill`(见下)。
   - **今日任务**:唯一使用 Card 的地方,`large` 形状(16dp),`surfaceContainerLow` 底。一天最多 3 条,值得占视觉重量。
   - **为你推荐**:不用 Card,改用**分段列表**(`ListItem`),用 `outlineVariant` 分隔线。601 条里的 5 条不该长成和"今天必须做的事"一样的形状。
2. **修正完成态**(D2):未完成 → `surfaceContainerLow`;已完成 → `surfaceContainerLowest` + 文字 `onSurfaceVariant` + 删除线。**让完成退到背景。**
3. **打卡改成 `SplitButton`**(D10/D11):
   - 主按钮 = 打卡,点击 → 弹簧形变 + `HapticFeedbackType.ToggleOn` + 卡片在 300ms 后下沉到列表底部。
   - 附属下拉 = "换一条 / 今天不做"。
   - **整卡不再可点**。进详情改由标题文字单独承担(加下划线提示),消除误触。
4. **口径分组标题带色 + 图标**(D3):`lensGroupTitle()` 之外,加 `lensColors` 圆点和图标。
5. **补 4 个缺失状态**(D9):
   - **空**:没有档案 → "填 3 个问题,推荐立刻变准" + 一个按钮。
   - **全部完成**:不是空状态,是一个**安静的庆祝** —— "今天的 3 件小事都做完了",配一次形变,不弹窗、不放彩带。
   - **错误**:资产/DB 读取失败 → "内容加载失败" + 重试,不再崩(配合架构侧加 try/catch)。
   - **刷新中**:`PullToRefreshBox`(P3)。
6. **`StreakPill`**:连续天数从纯文本升级为一个胶囊组件(数字用 tabular figures),`space4` 内边距,连续 ≥ 7 天时换成口径色填充。

**验收**:眯眼测试能立刻分辨"今天要做的事"和"可以看看的事";连续打卡 3 天后主按钮位置不位移;TalkBack 能读出"已完成 2 项,共 3 项"。

### 5.2 条目详情页(`EntryDetailScreen.kt`,262 行)

**模式:Learn。** 这是全 App 内容密度最高的页面,也是用户做决定的地方。**六栏成本/收益/证据/来源/备注**是产品的核心物件。

**问题**:D4、D12 之外,还有:六栏全部用同一个 `DetailSection` 折叠卡,没有主次;底部一个孤零零的 `Button`;以及架构侧那个"每次进详情重新解析 601 条 JSON"的性能问题。

**改造**:

1. **把 `CostMeter`(§3.3)放在最顶部**,替代现在第一屏只有徽标行 + 标题。用户进来第一眼就该看到"这条花多少赚多少"。
2. **六栏分级**,不再是六个一样的折叠卡:
   - **首屏必显**:"说人话" + `CostMeter` + `gain`。这是决策信息。
   - **次屏**:证据等级(带 A/B/C 的解释,现在是内联 `when` 映射)、成本明细。
   - **折叠**:来源、备注。默认收起。
   - 用**间距和字号**分层,而不是六张一模一样的卡。
3. **底部动作换成 `FloatingToolbar`**,把"加入待办"从底部按钮升级为悬浮工具栏:加入待办 / 收藏 / 复制文本 / 分享。工具栏自带 `WindowInsets` 处理。
4. **争议/待核实横幅保留**,但文案按 §9 改(现在把两条独立信息用 `append` 拼成一段)。
5. **修性能**(已在 P1 顺手完成):详情页已改用 `AppContainer` 的单例 `EntryRepository`,不再是 `remember { EntryRepository(...) }` 每次进详情重解析 601 条 JSON(见 §8 P2 执行记录)。

**验收**:进入详情页无白屏;首屏无需滚动即可看到成本/收益/性价比;工具栏在深色与浅色下均不遮挡内容。

### 5.3 条目库(`LibraryScreen.kt` 123 行 + `SectionScreen.kt` 99 行)

**模式:Explore + Compare。** 601 条,33 章。用户要么搜,要么按章翻。

**问题**:目录是 33 行同质的 `ListItem` + 分隔线;搜索是普通 `OutlinedTextField`;章内列表和搜索列表长得一模一样。这是**典型的 list-detail 场景**却只做了 list。

**改造**:

1. **搜索升级为 `SearchBar`(docked 变体)**:输入时展开为全屏结果,空查询时收起。`SearchBar` 的相关 API 在 alpha24 已稳定。
2. **目录页加"章"的视觉重量**:不要 33 行一样的行。按**口径**给章节分组着色(第 2 章 = 保命,第 6 章 = 守钱…具体映射要对着 `entries.json` 的 `lens` 字段定),并在每行右侧放一个极简的条数占比条。
3. **章内列表用表达性 ListItem 的分段变体**,并在顶部加一个**排序切换**:`ButtonGroup` + `ToggleButton`(按性价比 / 按证据等级 / 按原书顺序)。这一条直接兑现 D4:让用户能按"性价比"这个产品主张来浏览。
4. **大屏(≥ 600dp)改成 list-detail 双栏**(§7.3):左目录右详情,符合 M3 大屏质量规范。

**验收**:600dp 宽度下旋转设备不丢选中项;搜索 3 个字符内出结果;排序切换有明确选中态且不依赖颜色。

### 5.4 待办页(`TodoScreen.kt`,145 行)

**模式:Operate。**

**问题**:`it.task.type == "DAILY"` 用字符串字面量过滤(`TodoScreen.kt:54-55`);"每日习惯"空时写"今天的都做完了"——**但没任务 ≠ 都做完了**;删除无撤销。

**改造**:

1. 改用 `TaskEntity.TYPE_DAILY` 常量;过滤结果 `remember` 住并在 VM 里算,不要每次重组重算。
2. **修文案逻辑**:区分"今天没有安排习惯"(引导去今日页)和"今天的都完成了"(庆祝态)。
3. **删除改为可撤销**:Material 的规则是"撤销优于确认"。删除后给 Snackbar + 撤销,不要弹确认框。
4. **每日习惯与一次性待办用视觉区分**:每日习惯用口径色 + 圆形进度环(今天完成 2/3),一次性待办用 `outlineVariant` 的方框勾选。两类东西语义完全不同,不该长得一样。

**验收**:删除任意一条可在 5 秒内撤销;TalkBack 读出"每日习惯,2 项,已完成 1 项"。

### 5.5 引导页(`OnboardingScreen.kt`,326 行)

**模式:Configure。** 4 步向导,这是唯一有"流失率"风险的页面。

**问题**:进度用 `LinearProgressIndicator`;`HorizontalPager` 配 `userScrollEnabled = false` 但没给任何手势线索;底部动作区在"上一步/跳到目标/下一步"三态之间跳,布局会抖;选项是裸 `FilterChip` 流式排布,没有分组节奏。

**改造**:

1. **进度换 `WavyProgressIndicator`**(已毕业),并把"第 2 步,共 4 步"从 `bodySmall` 提到 `labelLarge`。
2. **允许滑动翻页** + 保留按钮。`userScrollEnabled = false` 让用户以为卡住了。
3. **固定底部动作栏高度**,三种状态用同一套 `Row` 布局,按钮位置不跳。第 4 步的主按钮用 full-width。
4. **多选换成 `ToggleButton` + `ButtonGroup`**(已毕业):`ToggleButton` 的选中态自带形状变化(圆→方),比 `FilterChip` 的纯颜色变化更适合"选中/未选中"这种二元状态。
5. **加"为什么问这个"的轻量说明**。现在只有目标那一步有 `bodySmall` 解释。健康类问卷问吸烟、饮酒、慢性病,用户会警惕 —— 每个敏感步骤给一行说明,降低放弃率。
6. **可跳过的地方标出来**。

**验收**:200% 字体缩放下底部动作栏不遮挡内容;每一步都能看到"这是第几/共几步";错误选择后能退回。

### 5.6 AI 问答(`ChatScreen.kt`,199 行)

**模式:Explore + Decide。**

**问题**:D12("思考中…");首条安全提示用一个和普通气泡一样的 `Bubble` 渲染,视觉上无法区分"系统常量"和"模型回答";无 Key 横幅用 `tertiaryContainer`,和口径色里的"别踩线"撞色。

**改造**:

1. **思考态换 `LoadingIndicator` 的形状语言**:等它毕业前,用 `WavyProgressIndicator` + 文案"正在检索 601 条建议并请教模型"。Loading copy 要说清在做什么。
2. **安全提示与模型回答分离**:安全提示固定为页面顶部的固定 `Surface`(不随滚动),而不是列表里第一个气泡。
3. **无 Key 横幅改用 `secondaryContainer`**,避免和口径色混淆。
4. **气泡形状**:用户侧 `large`+右对齐,助手侧 `extraSmall`+左对齐,形成方向感。错误态单独用 `errorContainer` + 图标(现在只有颜色差别)。
5. **补空状态**:首次进入且无 Key 时,给 3 个示例问题做成可点的 `SuggestionChip`("我抽烟,先看哪几条?")。

**验收**:发送后 200ms 内出现反馈;信号差时能看到明确的失败恢复路径(重试按钮),不是干等。

### 5.7 设置页(`SettingsScreen.kt`,270 行)

**模式:Configure。**

**问题**:D13(关于页长文);`emissions >= 2` 的状态 hack;`baseUrl/apiKey/model` 用普通 `remember`,旋转丢输入;所有分组用 `HorizontalDivider` 平铺。

**改造**:

1. **`emissions` hack 换成 UiState 带 `loading` 标志**。这是架构修复,但直接决定视觉:有 loading 标志才能在加载时显示骨架屏,而不是"先显示空再突然填上"。
2. **输入框改 `rememberSaveable`**,旋转不丢。
3. **分组改用表达性分段 ListItem**,替掉 `ListItem` + `HorizontalDivider` 的组合。
4. **关于页拆分**:现在是一整段免责声明。拆成"内容出处"(带可点链接)+"免责声明"(折叠,默认收起,可展开)。长文默认展开是 D13 的根源。
5. **API Key 输入框**:加"显示/隐藏"切换(现在是强制 `PasswordVisualTransformation`,用户无法核对粘贴的内容),加"测试连接"按钮 —— 现在保存后完全不知道有没有效。
6. **提醒时间**用表达性 `TimePicker`(等毕业),现在先保留 `AlertDialog` 但补 `HapticFeedback`。

**验收**:旋转设备后 API Key 输入不丢;关于页免责声明默认收起;保存后 3 秒内有明确结果反馈。

### 5.8 我的页(`MineScreen.kt`,127 行)

**模式:Configure(入口页)。**

**问题**:`goalLabel()` / `profileSummary()` 的领域→文案映射写在 UI 文件里(`MineScreen.kt:40-62`);档案摘要卡片用 `surfaceVariant`,和今日页的任务卡撞色。

**改造**:

1. 映射函数下沉到 `data/ProfileRepository.kt`(或新建 `ProfileLabels.kt`),UI 只消费。
2. 档案摘要改用 `primaryContainer`(这是用户的个人化信息,值得一点强调),和今日任务卡区分。
3. **目标 chips 换成 `ToggleButton`**,状态更清楚;已经选过的目标应该能在这里直接取消,而不是必须进编辑页。
4. 三个入口 `ListItem` 改成表达性分段列表 —— 这正好是它设计出来的场景。

**验收**:档案摘要与今日任务卡在深色/浅色下都不混淆;目标可直接在摘要卡上增删。

### 5.9 导航壳(`AppNav.kt`,188 行)

**问题**:固定 `NavigationBar`;`startRoute` 用 `produceState` 在 Composable 里直接读 DataStore;`navigateTab` 硬编码 `popUpTo(Routes.TODAY)` 作为锚点。

**改造**:

1. **换 `NavigationSuiteScaffold`**:compact → `ShortNavigationBar`,medium/横屏 → `WideNavigationRail`,这样 5.7 的大屏适配免费获得。
2. **起始路由的状态移到 ViewModel**,`AppNav` 只消费 UiState。
3. **路由改类型安全**(Kotlin Serialization 路由,依赖已引入)。`entry/{entryId}` 这种字符串路由在 6 个文件里手写,重构时必错。
4. **启用预测性返回**:Manifest 加 `android:enableOnBackInvokedCallback="true"`,详情页/聊天页接 `PredictiveBackHandler`。这是 2026 年 Android App 的基本预期。

**验收**:横屏和折叠屏下导航自动变为 rail;预测性返回动画在条目详情页可见。

---

## 6. 动效系统

### 6.1 根配置

```kotlin
MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = MotionScheme.expressive(),   // 可换成 standard() 做对比
    shapes = AppShapes,
    typography = AppTypography,
) { ... }
```

组件从 `MaterialTheme.motionScheme` 取 `defaultSpatialSpec()` / `fastSpatialSpec()` / effects spec,**不要手写 `tween(300)`**。这样全局动效保持一致,且能一处切换到 `standard()`。

### 6.2 本 App 的动效清单

| 场景 | 动效 | 强度 |
|---|---|---|
| **打卡** | 勾图标 scale 0.9→1.15→1.0 弹簧 + 触觉 + 卡片 300ms 后下沉重排 | 高 |
| 卡片下沉 | `animateItem()` 位置 + 淡出到 `surfaceContainerLowest` | 中 |
| **全部完成** | 三张卡依次收起 → 一句"今天的 3 件小事都做完了"淡入 + 一次形变 | 高,但只此一次 |
| 口径分组展开 | 高度弹簧(`AnimatedVisibility` + expand) | 中 |
| 聊天气泡入场 | 3 拍:scale 0.95 → 1.02 → 1.0,带 opacity | 中 |
| FAB → 对话页 | container transform(共享元素) | 中 |
| 盖章/工具条出现 | 从锚点边缘滑入 | 中 |
| 列表项点击 | ripple(系统自带,M3 有 sparkle) | 低 |
| 排序切换 | 列表 cross-fade + 位移动画 | 中 |

**节奏**:入场 250ms,出场 ~70%(175ms)。**不要用 bounce/elastic** —— 有阻尼的弹簧,不是弹跳球。

### 6.3 降级与无障碍

- 尊重系统"移除动画"设置(`Settings.Global.ANIMATOR_DURATION_SCALE == 0`)→ 全部降为瞬时。
- 设置页提供三档:**标准 / 减弱(只保留 100ms 淡入)/ 关闭**。健康类 App 里,前庭功能敏感的用户比想象中多。
- **触觉反馈不能是唯一反馈**。打卡的成功必须同时有:颜色 + 图标形变 + 文案变化,触觉只是加强。

---

## 7. 状态、无障碍、大屏

### 7.1 九状态矩阵

每个交互组件都要设计全 9 态。当前项目只有 1 态。

| 组件 | 缺失的状态 |
|---|---|
| 今日任务卡 | hover(平板/鼠标)、loading(骨架屏)、error、disabled |
| 推荐列表 | **loading、error、空、离线** |
| 搜索 | **加载中、无结果(已有)、错误、历史记录** |
| 聊天 | **空(首屏引导)、超时、限流(429)、未配 Key(已有)** |
| 设置保存 | **成功、失败、进行中** |
| 提醒开关 | **权限被拒、权限被系统收回** |
| 条目详情 | loading(已有但文案混淆)、**不存在(404 语义)** |
| 引导页 | **保存中、保存失败** |
| 全局 | **离线** |

`TodayViewModel.UiState` 应从 `loading: Boolean` 改为:

```kotlin
sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object Empty : TodayUiState          // 没档案
    data class Ready(val items: List<TaskItem>, val allDone: Boolean) : TodayUiState
    data class Error(val message: String, val retry: () -> Unit) : TodayUiState
}
```

这不只是设计问题 —— 它是 §2 的 D9 和上一轮审查里"资产读取无 try/catch"的共同解药。

### 7.2 无障碍清单

- **触控目标 ≥ 48×48dp**。`IconButton` 默认满足;`CostMeter`、口径色圆点等自绘组件必须显式加 `minimumInteractiveComponentSize` 或 `sizeIn`。
- **绝不只用颜色传达状态**(echo §3.2)。性价比档位用形状区分,口径用图标区分,完成态用删除线。
- **`CostMeter` 必须有语义标签**,因为它是纯图形。
- **字体缩放 200% 不破版**。禁止给文字容器写死高度;`OnboardingScreen` 底部动作栏和 `SettingsScreen` 的 ListItem 是首批要测的。
- **TalkBack 走一遍全流程**:归档 → 打卡 → 待办 → 搜索 → 详情 → 聊天。当前 `TodoRow` 的 `Checkbox` 无标签,`CostChips` 全是 `onClick = {}` 的假 chip。
- **焦点顺序**:`Scaffold` 的键盘顺序在 alpha23 才修,升级后要复测。
- 抽取 `strings.xml`(§8 P1),否则无障碍标签无法本地化维护。

### 7.3 响应式与大屏

**窗口宽度断点**(用 `material3-adaptive` 或 `material3-window-size-class`):

| 宽度 | 导航 | 条目库 | 今日 |
|---|---|---|---|
| < 600dp | `ShortNavigationBar` | 单栏 | 单列 |
| 600–840dp | `WideNavigationRail` | **list-detail 双栏** | 单列 + 右侧推荐侧栏 |
| ≥ 840dp | `WideNavigationRail` | list-detail + 三栏 | 双栏 |

**折叠屏**:`LibraryScreen` → `SectionScreen` → `EntryDetailScreen` 是教科书式的 list-detail 链,用 `NavigableListDetailPaneScaffold` 一次解决大屏和折叠屏。这不是可选的加分项 —— 大屏 App 质量规范里展开态不利用空间是明确的扣分项。

**不要"大屏删功能"**。所有功能在所有尺寸都可达。

---

## 8. 分阶段实施计划

### P0 · 工具链与资源地基(无视觉变化)

**阻塞项,必须先做。**

1. `res/` 目录(当前**完全不存在**):`values/{strings,themes,colors}.xml`、`values-night/`、`drawable/ic_notification.xml`(替掉 `android.R.drawable.ic_popup_reminder`)、自适应图标 + 单色图标、`xml/{backup_rules,data_extraction_rules,network_security_config}.xml`。
2. `compileSdk 34 → 37`、`targetSdk → 36`、AGP `8.5.2 → 8.13+`、Kotlin `2.0.20 → 2.2+`、Gradle wrapper 同步、Compose BOM 更新。
3. 建立 `gradle/libs.versions.toml`;`kotlinOptions` → `compilerOptions`。
4. 加 `androidx.lifecycle:lifecycle-runtime-compose` → 删掉 `ui/util/StateFlowExt.kt`。
5. 加 `androidx.compose.material3:material3:1.5.0-alpha29`(覆盖 BOM 的 1.4.0)、`material3-adaptive`、`androidx.graphics:graphics-shapes`、`material-icons-extended`。
6. `themes.xml` 配 `Theme.Material3.DayNight.NoActionBar` 等价物 + SplashScreen API,修掉深色冷启动白屏。
7. 开 R8 + 签名配置 + `lint {}` 块。

**验收**:`./gradlew assembleRelease` 产出**已签名**包;深色模式冷启动无白屏;通知图标在深色通知栏可见;24 个单测全绿。**此阶段不改任何一屏的视觉。**

**回滚**:P0 全部在独立分支,风险点是 AGP/Kotlin 升级。若 37 不可行,退到 `compileSdk 36` + `material3 1.4.0`,P1 只做 §3 的 token(不换主题容器),Expressive 组件推迟到 P2。

### P0 执行记录(2026-09-24 已完成)

**实际版本矩阵**(与上文预判的差异已标注)

| 项 | 上文预判 | 实际采用 |
|---|---|---|
| AGP | 8.13+ | **9.4.1** |
| Kotlin | 2.2+ | **2.4.20** |
| Gradle | 同步 | **9.7.1** |
| KSP | — | **2.3.12**(KSP 已脱离 Kotlin 版本号,改为独立 semver) |
| compileSdk / targetSdk | 37 / 36 | **37 / 36** |
| Compose BOM | 更新 | **2026.09.00** |
| material3 | 1.5.0-alpha29 | **1.5.0-alpha28**(本机缓存中已有,已验证) |
| material-icons | 要加 `-extended` | **只显式钉住 `material-icons-core:1.7.8`**,不加 extended |

> `material3-adaptive`、`graphics-shapes`、`material-icons-extended` 都**只声明在 catalog 里,没有进 `dependencies`** —— P0 不使用它们,引入未使用的依赖是负债。P2/P3 真正用到时再启用对应 catalog 条目即可。

**AGP 9 的破坏性变更(上文没预料到,踩了才知道)**

1. **AGP 9 内置 Kotlin 支持**:`org.jetbrains.kotlin.android` 插件必须删除,否则构建直接失败。同时 `jvmTarget` 从 `compileOptions.targetCompatibility` 自动推导,`kotlin { compilerOptions {} }` 块不再需要。
2. **`android.proguard.failOnMissingFiles` 默认 true**:`proguard-rules.pro` 必须真实存在,文件名写错即构建失败。
3. **`android.r8.strictFullModeForKeepRules` 默认 true**:`-keep class A` 不再隐含保留 `<init>()`,keep 规则要写全。
4. **`kotlinOptions {}` 已移除**,迁移到 `kotlin { compilerOptions {} }`(本项目改用隐式推导,无需该块)。
5. `android.useAndroidX`、`android.nonTransitiveRClass` 已是默认行为,属性已从 `gradle.properties` 移除。

**material3 1.3 → 1.5.0-alpha28 的 API 变更(已在本仓库修好)**

| 变更 | 影响位置 |
|---|---|
| `MenuAnchorType` → **`ExposedDropdownMenuAnchorType`**,且 `menuAnchor()` 的 `type` 变为必填 | `SettingsScreen.kt` |
| `ExposedDropdownMenu` 从 `ExposedDropdownMenuBoxScope` 的成员改为**扩展函数**,需单独 import | `SettingsScreen.kt` |
| `ListItem(headlineContent = …)` 已废弃,新重载把 headline 改为尾随 `content` lambda | 9 处(见"遗留项") |

**其他环境事实**

- **SDK platform 命名**:Android SDK Platform 17 的目录名是 `android-37.0`(major.minor 方案),`source.properties` 里 `AndroidVersion.ApiLevel=37.0`。`compileSdk = 37` 能直接解析它。
- **本机有两个 SDK**:`F:/android-sdk/android-sdk`(只有 android-34)与 `F:/SDK/android`(有 android-37.0 + build-tools 36.0.0)。`local.properties` 已改指后者。
- **`local.properties` 里的冒号必须转义**:写 `F\:/SDK/android`。不转义 lint 会报 `PropertyEscape` 错误并中断构建(该错误在改造前就存在,只是以前从未跑过 lint)。
- **Room 2.8 新增可空类型检查**:`doneDates()` 原本声明 `List<String?>`,新版 Room 会警告"可空类型参数无意义" → 已改为 `List<String>` 并把 SQL 补上 `date IS NOT NULL`。

**验证结果**

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | 24/24 通过 |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过(R8 minify + shrinkResources + 签名),APK **2.9 MB** |
| `lint` | **0 error**,11 warning |
| release APK 实测 | `compileSdkVersion=37`、`minSdkVersion=26`、`targetSdkVersion=36`;`mipmap/ic_launcher`、`mipmap/ic_launcher_round`、`drawable/ic_notification`、全部字符串、`fullBackupContent`、`networkSecurityConfig`、`dataExtractionRules` 均保留 |

11 个 warning 全部为:依赖存在更新版本(7 条)、`OldTargetApi`(targetSdk 36 非最新,刻意为之)、以及已修掉的 `ObsoleteSdkInt`。

**P1 可直接使用的 API 真名**(已在 `material3:1.5.0-alpha28` 的 AAR 中逐一核实)

| 用途 | 真实 API |
|---|---|
| Expressive 主题容器 | **`MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)`** —— 注意不是 release notes 里写的 `materialExpressTheme` |
| 亮色配色 | `expressiveLightColorScheme()` |
| 暗色配色 | **不存在 `expressiveDarkColorScheme()`** —— 暗色仍用 `darkColorScheme()` 或自定义 |
| 动效方案 | `MotionScheme` 接口 + `MotionScheme.expressive()` / `.standard()`;取 `defaultSpatialSpec()` / `fastSpatialSpec()` / `slowSpatialSpec()` / `defaultEffectsSpec()` / `fastEffectsSpec()` / `slowEffectsSpec()` |
| 是否已启用 Expressive 主题 | `LocalUsingExpressiveTheme` |
| 已存在的新组件 | `ButtonGroup`、`ToggleButton`、`SplitButton`、`FloatingToolbar`、`LoadingIndicator`、`WavyProgressIndicator`、`MaterialShapes` |

**遗留项(明确交给后续阶段)**

1. **9 处 `ListItem(headlineContent = …)` 弃用警告未迁移** —— 迁移到新重载就是 P2 的"表达性 ListItem",现在迁移会改变视觉,与 P0"不改任何一屏视觉"冲突,故保留。
2. **依赖可升版本**:material3 alpha29、okhttp 5.5.0、kotlinx-serialization 1.11.0、core-ktx 1.19.1、navigation 2.10.2、datastore 1.2.1、work 2.12.0。当前组合是"已验证通过"的一致集合,升级应作为独立可回滚的一步,不要混在 P1 里。
3. **正式签名密钥库尚未创建**,release 目前回退到 debug 签名(见 README「发布构建与签名」)。
4. **未加 androidTest 依赖**,留给 P3 的截图测试。
5. **未显式设置 `android:enableOnBackInvokedCallback`**,留给 P3 一并验证预测性返回。
6. 通知此前**没有 `contentIntent`**(点了没反应),P0 已补上 `PendingIntent`(带 `FLAG_IMMUTABLE`)。


### P1 · 设计系统骨架

1. `ui/theme/` 拆成 `Color/Type/Shape/Spacing/Lens/Motion.kt`。
2. 补齐 §3.1 缺失的 `surfaceContainer*` 等色槽。
3. 建立 §3.2 `LensColors` + `LocalLensColors`。
4. 建立 §3.4 中文行高的 `Typography`。
5. 形状回到 M3 尺度(§3.5)。
6. 间距阶梯(§3.6),逐步替换字面量。
7. `BetterLifeTheme` → `MaterialExpressiveTheme` + `MotionScheme.expressive()`。
8. 主题三选一设置 + 持久化。
9. `strings.xml` 抽取全部硬编码文案。
10. 实现 `CostMeter` 组件 + 单测 + 截图测试。

**验收**:主题三选一切换即时生效;`CostChip` 被 `CostMeter` 替换后,条目详情和推荐卡都能看到成本图形;**全项目 0 处硬编码中文字面量**(用自定义 lint 规则卡住)。

### P1 执行记录(2026-09-24 已完成)

| 项 | 结果 |
|---|---|
| 1 `ui/theme/` 拆分 | 新增 `Color/Type/Shape/Spacing/Lens/Motion.kt`,`Theme.kt` 只做装配 |
| 2 色槽补齐 | 品牌绿完整 tonal palette,补 `surfaceContainer*` / `surfaceDim` / `surfaceBright` / `inverse*` / `outlineVariant` / `scrim` |
| 3 `LensColors` | `LensColors` + `LocalLensColors` + `lensIcon`(盾/¥/时钟/警示三角);口径色刻意不跟随壁纸 |
| 4 中文排版 | 6 个档位,行高给到 1.6~1.75;日期改 `DateTimeFormatter.ofLocalizedDate`(跟随系统 locale) |
| 5 形状 | 回到 M3 尺度 4/8/12/16/28,让形状承担层级 |
| 6 间距 | `Spacing` 单一阶梯,各屏的字面量已替换 |
| 7 主题容器 | `MaterialExpressiveTheme` + `MotionScheme.expressive()`(alpha28 已毕业,**无需 `@OptIn`**) |
| 8 主题三选一 | 品牌绿/跟随壁纸/深色优先,DataStore 持久化,`MainActivity` 订阅后切换即时生效 |
| 9 `strings.xml` | 全部 UI 文案已抽取;新增 `data/EntryKeys` 把 entries.json 的机器可读 key 抽成常量 |
| 10 `CostMeter` | 组件 + `CostMeterModel` 纯逻辑 + 5 例单测;条目详情与今日推荐卡已使用,`CostChips` 已删除 |

**实际做法与本文的偏差 / 留待后续**

1. **「自定义 lint 规则」改用 JVM 单测 `UiNoChineseLiteralTest` 等价实现** —— 不新增 lint-api 模块、不与 AGP 版本耦合,直接跑在现有 `testDebugUnitTest` 里,违规即失败。只扫 `com/betterlife/app/ui/`;`ai`(提示词)/`recommend`/`data` 里的中文不是 UI 文案。
2. **间距字面量** —— 界面级已全部走 `Spacing`;**组件内部几何**(徽标内边距、`CostMeter` 的格子尺寸与 2dp 间隙)保留字面量,因为它们是组件尺寸而非「间距」,未纳入 token。
3. **截图测试** —— 按本文 §8 属 P3(需 androidTest 依赖),P1 只落单测。
4. **P0 遗留的 9 处 `ListItem(headlineContent=)` 弃用未迁移** —— 迁移会改视觉,属 P2「表达性 ListItem」。
5. `material-icons-extended` 已启用(P1-1 引入),用于口径图标;R8 会在 release 里裁掉未用到的图标。

### P2 · 组件替换(按屏拆 PR)

顺序按"用户价值 / 改动风险"排序:

1. **今日页**(§5.1)—— 价值最高、风险最高。
2. **条目详情**(§5.2)+ 顺手修 DI 与重复解析。
3. **条目库 + 章节页**(§5.3)—— 引入 `ButtonGroup` 排序切换。
4. **待办页**(§5.4)。
5. **引导页**(§5.5)—— 引入 `ToggleButton`。
6. **设置页 + 我的页**(§5.7 / §5.8)—— 引入表达性 ListItem。
7. **聊天页**(§5.6)。

每屏一个 PR,独立可回滚。**每屏完成后跑一次截图测试基线。**

**验收**:每屏 PR 附 4 张截图(浅色/深色 × 100%/200% 字体);§7.1 中该屏的缺失状态全部补齐。

### P2 执行记录(2026-09-24 已完成)

按屏拆 7 个提交,顺序与上文一致,每屏独立可回滚。

| 屏 | 结果 |
|---|---|
| 今日页 | 拆掉统一 Card 外壳:问候区回到纯文字 + `StreakPill`(等宽数字,连续 7 天转强调色),今日任务独占 Card(`large` + `surfaceContainerLow`),推荐改分段 `ListItem` + `outlineVariant` 分隔线、口径分组标题带图标与口径色;完成态退到 `surfaceContainerLowest` + 删除线;打卡换 `SplitButton`(打卡 + 换一条/今天不做),整卡不再可点,进详情由标题下划线承担;`UiState` 增加 `Empty`(还没档案)与 `Ready` 区分 |
| 条目详情 | `CostMeter` 提到首屏,与徽标、标题、收益组成决策区;六栏分层(说人话/收益/证据等级靠字号与间距分层,成本明细/来源/备注折叠);底部换 `HorizontalFloatingToolbar`;争议与待核实拆成两条独立提示;区分「正在打开」与「没找到」 |
| 条目库 + 章节页 | 目录按该章主导口径着色(图标 + 口径色)+ 条数占比条;章内顶部加排序切换(性价比/证据等级/原书顺序,排序在 VM 里算);`ListItem` 迁移到表达性重载 |
| 待办页 | 每日习惯与一次性待办在 VM 里分区:前者圆形勾选 + 口径色 + 分区进度环,后者方框勾选;空状态文案分清「今天还没安排」与「没任务」;删除改 Snackbar + 撤销(`restoreTask` 按原 id 写回) |
| 引导页 | 进度换 `LinearWavyProgressIndicator`;打开滑动翻页;底部动作栏改最小高度、三态共用一套 Row;单选/多选换 `ToggleButton`;每步顶部加一行「为什么问这个」 |
| 设置页 + 我的页 | 去掉 `emissions >= 2` 取值 hack,`UiState` 带 `loaded` 标志;输入框 `rememberSaveable`;API Key 支持显示/隐藏;新增「测试连接」;分组换表达性 `SegmentedListItem`;关于页拆出折叠免责声明;我的页的领域映射下沉到 `data/ProfileLabels` |
| 聊天页 | 思考态换 `LinearWavyProgressIndicator` + 「正在检索 N 条建议并请教模型」;安全提示改为顶部固定 Surface;无 Key 横幅改 `secondaryContainer`;气泡按方向区分形状;补三个可点示例问题 |

**验证**

| 项 | 结果 |
|---|---|
| `testDebugUnitTest` | 40/40 通过(P1 的 30 例 + 本阶段新增 10 例) |
| `assembleDebug` | 通过 |
| `lint` | 0 error |

新增单测:`DailyTaskPlannerTest` +1(换一条不重复补位)、`LibrarySortingTest` +6(主导口径与三种排序)、`ProfileLabelsTest` +3(映射完整性)。

**实际做法与本文的偏差 / 留待后续**

1. **没用 `SearchBar`。** alpha28 已把它重构为基于 `SearchBarState` 的新 API,并且**去掉了 `content` 槽**(`inputField` 之外不再接管结果列表),与「输入时就地出结果」的形态对不上。条目库改用同视觉语言的 `TextField`(大圆角 + `surfaceContainerHigh` + 无下划线)。等 API 稳定后再换。
2. **排序切换没用 `ButtonGroup`。** `ButtonGroup` 的形态是「按钮组 + 溢出菜单」,而排序是单选语义,用已稳定的 `SingleChoiceSegmentedButtonRow` + `SegmentedButton` 更贴切。
3. **「收藏」没有做(明确留给独立 PR)。** `entry_states` 是单状态主键,一个条目只能处于 TODO/DONE/DISMISSED 之一;加收藏要么改主键为复合、要么新建表,两者都要动数据库(`version = 1`,目前没有 migration)。这属于数据模型变更,不该混在「按屏替换组件」里。详情页工具栏当前是 **加入待办 / 复制文本 / 分享**。
4. **P0 遗留的 9 处 `ListItem(headlineContent = …)` 弃用形式已全部迁移**到表达性重载(headline 走尾随 `content`)。设置页与我的页的分组改用 `SegmentedListItem` + `ListItemDefaults.segmentedShapes`。
5. **条目库大屏 list-detail 与 `PullToRefreshBox`** 按上文仍属 P3,未在 P2 做。
6. **动效只用了默认的 `scaleIn/fadeIn`**,还没有按 §6.1 全面取用 `MaterialTheme.motionScheme` 的 spec(§6.2 属 P3)。
7. **截图测试仍然缺席。** §8 P2 的验收写着「每屏 PR 附 4 张截图」,但截图测试的基础设施(androidTest 依赖)被归在 P3;本阶段的验收是**手工截图 + 肉眼检查**,没有自动回归。这是 P2 阶段最大的验收缺口,建议下一轮先把 P3 的截图测试提到最前面,再动 P3 的动效。

### P3 · 动效与打磨

1. §6.2 动效清单逐条实现。
2. 动画降级档位设置。
3. `NavigationSuiteScaffold` 大屏导航。
4. 条目库 list-detail(§7.3)。
5. 预测性返回。
6. TalkBack 全流程走查 + 200% 字体走查。
7. Baseline Profile + `profileinstaller`。
8. 截图测试 + Compose UI 测试落入 CI。

**验收**:大屏/折叠屏上条目库双栏可用;TalkBack 能完整走完归档→打卡→搜索→详情;CI 里有截图回归门禁。

---

## 9. 文案规范(voice)

规则:**一个按钮一个动词**;错误是恢复路径;**句子大小写,感叹号一律删**;空状态要教会用户这个空间是什么。

| 位置 | 现在 | 改成 |
|---|---|---|
| 聊天加载 | `思考中…` | `正在检索 601 条建议并请教模型` |
| 详情加载 | `条目不存在或还在加载`(两个状态混为一谈) | 加载:`正在打开条目` / 真的没有:`没找到这条内容,可能已被移除` |
| 待办空 | `今天的都做完了`(但可能只是没任务) | 无任务:`今天还没有安排习惯,去今日页看看` / 已完成:`今天的都完成了` |
| 今日空 | `还没有今日任务。先完善档案,或去条目库逛逛。` | `填 3 个问题,推荐立刻变准` |
| 引导跳过 | `跳到目标` / `跳过本步` | `本步可跳过` / `直接到目标` |
| 免责 | 一整段 | 折叠标题:`免责声明与内容出处` |
| 删除待办 | 无确认无撤销 | Snackbar:`已删除 「低钠盐」` + `撤销` |

---

## 10. 范围纪律(明确不做)

避免把设计系统做成无底洞:

- **不引入图表库**。`CostMeter` 用 `Row` + `Canvas` 手写,20dp 高,不需要依赖。
- **不换字体**。中文走系统字体是正确解,自备字体只会让中西文行高打架。
- **不做多语言翻译**。P1 只做**资源化**(抽取 `strings.xml`),翻译是独立决策。
- **不做游戏化**(徽章、等级、排行榜)。这不只是范围问题 —— §1 第 4 条说了这个 App 不贩卖焦虑,排行榜和"你落后了"是同一类东西。真要做,单独设计。
- **不在 P1/P2 使用实验性 API**。`LoadingIndicator`、`MaterialShapes`、表达性 `TimePicker` 一律标 TODO 等毕业。

---

## 11. 验收总清单

发布前必须全绿:

- [ ] `./gradlew assembleRelease` 产出已签名包,`targetSdk ≥ 36`
- [ ] 深色模式冷启动无白屏;主题三选一即时生效
- [ ] 全项目无硬编码中文字面量
- [ ] 无硬编码 `dp` 间距字面量(间距走 token)
- [ ] 四个口径色在氘代色盲模拟下可区分,且每处都有配图标
- [ ] `CostMeter` 有 TalkBack 语义标签
- [ ] 200% 字体缩放下:引导页、设置页、详情页不破版
- [ ] TalkBack 能完整走完:归档 → 今日打卡 → 待办 → 搜索 → 详情 → 聊天 → 设置
- [ ] 600dp / 840dp 宽度下导航自动切换,条目库双栏
- [ ] 预测性返回在条目详情和聊天页可见
- [ ] 动画降级档位在"关闭"时全局瞬时
- [ ] 全部交互组件补齐 9 状态(§7.1)
- [ ] 截图测试覆盖 4 种组合(浅/深 × 100%/200%)
- [ ] CI 跑通 test + lint + 截图回归

---

## 12. 版本事实的来源与时效

本文的版本结论核对于 **2026-09-24**,来源:AndroidX Compose Material3 release notes(稳定版 1.4.0 / alpha 1.5.0-alpha29,更新于 2026-09-23)。

**这些结论会过期。** 每次动 P0 之前重新核对三件事:

1. `1.5.0` 是否已转正?转正后 §4.2 里所有"等稳定"的组件都能一次性启用。
2. `expressiveDarkColorScheme()` 是否出现?
3. `MaterialExpressiveTheme` 这个名字是否被改过?(release notes 与 API reference 目前不一致)

组件 API 在 alpha 线内**会变**(`ToggleButton` 在 alpha25/28 改过名字和参数顺序,`Slider` 在 alpha29 有过源码级破坏性变更)。**P2 每个 PR 都要对着锁定版本的 API reference 写,不要凭记忆。**
