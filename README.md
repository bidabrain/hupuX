# 虎扑 X

一个基于 **Kotlin Multiplatform** 的虎扑第三方客户端，同时支持 **Android**、**iOS** 和 **桌面端（macOS / Windows / Linux）**。界面简洁、无广告、无推送骚扰。

- **Android / 桌面端**：Compose Multiplatform 界面
- **iOS**：原生 SwiftUI 界面，复用同一套 KMP 业务逻辑（`Shared.framework`）

---

<table>
  <tr>
    <td><img src="doc/screenshots/home.jpg" width="260" alt="首页：今日比分横条 + 推荐流"></td>
    <td><img src="doc/screenshots/discover.jpg" width="260" alt="发现：关注的专区 + 专区网格"></td>
    <td><img src="doc/screenshots/score.jpg" width="260" alt="评分：赛程与球员评分"></td>
  </tr>
  <tr>
    <td align="center">首页</td>
    <td align="center">发现</td>
    <td align="center">评分</td>
  </tr>
</table>

---

## 平台支持

| 功能 | Android | iOS | 桌面端 |
|---|:---:|:---:|:---:|
| 浏览（首页/热榜/关注/专区/话题/搜索） | ✅ | ✅ | ✅ |
| 帖子详情（正文图文/视频 + 评论） | ✅ | ✅ | ✅ |
| 楼中楼递归展开 / 评论排序 | ✅ | ✅ | ✅ |
| 登录（WebView / 粘贴 Cookie） | ✅ | ✅ | ✅ |
| 点赞 / 收藏 / 推荐 / 回复（带图） | ✅ | ✅ | ✅ |
| 深色 / AMOLED 省电主题 + 字号调节 | ✅ | — | — |
| 赛事评分（赛程 / 球员评分 / 技术统计） | ✅ | — | — |
| 打分与评分区评论（五星打分 / 评论 / 回复 / 点亮） | ✅ | — | — |
| 发帖（图片 / 视频，OSS 直传） | ✅ | ✅ | ⚠️ 只读 |
| 我的 / 子列表 / 消息中心 | ✅ | ✅ | ✅ |
| 图片全屏查看 / 长按保存相册 | ✅ | — | — |

---

## 功能

### 首页
- **推荐**：从虎扑首页聚合热门帖子，顶部图片轮播展示图文内容
- **热榜**：展示虎扑热榜话题（按热度排名），点击话题进入其帖子列表，再点帖子进入详情
- **关注**：汇总所有已关注专区的最新动态
- 右上角：搜索 / 设置入口

### 发现
- 浏览全部专区分类（篮球、足球、综合等）
- **5 列网格排布**：共 243 个专区，网格比列表节省约 5 倍纵向空间；每格 logo 右上角有可点击的关注徽标（红底「+」/ 灰底「✓」）
- 进入专区查看帖子列表，支持加载更多
- **关注专区（需要登录）**：专区详情页右上角「关注」按钮
- **发帖（需要登录）**：进入任意专区后显示发帖入口，填写标题和正文即可发布；支持图片 / 视频上传

### 评分
- 首页顶部「今日比分」横条：覆盖西甲/德甲/意甲/法甲/中超等全部联赛（抓虎扑首页内联数据，仅展示不可点）
- 评分页进来**自动定位到今天**的比赛（赛程横跨整季，不定位要滑几个月），当天日期标红
- 评分页 12 个分区看赛程：NBA / CBA / WNBA / CUBA / 英超 / 世界杯 / 网球 / LOL / LPL / LCK / 王者荣耀 / 绝地求生，含双方比分、系列赛大比分、开赛时间
- 点进某场比赛看**全场评分榜**：球员评分、评分人数、评论数，以及得分/篮板/助攻/出场时间等技术统计
- 点进某位球员看**详情页**：平均分、参与人数、技术统计，以及评分区评论
- **打分（需要登录）**：五星制（一星 2 分），可修改也可取消
- **评论（需要登录）**：发评论、回复他人、点亮；评论按时间游标翻页，滑到底自动加载
- 浏览无需登录，只有打分和评论需要

### 搜索
- 关键词搜索帖子
- Android 端入口在**首页 / 发现页顶栏的搜索图标**（不再占用底部导航位）

