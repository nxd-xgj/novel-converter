#!/bin/bash
# Android APK 一键编译脚本（无需 root/sudo，全自动便携环境）
# 用法：bash build_apk.sh

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e " ${GREEN}~${NC} $1"; }
warn() { echo -e " ${CYAN}!${NC} $1"; }
err()  { echo -e " ${RED}x${NC} $1"; exit 1; }

echo ""
echo " ======================================"
echo "  小说编码转换 - APK 一键编译"
echo " ======================================"
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLS_DIR="$PROJECT_DIR/.tools"
mkdir -p "$TOOLS_DIR"

# ── 1. Java 17（便携版，无需 sudo）──
log "检查 Java..."
JAVA_BIN=""

# 先看看系统有没有
if command -v java &>/dev/null; then
    VER=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1)
    if [ "$VER" -ge 17 ] 2>/dev/null; then
        JAVA_BIN="java"
    fi
fi

# 没有就下载便携版
if [ -z "$JAVA_BIN" ]; then
    JDK_DIR="$TOOLS_DIR/jdk-17"
    if [ -f "$JDK_DIR/bin/java" ]; then
        JAVA_BIN="$JDK_DIR/bin/java"
        log "使用已下载的便携 JDK 17"
    else
        log "下载便携版 JDK 17（无需 sudo）..."
        OS=$(uname -s)
        ARCH=$(uname -m)
        case "$OS-$ARCH" in
            Linux-x86_64)  JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz" ;;
            Linux-aarch64) JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.11_9.tar.gz" ;;
            Darwin-x86_64) JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_mac_hotspot_17.0.11_9.tar.gz" ;;
            Darwin-arm64)  JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz" ;;
            *) err "不支持的系统架构: $OS-$ARCH" ;;
        esac

        rm -rf "$JDK_DIR"
        mkdir -p "$JDK_DIR"
        TAR="$TOOLS_DIR/jdk.tar.gz"
        if command -v curl &>/dev/null; then
            curl -L --progress-bar -o "$TAR" "$JDK_URL"
        else
            wget -q --show-progress -O "$TAR" "$JDK_URL"
        fi
        tar xzf "$TAR" -C "$JDK_DIR" --strip-components=1
        rm -f "$TAR"
        JAVA_BIN="$JDK_DIR/bin/java"
        log "JDK 17 就绪: $($JAVA_BIN -version 2>&1 | head -1)"
    fi
else
    log "系统 Java: $(java -version 2>&1 | head -1)"
fi
export JAVA_HOME="$(dirname $(dirname $JAVA_BIN))"
export PATH="$JAVA_HOME/bin:$PATH"

# ── 2. Python ──
log "检查 Python..."
if ! command -v python3 &>/dev/null; then
    err "请先安装 Python 3.8+\n   Ubuntu: sudo apt install python3 python3-pip\n   macOS:  brew install python3\n   CentOS: sudo yum install python3"
fi
log "Python: $(python3 --version)"

# ── 3. buildozer ──
log "检查 buildozer..."
if ! python3 -c "import buildozer" 2>/dev/null; then
    log "安装 buildozer + cython..."
    python3 -m pip install --user buildozer cython -q
fi
log "buildozer: $(python3 -m buildozer --version 2>/dev/null || echo 'ok')"

# ── 4. 必要系统工具 ──
for cmd in git unzip zip; do
    if ! command -v $cmd &>/dev/null; then
        warn "缺少 $cmd，请手动安装"
    fi
done

# ── 5. 编译 ──
cd "$PROJECT_DIR"
log "开始编译 APK..."
echo ""
echo " 首次编译会下载 Android SDK+NDK（约 2-3GB），需 10-20 分钟"
echo " 之后增量编译只需 2-5 分钟"
echo ""

python3 -m buildozer android debug

echo ""
if ls bin/*.apk 2>/dev/null; then
    APK=$(ls -t bin/*.apk 2>/dev/null | head -1)
    log "编译成功"
    log "输出: $APK"
    log "大小: $(ls -lh "$APK" | awk '{print $5}')"
else
    err "编译失败，检查上方输出"
fi
