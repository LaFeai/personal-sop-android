# 个人sop Android App

`个人sop` 是一个本地 Android APK，用来把容易拖延或遗忘的日常动作固定成 SOP。它按模块设置提醒周期、时间窗口和动作清单，通过 Android 精确闹钟唤醒并发送全能消息推送 Bark 通知，再由手机通知同步到手环震动。

这个项目是个人工具，不包含账号、云同步、社区、统计打卡或复杂任务管理功能。

## 初始状态

公开版 APK 首次安装后不会内置任何个人模块：

- 全局 Token 为空，需要用户自己填写。
- 模块数量为 0。
- 首页只提供 `+` 添加入口。
- 不包含作者的真实习惯、提醒时间、频率、提醒文案或动作清单。

## 通知链路

核心闭环：

```text
需要固定执行的动作
  -> App 按模块和周期安排系统精确闹钟
  -> 到点发送全能消息推送 Bark 通知
  -> 手机通知同步到手环震动
  -> 用户完成后在 App 中确认
  -> 当前周期停止持续提醒
```

本项目使用的是“全能消息推送 Bark”，不是官方 Bark `api.day.app` 服务。该服务由第三方提供，当前用户侧 token 服务价格为每月 4 元人民币。App 发送 POST 请求到：

```text
http://www.ggsuper.com.cn/push/api/v1/sendMsg_New.php
```

请求体中包含用户填写的 token、模块标题和提醒文案。请不要把敏感隐私写进提醒标题或文案。

## 功能

- 全局全能消息推送 Bark Token。
- 通过 `+` 新建模块。
- 删除当前模块。
- 模块字段：
  - 模块名称
  - 提醒文案
  - 启用 / 停用
  - 周期类型：每天 / 每周
  - 每周提醒日
  - 开始时间 / 结束时间
  - 提醒间隔
  - 持续提醒直到完成
  - 使用动作清单
  - 测试模式
- 每天模式隐藏每周提醒日。
- 未启用动作清单时隐藏动作清单编辑框。
- 动作清单全部勾选后自动标记本周期完成。
- 诊断/调试入口默认折叠。

## 时间窗口与提醒间隔

开始时间和结束时间定义提醒生效的时间窗口，提醒间隔定义这个窗口内重复提醒的频率。

例如时间窗口是 17:00-19:00，提醒间隔是 5 分钟，App 会在这个 2 小时窗口内按 5 分钟频率重复提醒，而不是只提醒一次。提醒间隔不应由开始/结束时间自动计算。

## 适合的 SOP 场景

适合 SOP 化的动作通常有这些特征：

- 重复发生。
- 做了有长期收益。
- 不做会慢慢变差。
- 每次临时决定会消耗意志力。
- 可以被拆成明确动作。

示例场景使用泛化描述，避免绑定任何人的真实习惯：

```text
示例：出门检查
- 周期：每天
- 时间窗口：出门前固定时间段
- 提醒方式：持续提醒直到完成
- 动作清单：钥匙、手机、钱包、门窗、电器

示例：周末整理
- 周期：每周
- 时间窗口：周末固定时间段
- 提醒方式：持续提醒直到完成
- 动作清单：桌面、垃圾、衣物、文件、补给品
```

## 构建环境

当前项目不是 Gradle 项目。它通过 `scripts/build-debug.ps1` 直接调用 Android SDK 命令行工具构建。

需要：

- Windows PowerShell
- Android Studio JBR
- Android SDK platform
- Android SDK build-tools
- Android SDK platform-tools

## 构建

在项目目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

输出 APK：

```text
build\personal-sop-debug.apk
```

## 安装

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "build\personal-sop-debug.apk"
```

启动：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.codex.personalsop/.MainActivity
```

## 使用

1. 安装 APK。
2. 填写全能消息推送 Bark Token。
3. 点击 `+` 新建模块。
4. 编辑模块名称、提醒文案、周期和时间规则。
5. 按需要开启 `持续提醒直到完成` 或 `使用动作清单`。
6. 点击 `保存`。
7. 按 Android 系统提示授予通知、精确闹钟等权限。

## 数据与隐私

- App 数据保存在本机 Android SharedPreferences。
- App 不做账号登录。
- App 不做云同步。
- App 不上传模块配置到项目作者或自有服务器。
- Bark Token 由用户自行填写并保存在本机。
- 发送提醒时，第三方全能消息推送 Bark 服务会收到 token、模块标题和提醒文案。
- 如果提醒文案包含个人隐私，第三方推送服务可能看到这些内容。

## 已知限制

- 当前只提供本地 APK 和手动构建脚本。
- 当前没有 Gradle、CI、应用商店签名或自动发布流程。
- 当前依赖 Android 精确闹钟权限和厂商后台策略，部分手机系统可能需要额外允许自启动、后台运行和通知权限。
