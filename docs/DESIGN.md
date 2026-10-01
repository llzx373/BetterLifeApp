# BetterLifeApp 设计指导说明书

面向继续开发这个 APP 的人。读完应能:改推荐规则、加档案字段、接新页面、换 AI 服务商、做对拍验证。

## 1. 产品模型

一切围绕书中「条目」展开。一条条目是一个建议(如「把家里的食盐换成低钠盐」),带:

- **成本标签**(机器可读):钱(0/少/多)、时间(少/中/多)、毅力(否/些/是)、收益(大/中/小)、口径(死亡率/金钱/时间/自由)
- **性价比档 ratio**:极高/高/一般,由成本分 `cs = 钱 + 时间 + 毅力`(各 0~2)与收益档推出,算法必须与源仓库 `index.html` 一致(收益=大:cs==0→极高,cs≤2→高,否则一般;收益=中:cs==0→高,否则一般;收益=小→一般)
- **证据等级** A/B/C 与 **dispute(争议)/ todo(待核实)** 标记

两条铁律(来自原书,全 App 遵守):

1. **口径之间不排序**。换寿命的和换钱的分开列,各排各的;UI 上就是「先保命/守住钱/省精力/别踩线」四个分组。
2. **待核实条目不进推荐**,只在条目库可见并标注;争议条目可见但要标「争议」。

## 2. 模块结构(包 com.betterlife.app)

```
data/        内容与持久层
  EntryModels.kt        entries.json / relevance_rules.json 的 DTO
  EntryRepository.kt    Room 内容表 → 内存索引(byId/bySection/sections/seedEntryIds);invalidate() 供同步后失效
  content/              ContentBootstrap(首装播种 + SS-NN→key 迁移)、ContentSyncRepository(增量/全量同步)
  db/                   Room v7(11 表):profile、tasks、entry_states、weekly_habits、
                        custom_entries、entry_notes、streak_leaves、chat_messages、
                        content_sections、content_entries、content_meta
  SettingsStore.kt      DataStore:API 配置、提醒时间、onboardingDone、搜索历史、已庆祝里程碑、每日一问进度(N1)
  NetworkMonitor.kt     连通性监听(壳层离线横幅的数据源)
  ProfileRepository.kt  Profile 领域模型 ↔ ProfileEntity;Profile.matches() 规则匹配;Profile.EMPTY 空档案、knownFields 部分已知、withAnswer 每日一问写档案(N1)
  health/               Health Connect:步数/睡眠读取、health_rules.json 自动核销规则
  backup/               本地 JSON 导出/导入(BackupManager + BackupCodec)
recommend/   纯 Kotlin,不依赖 Android,全部可单测
  RecommendationEngine.kt  规则打分 + 按口径分组排序
  DailySeedPicker.kt       每日习惯首次播种的示例挑选(种子池 ∩ 档案命中,空档案命中为空时落回种子池硬排取前 3)
  ProfileQuestions.kt      每日一问(N1):提问顺序、挑题(去重/暂缓/轮回)、有效档案重建
  EntryFilter.kt           条目库筛选(性价比档/证据等级/口径)
tasks/
  TaskManager.kt           任务读写、打卡、streak、加待办、HC 自动核销
  Streak.kt                computeStreak:连签 + 请假搭桥(纯函数)
  ReminderScheduler.kt     WorkManager 周期任务 + 本地通知(渠道 "daily");单任务提醒带日期维度
  NotifyDecision.kt        N2b 核销报喜的决策纯函数(报喜/汇总/不发,同日重发抑制);N2c 挽回通知决策(3 天窗口 + 7 天频控)
stats/       统计纯逻辑(零 Android 依赖,可单测):StatsCalculator / Achievements / StatsPeriod / StatsPrompt
ai/
  LlmClient.kt             OpenAI 兼容 chat/completions(预设 Kimi/DeepSeek);chatStream 走 SSE 流式
  EntryRetriever.kt        本地检索:bigram 重叠 + 标题加权 + 档案加分
  SystemPrompts.kt         安全护栏与回答格式(改不得,见 §6)
  AiAdvisor.kt             检索 + 调模型;无 Key 时降级;askStream / interpretStatsStream
widget/      Glance 桌面小组件:今日任务一览 + 一键打卡(TodayTasksWidget / WidgetUpdater)
viewmodel/   ViewModel 各带 Factory,手动 DI 在 BetterLifeApp.AppContainer
ui/          Compose 页面:onboarding / today / todo / library / chat / settings / mine / stats / dismissed
```

