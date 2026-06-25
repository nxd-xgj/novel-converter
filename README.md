# 小说编码批量转换工具

[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Android-green)]()
[![Python](https://img.shields.io/badge/Python-3.8%2B-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Release](https://img.shields.io/github/v/release/nxd-xgj/novel-converter)](https://github.com/nxd-xgj/novel-converter/releases)

任意中文编码（UTF-8 / GBK / BIG5 / UTF-16）TXT 小说转为纯 GBK，适配老旧阅读设备。

逐个字符提取重编码，自动剔除 Emoji 和不可见控制字符，连续空行压缩，大文件按章节分卷。

## 一键启动

**Windows**

下载 [install.ps1](https://github.com/nxd-xgj/novel-converter/releases/latest/download/install.ps1)，右键选择「使用 PowerShell 运行」。  
脚本会自动安装 Python、Flask，下载最新源码并启动服务。

**Linux**

下载 [NovelConverter](https://github.com/nxd-xgj/novel-converter/releases/latest/download/NovelConverter)，无需 Python 环境。

```bash
chmod +x NovelConverter
./NovelConverter
```

**Android (Termux)**

```bash
curl -fsSL https://raw.githubusercontent.com/nxd-xgj/novel-converter/main/setup.sh | bash
```

启动后浏览器自动打开 `http://localhost:5000`，拖拽 TXT 文件即可批量转换。

## Release 发行版

前往 [Releases](https://github.com/nxd-xgj/novel-converter/releases) 下载预编译成品：

- `NovelConverter` — Linux x86_64 单文件，自带运行时
- `install.ps1` — Windows PowerShell 一键安装脚本

## 工作原理

1. 解码：遍历 UTF-8 / GBK / BIG5 / UTF-16 等所有编码，取中文字符最多的结果
2. 清洗：逐字尝试编码为 GBK，不兼容的字符丢弃
3. 压缩：连续空行合并为一行，剔除不可见控制字符
4. 分卷：超过指定大小自动在段落边界切分
5. 输出：ZIP 打包下载，内含处理报告

## 手动运行

```bash
pip install flask
python backend.py
```

## 文件结构

```
├── backend.py          Flask 后端与编码引擎
├── templates/
│   └── index.html      Web 界面
├── setup.sh            Termux 安装脚本
├── install.ps1         Windows PowerShell 安装脚本
├── termux_shortcut/    Termux 桌面快捷方式
└── requirements.txt
```
