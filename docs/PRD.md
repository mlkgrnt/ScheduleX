# ScheduleX — 产品需求文档 (PRD)

> **版本**: 2.0 | **日期**: 2026-06-13 | **状态**: 定稿

---

## 1. 产品概述

### 1.1 一句话定位
**开源、无广告、LLM 驱动的通用大学课程表 Android 应用。**

### 1.2 背景与动机
WakeUp 课程表被作业帮收购后评分跌至 1.8/5，大量用户流失。中国大学生需要一款：
- 零广告、零收费的课程表应用
- 能自动从教务系统导入课程，无需手动输入
- 不依赖社区维护的解析器（传统方案需为每个学校写专用代码）

### 1.3 核心创新点
**用 LLM 替代传统 HTML 解析器。** 用户登录教务系统后，应用抓取课表页面 HTML，交给 LLM 解析为标准 JSON。这种方式天然支持任何教务系统，无需针对每个学校开发适配器。

---

## 2. 目标用户

| 特征 | 描述 |
|------|------|
| **身份** | 中国大学生（本科/研究生） |
| **痛点** | WakeUp 广告多、收费；手动录入课程繁琐；学校教务系统格式千奇百怪 |
| **技术水平** | 普通用户，能安装 APK + 填 API key |
| **设备** | Android 手机（API 26+，即 Android 8.0+） |

---

## 3. 核心功能

### 3.1 功能优先级

| 优先级 | 功能 | 说明 |
|--------|------|------|
| **P0** | LLM 驱动的课程导入 | WebView 登录 → 抓取 HTML → LLM 解析 → 预览确认 → 导入 |
| **P0** | 课程表展示 | 周视图网格，按周切换，颜色区分课程 |
| **P0** | BYOK LLM 配置 | 用户配置自己的 OpenAI/DeepSeek/通义千问 API key |
| **P0** | 手动添加/编辑课程 | 不依赖 LLM 的兜底方案 |
| **P1** | 上课提醒 | 课前 15/30 分钟通知，可自定义 |
| **P1** | 桌面小组件 | 今日课程 Widget |
| **P1** | 深色模式 | 跟随系统或手动切换 |
| **P2** | 课程详情页 | 点击课程查看完整信息 |
| **P2** | 多课表支持 | 不同学期/辅修课表切换 |
| **P2** | 数据导入/导出 | JSON 文件备份恢复 |
| **P3** | 路由市场 | 社区分享 WebView 登录脚本（简化导航流程） |
| **P3** | 附件/笔记 | 为课程添加笔记、文件附件 |

### 3.2 不做清单
- ❌ 空教室查询（各校 API 差异太大）
- ❌ 成绩查询（隐私风险）
- ❌ 社交/分享功能
- ❌ 内置广告、收费、会员体系

---

## 4. LLM 课程导入流程（核心）

### 4.1 完整流程

```
┌─────────────────────────────────────────────────┐
│ Step 1: 配置 LLM                                 │
│   用户输入 API key → 选择 provider → 验证连通性    │
│   (首次使用时引导，之后跳过)                        │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 2: 选择学校                                  │
│   搜索框选择学校 → 自动匹配教务系统类型              │
│   → 加载对应 WebView 导航脚本（如有）               │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 3: WebView 登录                             │
│   打开教务系统登录页 → 用户自行输入账号密码登录      │
│   → 自动/手动导航到「个人课表」页面                  │
│   (提供"自动导航"按钮，也允许用户手动操作)           │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 4: 抓取课表 HTML                            │
│   JS 注入 → 提取课表区域的 HTML                    │
│   → 智能精简：只保留课表相关标签和文本               │
│   → 去除广告、导航栏、页脚等无关内容                │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 5: LLM 解析                                 │
│   构造 Prompt：System 指令 + HTML 内容             │
│   → 发送给 LLM API                               │
│   → LLM 返回标准课程 JSON                         │
│   → 解析 JSON，容错处理                           │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 6: 预览与确认                               │
│   展示解析结果列表（课程名、时间、地点、教师）       │
│   → 用户可逐条检查、删除、编辑                     │
│   → 确认无误后点击「导入」                         │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│ Step 7: 写入数据库                               │
│   预览数据 → Room 写入 CourseBase + CourseDetail   │
│   → 去重合并（同名课程合并时间槽）                  │
│   → 返回课表主界面展示                            │
└─────────────────────────────────────────────────┘
```