依赖方向:ui → viewmodel → (recommend / tasks / ai) → data。recommend 和 EntryRetriever 不许 import Android 类,保持可单测。

## 3. 内容管线(tools/)

- 上游书库以 **git submodule** 挂在 `upstream/HowToLiveBetter`(GitHub: `eternity4719/HowToLiveBetter`),clone 本仓库用 `git clone --recursive` 或事后 `git submodule update --init`。
- `build_content.py`:解析 submodule 的 `book/*.md`,输出 `app/src/main/assets/entries.json`。解析正则复刻自源仓库 index.html,**源书格式变了先改这里**,改完跑脚本看自检输出(601 条、六栏完整率、档位分布、key 冲突检查)。`--version` 传上游 commit short hash 作为 contentVersion。
- `build_content_pack.py`:把 entries.json 打成发布包(manifest.json + entries.json.gz 全量 + patch.json 增量),供人工发布到 GitHub Release(发布步骤见 [BUILD.md](BUILD.md)「内容管线」,刻意不走 CI:整体改标题的继承关系需要人工核对)。
- `gen_rules.py` → `tools/relevance_rules.json` → 手动拷贝到 `app/src/main/assets/`。规则按 SS-NN 人工编写,**输出时自动换算成稳定 key**;脚本自带 id 存在性校验。

**稳定 key(2026-09-28 起)**:条目主键是 `key = sha1(节标题+条目标题)[:12]`,不是位置序号 `SS-NN`。上游插入条目会让条号顺延,但标题按上游约定只加字不换词,所以 key 稳定。`SS-NN` 仍保留在 entries.json 的 `id` 字段,仅供老用户首启迁移(五张用户表的 entryId 由 ContentBootstrap 改写)与旧备份导入改写(BackupManager,备份 format 仍 6 不 bump——格式没变只是 id 语义变了)使用。节同理有 `key`。标题「增减几个字」有两层相似度兼容:**构建期** `build_content.py --prev-entries`(发布前先把上一版 entries.json 留底)按标题相似度继承旧 key(SequenceMatcher,阈值 0.8、候选唯一,节先继承、条目再先精确后模糊,节改名导致的整节连锁断裂由条目的精确匹配兜住);**运行时** ContentSyncRepository 对下架且有用户数据的 key 在同节在架条目里做 bigram Jaccard 保守重挂(阈值 0.85 + 分差 0.05,见 TitleSimilarity)。两层都没接住的 → 旧 key 消失、新 key 出现,用户数据脱钩但保留;在 `app/src/main/assets/key_aliases.json` 登记 旧key→新key 别名,同步时会把用户数据挂回新条目。构建与打包脚本都对「同节内消失+新增」打警告提醒登记别名,由发布人人工判断是否登记。

**运行时内容同步**:内容落 Room(`content_sections/content_entries/content_meta`)。首装/升级时 ContentBootstrap 把捆绑 entries.json 播种进库;`ContentSyncWorker`(WorkManager 24h + 联网约束,Application.onCreate 注册)从滚动 Release `content-latest` 拉 manifest 比对版本,基线匹配走 patch.json 增量、否则 entries.json.gz 全量;应用后在事务内对照 manifest 做全量 hash 校验,失败回滚等下轮。下架条目标 `removed`(行永不删):浏览/推荐/每日规划剔除,详情页展示快照 + 下架横幅,用户数据不受影响。设置页「检查内容更新」可手动触发。

entries.json 单条字段:`key/secKey/hash`(稳定标识与内容哈希)、`id`(节号-条号,仅迁移用)、`sec/n/title`、`cost/human/gain/grade/src/note`、`money/time/will/level/lens`、`cs/ratio/dispute/todo`、`hay`(检索用小写拼接)、`removed`。

## 4. 推荐引擎语义(relevance_rules.json)

```json
{"when": {"smoking": ["yes"], "chronic": ["kidney"]},
 "boostEntryIds": [...], "boostSections": ["<节key>"], "excludeEntryIds": [...],
 "weight": 100, "reason": "..."}
```

