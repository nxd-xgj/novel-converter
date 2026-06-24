#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════
#  小说编码转换工具 - Termux 一键引导脚本
#  用法（在 Termux 中粘贴一行）：
#    curl -fsSL https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/setup.sh | bash
#
#  功能：
#    首次运行 → 自动安装 Python+pip → 装 Flask → git clone 项目
#    再次运行 → git pull 更新 → 检查环境 → 直接启动
#    任何时候 → 自动打开手机浏览器到 http://localhost:5000
# ═══════════════════════════════════════════════════════════════

set -e

# ─── 配置（请修改为你的 GitHub 仓库地址）─────────────────
GITHUB_REPO="https://github.com/YOUR_USER/YOUR_REPO.git"
PROJECT_NAME="novel-converter"
PROJECT_DIR="$HOME/$PROJECT_NAME"
SERVER_PORT=5000

# ─── 颜色 ─────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

banner() {
    echo ""
    echo -e " ${CYAN}╔══════════════════════════════════════════════╗${NC}"
    echo -e " ${CYAN}║${NC}   ${BOLD}📖 小说编码批量转换工具${NC}                      ${CYAN}║${NC}"
    echo -e " ${CYAN}║${NC}   UTF-8 / GBK / BIG5 / UTF-16 → 纯 GBK     ${CYAN}║${NC}"
    echo -e " ${CYAN}╚══════════════════════════════════════════════╝${NC}"
    echo ""
}

log()  { echo -e " ${GREEN}✓${NC} $1"; }
warn() { echo -e " ${YELLOW}⚠${NC} $1"; }
err()  { echo -e " ${RED}✗${NC} $1"; exit 1; }
info() { echo -e " ${CYAN}→${NC} $1"; }

# ─── 环境检测 ──────────────────────────────

check_termux() {
    if [ ! -d /data/data/com.termux/files/usr ]; then
        err "请在 Termux 中运行此脚本\n  下载: https://f-droid.org/packages/com.termux/"
    fi
    log "Termux 环境已确认"
}

# ─── 安装依赖 ──────────────────────────────

install_python() {
    if command -v python3 &>/dev/null || command -v python &>/dev/null; then
        log "Python 已安装: $(python3 --version 2>/dev/null || python --version 2>/dev/null)"
        return 0
    fi
    
    info "正在安装 Python..."
    pkg update -y -o Dpkg::Options::="--force-confdef" 2>/dev/null
    pkg install -y python 2>/dev/null || {
        warn "pkg install 失败，尝试用 apt..."
        apt-get update -y 2>/dev/null
        apt-get install -y python 2>/dev/null
    }
    
    if command -v python3 &>/dev/null; then
        log "Python 安装成功: $(python3 --version)"
    else
        err "Python 安装失败，请手动执行: pkg install python"
    fi
}

install_pip_deps() {
    info "检查 Python 依赖..."
    
    # 确保 pip 可用
    python3 -m pip --version &>/dev/null || {
        info "安装 pip..."
        python3 -m ensurepip --upgrade 2>/dev/null || true
    }
    
    # 升级 pip
    python3 -m pip install --upgrade pip -q 2>/dev/null || true
    
    # 安装依赖
    local deps="flask werkzeug jinja2 markupsafe itsdangerous"
    for pkg in $deps; do
        python3 -c "import ${pkg//-/_}" 2>/dev/null && continue
        info "安装 $pkg..."
        python3 -m pip install "$pkg" -q 2>/dev/null || {
            # 重试 - Termux 中偶尔有网络问题
            sleep 2
            python3 -m pip install "$pkg" -q 2>/dev/null || true
        }
    done
    
    # 验证 Flask
    if python3 -c "import flask" 2>/dev/null; then
        log "所有 Python 依赖就绪"
    else
        warn "部分依赖安装失败，尝试备用源..."
        python3 -m pip install flask -i https://pypi.org/simple/ --trusted-host pypi.org 2>/dev/null
        python3 -m pip install flask 2>/dev/null || true
    fi
}

install_git() {
    if command -v git &>/dev/null; then
        log "Git 已可用"
        return 0
    fi
    info "安装 Git..."
    pkg install -y git 2>/dev/null || apt-get install -y git 2>/dev/null || true
}

# ─── 项目代码 ──────────────────────────────

