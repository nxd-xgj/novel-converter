# ═══════════════════════════════════════════════════════════════
#  小说编码批量转换工具 - Windows PowerShell 安装启动脚本
#  用法：右键 → 使用 PowerShell 运行
#  或：  powershell -ExecutionPolicy Bypass -File install.ps1
# ═══════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$host.UI.RawUI.WindowTitle = "小说编码转换工具"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host ""
Write-Host " ╔══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host " ║     📖 小说编码批量转换工具 - Windows 安装器     ║" -ForegroundColor Cyan
Write-Host " ║     UTF-8/GBK/BIG5 → 纯 GBK · 一键安装启动     ║" -ForegroundColor Cyan
Write-Host " ╚══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = "$env:USERPROFILE\novel-converter"

# ── 1. 检测 Python ──
Write-Host "[1/4] 检测 Python..." -ForegroundColor Yellow
$python = $null
try { $python = Get-Command python -ErrorAction Stop | Select-Object -ExpandProperty Source }
catch {
    try { $python = Get-Command python3 -ErrorAction Stop | Select-Object -ExpandProperty Source }
    catch {}
}

if (-not $python) {
    Write-Host "      未找到 Python，正在通过 winget 安装..." -ForegroundColor Yellow
    try {
        winget install Python.Python.3.12 --silent --accept-package-agreements
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        Start-Sleep -Seconds 5
        $python = Get-Command python -ErrorAction Stop | Select-Object -ExpandProperty Source
    } catch {
        Write-Host "❌ 自动安装失败！请手动安装 Python:" -ForegroundColor Red
        Write-Host "   https://www.python.org/downloads/" -ForegroundColor Cyan
        Write-Host "   安装时请勾选 'Add Python to PATH'" -ForegroundColor Yellow
        Read-Host "按回车退出"
        exit 1
    }
}

Write-Host "      ✅ Python: $(& $python --version)" -ForegroundColor Green

# ── 2. 安装 Flask ──
Write-Host "[2/4] 安装依赖..." -ForegroundColor Yellow
& $python -m pip install flask werkzeug -q 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "      ⚠ pip 安装失败，重试中..." -ForegroundColor Yellow
    & $python -m pip install flask werkzeug --trusted-host pypi.org --trusted-host files.pythonhosted.org -q
}
Write-Host "      ✅ Flask 已就绪" -ForegroundColor Green

# ── 3. 下载/更新源码 ──
Write-Host "[3/4] 准备项目文件..." -ForegroundColor Yellow
$repoUrl = "https://github.com/nxd-xgj/novel-converter/archive/refs/heads/main.zip"

if (Test-Path "$ProjectDir\backend.py") {
    Write-Host "      项目已存在，跳过下载" -ForegroundColor Gray
} else {
    Write-Host "      正在下载..." -ForegroundColor Gray
    New-Item -ItemType Directory -Force -Path $ProjectDir | Out-Null
    $zipPath = "$env:TEMP\novel-converter.zip"
    try {
        Invoke-WebRequest -Uri $repoUrl -OutFile $zipPath -UseBasicParsing
        Expand-Archive -Path $zipPath -DestinationPath "$env:TEMP\novel-tmp" -Force
        Copy-Item -Path "$env:TEMP\novel-tmp\novel-converter-main\*" -Destination $ProjectDir -Recurse -Force
        Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\novel-tmp" -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "      ✅ 下载完成" -ForegroundColor Green
    } catch {
        Write-Host "      ⚠ 下载失败，请手动下载并解压到: $ProjectDir" -ForegroundColor Yellow
        Write-Host "      $repoUrl" -ForegroundColor Cyan
    }
}

# ── 4. 启动 ──
Write-Host "[4/4] 启动服务器..." -ForegroundColor Yellow
Set-Location $ProjectDir
New-Item -ItemType Directory -Force -Path "$ProjectDir\workspace\uploads" | Out-Null

Write-Host ""
Write-Host " ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
Write-Host "  ✅ 服务器已启动！" -ForegroundColor Green
Write-Host "  📱 浏览器打开: http://localhost:5000" -ForegroundColor Cyan
Write-Host "  🛑 关闭此窗口即可停止服务" -ForegroundColor Yellow
Write-Host " ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
Write-Host ""

Start-Process "http://localhost:5000"
& $python backend.py

Read-Host "按回车退出"
