#!/data/data/com.termux/files/usr/bin/bash
"""
小说编码转换工具 - Termux 一键安装脚本
在 Android 手机上直接运行，无需编译 APK
"""

echo ""
echo " ═══════════════════════════════════════════"
echo "   小说编码批量转换工具 - Termux 安装脚本"
echo " ═══════════════════════════════════════════"
echo ""

# 检查 Termux
if [ ! -d /data/data/com.termux ]; then
    echo "❌ 请在 Termux 中运行此脚本"
    echo "   下载: https://f-droid.org/packages/com.termux/"
    exit 1
fi

echo "📦 更新包管理器..."
pkg update -y 2>/dev/null

echo "📦 安装 Python..."
pkg install -y python 2>/dev/null

echo "📦 安装 Flask..."
pip install flask werkzeug

echo ""
echo "📁 创建工具目录..."
mkdir -p ~/novel_converter/templates
mkdir -p ~/novel_converter/workspace

echo "📄 下载工具文件..."
cd ~/novel_converter

# 下载后端文件
cat > backend.py << 'PYEOF'
# (文件内容由安装脚本自动生成，见下方说明)
PYEOF

cat > templates/index.html << 'HTMLEOF'
# (HTML前端文件，见下方说明)
HTMLEOF

echo ""
echo " ✅ 安装完成！"
echo ""
echo " 🚀 启动方法："
echo "    cd ~/novel_converter"
echo "    python backend.py"
echo ""
echo " 📱 然后用手机浏览器打开："
echo "    http://localhost:5000"
echo ""
echo " 💡 提示："
echo "    - 将 TXT 小说文件传入手机后，在浏览器中选择上传"
echo "    - 转换完成后下载 ZIP 包到手机"
echo "    - 将 GBK 编码的文件传送到你的阅读设备"
echo ""
