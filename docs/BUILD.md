# 构建、CI 与发布

面向要构建、打包、发布本项目的人。项目是什么、有哪些功能见根目录 [README.md](../README.md);
架构与二次开发指引见 [DESIGN.md](DESIGN.md)。

## 环境

JDK 17+(本项目在 JDK 21 上验证)、Android SDK(**platform-37 + build-tools 36.0.0**)。
`local.properties` 里配置 `sdk.dir`。

依赖与工具链版本集中在 [`gradle/libs.versions.toml`](../gradle/libs.versions.toml),不要在模块脚本里硬编码版本号。

```bash
./gradlew testDebugUnitTest   # 单元测试
./gradlew assembleDebug       # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # 产出 app/build/outputs/apk/release/BetterLife-<versionName>-<versionCode>.apk(R8 压缩)
./gradlew lint                # 静态检查(abortOnError:有错误即构建失败)
./gradlew validateDebugScreenshotTest # 截图回归:把各屏与设计组件和基线图逐像素比对
./gradlew updateDebugScreenshotTest   # 视觉有意改动后重刷基线(改完记得看一眼 diff)
./gradlew :app:generateBaselineProfile # 生成 baseline profile —— 需要连着真机/模拟器,CI 不跑
```

截图测试跑在宿主 JVM 上,不需要设备或模拟器;基线图入库,位于 `app/src/screenshotTestDebug/reference/`。

模块:`:app`(应用本身)与 `:baselineprofile`(baseline profile 生成器,`com.android.test` 模块,只跑在真机/模拟器上)。

构建:AGP 9.4.1(内置 Kotlin 支持)· Kotlin 2.4.20 · KSP 2.3.12 · Gradle 9.7.1。
SDK:最低 Android 8.0(API 26),目标 Android 16(API 36),compileSdk 37。

## CI

CI 见 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml):`build` 作业跑单测 + lint + release 构建,
`screenshot` 作业跑截图回归(失败时上传 reference/actual/diff 报告)。基线目前是在 Windows 上生成的,
首次在 Linux runner 上跑若出现亚像素级差异,处理办法见 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) §8「P3-A2」。

### 版本号约定

`versionName` 三段式:大重构迭代第一位、大特性更新迭代第二位、正式版本迭代第三位;
`versionCode` 即版本号后面的「-n」开发迭代号(0.1.2-3 ↔ versionCode 3),**开发中只递增 versionCode**
(见 `app/build.gradle.kts`)。

## 发布构建与签名

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

三者都未配置时,release 构建会**回退到 debug 签名并打印警告** —— 这样任何机器上都能跑通
`assembleRelease`,但产出物不能用于正式分发。

## 内容管线

上游书库以 git submodule 挂在 `upstream/HowToLiveBetter`;书中条目不手抄,由脚本解析生成:

```bash
git submodule update --init                  # 首次 clone 后拉上游内容
python tools/build_content.py                # book/*.md → app/src/main/assets/entries.json
python tools/gen_rules.py                    # 重新生成 tools/relevance_rules.json,需手动拷到 app/src/main/assets/
python tools/build_content_pack.py           # 打内容发布包(manifest + 全量 gz + 增量 patch)
```

- `entries.json`:601 条结构化条目,含机器可读的成本标签(钱/时间/毅力/收益/口径)、性价比档(极高/高/一般)、
  证据等级(A/B/C)。条目主键是标题派生的稳定 key(上游插入条目导致条号顺延时不受影响);
  标题轻微改动(增减几个字)由 `--prev-entries` 相似度继承兜住,机制见 [DESIGN.md](DESIGN.md) §3。
- `relevance_rules.json`:55 条档案 → 条目的加权/排除规则 + 17 条每日习惯种子池(首次播种示例用)。

**内容更新分发(纯人工,不走 CI)**:上游整体改标题这类变更需要人来判断继承关系、登记别名,
机械式自动构建容易接错,所以内容包**每次都人工构建、人工核对、人工发布**;App 内
`ContentSyncWorker` 每 24h 从滚动 Release `content-latest` 增量同步,设置页可手动「检查内容更新」。

发布一次内容更新的完整步骤:

```bash
# 1. 拉上游最新
git submodule update --remote upstream/HowToLiveBetter
NEW=$(git -C upstream/HowToLiveBetter rev-parse --short HEAD)

# 2. 留底上一版 entries.json 做 key 继承,再重建
cp app/src/main/assets/entries.json /tmp/prev_entries.json
python tools/build_content.py --version "$NEW" --prev-entries /tmp/prev_entries.json
```

**人工检查点**:看脚本输出的新增/消失/未继承警告。若有「同节内消失+新增」且判断是同一
条目被整体改标题,在 `app/src/main/assets/key_aliases.json` 登记 `旧key→新key` 别名后重跑上一步
确认警告消除;确认是上游真删/真增的,无需处理。

```bash
# 3. 打增量包(以上一次发布的 manifest 为基线;--prev-manifest 也接受本地路径)
python tools/build_content_pack.py \
  --prev-manifest "https://github.com/<owner>/<repo>/releases/download/content-latest/manifest.json" \
  --base-url    "https://github.com/<owner>/<repo>/releases/download/content-latest"

# 4. 人工核对 build/content/patch.json(updated/removedKeys 是否符合预期),然后发布
gh release upload content-latest \
  build/content/manifest.json build/content/patch.json build/content/entries.json.gz --clobber
# 首次发布先建 Release:
# gh release create content-latest build/content/manifest.json build/content/entries.json.gz \
#   --title "内容包(content-latest)" --notes "App 端内容同步的滚动 Release"

# 5. 提交 submodule 指针与重建产物,保证 APK 构建可复现同一版内容
git add upstream/HowToLiveBetter app/src/main/assets/entries.json app/src/main/assets/key_aliases.json
git commit -m "内容更新: 上游 $NEW"
```
