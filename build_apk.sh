#!/bin/bash
# Android APK 一键编译脚本
# 用法：bash build_apk.sh
# 首次运行会下载 Android SDK/NDK（约 2GB），之后编译只需 2-5 分钟

set -e

echo "========================================"
echo " 小说编码转换 - APK 编译工具"
echo "========================================"
echo ""

# 检查 Python
if ! command -v python3 &>/dev/null; then
    echo "请先安装 Python 3.8+"
    echo "  Ubuntu: sudo apt install python3 python3-pip"
    echo "  macOS:  brew install python3"
    exit 1
fi

# 安装 buildozer
if ! command -v buildozer &>/dev/null; then
    echo "正在安装 buildozer..."
    pip3 install --user buildozer cython
fi

# 安装 Java（Android SDK 需要）
if ! command -v java &>/dev/null; then
    echo "正在安装 Java..."
    if command -v apt-get &>/dev/null; then
        sudo apt-get install -y openjdk-17-jdk
    elif command -v brew &>/dev/null; then
        brew install openjdk@17
    else
        echo "请手动安装 JDK 17+"
    fi
fi

echo ""
echo "开始编译 APK（首次需下载 Android SDK/NDK，约 10-20 分钟）..."
echo ""

buildozer android debug

echo ""
echo "========================================"
if [ -f bin/*.apk ]; then
    APK=$(ls bin/*.apk 2>/dev/null | head -1)
    echo " 编译成功！"
    echo " APK 文件: $APK"
    echo " 大小: $(ls -lh $APK | awk '{print $5}')"
else
    echo " 编译失败，请检查上方错误信息"
fi
echo "========================================"
