[app]

# 应用信息
title = 小说编码转换
package.name = novelconverter
package.domain = com.novelconverter
source.dir = src
source.include_exts = py,png,jpg,kv,atlas,ttf
version = 1.0.0
version.regex = __version__ = ['"](.*)['"]
version.filename = src/version.py

# 编译需求
requirements = python3,flask,werkzeug,jinja2

# Android 特定
android.permissions = INTERNET
android.api = 34
android.minapi = 21
android.sdk = 34
android.ndk = 27
android.gradle_dependencies = 'androidx.webkit:webkit:1.10.0'

# 屏幕方向
orientation = landscape
osx.codesign = None

# 架构
android.archs = arm64-v8a, armeabi-v7a

# 图标
# icon = icon.png

# 启动时显示日志
log_level = 2

[buildozer]

# 下载目录
download_dir = ./.buildozer/downloads
local_recipes = ./recipes/

[warn]
# 允许外部存储写入
android.allow_external_storage = True