### 4.2 HTML 提取策略

JS 注入脚本负责从课表页面提取干净的 HTML：

```javascript
// 提取策略（按优先级）
1. 查找特定 ID/Class：#kbtable, .kbcontent, #courseTable 等
2. 查找最大 <table>：如果无特定标记，取页面中最大的表格
3. 保留结构：<table>, <tr>, <td>, <th>, <div>, <span>, <br>
4. 去除噪音：<script>, <style>, <nav>, <footer>, 广告 div
5. 保留关键属性：title（常含教师/教室信息）, colspan, rowspan
6. 精简文本：去除多余空白，保留换行结构
7. 限制大小：如果 HTML > 15KB，进一步裁剪（只保留课表区域）
```

### 4.3 LLM Prompt 设计

**System Prompt（固定）：**

```
你是一个课程表解析助手。用户会给你一个教务系统课表页面的 HTML 片段。
请从中提取所有课程信息，返回一个 JSON 数组。

输出格式（严格遵循，不要添加任何其他文本）：
[
  {
    "name": "课程名称",
    "teacher": "教师姓名（如有）",
    "location": "上课地点（如有）",
    "day": 1,           // 星期几（1=周一，7=周日）
    "startPeriod": 1,   // 开始节次
    "endPeriod": 2,     // 结束节次
    "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],  // 上课周次列表
    "type": "all"       // all=每周, odd=单周, even=双周
  }
]

注意：
- 如果 HTML 中有「单周」「双周」「奇数周」「偶数周」等标记，设置对应的 type
- 如果周次是连续的（如 5-16 周），展开为数组 [5,6,7,...,16]
- 如果某课程在多个时间段上课，每个时间段单独一条记录
- day 的取值：周一=1, 周二=2, ..., 周日=7
- 如果无法确定某个字段，使用 null
- 只输出 JSON 数组，不要输出其他任何内容
```

**User Prompt 模板：**

```
请解析以下课表 HTML：

```html
{extracted_html}
```
```

### 4.4 错误处理

| 场景 | 处理方式 |
|------|----------|
| LLM 返回非法 JSON | 尝试提取 JSON 子串重新解析；失败则提示重试 |
| LLM 返回空数组 | 提示"未找到课程"，建议检查是否在课表页面 |
| API key 无效 | 提示重新配置 |
| 网络超时 | 提示重试，支持重试按钮 |
| 解析结果明显错误 | 用户可在预览页手动编辑/删除错误条目 |
| HTML 太大（>20KB） | 自动裁剪，只保留课表核心区域 |

---

## 5. 技术架构

### 5.1 技术栈

| 层级 | 技术 |
|------|------|
| **语言** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **架构** | MVVM (ViewModel + StateFlow) |
| **本地存储** | Room (SQLite) |
| **偏好存储** | DataStore |
| **网络** | Ktor Client (LLM API) / WebView (教务系统) |
| **JSON** | Kotlinx Serialization |
| **最低 API** | Android 8.0 (API 26) |
| **目标 API** | Android 14 (API 34) |

### 5.2 模块划分

