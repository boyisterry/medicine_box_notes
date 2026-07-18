# MedicineBoxNotes Android · 家庭药箱与病历本

原生 Android 实现，面向家庭场景集中管理病历、处方、检查附件、常备药、服药打卡与复诊提醒。应用坚持离线优先：业务数据、图片、OCR 和 AI 推理默认留在设备本地。

**中文** | [English](./README.md) · [产品与技术方案](./doc/方案设计文档.md)

## 为什么做这个项目

家庭健康这件事，至今仍停留在「纸质时代」。病历是一叠散落的纸，分散在不同医院、诊所和抽屉里；家里的药箱是个黑盒；而最需要照顾的人，往往最容易忘掉一次药。MedicineBoxNotes 正是为解决这几件日常烦恼而生：

- **病历分散。** 纸质病历容易丢、更难拼起来——一个人的就诊史可能散落在好几家医院，从没有汇集在一处。
- **库存不清。** 买药回家才发现同款在柜子深处还躺着一盒没拆封。清楚地掌握家里到底备了什么药，能避免重复购买。
- **药品过期。** 没有清单就很难知道哪些药已经悄悄过了保质期。应用会跟踪效期并及时提醒。
- **忘记服药。** 慢性病药只有按时吃才有效。可靠的提醒让日常服药不再被遗忘。

于是「药箱」成了一个安静、统一的地方：一眼看到家里备了多少药、随时翻看药品说明书，到点了还能可靠地提醒你按时吃药。

病历和处方是高度私人的内容，因此应用不会把它们上传到云端再去回答你的问题。取而代之的是在设备本地运行一个大型语言模型来完成自然语言问答——你在保有全部数据所有权的同时，依然能基于自己的病历得到快速、可对话的回答。

## 已实现

- 五个完整模块：首页、病历、药箱、查询、设置；采用暖纸色、砖红主色、纸感卡片和浮动五栏导航。
- 首次默认英语，可在设置中即时切换简体中文、日语、法语、德语、西班牙语和韩语；选择会持久化保存。
- Room 数据库与完整 CRUD：家庭成员、病历、附件、处方、药品、服药日志、药盒扫描资源。
- 处方同步到药箱采用软锚定；删除病历或成员不会误删库存和服药计划。
- CameraX 拍照、系统 Photo Picker、私有图片与缩略图存储、ML Kit bundled 中英文 OCR。
- 前一天与当天 09:00 复诊提醒、重启重建、通知跳转；今日服药待办与库存预警。
- 病历 A4 PDF 导出；SAF 加密备份导入/导出（AES-256-GCM、SHA-256 清单、UUID 合并）。
- 规则 AI 始终可用；arm64 设备通过 Hugging Face 单一下载源获取 LiteRT-LM / Gemma，支持断点续传、ETag、SHA-256 校验、GPU→CPU 回退、流式回答和取消。
- AI 风险确认、可选字段归一化、无来源安全门，OCR 原文和来源锚点保留供核对。

## 技术栈与模块

- Kotlin 2.2、JDK 17、Gradle Kotlin DSL、AGP 8.13
- Jetpack Compose + Material 3 + Navigation Compose
- Room + Repository + ViewModel + StateFlow
- CameraX、ML Kit Text Recognition、LiteRT-LM
- 最低 Android 8.0（API 26），编译/目标 API 36

```text
app/                页面、导航、相机、提醒、PDF、备份、模型下载
core-model/         领域模型与库存/服药规则
core-database/      Room 实体、DAO、事务与 Repository
core-designsystem/  设计 Token 与纸感 Compose 组件
core-ai/            OCR、规则 AI、Gemma LiteRT-LM 客户端
doc/                产品方案与原始 Logo/图片资源
```

## 构建与安装

项目需要 JDK 17 与 Android SDK 36。配置 `local.properties` 的 `sdk.dir` 后执行：

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果工作区位于会生成 AppleDouble 文件的外置磁盘，工程已将 Gradle 构建产物定向到 `/private/tmp/medicine-box-notes-build`；Debug APK 位于：

```text
/private/tmp/medicine-box-notes-build/app/outputs/apk/debug/app-debug.apk
```

运行完整验证：

```bash
./gradlew :core-model:test :core-ai:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Release 产物默认未签名，正式发布前请配置私有签名并妥善保管密钥。

## 当前边界

- Gemma 模型约 2.6 GB，不随 APK 打包；无模型或设备条件不足时自动保持规则模式。
- 医疗 OCR/AI 结果仅用于整理和检索，不能替代医生诊断；使用前必须核对原始材料。
- 首版不包含账号、云数据库或跨端同步。
