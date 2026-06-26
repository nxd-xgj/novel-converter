[app]
title = 小说编码转换
package.name = novelconverter
package.domain = com.novelconverter
source.dir = src
source.include_exts = py,png,jpg,kv,atlas,html,ttf
version = 2.0.0
requirements = python3,kivy,flask,werkzeug,jinja2,markupsafe,itsdangerous
android.permissions = INTERNET
android.api = 34
android.minapi = 21
android.ndk = 25c
android.arch = arm64-v8a
orientation = portrait
fullscreen = 0
log_level = 1

[buildozer]
log_level = 1
warn_on_root = 1