```
ScheduleX/
├── app/
│   ├── src/main/
│   │   ├── java/com/schedulex/
│   │   │   ├── data/
│   │   │   │   ├── db/              # Room 数据库
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── CourseDao.kt
│   │   │   │   │   └── TableDao.kt
│   │   │   │   ├── model/           # 数据模型
│   │   │   │   │   ├── Course.kt
│   │   │   │   │   ├── TimeSlot.kt
│   │   │   │   │   └── School.kt
│   │   │   │   └── repository/      # 数据仓库
│   │   │   │       └── CourseRepository.kt
│   │   │   ├── llm/                 # LLM 服务层
│   │   │   │   ├── LlmService.kt        # 通用 LLM 调用接口
│   │   │   │   ├── LlmConfig.kt         # Provider 配置模型
│   │   │   │   ├── providers/
│   │   │   │   │   ├── OpenAiProvider.kt
│   │   │   │   │   ├── DeepSeekProvider.kt
│   │   │   │   │   ├── QwenProvider.kt
│   │   │   │   │   └── CustomProvider.kt  # 兼容 OpenAI 格式
│   │   │   │   └── HtmlExtractor.kt     # HTML 精简逻辑
│   │   │   ├── import/              # 导入流程
│   │   │   │   ├── ImportViewModel.kt
│   │   │   │   ├── WebViewScreen.kt     # WebView 登录+导航
│   │   │   │   ├── PreviewScreen.kt     # 解析结果预览
│   │   │   │   └── SchoolDatabase.kt    # 学校信息
│   │   │   ├── ui/
│   │   │   │   ├── home/            # 课表主界面
│   │   │   │   ├── course/          # 课程编辑
│   │   │   │   ├── settings/        # 设置页
│   │   │   │   ├── theme/           # Material 3 主题
│   │   │   │   └── components/      # 通用组件
│   │   │   ├── widget/              # 桌面小组件
│   │   │   ├── reminder/            # 上课提醒
│   │   │   └── navigation/          # 导航路由
│   │   └── assets/
│   │       ├── school_data.json     # 学校+教务系统数据库
│   │       └── extract_schedule.js  # 课表 HTML 提取脚本
│   └── build.gradle.kts
├── docs/
│   └── PRD.md
└── README.md
```

### 5.3 数据模型

```kotlin
// === 核心实体 ===

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 课程名
    val teacher: String?,          // 教师
    val color: String,             // 显示颜色 (hex)
    val tableId: Long = 1          // 所属课表
)

@Entity(tableName = "time_slots",
    foreignKeys = [ForeignKey(Course::class, ...)])
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,            // FK → Course
    val day: Int,                  // 1-7 (周一-周日)
    val startPeriod: Int,          // 开始节次
    val endPeriod: Int,            // 结束节次
    val location: String?,         // 上课地点
    val weeks: List<Int>,          // 上课周次 [1,2,3,...,16]
    val type: WeekType             // ALL, ODD, EVEN
)

enum class WeekType { ALL, ODD, EVEN }

// === LLM 配置 ===

@Entity(tableName = "llm_configs")
data class LlmConfig(
    @PrimaryKey val provider: String,  // "openai", "deepseek", "qwen", "custom"
    val apiKey: String,                // 加密存储
    val baseUrl: String,               // API 基础 URL
    val model: String,                 // 模型名称
    val isActive: Boolean = true
)

// === 学校信息 ===

@Entity(tableName = "schools")
data class School(
    @PrimaryKey val code: String,      // 学校代码
    val name: String,                  // 学校名称
    val systemType: String,            // "qiangzhi", "zhengfang", "urp", etc.
    val loginUrl: String,              // 教务系统登录 URL
    val scheduleUrl: String?,          // 直达课表页 URL（如有）
    val navScript: String?             // 自动导航 JS 脚本（可选）
)
```

### 5.4 LLM Provider 接口

```kotlin
interface LlmProvider {
    val name: String
    val defaultBaseUrl: String
    val supportedModels: List<String>

    suspend fun chat(
        systemPrompt: String,
        userMessage: String,
        config: LlmConfig
    ): String  // 返回 LLM 响应文本

    suspend fun validateKey(config: LlmConfig): Boolean  // 验证 API key
}
```

---

## 6. BYOK 模式

### 6.1 设计原则
- **开发阶段**：使用开发者内置 API key（环境变量注入，不写入代码）
- **发布阶段**：无内置 key，用户必须自行配置
- **密钥安全**：API key 存储在 EncryptedSharedPreferences，不明文保存

### 6.2 支持的 Provider（v1.0）

| Provider | Base URL | 推荐模型 | 说明 |
|----------|----------|----------|------|
| **OpenAI** | `https://api.openai.com/v1` | `gpt-4o-mini` | 通用，略贵 |
| **DeepSeek** | `https://api.deepseek.com/v1` | `deepseek-chat` | 便宜，中文好 |
| **通义千问** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | 国内直连 |
| **自定义** | 用户填写 | 用户填写 | 兼容 OpenAI API 格式 |

