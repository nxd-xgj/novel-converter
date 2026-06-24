@echo off
chcp 65001 >nul
title NovelConverter - Windows 构建工具
echo ═══════════════════════════════════════════
echo   小说编码批量转换工具 - Windows 构建脚本
echo ═══════════════════════════════════════════
echo.

REM 检查 Python
where python >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 未找到 Python！请先安装 Python 3.8+
    echo    下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo ✅ Python: 
python --version

REM 安装 PyInstaller
echo.
echo 📦 安装 PyInstaller...
pip install pyinstaller flask -q

if %ERRORLEVEL% NEQ 0 (
    echo ❌ 安装依赖失败
    pause
    exit /b 1
)

echo ✅ 依赖安装完成

REM 清理旧的构建文件
if exist dist rmdir /s /q dist >nul 2>&1
if exist build rmdir /s /q build >nul 2>&1

REM 构建
echo.
echo 🔨 正在编译为 Windows EXE（约需 1-2 分钟）...
pyinstaller --onefile --name "NovelConverter" ^
    --add-data "templates;templates" ^
    --hidden-import flask ^
    --hidden-import werkzeug ^
    --hidden-import jinja2 ^
    --hidden-import markupsafe ^
    --hidden-import itsdangerous ^
    --hidden-import click ^
    --clean --noconfirm ^
    backend.py

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ 构建成功！
    echo.
    echo 📁 输出文件: %~dp0dist\NovelConverter.exe
    echo.
    echo 🚀 双击 NovelConverter.exe 即可启动
    echo    启动后浏览器将自动打开 http://localhost:5000
) else (
    echo ❌ 构建失败，请检查错误信息
)

echo.
pause