clone_or_update() {
    if [ -d "$PROJECT_DIR/.git" ]; then
        info "项目已存在，检查更新..."
        cd "$PROJECT_DIR"
        # stash 本地改动（如果有），然后拉取
        git stash -q 2>/dev/null || true
        if git pull -q 2>/dev/null; then
            log "代码已更新至最新版本"
        else
            # 网络问题？用本地代码继续
            warn "无法连接 GitHub，使用现有代码继续"
        fi
    else
        info "从 GitHub 下载项目..."
        mkdir -p "$PROJECT_DIR"
        
        if git clone -q "$GITHUB_REPO" "$PROJECT_DIR" 2>/dev/null; then
            log "项目下载完成"
        else
            err "GitHub 连接失败\n请确认仓库地址正确：$GITHUB_REPO\n或者手动克隆后重试"
        fi
    fi
    
    cd "$PROJECT_DIR"
}

# ─── 启动服务器 ────────────────────────────

start_server() {
    info "启动服务器..."
    cd "$PROJECT_DIR"
    
    # 杀掉旧进程
    local old_pid=$(pgrep -f "python.*backend.py" 2>/dev/null || true)
    if [ -n "$old_pid" ]; then
        kill "$old_pid" 2>/dev/null || true
        sleep 1
    fi
    
    # 创建 workspace 目录
    mkdir -p "$PROJECT_DIR/workspace/uploads"
    
    # 后台启动 Flask
    nohup python3 backend.py > "$PROJECT_DIR/server.log" 2>&1 &
    local pid=$!
    
    # 等待启动
    sleep 2
    
    if kill -0 "$pid" 2>/dev/null; then
        log "服务器已启动 (PID: $pid)"
        log "地址: http://localhost:$SERVER_PORT"
        
        # 打开浏览器
        if command -v termux-open-url &>/dev/null; then
            termux-open-url "http://localhost:$SERVER_PORT"
            log "浏览器已打开"
        elif command -v xdg-open &>/dev/null; then
            xdg-open "http://localhost:$SERVER_PORT"
        fi
        
        # 把 PID 写入文件，方便后续管理
        echo "$pid" > "$PROJECT_DIR/.server.pid"
    else
        err "服务器启动失败，查看日志: cat $PROJECT_DIR/server.log"
    fi
}

stop_server() {
    if [ -f "$PROJECT_DIR/.server.pid" ]; then
        local pid=$(cat "$PROJECT_DIR/.server.pid")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
            log "服务器已停止"
        fi
        rm -f "$PROJECT_DIR/.server.pid"
    fi
}

# ─── 快捷命令注册 ──────────────────────────

register_alias() {
    local bashrc="$HOME/.bashrc"
    local alias_cmd="alias novel='bash $PROJECT_DIR/setup.sh'"
    local stop_cmd="alias novel-stop='bash $PROJECT_DIR/setup.sh --stop'"
    local update_cmd="alias novel-update='bash $PROJECT_DIR/setup.sh --update'"
    
    touch "$bashrc"
    grep -q "alias novel=" "$bashrc" 2>/dev/null || echo "$alias_cmd" >> "$bashrc"
    grep -q "alias novel-stop=" "$bashrc" 2>/dev/null || echo "$stop_cmd" >> "$bashrc"
    grep -q "alias novel-update=" "$bashrc" 2>/dev/null || echo "$update_cmd" >> "$bashrc"
    
    log "快捷命令已注册:"
    info "  novel         → 启动工具"
    info "  novel-stop    → 停止服务"
    info "  novel-update  → 更新代码"
    info "  新开 Termux 窗口后生效"
}

# ─── 主流程 ────────────────────────────────

main() {
    case "${1:-}" in
        --stop|-s)
            banner
            stop_server
            exit 0
            ;;
        --update|-u)
            banner
            check_termux
            install_git
            clone_or_update
            stop_server
            start_server
            exit 0
            ;;
        --help|-h)
            echo "用法: bash setup.sh [选项]"
            echo "  (无参数)  →  安装 + 启动"
            echo "  --stop    →  停止服务"
            echo "  --update  →  更新代码并重启"
            echo "  --help    →  显示帮助"
            exit 0
            ;;
    esac
    
    banner
    
    # 四步走
    check_termux
    install_python
    install_pip_deps
    install_git
    clone_or_update
    register_alias
    stop_server  # 先停旧服务
    start_server
    
    echo ""
    echo -e " ${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${GREEN}全部就绪！${NC}"
    echo -e "  📱 浏览器已打开: ${CYAN}http://localhost:$SERVER_PORT${NC}"
    echo -e "  🛑 停止: ${YELLOW}novel-stop${NC}"
    echo -e "  🔄 更新: ${YELLOW}novel-update${NC}"
    echo -e " ${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

main "$@"
