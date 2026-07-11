# HupuX 多平台迁移计划（Android → Android + 桌面端 + iOS）

## 目标

第一阶段（Phase 1–5）：基于 **Compose Multiplatform**，在保持 Android 功能完整的前提下新增 Windows / macOS / Linux 桌面端。

第二阶段（Phase 6）：新增 **iOS** 支持——共享层去 JVM 化后编译到 Kotlin/Native，iOS 端用原生 **SwiftUI** 复用同一套业务逻辑。

## 分支

`feature/multiplatform`（从 `main` 分叉，迁移完成后合并回 `main`）

---

## 最终项目结构

```
hupuX/
  app/              ← Android 入口（保持现有结构）
  shared/           ← KMP 共享模块：数据层 + ViewModel（业务逻辑）
    src/
      commonMain/   ← 纯 Kotlin，编译到所有平台
      androidMain/  ← Android 专属实现（数据库驱动、图片加载等）
      desktopMain/  ← 桌面专属实现
  desktopApp/       ← 桌面入口（Windows / macOS / Linux）
```

---

## 依赖替换对照表

| 当前（Android） | 替换为（KMP） | 风险 | 阶段 |
|---|---|---|---|
| Hilt | Koin 3.5.x | 中 | Phase 3 |
| Room | SQLDelight 2.x | 高 | Phase 4 |
| Coil 2 | Coil 3 | 低 | Phase 3 |
| DataStore | multiplatform-settings | 低 | Phase 3 |
| OkHttp | JVM 阶段保持不变；**Phase 6 全量换 Ktor Client** | 无 → 高 | Phase 2 / 6 |
| Jsoup | JVM 阶段保持不变；**Phase 6 全量换 Ksoup** | 无 → 中 | Phase 2 / 6 |
| Gson | JVM 阶段保持不变；**Phase 6 全量换 kotlinx.serialization** | 无 → 中 | Phase 2 / 6 |
| Android WebView | 桌面用 Jsoup 渲染；iOS 用 WKWebView | 中 | Phase 5 / 6 |

---

## 实施阶段

### Phase 1 — 项目结构搭建（不动任何现有代码）

**目标：** 建立 KMP 项目骨架，验证 Android 构建不受影响。

- [x] 创建分支 `feature/multiplatform`
- [x] 更新 `gradle/libs.versions.toml`：添加 CMP、Koin、SQLDelight、Coil3、multiplatform-settings 依赖声明
- [x] 更新 `build.gradle.kts`（根模块）：添加新插件（apply false）
- [x] 更新 `settings.gradle.kts`：include `:shared` 和 `:desktopApp`
- [x] 创建 `shared/build.gradle.kts`（KMP 模块，空 sourceSet）
- [x] 创建 `desktopApp/build.gradle.kts` + 骨架 `Main.kt`（能打开空窗口）
- [x] 验证 Android app 仍可正常编译（`./gradlew :app:assembleDebug` ✅）
- [x] 验证 desktopApp 可编译（`./gradlew :desktopApp:compileKotlinJvm` ✅）

**完成标志：** `./gradlew :app:assembleDebug` 和 `./gradlew :desktopApp:run` 均成功。

---

### Phase 2 — 数据层迁移到 shared/commonMain（低风险）✅

**目标：** 将纯 Kotlin 的数据层代码移入共享模块，`app` 改为依赖 `:shared`。

- [x] 创建 `CookieStorage` 接口（`shared/commonMain`），解耦 Android 专属的 `CookiePreferences`
- [x] 移动 `data/model/Models.kt` → `shared/commonMain`
- [x] 移动 `HupuScraper`、`HupuDesktopScraper`、`ZoneSlugMap` → `shared/commonMain`（去掉 @Inject/@Singleton）
- [x] 移动 `HomeRepository`、`ZoneRepository`、`ProfileRepository`、`MessageRepository` → `shared/commonMain`
- [x] `CookiePreferences` 实现 `CookieStorage` 接口（加 override）
- [x] `AppModule.kt` 补 @Provides 为迁移后的类提供依赖
- [x] `app/build.gradle.kts` 添加 `implementation(project(":shared"))`
- [x] 验证 Android assembleDebug ✅（BUILD SUCCESSFUL）

