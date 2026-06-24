# 小说编码批量转换工具

[![Platform](https://img.shields.io/badge/Platform-Termux%20%7C%20Linux%20%7C%20Windows%20%7C%20macOS-green)]()
[![Python](https://img.shields.io/badge/Python-3.8%2B-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

将任意中文编码（UTF-8 / GBK / GB18030 / BIG5 / UTF-16）的 TXT 小说统一转换为**纯 GBK 编码**，让老旧/特殊阅读设备也能正常读取。

## ⚡ 一行命令安装（Termux 安卓手机）

```bash
curl -fsSL https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/setup.sh | bash
```

首次运行自动安装 Python、pip、Flask、git clone 项目，之后每次运行自动 `git pull` 更新。

启动后手机浏览器自动打开 → 拖拽上传 TXT → 一键转换 → 下载 ZIP。

## 📱 Termux 桌面快捷方式（可选）

```bash
mkdir -p ~/.shortcuts
cp termux_shortcut/novel-converter ~/.shortcuts/
chmod +x ~/.shortcuts/novel-converter
# 刷新 Termux Widget 即可在桌面看到图标
```

## 🖥️ PC 端使用

```bash
# 1. 克隆项目
git clone https://github.com/YOUR_USER/YOUR_REPO.git
cd YOUR_REPO

# 2. 安装依赖
pip install flask

# 3. 启动（自动打开浏览器）
python backend.py
```

## 📋 快捷命令

| 命令 | 作用 |
|------|------|
| `novel` | 启动工具（已注册 alias 后） |
| `novel-stop` | 停止服务器 |
| `novel-update` | 拉取最新代码并重启 |

## ⚙️ 工作原理

1. **智能编码检测**：自动识别 UTF-8、GBK、BIG5、UTF-16 等 7 种中文编码
2. **非法字节修复**：扫描并清理 GBK 文件中不兼容的字节序列
3. **安全转换**：UTF-8 → Unicode → 纯 GBK，自动丢弃 Emoji 等无法编码的字符
4. **批量处理**：支持同时上传多个 TXT 文件
5. **ZIP 打包下载**：转换完成后一键下载全部文件

## 📂 项目结构

```
.
├── setup.sh              # Termux 一键安装引导脚本
├── backend.py            # Flask Web 服务器 + 编码引擎
├── templates/
│   └── index.html        # Web 前端界面（拖拽上传 + 进度展示）
├── termux_shortcut/
│   └── novel-converter   # Termux 桌面小组件脚本
└── README.md
```

## 🔧 手动配置

如需修改 GitHub 仓库地址，编辑 `setup.sh` 第 15 行：

```bash
GITHUB_REPO="https://github.com/YOUR_USER/YOUR_REPO.git"
```

改为你自己的仓库地址即可。