- 条目与节的引用都是**稳定 key**(见 §3);gen_rules.py 里按 SS-NN/节号编写,输出时自动换算
- `when` 全部字段匹配才生效(档案值 ∈ 数组);`{}` = 所有人;**布尔一律写字符串 `"true"/"false"`**(写成 JSON 布尔会在反序列化时崩溃,已踩过)
- 打分:命中规则的 weight 累加(boostEntryIds 直接加;boostSections 加给该节每条);命中规则的 excludeEntryIds **无条件剔除**;todo=true 与 removed(上游下架)剔除
- 排序:按 lens 分组(固定序 死亡率→金钱→时间→自由),组内 score desc → ratio → grade → cs asc,每组 topN(默认 5)
- 调用方排除(LibraryViewModel):DONE、DISMISSED,以及**已加入任一计划**的条目(2026-09-29 起:TODO 一次性待办、STATE_DAILY 每日习惯、weekly_habits 每周习惯模板,由 `TaskManager.plannedEntryIdsFlow` 汇总)——已在计划里的内容不再出现在推荐列表;`EntryStateDao.excludedIds` 只服务播种,语义不变

**改规则的方法论**:加分规则回答「这条建议为谁而写」,排除规则回答「备注里写了谁不能用」。新加档案字段时,先在 `Profile.fieldValues()` 注册,再补规则,最后加引擎单测。

### 每日任务

每日任务是**纯用户自选**机制:`entry_states` 的 `STATE_DAILY` 集合即每日习惯清单,每天由 `TaskManager.ensureTodayTasks` 落成当天的任务行(幂等,靠 `countDailyByDate > 0` 判重)。自选习惯只按 DISMISSED 过滤、不按 DONE——打卡完成会写 DONE,按 DONE 过滤的话习惯打一次卡就会从每日任务里消失(2026-09-28 修)。

用户一条习惯都没有且从未播种过时(新用户,或老用户升级到自选机制后的第一天),`DailySeedPicker` 从 `seedEntryIds` 种子池(17 条,全是「今天做一次、明天还要做」的习惯)里按档案命中挑 3 条示例写入 STATE_DAILY——种子池 ∩ 档案命中 boost ∩ 非 todo/removed ∩ 非 exclude,按 ratio/grade/cs 稳定排序取前 3。示例可删可改;DataStore 标记 `daily_habits_seeded` 无论挑到几条只播一次,用户主动删光习惯后不会再被塞回来(空态引导去条目库自选)。早期的「系统每天从白名单轮换塞 3 条 + 换一条补位」已随这次改动移除(2026-09-28)。

HC 自动核销只核销当天有 DAILY 行的条目:想让计步/睡眠每天固定出现并被自动核销,保持它们在每日习惯清单里即可(首次播种的示例里就含这两条,档案命中时)。

### 每周习惯

「一周 N 次」的任务(如一周运动 3 次)走另一套机制:模板存 `weekly_habits` 表(entryId + timesPerWeek),每次打卡在 `tasks` 表插一行 `type=WEEKLY, date=当天, done=1`。本周进度 = 打卡日落在本周区间(周一~周日,`weekRange()`)内的行数,**跨周天然归零,不需要重置**;打卡行留在库里即历史。待办页有独立分区,一次性待办可转为每周习惯。

## 5. 数据流

```
onboarding 填档案 → ProfileEntity(N1:可「先随便看看」跳过,空档案 Profile.EMPTY 直入,只有 {} 普惠规则生效)
  → TodayViewModel:TaskManager.ensureTodayTasks(profile)(幂等,按日期)
                   RecommendationEngine 出分组推荐
  → 打卡 → TaskEntity.done + note(随手记)+ doneBy(manual/auto:hc/widget)→ streak 从昨天往前数(今天未打卡不清零,请假日搭桥)
  → 任何打卡完成顺手写 entry_states 的 DONE(推荐排除「做过」的);撤销打卡不清
  → 加入待办 → TaskEntity(ONCE,可带 dueDate)+ EntryStateEntity(TODO)
  → 待办转换 → STATE_DAILY 进每日 / weekly_habits + WEEKLY 打卡行进每周
  → 自定义任务 → custom_entries 存标题(id 为 "custom:<UUID>"),三种类型复用同一套打卡/提醒逻辑
ReminderScheduler:WorkManager 每天 ensureTodayTasks + 通知未完成数 + autoCompleteByHealth(+ N2c 挽回通知判断)
「今天」是 TaskManager 里的 StateFlow,MainActivity.onResume 时 refreshToday() 并写 DataStore `last_active_date`(N2c);
进程跨夜存活时任务查询随之切到新日期(此前 LocalDate.now() 在 Flow 创建时固化,跨天不刷新)
小组件:TodayTasksWidget 渲染前幂等 ensureTodayTasks,打卡走同一个 TaskManager;
数据改动后由 WidgetUpdater.refresh 刷新(TodayViewModel 改动后、DailyReminderWorker 跑完后)
```

