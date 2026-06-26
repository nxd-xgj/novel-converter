[app]
title = 小说编码转换
package.name = novelconverter
package.domain = com.novelconverter
source.dir = src
source.include_exts = py,png,jpg,kv,atlas,html,ttf
version = 2.0.0
requirements = python3,flask,werkzeug,jinja2,markupsafe,itsdangerous,pyjnius
android.permissions = INTERNET
android.api = 33
android.minapi = 21
android.ndk = 27
android.arch = arm64-v8a
android.accept_sdk_license = True
android.bootstrap = webview
orientation = portrait
fullscreen = 0
log_level = 2

[buildozer]
log_level = 2
warn_on_root = 1
