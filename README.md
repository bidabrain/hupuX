# 虎扑 X

一个用 Jetpack Compose 编写的虎扑第三方 Android 客户端，界面简洁、无广告、无推送骚扰。

---

## 功能

### 首页
- **推荐**：从虎扑首页聚合热门帖子，顶部轮播图展示图文内容
- **关注**：汇总所有已关注专区的最新动态，按时间排序

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

### 数据来源

通过解析虎扑移动端页面的 `__NEXT_DATA__` JSON 以及 REST API 获取数据，无需登录即可浏览。

---

## 项目结构

```
app/src/main/java/com/hupux/
├── data/
│   ├── local/          # Room 数据库（收藏、已关注专区）
│   ├── model/          # 数据模型（Post、Comment、Zone 等）
│   ├── repository/     # 数据仓库层
│   └── scraper/        # HupuScraper：网页 / API 数据抓取
├── di/
│   └── AppModule.kt    # Hilt 依赖注入模块
└── ui/
    ├── home/           # 首页（推荐 + 关注）
    ├── zone/           # 发现 + 专区详情
    ├── post/           # 帖子详情 + 评论
    ├── search/         # 搜索
    ├── favorites/      # 收藏
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