### 6.3 首次使用引导

```
首次打开 App
    ↓
欢迎页 → "开始使用"
    ↓
LLM 配置页
    ↓
┌──────────────────────────────────┐
│  选择 LLM Provider               │
│                                  │
│  [DeepSeek] ← 推荐，便宜好用     │
│  [OpenAI]                        │
│  [通义千问]                      │
│  [自定义]                        │
│                                  │
│  API Key: [________________]     │
│  模型:    [deepseek-chat    ▼]  │
│                                  │
│  [验证并继续]                    │
└──────────────────────────────────┘
    ↓
验证通过 → 进入课表主页
```

---

## 7. UI 设计

### 7.1 页面结构

```
App
├── 📅 课表主页 (HomeScreen)
│   ├── 周视图网格（主内容）
│   ├── 顶部：当前周次 + 左右切换
│   ├── 底部：Tab 导航栏
│   └── FAB：添加课程 / 导入课表
│
├── 📥 课程导入 (ImportFlow)
│   ├── 学校选择页
│   ├── WebView 登录页
│   ├── 解析预览页
│   └── 导入成功页
│
├── ✏️ 课程编辑 (EditScreen)
│   ├── 基本信息（名称、教师、颜色）
│   └── 时间安排（星期、节次、周次、地点）
│
├── ⚙️ 设置 (SettingsScreen)
│   ├── LLM 配置（Provider、Key、模型）
│   ├── 外观（深色模式、主题色）
│   ├── 提醒（提前时间、铃声）
│   ├── 数据管理（导入/导出、清除）
│   └── 关于（版本、开源地址）
│
└── 🧩 桌面小组件 (Widget)
    ├── 今日课程列表
    └── 当前课程卡片
```

### 7.2 课表网格设计

```
     │ 周一  │ 周二  │ 周三  │ 周四  │ 周五  │ 周六  │ 周日  │
─────┼───────┼───────┼───────┼───────┼───────┼───────┼───────┤
第1节│  8:00 │       │       │       │       │       │       │
第2节│  8:50 │ █████ │       │       │       │       │       │
─────┼───────┤ █████ │       │       │       │       │       │
第3节│ 10:00 │ 神经  │       │       │       │       │       │
第4节│ 10:50 │ 网络  │ ████ │       │       │       │       │
─────┼───────┼───────┤ ████ │       │       │       │       │
第5节│ 11:40 │       │ 深度 │       │       │       │       │
     │       │       │ 学习 │       │       │       │       │
─────┼───────┼───────┼───────┼───────┼───────┼───────┼───────┤
第6节│ 14:00 │       │       │       │       │       │       │
第7节│ 14:50 │       │       │       │       │██████ │       │
─────┼───────┼───────┼───────┼───────┼───────┤██████ │       │
第8节│ 16:00 │       │       │       │       │ 足球  │       │
第9节│ 16:50 │       │       │       │██████ │ 俱乐部│       │
─────┼───────┼───────┼───────┼───────┤██████ ├───────┼───────┤
第10节│ 19:00│       │       │       │  足球 │       │       │
第11节│ 19:50│       │       │       │  俱乐部│      │       │
─────┴───────┴───────┴───────┴───────┴───────┴───────┴───────┘
```

- 课程卡片用不同颜色区分
- 点击卡片查看详情（教师、地点、周次）
- 长按卡片进入编辑
- 顶部显示「第 X 周」，可左右滑动切换

---

## 8. 学校数据库

### 8.1 数据结构

