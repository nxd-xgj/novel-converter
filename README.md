# 小说编码转换

一个纯浏览器端工具，将 TXT 小说转换为纯 GBK 编码，适配老旧电子书阅读器。

## 特点

- 🚀 **零安装**：单个 HTML 文件，拖进浏览器就能用
- 📵 **本地运行**：不上传服务器，完全在本地处理
- 🎯 **自动检测编码**：支持 UTF-8、GBK、GB2312、GB18030、BIG5 等
- 📦 **批量打包**：多文件转 ZIP 下载
- ✂️ **实用功能**：压缩空行、按体积分卷
- 📱 **移动端友好**：Android/iOS 浏览器均可用

## 使用方法

1. 下载 `standalone.html`
2. 用浏览器打开
3. 拖入 TXT 文件
4. 点击「开始转换」→ 自动下载 ZIP

> 也可托管在 GitHub Pages 直接访问：`https://nxd-xgj.github.io/novel-converter/standalone.html`

## 工作原理

```
拖入文件 → JS 检测编码 → 解码为文本 → 压缩/分卷 → 用内置 GBK 编码表编码 → 打包 ZIP 下载
```

内置 14287 条 Unicode→GBK 映射表，二分查找 O(log N)，10MB 文件约 2 秒完成。

## 技术栈

纯前端，无后端依赖：
- 原生 FileReader + TextDecoder（编码检测）
- 自研 GBK 编码引擎（14K 条目二分查找表）
- JSZip CDN（打包下载）