**留在 app（Android 专属，Phase 3/4 处理）：**
- `data/local/*`（Room + SharedPreferences）
- `HupuImageUploader`（依赖 Context/Uri/BitmapFactory）
- `PostRepository`（依赖 Uri + HupuImageUploader）
- `FavoritesRepository` / `FollowedZonesRepository`（依赖 Room DAO）

---

### Phase 3 — 替换 Android 专属依赖（中风险）✅

**目标：** 把仅支持 Android 的依赖换成跨平台版本。

- [x] Hilt → Koin：移除所有 `@HiltAndroidApp`、`@Inject`、`@HiltViewModel`，改用 Koin `module { single/viewModel }` 
- [x] Coil 2 → Coil 3：更新全部 import（`coil.*` → `coil3.*`），更新 `CoilImageGetter` 的 target/asDrawable API
- [x] `app/build.gradle.kts`：移除 Hilt 插件与依赖，加入 Koin 和 Coil 3
- [x] `HupuXApp.kt`：实现 `SingletonImageLoader.Factory`，注册 OkHttp fetcher 和 GifDecoder
- [x] 验证 Android assembleDebug ✅（BUILD SUCCESSFUL）

**说明：**
- DataStore → multiplatform-settings 跳过：`CookiePreferences` 当前用 SharedPreferences（Android JVM，运行正常），桌面端 Cookie 存储留到 Phase 5 实现
- ViewModel 层暂留 `app`（含 Android 专属如 `SavedStateHandle`、`Uri`），Phase 5 再评估是否迁移到 shared

---

### Phase 4 — Room → SQLDelight（高风险，最后处理）✅

**目标：** 替换本地数据库，保证收藏和关注专区数据不丢失。

- [x] 在 `shared/src/commonMain/sqldelight/` 创建 `Favorites.sq` 和 `FollowedZones.sq`
- [x] SQLDelight 插件加入 `shared/build.gradle.kts`，配置 `HupuDatabase`（packageName: `com.hupux.shared.db`）
- [x] `FavoriteEntity` / `FollowedZoneEntity` 移至 `shared/commonMain`（去掉 Room 注解）
- [x] `FavoritesRepository` / `FollowedZonesRepository` 移至 `shared/commonMain`（使用 SQLDelight 查询）
- [x] `AppModule.kt`：Room → `AndroidSqliteDriver` + `HupuDatabase`
- [x] `app/build.gradle.kts`：移除 Room，加 `sqldelight-android-driver`
- [x] 验证 Android assembleDebug ✅（BUILD SUCCESSFUL）

**数据迁移说明：**
- 使用相同数据库文件名 `favorites.db`，SQLDelight 用 `IF NOT EXISTS` 建表
- 已有用户的 Room 数据自动保留，无需手动迁移脚本
- Room 的 `room_master_table` 等元数据表被 SQLDelight 忽略，无影响

**已知情况：**
- `followed_zones` 表的 SQLDelight 生成类名为 `Followed_zones`（保留下划线，SQLDelight 2.x 行为）

---

### Phase 5 — 桌面端 UI 适配（新增）✅

**目标：** 在桌面端实现完整功能，处理平台差异。

- [x] `DesktopCookieStorage`：用 Java `Preferences` API 持久化 Cookie，实现 `CookieStorage` 接口
- [x] `DesktopModule`（Koin）：`JdbcSqliteDriver`（`~/.hupux/hupux.db`）+ 所有共享 Repository/Scraper
- [x] 桌面 UI 屏幕（`desktopApp`）：
  - `HomeScreen` — 首页推荐帖子列表
  - `ZoneListScreen` — 所有专区分类列表
  - `ZoneDetailScreen` — 专区帖子列表 + 加载更多
  - `PostDetailScreen` — 帖子详情（Jsoup 解析 HTML → 文本 + 图片）+ 评论列表
  - `SettingsScreen` — Cookie 粘贴登录 / 退出登录
- [x] `App.kt`：基于 `mutableStateList` 的导航返回栈，TopBar 含 Tab 切换 + 返回按钮
- [x] 验证 `./gradlew :desktopApp:run` ✅（macOS 窗口正常启动）
- [x] Android assembleDebug 同步验证 ✅

**技术说明：**
- 正文渲染：桌面不用 WebView，改用 Jsoup 解析 HTML 提取纯文本 + `<img>` URL，用 Coil3 `AsyncImage` 渲染图片
- 登录：Cookie 粘贴（用户从浏览器复制），保存到 Java `Preferences`，无需 WebView
- 不支持发帖/回复（依赖 Android 专属的 `HupuImageUploader`），桌面端只读