```json
{
  "schools": [
    {
      "code": "10560",
      "name": "汕头大学",
      "province": "广东",
      "systemType": "qiangzhi",
      "loginUrl": "https://jwc.stu.edu.cn/",
      "scheduleUrl": null,
      "navScript": "document.querySelector('a[href*=kbtable]')?.click();"
    }
  ],
  "systemTypes": [
    {
      "id": "qiangzhi",
      "name": "强智教务",
      "loginPatterns": ["用户名", "密码", "验证码"],
      "schedulePatterns": ["kbtable", "kbcontent"]
    },
    {
      "id": "zhengfang",
      "name": "正方教务",
      "loginPatterns": ["TextBox1", "TextBox2"],
      "schedulePatterns": ["xskbcx", "Table1"]
    },
    {
      "id": "urp",
      "name": "URP 教务",
      "loginPatterns": ["j_username", "j_password"],
      "schedulePatterns": ["courseName", "classDay"]
    }
  ]
}
```

### 8.2 数据来源
- 初始内置 Top 100 高校
- 用户可手动添加/编辑学校信息
- 未来可接入社区贡献的学校数据库

---

## 9. 开发里程碑

### Phase 1: 核心骨架（Week 1-2）
- [x] 项目脚手架（Compose + Room + Navigation）
- [ ] 数据模型（Course, TimeSlot, LlmConfig）
- [ ] Room 数据库 + DAO
- [ ] 课表主页 UI（静态展示）
- [ ] 课程手动添加/编辑

### Phase 2: LLM 导入（Week 3-4）
- [ ] LLM Provider 抽象层
- [ ] OpenAI / DeepSeek Provider 实现
- [ ] HTML 提取 JS 脚本
- [ ] LLM Prompt + JSON 解析
- [ ] BYOK 配置页
- [ ] WebView 登录 + 课表页导航
- [ ] 解析预览页

### Phase 3: 完善体验（Week 5-6）
- [ ] 上课提醒（WorkManager + Notification）
- [ ] 桌面小组件（Glance）
- [ ] 深色模式
- [ ] 数据导入/导出（JSON）
- [ ] 错误处理和边界情况

### Phase 4: 打磨发布（Week 7-8）
- [ ] 学校数据库（Top 100）
- [ ] 首次使用引导流程
- [ ] 性能优化
- [ ] README + 开源文档
- [ ] F-Droid / GitHub Release

---

## 10. 开放问题

| # | 问题 | 待定方案 |
|---|------|----------|
| 1 | 课表 HTML 提取的通用性？不同系统 DOM 结构差异大，JS 提取脚本是否需要按系统类型定制？ | 先用通用提取（最大 table），失败再用定制脚本 |
| 2 | LLM 解析准确率？幻觉/遗漏怎么办？ | 预览页让用户确认 + 保留手动编辑能力 |
| 3 | API key 存储安全？ | EncryptedSharedPreferences，不上传不备份 |
| 4 | 国内用户无法访问 OpenAI？ | 优先推荐 DeepSeek/通义千问 |
| 5 | 课表 HTML 包含敏感信息（学号等）？ | JS 提取时只保留课表区域，去除个人信息 |
| 6 | 是否需要后端服务？ | v1.0 纯客户端，无后端 |
| 7 | 路由市场怎么实现？ | v2.0 考虑 GitHub repo 或独立服务 |

---

## 附录 A: 竞品对比

| 特性 | ScheduleX | WakeUp | 超级课程表 | 小日常 |
|------|-----------|--------|-----------|--------|
| 开源 | ✅ Apache-2.0 | ❌ 被收购 | ❌ | ❌ |
| 无广告 | ✅ | ❌ 大量 | ❌ | ✅ |
| 自动导入 | ✅ LLM | ✅ 传统解析 | ✅ | ❌ |
| 通用性 | ✅ 任何学校 | ⚠️ 需解析器 | ⚠️ 有限 | N/A |
| 免费 | ✅ 永久免费 | ❌ 部分收费 | ❌ | ✅ |
| 深色模式 | ✅ | ✅ | ✅ | ✅ |

## 附录 B: 参考资源

- [WakeUp Schedule Kotlin (fork)](https://github.com/tKM9WsmQUaUgNttn3DGUsHkxG8/WakeupSchedule_Kotlin)
- [CourseAdapter (parser library fork)](https://github.com/NYIST-CIPS/CourseAdapter)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)
- [DeepSeek API 文档](https://platform.deepseek.com/api-docs)
- [通义千问 API 文档](https://help.aliyun.com/zh/dashscope/)
