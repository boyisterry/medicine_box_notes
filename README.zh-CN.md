# MedicineBoxNotes · 家庭药箱与病历本

> 一款面向家庭场景的 iOS 健康记录 App，把"家庭成员的病历 / 处方 / 检查附件 / 家中常备药"集中到一处。
> **离线优先、本地可追溯、端侧 AI 辅助整理**——数据和 AI 都锁在本机。

[中文](./README.zh-CN.md) | **English**

📖 完整的产品与技术方案见：[`doc/方案设计文档.md`](./doc/方案设计文档.md)

---

## 它解决什么问题

| 痛点 | App 的应对 |
|---|---|
| 医疗信息分散在纸质单据和聊天记录里，复诊翻不到 | 相机电子化所有单据 → 自动 OCR + AI 结构化 → 按家庭成员归档，支持全文检索 |
| 医生问"之前吃过什么药、看过什么病"答不上来 | 病历按时间倒序、按年份分组，附件可搜索，处方与药箱联动可溯源 |
| 常备药吃完了才发现、过期了还在吃 | 药箱库存管理 + 低库存预警（≤5）+ 服药计划派生今日待办 |
| 复诊总忘 | 设复诊日期后，本地通知在**前一天 9:00 + 当天 9:00** 两次提醒，点通知直达病历 |
| 想问"女儿去年咳嗽吃过什么药"只能手动翻 | 底部"查询"Tab：本地检索召回 + 端侧 Gemma 生成**带引用来源**的回答 |
| 医疗隐私敏感，不敢上传云端做 AI | 默认本地存储；AI 推理**完全在设备端**；唯一联网（DuckDuckGo）只发用户问题、不传任何病历/药品数据 |
| AI 会编造（幻觉），用在医疗上很危险 | 端到端防幻觉设计：提示词硬规则 + JSON 全可选 + "没有/不知道"归一化 + 问答安全门 + 风险确认 + 结果标记"待核对" |

---

## 核心特性

- **三大资产**：病历档案（`MedicalRecord`）、家庭药箱（`MedicineItem`）、家庭成员（`FamilyMember`），以及派生的服药日志（`MedicationLog`）。
- **录入线**：拍照 → Vision OCR → 端侧 AI 结构化抽取 → 用户确认 → 入库。
- **使用线**：首页今日待办 / 复诊提醒 / 低库存 / 自然语言本地问答。
- **5 Tab 形态**：首页 / 病历 / 药箱 / 查询 / 设置（iOS 17+ 原生 SwiftUI）。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 平台 / 语言 | iOS 17+ / Swift 5 |
| UI | SwiftUI（自定义浮动 TabBar、纸感卡片体系） |
| 数据 | SwiftData（`@Model`），兼容 CloudKit 自动同步 |
| OCR | Apple Vision（`VNRecognizeTextRequest`，中英双语） |
| 端侧模型 | Gemma 4 E2B（`.litertlm`，约 2.58GB）+ LiteRT-LM，经 `GemmaLiteRtBridge.xcframework` 桥接 |
| 联网搜索 | DuckDuckGo Instant Answer API（仅查询页手动开启，5s 超时，只发用户问题） |
| 模型分发 | 运行时下载（Hugging Face / HF-Mirror，断点续传） |

---

## 设计原则

1. **离线优先、本地可追溯**：敏感医疗数据默认本机；AI 推理在端侧；联网可选且最小化。
2. **AI 是"受约束的整理器"，不是"自由推理助手"**：只做摘要 / 抽取 / 归纳 / 检索 / 问答，不诊断、不治疗建议、不脑补。
3. **结果必须可核对**：永远保留原图 / OCR 原文，AI 结果标记"待核对"，回答带引用来源。

防幻觉是本项目最具技术含量的部分（提示词硬规则 → 输出校验归一化 → 问答安全门三层设计），详见方案文档 [第四节](./doc/方案设计文档.md)。

---

## 项目结构

```text
medicine_box_notes/
├── MedicineBoxNotes.xcodeproj/
├── Frameworks/
│   └── GemmaLiteRtBridge.xcframework          # LiteRT-LM ObjC++ 桥接（arm64 真机+模拟器）
├── MedicineBoxNotes/
│   ├── MedicineBoxNotesApp.swift               # 入口 + SwiftData 全模型 + 主页面 + 内嵌服务
│   ├── MedicalAI*.swift                        # 端侧 AI 抽象层（门面 / 提示词 / 状态 / 模型）
│   ├── GemmaEngine.swift                       # 端侧推理 actor 引擎
│   ├── Services/                               # 模型下载 / AI 配置 / 联网搜索 / 今日计划 / 图片预处理
│   ├── Views/                                  # HomeView + Settings 下各页面
│   ├── Components/                             # 15 个纸感组件
│   └── Theme/                                  # 设计 token（颜色 / 字体 / 度量 / 成员色板）
├── medicine_box_design/                        # 设计原型（steady + bold 两套方案）
└── doc/
    └── 方案设计文档.md                          # ← 完整方案文档（以此为准）
```

---

## 构建与运行

1. 用 **Xcode** 打开 `MedicineBoxNotes.xcodeproj`。
2. 目标系统 **iOS 17+**，建议 **arm64** 真机或 Apple Silicon 模拟器（端侧模型仅打包 arm64 slice）。
3. 首次使用"查询"等 AI 能力前，需在 **设置 → AI 能力** 下载 Gemma 4 E2B 模型（约 2.58GB，建议 Wi-Fi，需 ≥6GB 可用空间）。
4. 默认无模型时 App 走**规则回退**（Vision OCR + 正则），永不因模型缺失而不可用。

---

## 视觉风格

整体走**"医疗纸本 / 病历册"**的视觉隐喻：米色暖纸底 + 白色大圆角卡片 + 宋体衬线标题 + 砖红主色 + 低饱和成员色点 + 紫色专属 AI 标识 + 浮动毛玻璃 TabBar。刻意避开"冰冷科技蓝"，传递"家庭、关怀、可核对"的气质。完整设计系统见方案文档 [第七节](./doc/方案设计文档.md)。

---

## 已知边界

- 无独立测试 target。
- `MedicineBoxNotesApp.swift` 仍是约 4900 行的单文件大文件，模块化拆分进行中。
- CloudKit 兼容约束：全模型不用 `.unique`、关系默认 nullify，删改需手动级联清理。
- 本地与 iCloud 是两套独立容器，切换 backend 只切换视图、**不自动迁移数据**。
- AI / OCR 结果可能不完整，页面始终保留原图 / OCR 原文供核对。

---

> 本 README 为入口概览。字段名、结构、实现细节一律以 **代码** 与 [`doc/方案设计文档.md`](./doc/方案设计文档.md) 为准。