### 帖子详情
- 正文完整显示图片（含虎扑自定义 `<center class="hupu-img">` 格式）与视频
- Android / iOS：WebView（WKWebView）渲染正文，支持 HTML5 `<video>`
- 评论列表：用户头像、楼主标识、引用回复、点赞数、评论内嵌图片
- **评论排序**：正序 / 倒序 / 最热（按点亮数）
- 点击「X 条回复」展开子回复面板，支持**无限递归嵌套展开**
- **收藏 / 推荐 / 回复 / 点赞（需要登录）**：登录后自动切换桌面版 API 解锁；回复支持插图
- **回复签名**：设置中可自定义回复末尾自动追加的签名
- Android：图片点击全屏查看（双指缩放 / GIF），长按保存至相册

### 我的（需要登录）
- 头像、昵称、等级、地区、注册时长、统计数据
- **我的发帖 / 回帖 / 推荐**：分页加载
- **消息中心**：回复 / @我 / 点亮，三个分类

### 登录
- WebView 登录（登录成功自动提取 Cookie）
- 设置中直接粘贴从浏览器复制的 Cookie 字符串（进入设置会回显当前 Cookie）

### 设置 → 外观
- **主题**：跟随系统 / 浅色 / 深色
- **纯黑背景**：深色下可开启，AMOLED 屏省电防烧屏
- **显示字号**：小 / 标准 / 大 / 特大四挡，只放大文字不放大图标间距

### 设置 → 关于
- **检查更新**：对比 GitHub 最新 release 的版本号，有新版可一键跳转下载页
  （走 release 的 atom feed 而非 GitHub API——后者对未认证请求限流到每 IP 每小时 60 次）

---

## 技术栈

### 共享层（`shared`，编译到 Android / iOS / 桌面端）

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin Multiplatform |
| 网络 | **Ktor Client**（Android/Desktop：OkHttp 引擎；iOS：Darwin 引擎） |
| JSON 解析 | **kotlinx.serialization** |
| HTML 解析 | **Ksoup** |
| 本地数据库 | SQLDelight（Android/Desktop：JDBC/Android driver；iOS：Native driver） |
| 依赖注入 | Koin（Android/Desktop）；iOS 用手工装配 `IosDependencies` |
| 时间 | kotlinx-datetime |

### 各端 UI

| 平台 | UI | 说明 |
|---|---|---|
| Android | Jetpack Compose + Material 3 | Navigation Compose、Coil 3、WebView |
| 桌面端 | Compose Desktop | Coil 3 |
| iOS | **SwiftUI（原生）** | WKWebView（正文/登录）、CryptoKit（OSS 上传签名）、AsyncImage |

> iOS 端通过 `Shared.framework` 调用共享的 scraper / repository；Kotlin 的 `suspend` 函数在 Swift 侧自动桥接为 `async/await`。

### 数据来源

| 接口类型 | 说明 |
|---|---|
| 移动端 SSR（`m.hupu.com`） | 首页、专区、帖子详情，解析 `__NEXT_DATA__` JSON；**未登录默认走此接口** |
| 桌面端 SSR / REST（`bbs.hupu.com`） | **登录后自动切换**：评论列表、子回复、收藏、点赞、推荐、回复、发帖 |
| 桌面端 REST（`my.hupu.com/pcmapi/`） | 个人资料、我的发帖/回帖/推荐、消息中心（需 Cookie） |
| 虎扑图床 + 阿里云 OSS | 发帖 / 回复插图与视频上传（STS 临时凭证直传，OSS V1 手动签名） |

详细 API 文档见 [`doc/api/`](doc/api/README.md)（赛程与评分见 [`match-score.md`](doc/api/match-score.md)）；界面设计规范（颜色 Token / 层级 / 导航 / 网格）见 [`doc/ui-design.md`](doc/ui-design.md)。

---

## 项目结构

```
hupuX/
├── shared/                    # KMP 共享模块（Android / iOS / 桌面端）
│   └── src/
│       ├── commonMain/kotlin/com/hupux/data/
│       │   ├── model/         # 数据模型
│       │   ├── repository/    # 数据仓库层
│       │   └── scraper/       # HupuScraper（移动端）、HupuDesktopScraper（桌面端）
│       ├── androidMain/       # Android 平台实现（ioDispatcher 等）
│       ├── desktopMain/       # 桌面平台实现
│       └── iosMain/kotlin/com/hupux/ios/
│           ├── IosCookieStorage.kt   # NSUserDefaults Cookie 存储
│           └── IosDependencies.kt    # iOS 依赖装配（暴露给 Swift）
│
├── app/                       # Android 模块（Compose）
├── desktopApp/                # 桌面端模块（Compose Desktop）
└── ios/                       # iOS 模块（SwiftUI，独立 Xcode 工程）
    └── HupuX/HupuX/
        ├── HupuXApp.swift / RootView.swift    # 入口 + 底部 Tab
        ├── HomeView / ZoneListView / SearchView / ProfileView
        ├── PostDetailView / ZoneDetailView / TopicDetailView / MessageView
        ├── NewPostView / SettingsView / LoginWebView
        ├── HupuUploader.swift                 # 图片/视频 OSS 上传（CryptoKit 签名）
        └── Deps.swift / Session.swift / Routes.swift / Theme.swift
```

