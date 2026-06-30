# 小说编码转换

将 TXT 小说转为纯 GBK 编码，适配老旧电子书阅读器。

## 方案一：浏览器版（电脑）
`standalone.html` 单个文件，拖进浏览器即用，无需安装任何东西。

## 方案二：Android APK（手机）

APK 包 — 给朋友用的，手机上操作。

### 安装

#### GitHub Actions
每次 push 到 `main` 自动构建，去 [Actions](https://github.com/nxd-xgj/novel-converter/actions) 下载 APK 产物。

### Windows 一键安装
```powershell
# 下载最新 APK
curl -L -o novel-converter.apk https://github.com/nxd-xgj/novel-converter/releases/latest/download/app-release.apk
# 安装（需 ADB 连接手机）
adb install novel-converter.apk
```

### 手动
直接下载 [Release](https://github.com/nxd-xgj/novel-converter/releases) 中的 APK，传到手机安装。

## 使用方法

### 浏览器版
1. 下载 `standalone.html`
2. 浏览器打开
3. 拖入 TXT 文件 → 开始转换 → 下载 ZIP

### Android 版

1. 点选/多选 TXT 文件
2. 等待编码检测完成
3. 点「开始」→ 转换后自动弹出分享，发送 ZIP

## 构建

```bash
cd android
./gradlew assembleRelease
```
APK 输出至 `app/build/outputs/apk/release/`。

## 技术栈

Kotlin + Material 3 + Coroutines + juniversalchardet + GitHub Actions CI