## 6. AI 模块与护栏

`SystemPrompts.kt` 的护栏是产品安全底线,**改动需要明确理由**:

- 正在发生的急症 → 先说 120/119 和第一个现场动作(出处第 13 节)
- 自杀念头/活不下去 → 先给心理援助热线 12356,不劝导不评价
- 已被传唤/拘留/起诉 → 指向第 8 节,建议找律师
- 回答必须基于检索到的条目,注明「第 X 节第 Y 条」;书里没写的直说;数字照抄不改;末尾带「通用口径,不替代医生/律师」

检索是本地的(EntryRetriever),只有 top6 条目内容进 prompt,档案只以摘要形式进 prompt。换服务商:设置页选预设或自定义 baseUrl/model,OpenAI 兼容即可;baseUrl 已含 `/v1` 时客户端不会重复拼接。

**供应商卡片与并行回答**:配置是「多供应商卡片 + 一张当前使用」(AiProvider 存 DataStore 的 `ai_providers_json`,旧单组配置只读迁移);聊天页可多选供应商并行提问(`resolveChatProviders`,空选择回退当前使用),谁先答完谁先上屏,气泡标注供应商名。

**AI 搜索(互联网模式)**:聊天页输入栏可切「知识库 / 互联网」。互联网走博查 web-search(`BochaWebSearcher`,实现参考 HeartKindle 但只取 web-search;不做 tool calling——本项目是单发非流式,采用「先搜后问」):搜索结果按编号注入 prompt,要求模型用【N】标注来源。key/端点存设置(`search_api_key/search_endpoint`,空端点 = 博查默认)。回答带结构化来源(AiAnswer.sources:知识库 = top 条目「第X节第Y条」,互联网 = 标题+链接可点开),来源以注入 prompt 的资料为准,不解析模型文本。

## 7. 构建与验证

```bash
python tools/build_content.py      # 内容变更后重跑,看自检统计
./gradlew testDebugUnitTest        # 289 例(46 类):引擎/播种挑选/检索器/档案映射/排序/规则一致性/提醒继承与重排/备份/统计/聊天/内容包完整性/同步逻辑等
./gradlew assembleDebug
```

冒烟路径(新机器或改动后必走):onboarding → 今日页出任务和推荐 → 打卡出 streak → 待办页三分区(每日/每周/一次性) → 条目库搜索 → (配 Key)AI 问答 → 设置开提醒;N1:跳过向导直入今日页(有推荐 + 3 条示例习惯 + 问题卡片)、答一题推荐变化。

## 8. 已知取舍与路线