---

## 环境要求

| 平台 | 要求 |
|---|---|
| Android | Android Studio + JDK 17；minSdk 26（Android 8.0）/ targetSdk 35 |
| 桌面端 | JDK 17；macOS / Windows / Linux |
| iOS | **Xcode 16+**（macOS）；部署目标 iOS 16+ |

---

## 构建与运行

```bash
git clone https://github.com/bidabrain/hupuX.git
cd hupuX

# Android：构建 Debug 包（APK 位于 app/build/outputs/apk/debug/）
./gradlew :app:assembleDebug

# 桌面端：运行 / 打包
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDmg   # macOS .dmg
./gradlew :desktopApp:packageMsi   # Windows .msi
./gradlew :desktopApp:packageDeb   # Linux .deb
```

### iOS（⚠️ 无签名，需自行签名安装）

> **iOS 客户端不包含开发者签名。** 本项目未加入 Apple Developer Program，CI 产出的是**未签名的 `.ipa`**，**无法直接安装到 iPhone**，需要你用自己的 Apple ID 自行签名后侧载。

**方式一：Xcode 直装（推荐，免费 Apple ID 即可）**

1. 用 Xcode 打开 `ios/HupuX/HupuX.xcodeproj`
2. 选中 **HupuX** target → **Signing & Capabilities** → 勾选 *Automatically manage signing*，Team 选你自己的 Apple ID（Personal Team）
3. 连接 iPhone，顶部选中你的设备，**Cmd + R** 直接编译安装

> 首次编译会自动通过 Run Script 调 Gradle 生成 `Shared.framework`（`./gradlew :shared:embedAndSignAppleFrameworkForXcode`），无需手动操作。

**方式二：侧载现成的未签名 `.ipa`**

1. 从 [Releases](https://github.com/bidabrain/hupuX/releases) 下载 `HupuX-unsigned.ipa`
2. 用 **Sideloadly** / **AltStore** 等工具，输入你的 Apple ID 签名并安装到 iPhone

> 免费 Apple ID 签名的 App 有效期为 **7 天**，到期需重新签名；付费开发者账号为 1 年。

**命令行编译（模拟器，无需签名）**

```bash
cd ios/HupuX
xcodebuild -project HupuX.xcodeproj -scheme HupuX \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build CODE_SIGNING_ALLOWED=NO
```

---

## 发布流程（CI）

推送到 `main` 后由 GitHub Actions 全自动完成，**不需要手动改版本号**：

1. 纯文档改动（`**.md` / `doc/**` / 仓库配图）**不触发** CI；
2. 触发后先算出新版本号（默认 patch +1，如本次推送自己改了 `appVersion` 则以手动值为准）；
3. 用该版本号构建 Android / macOS(ARM+Intel) / Windows / Linux / iOS 六个产物；
4. **全部成功后**才把版本号写回 `gradle.properties` 与 iOS 的 `project.pbxproj`，
   并发布 GitHub Release `v<版本号>`。构建失败则仓库保持不变，不会留下没有 release 的版本号。

想发 minor/major 版本时，手动把 `gradle.properties` 的 `appVersion` 改成目标版本再推即可
（`appVersionCode` 忘了改也没关系，CI 会自动 +1，保证 Android 能覆盖安装）。

> ⚠️ 提交信息里**不要出现方括号包起来的 skip-ci 指令**（`skip ci` / `ci skip` 那两种写法）。
> GitHub 见到就会跳过整条流水线，于是既不构建也不发版——连"解释这个机制"时顺手写进提交信息
> 都会中招。CI 自己回写版本号的那条提交是故意带它的，用来防止递归触发。

---

## Star History

<a href="https://www.star-history.com/?type=date&repos=bidabrain%2FhupuX">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=bidabrain/hupuX&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=bidabrain/hupuX&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=bidabrain/hupuX&type=date&legend=top-left" />
 </picture>
</a>

---

## 免责声明

本项目为个人学习用途，数据来源于虎扑（hupu.com）公开页面。请勿用于任何商业目的。
