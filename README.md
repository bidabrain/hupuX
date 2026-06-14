# 虎扑 X

一个用 Jetpack Compose 编写的虎扑第三方 Android 客户端，界面简洁、无广告、无推送骚扰。

---

## 功能

### 首页
- **推荐**：从虎扑首页聚合热门帖子，顶部轮播图展示图文内容
- **关注**：汇总所有已关注专区的最新动态，按时间排序
- 右上角设置入口

### 发现
- 浏览全部专区分类（篮球、足球、综合等）
- 进入专区查看帖子列表，支持下拉加载更多

### 搜索
- 关键词搜索帖子

### 收藏
- 一键收藏帖子，本地持久化存储，离线可用

### 帖子详情
- 渲染 HTML 正文内容，支持内联图片、表情包
- 评论列表，显示楼主标识、引用回复、点赞数
- 点击「X 条回复」展开子回复面板（ModalBottomSheet）
- 子回复支持**无限递归展开**，可层层深入查看嵌套回复，并可逐层返回
- 收藏 / 取消收藏

### 我的（需要登录）
- 头像、昵称、等级、地区、注册时长
- 关注 / 粉丝 / 帖子 / 推荐 / 赞 / 声望 统计
- **我的帖子（回帖列表）**：显示本人所有回帖内容，含引用块，触底自动加��
- **我的推荐**：显示本人推荐过的帖子列表，含专区标签，触底自动加载
- **我关注的专区**：横滑展示关注的专区，点击直接进入专区帖子列表
- 点击帖子、回帖、推荐均可跳转到帖子详情页

### 登录与设置
- **WebView 登录**��内嵌浏览器打开虎扑登录页，登录成功后自动提取 Cookie
- **���动 Cookie**：在设置中直接粘贴从浏览器复制的 Cookie 字符串，优先级高于 WebView 登录
- 清除登录一键退出

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 依赖注入 | Hilt |
| 本地数据库 | Room |
| 网络 | OkHttp |
| 数据解析 | Gson（JSON API）、Jsoup（HTML） |
| 图片加载 | Coil |
| 导航 | Navigation Compose |
| Cookie 持久化 | SharedPreferences |

### 数据来源

| 接口类型 | 说明 |
|---|---|
| 移动端 SSR（`m.hupu.com`） | 首页、专区列表、专区详���、帖子详情，解析页面内 `__NEXT_DATA__` JSON |
| 移���端 REST API（`m.hupu.com/api/v2/`） | 子回复列表 |
| 桌面端 REST API（`my.hupu.com/pcmapi/`） | 个人资料、我的回帖、我的推荐、我关注的专区（需 Cookie） |
| 桌面端 HTML（`my.hupu.com`） | 关注专区列表，用 Jsoup 解析 DOM |

桌面端 slug 与移动端 topicId 的对应关系���254 个专区）通过解析 `www.hupu.com` 的 `hotSearchData` 生成并硬编码，无需运行时额外请求。

---

## 项目结构

```
app/src/main/java/com/hupux/
├── data/
│   ├── local/          # Room 数据库（收藏、关注专区）+ CookiePreferences
│   ├── model/          # 数据模型（Post、Zone、Comment、UserProfile 等）
│   ├── repository/     # 数据仓库层（Home / Post / Zone / Profile / Favorites）
│   └── scraper/
│       ├── HupuScraper.kt          # 移动端数据抓取
│       ├── HupuDesktopScraper.kt   # 桌面端数据抓取（需 Cookie）
│       └── ZoneSlugMap.kt          # 桌面 slug → 移动 topicId 映射表
├── di/
│   └── AppModule.kt    # Hilt 依赖注入模块
└── ui/
    ├── home/           # 首页（推荐 + 关注）
    ├── zone/           # 发现 + 专区详情
    ├── post/           # 帖子详情 + 评论
    ├── search/         # 搜索
    ├── favorites/      # 收藏
    ├── profile/        # 我的（个人资��� + 回帖 + 推荐 + 关注专区 + WebView 登录）
    ├── settings/       # 设置（手动 Cookie）
    ├── navigation/     # 底部导航 + 路由
    └── theme/          # 颜色、主题（支持深色模式）
```

---

## 环境要求

- Android Studio Hedgehog 或更新版本
- JDK 17
- Android SDK：minSdk **26**（Android 8.0）/ targetSdk **35**
- Gradle 8.x（通过项目自带的 Wrapper 自动管理）

---

## 构建与运行

```bash
# 克隆仓库
git clone https://github.com/你的用户名/hupuX.git
cd hupuX

# 直接用 Android Studio 打开，或命令行构建 Debug 包
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

---

## 免责声明

本项目为个人学习用途，数据来源于虎扑（hupu.com）公开页面。请勿用于任何商业目的。