- `collectAsStateWithLifecycle` 已改用官方 `androidx.lifecycle.compose` 实现(此前是本仓库 `ui/util/StateFlowExt.kt` 的本地替代品,引入 `lifecycle-runtime-compose` 后已删除)
- 今日页 `TodayViewModel.UiState` 是 sealed:`Loading` / `Ready(items, profileIncomplete, questionField)` / `Error`(N1 起移除了 `Empty`:没有档案也进 `Ready`,空档案照常出推荐与示例习惯)。资产与数据库读取包了 try/catch,失败不再崩溃而是出「内容加载失败」+ 重试
- **N2a 断签文案去债务化(2026-10-01)**:连签判定逻辑不动(`computeStreak` 请假搭桥已够),只改展示——连签为 0(断签/未开始)时今日页问候区胶囊与统计页连续天数卡都不写「0 天」,统一落中性文案 `streak_fresh_start`(随时重新开始);断签次日打卡按新连签安静起步,全 App 无「你断了 N 天」式提示。小组件本就不展示连签数字,口径天然一致;成就(Achievements)只产出里程碑 key、无文案,判定不动
- **N1 冷启动直入与渐进档案(2026-10-01)**:空档案 `Profile.EMPTY`(knownFields=空集)只命中 {} 普惠规则,`matches()` 对未知字段一律不命中;knownFields 不落库(`toEntity` 拒绝),已答集合存 SettingsStore(`profile_questions_answered` 等),读取侧由 `ProfileQuestions.effectiveProfile` 重建。onboarding 第一步可「先随便看看」(只写 onboardingDone 不写档案);完整向导保存 = 全部字段已答,每日一问终止。今日页顶部:有题问问题卡片(一天一题、「暂不回答」次日换下一题、暂缓字段轮回),没题但档案未填完时兜底「完善档案」Banner;答完落库、推荐当页重算并短暂显示「推荐已更新」。老用户迁移:首启时已有档案则全部字段标为已答,不补问。DailyReminderWorker/小组件的规划门槛从「有档案」改为「看过引导」(装完未打开不静默播种)
- `EntryDetailScreen` 改用 `AppContainer` 的单例 `EntryRepository`,不再 `remember { EntryRepository(context) }` 每次进详情重解析 601 条 JSON
- 条目状态 `entry_states` 用 `(entryId, state)` 复合主键:加入待办、已完成、不再推荐、已收藏、自选每日(STATE_DAILY)彼此正交,不会互相覆盖。**数据库 v7,真实迁移 + `exportSchema = true`**(v4 加 `weekly_habits`,v5 加 `custom_entries`,v6 给 tasks 加 note/doneBy/dueDate 并新增 entry_notes、streak_leaves、chat_messages 三表,v7 加 content_sections/content_entries/content_meta 三张内容表;schema JSON 在 `app/schemas/`,androidTest `MigrationTest` 用 `MigrationTestHelper` 校验,destructive fallback 已移除)。迁移链只保证 v5→v7;数据库版本 ≤4 的设备(均为未发布的开发构建)升级需卸载重装,不为 pre-release 版本补迁移链
- **内容入 Room 与上游同步(2026-09-28)**:条目内容从 assets 只读改为 Room 内容表驱动(见 §3);`EntryRepository.entriesData()` 变为 suspend(先经 ContentBootstrap 幂等播种),全量调用点已改;老用户五张用户表的 `SS-NN` entryId 首启时一次性改写为稳定 key(DataStore 标记 `content_id_migrated`);旧备份导入时按同一份捆绑映射改写(BackupManager,备份 format 仍 6 不 bump——格式没变只是 id 语义变了)。「第X节第Y条」标签仍由 sec/n 在展示层现算,条号顺延不影响用户数据
- **DONE 语义(2026-09-27 起)**:任何打卡完成(手动/小组件/自动核销/每周/补卡)都顺手写 `entry_states` 的 DONE,推荐引擎据此排除「做过」的内容,推荐池得以轮换;撤销打卡不清 DONE(「做过」这个事实不变)。配套修复:`ensureTodayTasks` 里用户自选每日习惯(STATE_DAILY)只按 DISMISSED 过滤、不按 DONE——否则自选习惯打一次卡就会从每日规划里消失。DONE 在 UI 上展示为「已完成」徽标(条目库列表/详情、待办页一次性分区),「已加入计划」展示为「已加入」徽标;书库目录每章与今日页每个口径分组显示 待看/完成/忽略 统计(`recommend/EntryStats.kt` 纯函数,每条目只落一个桶、DONE 优先于 DISMISSED)。TODO 与 ONCE 任务行同生命周期:完成/删除 ONCE 行清 TODO,撤销打卡/恢复删除补回(2026-09-29)
- **Health Connect 自动核销(B1)**:条目→指标的映射写在 `assets/health_rules.json`,保守起见只收无歧义的两条(02-11 步数 ≥7000、02-13 睡眠 ≥7h)——误判自动打卡比不打卡更伤信任。`TaskManager.autoCompleteByHealth` 只核销映射内且当天有未完成 DAILY 行的条目;HC 不可用/缺权限/读取异常都安静返回空结果;打卡备注写达标证据(「今日步数 9234 ≥ 7000」),`doneBy` 区分来源(manual / auto:hc / widget)。睡眠窗口固定 [昨 18:00, 今 12:00),与查询时刻无关。触发点:今日页授权后、DailyReminderWorker
- **HC 达标报喜通知(N2b,2026-10-01)**:核销结果(`AutoCompleteResult`:条数 + 证据文案)交给纯函数 `tasks/NotifyDecision.kt` 的 `decideAutoNotify` 决策——全部完成且当天没报喜过 → 只发一条报喜(证据进文案,复用 "daily" 渠道,通知 id 1002);还有未完成 → 证据合并进每日汇总(「…已自动打卡;今天还有 N 条任务未完成」);同日同事件不重复发(DataStore `hc_praise_sent_date` 记当日已发);报喜开关 `hc_praise_enabled` 默认开,设置页「每日提醒」分组。两个触发点(今日页授权后由 TodayViewModel 发、DailyReminderWorker 由 worker 发)共用同一决策与频控标记
- **3 日未打开挽回通知(N2c,2026-10-01)**:`MainActivity.onResume` 顺手写 DataStore `last_active_date`(打开即重置计时);`DailyReminderWorker` 每天交给纯函数 `decideReengageNotify` 决策——满 3 天未打开且距上次发送满 7 天才发(`reengage_sent_date` 记上次发送;`last_active_date` 为空=老用户升级后还没打开过新版,不发等首次打开),文案「回来补个卡?昨天的还能补」,复用 "daily" 渠道,通知 id 1003,点击经 `MainActivity.EXTRA_OPEN_ROUTE` 深链到待办页(AppNav 消费一次即置空);开关 `reengage_enabled`(「久未打开提醒」)默认开,与报喜开关同组
- **数据导出/导入(B2)**:`data/backup/` 本地 JSON(format `version = 6`),覆盖 7 张持久表(profile/tasks/entry_states/weekly_habits/custom_entries/entry_notes/streak_leaves);chat_messages 不备份,DataStore 里的 API Key 等敏感配置不出设备。导入先完整解析+校验版本,全部通过才在单事务里清写(失败回滚,现有数据不变);taskId 原样保留,导入后重排未完成 ONCE 任务的提醒 work
- **聊天持久化与流式(C5)**:对话落 `chat_messages` 表,封顶保留最新 200 条(ChatViewModel.trimToLatest);`LlmClient.chatStream` 按 SSE 逐行读增量,`AiAdvisor.askStream`/`interpretStatsStream` 流式失败时保留已收残缺内容、一条没收到则回退单发。统计页 AI 解读用 STATS_INTERPRET 提示词(不检索不搜网,统计摘要直接进 prompt)
- **统计与成就(B6/B7)**:`stats/` 是纯 Kotlin(StatsCalculator 出连续天数/近 8 周趋势/周期报告,Achievements 出里程碑判定),ViewModel 把 TaskEntity 折成 StatsTaskRow 喂进去,趋势数学与成就判定都能脱机单测。UI 手写 Canvas 柱状图(§10 禁图表库),带 TalkBack 逐周摘要;成就定位**回顾**而非竞争——无徽章墙/排行榜,新达成只在统计页一次性低调「新」徽标(已庆祝集合存 DataStore)
- **桌面小组件(B5)**:Glance 1.1.1,今日任务一览 + 一键打卡/撤销(doneBy="widget");数据直读 Room,渲染前幂等 ensureTodayTasks。刷新触发:小组件自身动作、TodayViewModel 改动成功后 `WidgetUpdater.refresh`、DailyReminderWorker 跑完后 updateAll
- **宽屏分层断点(B4)**:条目库宽度分四档——<600dp 单栏 + 路由;600–839dp 双栏(目录 | 章内条目,`TWO_PANE_MIN_WIDTH = 600.dp`),详情走整屏路由;840–1199dp 双栏(章内条目 | 条目详情,`LIST_DETAIL_MIN_WIDTH = 840.dp`),目录收成列表栏底部的章节选择器(按钮 + DropdownMenu);≥1200dp 三栏(目录 | 章内条目 | 条目详情,`THREE_PANE_MIN_WIDTH = 1200.dp`,`LibraryScreen.LibraryThreePane` 用两个嵌套 `ListDetailPaneScaffold`)。840 直接上三栏会让手机横屏挤成窄竖条,所以三栏门槛提到 1200。常驻详情栏的两个档位点条目只更新 selectedEntryId 不跳路由;BackHandler 组合顺序保证详情独占一屏时系统返回先退回条目列表(返回行为留 A2① 真机复查);三个宽屏档位是裸 `ListDetailPaneScaffold`,已在 `LibraryScreen` 的 when 分支统一补 `statusBarsPadding`(此前顶部控件被系统状态栏压住、点击被拦截),条目库操作件(搜索/章节选择/排序/筛选)同步置底
- 自定义任务(待办页「新增」入口,一次性/每日/每周)没有条目库条目:标题存 `custom_entries`,id 用 "custom:<UUID>" 前缀;打卡、计时、提醒、streak 全部复用现有逻辑,仅没有详情页/收藏/推荐入口
- 单任务可设独立提醒时间:`TaskEntity.remindAtMinutes`(null=跟随全局汇总),按 taskId 入队 unique OneTimeWorkRequest 到点触发,每日习惯次日重建时继承上次设置;TodoScreen 闹钟入口设置/清除,今日卡展示提醒时间。一次性待办另有 `dueDate` 截止日(C6):触发时刻由 `tasks/TaskReminderTiming.kt` 的 `nextTriggerMillis`(带可选日期参数的纯函数)算出,设了独立提醒的按截止日触发,新建自定义待办也可直接带日期
- AI 配置改为多供应商卡片:DataStore 单 key `ai_providers_json` 存 `List<AiProvider>`(kotlinx.serialization),`active_provider_id` 记「当前使用」;生效卡 = active 且 enabled,否则回退第一个 enabled(`resolveActiveProvider`)。JSON 缺失时一次性内存迁移:旧 `api_base_url/api_key/api_model` 有值折成单张启用卡,否则播种 Kimi/DeepSeek 两张无 key、默认禁用的预设卡,首次写卡时落盘
- 番茄钟结束提示音可自定义:`timer_ringtone_uri`(空串=系统默认通知音),设置页走系统 `ACTION_RINGTONE_PICKER`;响铃仍挂在 Composition 上,页面不在前台不响(前台服务是另一件事)
- ~~第二阶段计划:Health Connect 接入、数据图表、成就系统~~ **已完成(2026-09-27)**:HC 自动核销、统计页(图表 + 周/月报)、回顾式成就均已落地,见本节上文对应条目;剩 Google Play 健康数据申报(上架流程里处理)
- **已完成列表页(C9)**:「我的」页入口进 `ui/completed/CompletedScreen.kt`(镜像 DismissedScreen),列出全部 DONE 条目,行尾「撤销完成」走 `TaskManager.unmarkDoneBefore` 清状态、条目回到推荐池
- **统计页条目完成度(C10)**:`StatsViewModel` 消费 `EntryStateDao.allStatesFlow()` + `computeEntryStats`,按口径(固定口径序)与按章出完成度;`StatsScreen.CompletionCard` 每行 名称 + 细进度条 + 完成/总数——进度条只是加强、数字才是主信息,保持「回顾而非竞争」。整屏截图基线拍不到第 5 张卡(首屏之外),另给组件级 `StatsCompletionCard` 基线
- **推荐「换一批」(C11)**:`RecommendationEngine.recommend` 加 `offset` 参数,组内候选超过 topN 时 `rotateWindow` 轮转取窗口(offset=0 与旧行为完全一致);轮次存 DataStore(`recommendOffsetFlow`/`bumpRecommendOffset`),今日页推荐区标题行「换一批」按钮触发
- **习惯养成提示(C12)**:`tasks/WeeklyGraduation.kt` 纯函数 `consecutiveReachedWeeks`(本周未达标不算断签,从上周起计;某周打卡数不足 timesPerWeek 即断签),阈值 `GRADUATION_CONSECUTIVE_WEEKS = 4`;`TodoScreen.GraduationBanner` 是一次性安静横幅,不弹窗不催促,点「知道了」记入 DataStore 不再出现
- 条目内容的 LICENSE 归原书仓库,分发 APK 即分发其内容,关于页须保留出处
