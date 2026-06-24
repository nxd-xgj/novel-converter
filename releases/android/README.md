# Android 构建指南

## 方法一：Termux 直接运行（推荐，无需编译）

1. 安装 [Termux](https://f-droid.org/packages/com.termux/)（F-Droid 版本）
2. 在 Termux 中运行：
   ```bash
   pkg install -y python
   pip install flask
   cd ~/novel_converter
   python backend.py
   ```
3. 手机浏览器打开 `http://localhost:5000` 即可使用

## 方法二：Buildozer 编译 APK

### 前置条件
- Linux 系统（Ubuntu/Debian 推荐）
- Python 3.8+
- Docker（可选，推荐）

### 步骤

```bash
# 1. 安装 buildozer
pip install buildozer cython

# 2. 进入 android 目录
cd android

# 3. 初始化
buildozer android init

# 4. 修改 buildozer.spec（已提供）

# 5. 编译 APK（首次编译会下载 Android SDK/NDK，约 2-3GB）
buildozer android debug

# 6. 输出文件在 bin/ 目录
ls bin/*.apk
```

### 使用 Docker 编译（推荐，避免污染系统）

```bash
# 使用 kivy/buildozer Docker 镜像
docker run --interactive --tty --rm \
    --volume "$(pwd)/android:/home/user/hostcwd" \
    kivy/buildozer:1.6.0 \
    buildozer android debug
```

## 方法三：手动 Android Studio 项目

1. 使用 Python 的 Chaquopy 插件嵌入 Python 运行时
2. 使用 WebView 加载本地 Flask 服务器
3. 参考 `src/main.py` 中的启动逻辑

## 文件结构

```
android/
├── buildozer.spec          # Buildozer 构建配置
├── install_termux.sh       # Termux 一键安装脚本
├── src/
│   ├── main.py             # Android 启动器（Kivy + Flask）
│   ├── templates/          # 前端页面
│   │   └── index.html
│   └── version.py          # 版本号
└── README.md               # 本文件
```
