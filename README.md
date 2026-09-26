# BetterLifeApp 高性价比人生助手

一款 Android 原生 APP,基于《高性价比人生指南》(github.com/eternity4719/HowToLiveBetter)的 601 条建议,回答一个问题:**你现在该把精力放在什么事情上。**

- 填写一份个人档案(年龄段、吸烟/饮酒、运动睡眠、慢性病、职业、家庭、目标)
- APP 按书中条目的性价比档和证据等级,结合档案给出**该做什么、不该做什么**的推荐
- 运动、饮食、睡眠这类每天都要做的事,自动生成**今日任务**,打卡记连续天数
- 一次性的行动项(换低钠盐、约体检)进**待办列表**
- 内置 AI 问答(自带 API Key,Kimi/DeepSeek),答案基于书中条目并注明出处

> 内容给的是通用口径,不替代医生、律师、会计。

## 功能一览

| 页面 | 说明 |
| --- | --- |
| 档案向导 | 四步填写:基本情况 → 健康习惯 → 家庭与计划 → 目标,除年龄段外都可跳过 |
| 今日 | 每日任务打卡(含连续天数)+ 今日步数卡片 + 按口径分组的推荐(先保命/守住钱/省精力/别踩线) |
| 待办 | 每日习惯 + 一次性待办,勾选完成 |
| 条目库 | 33 章 601 条完整浏览、搜索、六栏详情(成本/说人话/收益/证据/来源/备注) |
| AI 问答 | 本地检索书中条目 + 大模型回答,注明「第 X 节第 Y 条」;无 Key 时降级为规则回答 |
| 设置 | API Key、每日提醒时间(本地通知)、主题与动效档位、档案修改、关于 |

> 今日步数默认走 Health Connect:Android 14+ 系统内置,旧版本需安装 Health Connect App;
> 小米用户需先在「小米运动健康 → 我的 → 三方资料管理」里开启对 Health Connect 的授权。
> 设备没有 Health Connect 时自动降级为本机计步传感器(需授予「身体活动」权限)。

## 构建

环境:JDK 17+(本项目在 JDK 21 上验证)、Android SDK(**platform-37 + build-tools 36.0.0**)。`local.properties` 里配置 `sdk.dir`。

```bash
./gradlew testDebugUnitTest   # 47 个单元测试
./gradlew assembleDebug       # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # 产出 app/build/outputs/apk/release/app-release.apk(R8 压缩)
./gradlew lint                # 静态检查(abortOnError:有错误即构建失败)
./gradlew validateDebugScreenshotTest # 截图回归:把各屏与设计组件和基线图逐像素比对
./gradlew updateDebugScreenshotTest   # 视觉有意改动后重刷基线(改完记得看一眼 diff)
./gradlew :app:generateBaselineProfile # 生成 baseline profile —— 需要连着真机/模拟器,CI 不跑
```

截图测试跑在宿主 JVM 上,不需要设备或模拟器;基线图入库,位于 `app/src/screenshotTestDebug/reference/`。

CI 见 [`.github/workflows/ci.yml`](.github/workflows/ci.yml):`build` 作业跑单测 + lint + release 构建,`screenshot` 作业跑截图回归(失败时上传 reference/actual/diff 报告)。基线目前是在 Windows 上生成的,首次在 Linux runner 上跑若出现亚像素级差异,处理办法见 [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) §8「P3-A2」。

依赖与工具链版本集中在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml),不要在模块脚本里硬编码版本号。

### 发布构建与签名

正式签名有三种配置方式,按优先级:**环境变量 > 根目录 `.env` > `keystore.properties`**(三者都不入库,见 `.gitignore`)。

日常开发推荐 `.env`:复制 `.env.example` 为 `.env` 并填入真实值(值不要加引号):

```bash
BETTERLIFE_KEYSTORE_FILE=D:\path\to\your-release.keystore
BETTERLIFE_KEYSTORE_PASSWORD=...
BETTERLIFE_KEY_ALIAS=betterlife
BETTERLIFE_KEY_PASSWORD=...
```

CI 或临时打包可用环境变量(同名):

```bash
export BETTERLIFE_KEYSTORE_FILE=/absolute/path/to/your-release.keystore
export BETTERLIFE_KEYSTORE_PASSWORD=...
export BETTERLIFE_KEY_ALIAS=...
export BETTERLIFE_KEY_PASSWORD=...
```

`keystore.properties` 仍兼容旧格式:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

生成密钥库:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias betterlife
```

三者都未配置时,release 构建会**回退到 debug 签名并打印警告** —— 这样任何机器上都能跑通 `assembleRelease`,但产出物不能用于正式分发。

## 内容管线

书中条目不手抄,由脚本从源仓库解析生成:

```bash
python tools/build_content.py                 # book/*.md → app/src/main/assets/entries.json
python tools/gen_rules.py                     # 重新生成 tools/relevance_rules.json,需手动拷到 app/src/main/assets/
```

- `entries.json`:601 条结构化条目,含机器可读的成本标签(钱/时间/毅力/收益/口径)、性价比档(极高/高/一般)、证据等级(A/B/C)
- `relevance_rules.json`:55 条档案 → 条目的加权/排除规则 + 18 条每日习惯白名单

## 技术栈

Kotlin + Jetpack Compose(Material 3)· Room · DataStore · WorkManager · Navigation Compose · OkHttp · Kotlinx Serialization。

模块:`:app`(应用本身)与 `:baselineprofile`(baseline profile 生成器,`com.android.test` 模块,只跑在真机/模拟器上)。

构建:AGP 9.4.1(内置 Kotlin 支持)· Kotlin 2.4.20 · KSP 2.3.12 · Gradle 9.7.1。
SDK:最低 Android 8.0(API 26),目标 Android 16(API 36),compileSdk 37。

详细架构与二次开发指引见 [docs/DESIGN.md](docs/DESIGN.md);设计系统与 M3 Expressive 改造路线见 [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md);**接下来该做什么见 [todo.md](todo.md)**(含发布前必须项、已知缺口与「不要做的事」)。

## 内容出处与许可

条目内容来自《高性价比人生指南》,版权归原作者所有,按其仓库 LICENSE 使用;APP 关于页已注明出处。本仓库代码与书中内容各自遵循其许可。