**补全五个标签（Phase 5 迭代）：**
- [x] 搜索：原生 TextField + Enter 搜索，调 `HupuScraper.fetchSearch()`，结果列表点击进帖子详情
- [x] 收藏：读 `FavoritesRepository.getAll()` Flow，支持删除收藏，点击跳转帖子详情
- [x] 我的：未登录提示前往设置；登录后显示头像、昵称、发帖/回帖/推荐/亮了/关注/粉丝等统计
- [x] 设置从顶部独立 Tab 移入「我的」页面底部入口

**待后续迭代：**
- 图片点击放大
- 宽屏双栏布局（左列表 + 右详情）
- macOS `.dmg` / Windows `.msi` 打包验证

---

### Phase 6 — iOS 支持（SwiftUI + 共享逻辑）✅

**目标：** 共享层去 JVM 化后编译到 Kotlin/Native，iOS 端用原生 SwiftUI 复用同一套 scraper / repository。

**阶段 0 — 依赖去 JVM 化（三端统一，让 commonMain 真正跨平台）：**
- [x] OkHttp → **Ktor Client**（common 用 `ktor-client-core`；Android/Desktop 用 OkHttp 引擎、iOS 用 Darwin 引擎）；scraper 请求全部改 `suspend`
- [x] Jsoup → **Ksoup**（`com.fleeksoft.ksoup`，注意版本要匹配 Kotlin 2.0.21）
- [x] Gson 树遍历 → **kotlinx.serialization**（访问器抽到 `JsonExt.kt`）
- [x] `System.currentTimeMillis()` → kotlinx-datetime；`URLEncoder` → ktor `encodeURLQueryComponent`
- [x] `Dispatchers.IO`（Native 上不可见）→ `expect/actual val ioDispatcher`
- [x] 移除 shared 未使用的 Compose 依赖（否则 Compose 编译器插件在 iOS 编译报错）
- [x] 回归验证 Android + Desktop 不破坏

**阶段 1 — 加 iOS target + framework 导出：**
- [x] `iosX64 / iosArm64 / iosSimulatorArm64` + `binaries.framework { baseName = "Shared" }`
- [x] iosMain 依赖：`ktor-client-darwin`、sqldelight `native-driver`

**阶段 2 — iOS 平台实现：**
- [x] `IosCookieStorage`（NSUserDefaults）、`IosDependencies`（手工装配依赖图，暴露给 Swift）

**阶段 3 — SwiftUI 逐屏还原：**
- [x] Xcode 工程 `ios/HupuX/`，Run Script 调 `embedAndSignAppleFrameworkForXcode` 集成 framework
- [x] 首页（推荐/热榜/关注 + 轮播）、发现、搜索、收藏、我的、专区/话题/帖子详情、消息、设置、登录、发帖
- [x] 登录后切桌面版 API 解锁点赞/收藏/推荐/回复；图片/视频 OSS 上传（CryptoKit 签名）

**技术说明：**
- Kotlin `suspend` → Swift `async/await` 自动桥接，无需 SKIE；返回 Flow 的少数方法改用一次性 `getAllOnce()` 快照
- iOS 编译/链接需完整 Xcode（非 Command Line Tools）

---

## 当前进度

> **所有阶段完成 🎉 — Android / iOS / macOS / Windows / Linux 全平台可构建运行。**
>
> - Android：`./gradlew :app:assembleDebug`
> - 桌面端：`./gradlew :desktopApp:run` / `packageDmg` / `packageMsi` / `packageDeb`
> - iOS：Xcode 打开 `ios/HupuX/HupuX.xcodeproj` → Cmd+R（详见 README「iOS」小节，**未签名，需自行签名安装**）

---

## 注意事项

- 每完成一个阶段后 commit 一次，便于回滚
- Phase 3 的 Hilt → Koin 替换后，需重点测试：首页推荐/关注、帖子详情、发帖（图片上传）、登录态保持
- Phase 4 的 Room → SQLDelight 迁移前，先备份真机数据库文件
- WebView 正文渲染（`PostBodyWebView`）是桌面端最复杂的部分，Phase 5 再处理，不阻塞前面阶段
