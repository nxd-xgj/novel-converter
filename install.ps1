# 小说编码转换 - Windows 一键安装
# 用法：打开 PowerShell，粘贴下面一行回车即可
#   irm https://raw.githubusercontent.com/nxd-xgj/novel-converter/main/install.ps1 | iex

$ErrorActionPreference = "Stop"
$host.UI.RawUI.WindowTitle = "小说编码转换 - 安装中"

Write-Host ""
Write-Host "  小说编码批量转换工具 - 一键安装" -ForegroundColor Cyan
Write-Host ""

$Dir = "$env:USERPROFILE\novel-converter"

# 1. Python
$py = $null
try { $py = (Get-Command python -ErrorAction Stop).Source } catch {}
if (-not $py) {
    Write-Host "~ 正在安装 Python..." -ForegroundColor Yellow
    $url = "https://www.python.org/ftp/python/3.12.5/python-3.12.5-amd64.exe"
    $exe = "$env:TEMP\python-install.exe"
    Invoke-WebRequest -Uri $url -OutFile $exe -UseBasicParsing
    Start-Process $exe -ArgumentList "/quiet InstallAllUsers=0 PrependPath=1" -Wait
    $env:Path = [Environment]::GetEnvironmentVariable("Path","User") + ";" + [Environment]::GetEnvironmentVariable("Path","Machine")
    python --version 2>&1 | Out-Null
}
Write-Host "  Python: $(python --version 2>&1)" -ForegroundColor Green

# 2. Flask
Write-Host "~ 安装依赖..." -ForegroundColor Yellow
python -m pip install flask -q
Write-Host "  依赖就绪" -ForegroundColor Green

# 3. 下载源码
Write-Host "~ 下载项目文件..." -ForegroundColor Yellow
if (!(Test-Path "$Dir\backend.py")) {
    New-Item -ItemType Directory -Force -Path "$Dir" | Out-Null
    $zip = "$env:TEMP\novel.zip"
    Invoke-WebRequest -Uri "https://github.com/nxd-xgj/novel-converter/archive/refs/heads/main.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive $zip "$env:TEMP\novel-tmp" -Force
    Copy-Item "$env:TEMP\novel-tmp\novel-converter-main\*" $Dir -Recurse -Force
}
Write-Host "  项目就绪" -ForegroundColor Green

# 4. 启动
Write-Host ""
Write-Host "  启动成功！浏览器打开: http://localhost:5000" -ForegroundColor Cyan
Write-Host ""

Start-Process "http://localhost:5000"
Set-Location $Dir
python backend.py
