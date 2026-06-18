# 虎扑 X

一个基于 Kotlin Multiplatform + Compose Multiplatform 的虎扑第三方客户端，同时支持 **Android** 和 **桌面端（macOS / Windows / Linux）**。界面简洁、无广告、无推送骚扰。

---

<img src="screenshot.png" width="600" alt="首页截图">

---

## 功能

### 首页
- **推荐**：从虎扑首页聚合热门帖子，顶部轮播图展示图文内容（Android）
- **关注**：汇总所有已关注专区的最新动态，按时间排序
- 右上角设置入口（Android）

### 发现
- 浏览全部专区分类（篮球、足球、综合等）
- 进入专区查看帖子列表，支持加载更多
- **发帖（需要登录）**：进入任意专区后显示发帖入口，填写标题（4-40 字）和正文即可发布；支持插图上传

### 搜索
- 关键词搜索帖子（桌面版支持回车触发）

### 收藏
- 一键收藏帖子，本地持久化存储，离线可用

### 帖子详情
- 正文完整显示图片（含虎扑自定义 `<center class="hupu-img">` 格式）
- Android：WebView 渲染正文，支持视频（HTML5 `<video>`）；图片点击全屏查看（双指缩放 / 平移 / GIF 循环播放）
- 评论列表：显示用户头像、楼主标识、引用回复（桌面端登录后可见）、点赞数
- 评论支持**倒序**显示（登录后全量加载再倒序，未登录仅对当前页倒序）
- 点击「X 条回复」展开子回复面板，支持无限递归嵌套展开（Android & 桌面端登录后）
- Android：正文 / 评论图片点击全屏查看，支持双指缩放；**长按图片可保存至相册**
- 本地收藏 / 取消收藏
- Android：分享帖子链接至其他应用；大图查看器内置保存按钮可直接保存至相册
- **收藏（需要登录）**：主贴下方「收藏」按钮直接同步至虎扑账号，再次点击取消收藏
- **推荐（需要登录）**：主贴下方「推荐」按钮，已推荐显示灰色
- **回复（需要登录）**：点击评论卡片上的「回复」按钮发送回复；支持引用指定评论；桌面版底部常驻回复输入框

### 我的（需要登录）
- 头像、昵称、等级、地区、注册时长
- **第一行统计（不可点击）**：关注 / 粉丝 / 赞 / 声望
- **第二行统计（点击进入列表）**：发帖 / 回帖 / 推荐 / 收藏（显示虎扑账号收藏数）
- **我的发帖**：本人发布的所有帖子，含专区标签、回复数，分页加载
- **我的回帖**：本人所有回帖内容，含引用块，分页加载
- **我的推荐**：本人推荐过的帖子，分页加载
- **我的收藏**：从虎扑服务端实时拉取的收藏帖子列表，分页加载
- **我关注的专区**：横滑展示，点击直接进入专区列表
- **消息中心**：提到我的 / 评论 / 亮了·推荐，显示发送者、内容摘要、所在帖子；有未读时消息入口显示红色气泡数字

### 导航体验
- Android：底部红色渐变导航栏 + 白色胶囊按钮；双击 Tab 平滑滚回顶部；二次返回退出
- 桌面端：顶部红色渐变导航栏 + 白色胶囊按钮（与安卓风格统一）；支持多级页面返回

### 登录
- Android：内嵌 WebView 登录页，登录成功后自动提取 Cookie
- 桌面端 & Android：在设置中直接粘贴从浏览器复制的 Cookie 字符串

---

## 技术栈

### 共享（Android + 桌面端）

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin Multiplatform |
| UI | Compose Multiplatform + Material 3 |
| 网络 | OkHttp |
| 数据解析 | Gson（JSON API）、Jsoup（HTML） |
| 本地数据库 | SQLDelight |
| 依赖注入 | Koin |
| 图片加载 | Coil 3 |

### 仅 Android

| 层级 | 技术 |
|---|---|
| 架构 | MVVM（ViewModel + StateFlow） |
| 依赖注入 | Hilt |
| 本地数据库 | Room |
| Cookie 持久化 | DataStore |
| 导航 | Navigation Compose |

