# HupuX 多平台迁移计划（Android → Android + Windows + macOS）

## 目标

基于 **Compose Multiplatform**，在保持 Android 版功能完整的前提下，新增 Windows 和 macOS 桌面端支持。

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
| OkHttp | 保持不变（JVM 通用） | 无 | Phase 2 |
| Jsoup | 保持不变（JVM 通用） | 无 | Phase 2 |
| Android WebView | 桌面用 JCEF（Java CEF） | 中 | Phase 5 |

---

## 实施阶段

### Phase 1 — 项目结构搭建（不动任何现有代码）

**目标：** 建立 KMP 项目骨架，验证 Android 构建不受影响。

- [ ] 创建分支 `feature/multiplatform` ✅（已完成）
- [ ] 更新 `gradle/libs.versions.toml`：添加 CMP、Koin、SQLDelight、Coil3、multiplatform-settings 依赖声明
- [ ] 更新 `build.gradle.kts`（根模块）：添加新插件（apply false）
- [ ] 更新 `settings.gradle.kts`：include `:shared` 和 `:desktopApp`
- [ ] 创建 `shared/build.gradle.kts`（KMP 模块，空 sourceSet）
- [ ] 创建 `desktopApp/build.gradle.kts` + 骨架 `Main.kt`（能打开空窗口）
- [ ] 验证 Android app 仍可正常编译

**完成标志：** `./gradlew :app:assembleDebug` 和 `./gradlew :desktopApp:run` 均成功。

---

### Phase 2 — 数据层迁移到 shared/commonMain（低风险）

**目标：** 将纯 Kotlin 的数据层代码移入共享模块，`app` 改为依赖 `:shared`。

- [ ] 移动数据模型（`data/model` 下的 data class）→ `shared/commonMain`
- [ ] 移动 Scraper（`HupuScraper`、`HupuDesktopScraper`、`HupuImageUploader`、`ZoneSlugMap`）→ `shared/commonMain`
- [ ] 移动 Repository（所有 `*Repository.kt`）→ `shared/commonMain`（暂时保留 Hilt 注解）
- [ ] `app/build.gradle.kts` 添加 `implementation(project(":shared"))`
- [ ] 验证 Android 功能正常

---

### Phase 3 — 替换 Android 专属依赖（中风险）

**目标：** 把仅支持 Android 的依赖换成跨平台版本。

- [ ] Hilt → Koin：移除 `@HiltAndroidApp`、`@Inject`、`@HiltViewModel`，改用 Koin module
- [ ] Coil 2 → Coil 3：更新 import 和 API 调用
- [ ] DataStore → multiplatform-settings：迁移 Cookie 存储（`CookiePreferences.kt`）
- [ ] ViewModel 层移入 `shared/commonMain`（Koin 注入替代 Hilt）
- [ ] 验证 Android 全功能测试（登录、发帖、收藏、回帖列表等）

---

### Phase 4 — Room → SQLDelight（高风险，最后处理）

**目标：** 替换本地数据库，保证收藏和关注专区数据不丢失。

- [ ] 在 `shared/src/commonMain/sqldelight/` 创建 `.sq` 文件（Favorites、FollowedZones）
- [ ] 实现 Android 驱动（`AndroidSqliteDriver`）和桌面驱动（`JdbcSqliteDriver`）
- [ ] 替换 `FavoritesRepository` 和 `FollowedZonesRepository` 的 Room DAO 调用
- [ ] 添加数据迁移逻辑（从 Room 数据库迁移到 SQLDelight）
- [ ] 验证收藏和关注功能数据读写正确

---

### Phase 5 — 桌面端 UI 适配（新增）

**目标：** 在桌面端实现完整功能，处理平台差异。

- [ ] 将 Compose UI 屏幕迁移到 `shared/commonMain`（Navigation 用 Compose Navigation）
- [ ] 正文渲染：Android 用 WebView，桌面用 JCEF（或 WebView for Compose Desktop）
- [ ] 登录流程：Android 用 WebView，桌面用系统浏览器 + 手动粘贴 Cookie
- [ ] 桌面端窗口布局适配（宽屏双栏等）
- [ ] 打包测试：macOS `.dmg`、Windows `.msi`

---

## 当前进度

> **当前阶段：Phase 1（进行中）**
> 
> 已完成：分支创建
> 
> 下一步：更新 Gradle 配置文件，建立模块骨架

---

## 注意事项

- 每完成一个阶段后 commit 一次，便于回滚
- Phase 3 的 Hilt → Koin 替换后，需重点测试：首页推荐/关注、帖子详情、发帖（图片上传）、登录态保持
- Phase 4 的 Room → SQLDelight 迁移前，先备份真机数据库文件
- WebView 正文渲染（`PostBodyWebView`）是桌面端最复杂的部分，Phase 5 再处理，不阻塞前面阶段
