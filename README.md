# 小说编码批量转换工具

[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Android-green)]()
[![Python](https://img.shields.io/badge/Python-3.8%2B-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Release](https://img.shields.io/github/v/release/nxd-xgj/novel-converter)](https://github.com/nxd-xgj/novel-converter/releases)

任意中文编码（UTF-8/GBK/BIG5/UTF-16）TXT 小说 → **纯 GBK**，适配老旧阅读设备。
逐个字符提取重编码、自动剔除 Emoji/控制字符、空白行压缩、大文件分卷。

## 🚀 一键启动

| 平台 | 方式 | 说明 |
|------|------|------|
| 🪟 **Windows** | 右键 `install.ps1` → 使用 PowerShell 运行 | 自动装 Python + Flask + 下载源码 + 启动 |
| 🐧 **Linux** | 下载 [Release](https://github.com/nxd-xgj/novel-converter/releases) 中的 `NovelConverter` | 单文件 20+MB，开箱即用 |
| 📱 **Android** | Termux 中粘贴一行 | `curl -fsSL https://raw.githubusercontent.com/nxd-xgj/novel-converter/main/setup.sh \| bash` |

启动后浏览器自动打开 `http://localhost:5000` → 拖拽 TXT → 一键转换 → 下载 ZIP。

## 📦 Release 发行版

去 [Releases](https://github.com/nxd-xgj/novel-converter/releases) 下载预编译文件：

- **`NovelConverter`** — Linux x86_64 单文件可执行，无需装 Python
- **`install.ps1`** — Windows PowerShell 一键安装脚本

## ⚙️ 功能

- 🔍 智能解码：遍历 UTF-8/GBK/BIG5/UTF-16 等全部编码，选中文最多的
- 🧹 逐字清洗：每个字符单独试编码 GBK，编不了就扔
- 📉 文本瘦身：连续空行压缩、控制字符剔除
- ✂️ 大文件分卷：超过指定大小自动按段落切分
- 📦 ZIP 打包：转换完一键下载，附带处理报告

## 📂 源码结构

```
├── backend.py          # Flask 后端 + 编码引擎 v2
├── templates/
│   └── index.html      # Web 前端界面
├── setup.sh            # Termux 一键安装脚本
├── install.ps1         # Windows PowerShell 一键安装脚本
├── termux_shortcut/    # Termux 桌面快捷方式
└── requirements.txt    # Python 依赖
```

## 🛠 手动运行

```bash
pip install flask
python backend.py
# 浏览器打开 http://localhost:5000
```