### 数据来源

| 接口类型 | 说明 |
|---|---|
| 移动端 SSR（`m.hupu.com`） | 首页、专区列表、专区详情、帖子详情，解析 `__NEXT_DATA__` JSON |
| 桌面端 SSR（`bbs.hupu.com`） | 登录后帖子评论列表（解析 `__NEXT_DATA__`，每页 20 条） |
| 桌面端 REST API（`bbs.hupu.com/api/v2/`） | 子回复列表、帖子收藏 / 取消收藏 |
| 桌面端 REST API（`bbs.hupu.com/pcmapi/`） | 提交回复、发帖、点亮、推荐 |
| 虎扑图床 + 阿里云 OSS | 发帖插图上传（STS 临时凭证直传） |
| 桌面端 REST API（`my.hupu.com/pcmapi/`） | 个人资料、我的发帖 / 回帖 / 推荐、消息中心（需 Cookie） |
| 桌面端 HTML（`my.hupu.com`） | 关注专区列表、消息中心初始数据、我的收藏列表 |

详细 API 文档见 [`doc/api/`](doc/api/README.md)。

---

## 项目结构

```
hupuX/
├── shared/                    # KMP 共享模块（Android + 桌面端）
│   └── src/commonMain/kotlin/com/hupux/
│       ├── data/
│       │   ├── model/         # 数据模型（Post、Zone、Comment、UserProfile 等）
│       │   ├── repository/    # 数据仓库层
│       │   └── scraper/       # HupuScraper（移动端）、HupuDesktopScraper（桌面端）
│       └── CookieStorage.kt
│
├── app/                       # Android 模块
│   └── src/main/java/com/hupux/
│       ├── data/local/        # Room 数据库 + DataStore
│       ├── di/                # Hilt 模块
│       └── ui/
│           ├── home/          # 首页
│           ├── zone/          # 发现 + 专区详情
│           ├── post/          # 帖子详情 + 评论
│           ├── search/        # 搜索
│           ├── favorites/     # 本地收藏
│           ├── profile/       # 我的（含 WebView 登录、我的收藏列表）
│           ├── settings/      # 设置
│           ├── navigation/    # 底部导航 + 路由
│           └── theme/         # 颜色 / 主题
│
└── desktopApp/                # 桌面端模块（Compose Desktop）
    └── src/jvmMain/kotlin/com/hupux/desktop/
        ├── App.kt             # 顶部导航 + 路由
        ├── Main.kt            # 窗口入口
        ├── data/              # DesktopCookieStorage、DesktopImageUploader
        ├── di/                # Koin 模块
        └── ui/
            ├── HomeScreen.kt
            ├── ZoneListScreen.kt
            ├── ZoneDetailScreen.kt
            ├── PostDetailScreen.kt
            ├── SearchScreen.kt
            ├── FavoritesScreen.kt
            ├── ProfileScreen.kt
            ├── SettingsScreen.kt
            ├── MessagesScreen.kt
            ├── UserThreadListScreen.kt
            ├── UserReplyListScreen.kt
            ├── UserRecommendListScreen.kt
            ├── UserFavoriteListScreen.kt
            └── theme/         # AppTheme（颜色 / 字体缩放 / 深色模式）
```

---

## 环境要求

### Android
- Android Studio Hedgehog 或更新版本
- JDK 17
- Android SDK：minSdk **26**（Android 8.0）/ targetSdk **35**

### 桌面端
- JDK 17
- macOS / Windows / Linux

---

## 构建与运行

```bash
# 克隆仓库
git clone https://github.com/bidabrain/hupuX.git
cd hupuX

# Android：构建 Debug 包
./gradlew :app:assembleDebug
# APK 位于 app/build/outputs/apk/debug/

# 桌面端：直接运行
./gradlew :desktopApp:run

# 桌面端：打包（macOS 生成 .dmg，Windows 生成 .msi，Linux 生成 .deb）
./gradlew :desktopApp:packageDmg
./gradlew :desktopApp:packageMsi
./gradlew :desktopApp:packageDeb
```

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
