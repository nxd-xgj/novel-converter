# 小说编码批量转换工具 - 发布包
================================

## Linux
- **文件**: `linux/NovelConverter` (单文件, 25MB)
- **系统**: x86_64 Linux
- **使用**: 下载后 `chmod +x NovelConverter && ./NovelConverter`
- **依赖**: 无（自带 Python 运行时）

## Windows
- **文件**: `windows/build_windows.bat` + `windows/NovelConverter.spec`
- **系统**: Windows 10/11（需要 Python 3.8+）
- **使用**: 双击 `build_windows.bat` → 自动编译 → 运行 `dist/NovelConverter.exe`
- 或直接 `pip install flask && python backend.py`

## Android
- **方式一**: 安装 Termux → 运行 `setup.sh` 自动装环境启动
- **方式二**: 用 `android/` 目录下的 Buildozer 配置编译 APK（需 Linux）
