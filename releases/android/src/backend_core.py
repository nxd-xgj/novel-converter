#!/usr/bin/env python3
"""
小说编码批量转换工具 - 后端服务
功能：检测文本编码，将任意中文编码统一转换为纯 GBK
支持：UTF-8 / GBK / GB18030 / BIG5 / UTF-16 等 → 纯 GBK
"""

import os
import re
import io
import sys
import json
import uuid
import zipfile
import tempfile
import traceback
import webbrowser
import threading
from pathlib import Path
from datetime import datetime
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed

from flask import Flask, request, jsonify, send_file, make_response, send_from_directory
from werkzeug.utils import secure_filename

# ─── PyInstaller 兼容路径 ────────────────────────────────
def resource_path(relative_path):
    """获取资源的绝对路径（支持 PyInstaller 打包）"""
    try:
        base_path = sys._MEIPASS
    except AttributeError:
        base_path = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base_path, relative_path)

# ─── Flask App ───────────────────────────────────────────────
app = Flask(__name__, template_folder=resource_path('templates'))
app.config['MAX_CONTENT_LENGTH'] = 500 * 1024 * 1024  # 500MB 上限

# 工作目录（可执行文件同目录下的 workspace/）
if getattr(sys, 'frozen', False):
    BASE_DIR = Path(sys.executable).parent
else:
    BASE_DIR = Path(__file__).parent
WORK_DIR = BASE_DIR / 'workspace'
WORK_DIR.mkdir(exist_ok=True)
UPLOAD_DIR = WORK_DIR / 'uploads'
UPLOAD_DIR.mkdir(exist_ok=True)

# ─── 编码检测 ───────────────────────────────────────────────

# GBK/GB2312/GB18030 前导字节范围
GB_LEAD_MIN, GB_LEAD_MAX = 0x81, 0xFE
# UTF-8 CJK 三字节前缀 (U+4E00 ~ U+9FFF)
UTF8_CJK_MIN, UTF8_CJK_MAX = 0xE4, 0xE9

# BIG5 常见前导字节范围
BIG5_LEAD_RANGES = [(0xA1, 0xFE)]

# UTF-16 BOM
UTF16_BE_BOM = b'\xfe\xff'
UTF16_LE_BOM = b'\xff\xfe'
UTF8_BOM = b'\xef\xbb\xbf'


class EncodingDetector:
    """多策略中文编码检测器"""

    ENC_CANDIDATES = [
        ('utf-8',      'UTF-8'),
        ('utf-8-sig',  'UTF-8 BOM'),
        ('gbk',        'GBK'),
        ('gb2312',     'GB2312'),
        ('gb18030',    'GB18030'),
        ('big5',       'BIG5'),
        ('big5hkscs',  'BIG5-HKSCS'),
        ('utf-16',     'UTF-16'),
        ('utf-16-le',  'UTF-16 LE'),
        ('utf-16-be',  'UTF-16 BE'),
        ('cp936',      'CP936'),
    ]

    @classmethod
    def detect(cls, data: bytes) -> dict:
        """检测字节数据的编码，返回详细信息"""
        result = {
            'size': len(data),
            'matches': [],
            'best_encoding': None,
            'best_label': None,
            'best_cjk_count': 0,
            'is_utf8': False,
            'is_gbk': False,
            'has_bom': False,
            'bom_type': None,
            'issues': [],
        }

        # 1. BOM 检测
        if data[:3] == UTF8_BOM:
            result['has_bom'] = True
            result['bom_type'] = 'UTF-8 BOM'
        elif data[:2] == UTF16_LE_BOM:
            result['has_bom'] = True
            result['bom_type'] = 'UTF-16 LE BOM'
        elif data[:2] == UTF16_BE_BOM:
            result['has_bom'] = True
            result['bom_type'] = 'UTF-16 BE BOM'

        # 2. 采样检测（取前 200KB 加速）
        sample = data[:200000]

        # 3. 遍历候选编码
        for codec, label in cls.ENC_CANDIDATES:
            ok, cjk_count, error_pos = cls._try_decode(sample, codec, label)
            if ok:
                result['matches'].append({
                    'codec': codec,
                    'label': label,
                    'cjk_chars_in_sample': cjk_count,
                })
                if cjk_count > result['best_cjk_count']:
                    result['best_cjk_count'] = cjk_count
                    result['best_encoding'] = codec
                    result['best_label'] = label

        # 4. 标记 UTF-8 / GBK 兼容性
        for m in result['matches']:
            if m['codec'] in ('utf-8', 'utf-8-sig'):
                result['is_utf8'] = True
            if m['codec'] in ('gbk', 'gb2312', 'gb18030', 'cp936'):
                result['is_gbk'] = True

        # 5. 全量 GBK 验证：检查是否有非法字节
        if result['is_gbk']:
            issues = cls._find_gbk_issues(data)
            if issues:
                result['issues'] = issues[:10]  # 最多报 10 个
                result['gbk_clean'] = False
            else:
                result['gbk_clean'] = True
        else:
            result['gbk_clean'] = False

        # 6. 决定推荐动作
        if result['is_gbk'] and result['gbk_clean']:
            result['action'] = 'none'  # 已经是干净 GBK
        elif result['is_gbk'] and not result['gbk_clean']:
            result['action'] = 'repair'  # GBK 但需要修复
        elif result['is_utf8']:
            result['action'] = 'convert'  # UTF-8 需要转换
        else:
            result['action'] = 'convert'  # 其他编码需要转换

        return result

    @staticmethod
    def _try_decode(data, codec, label):
        """尝试解码并统计 CJK 字符数"""
        try:
            text = data.decode(codec)
            cjk = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
            return True, cjk, None
        except (UnicodeDecodeError, UnicodeError) as e:
            return False, 0, e.start if hasattr(e, 'start') else None

    @staticmethod
    def _find_gbk_issues(data: bytes) -> list:
        """查找 GBK 解码中的非法字节位置"""
        issues = []
        i = 0
        n = len(data)
        while i < n:
            b = data[i]
            if b <= 0x7F:
                i += 1
            elif GB_LEAD_MIN <= b <= GB_LEAD_MAX:
                if i + 1 >= n:
                    issues.append({
                        'position': i,
                        'byte': f'0x{b:02X}',
                        'reason': 'GBK lead byte at end of file',
                    })
                    break
                b2 = data[i + 1]
                if not (0x40 <= b2 <= 0xFE and b2 != 0x7F):
                    issues.append({
                        'position': i,
                        'bytes': f'0x{b:02X} 0x{b2:02X}',
                        'reason': 'Invalid GBK trail byte',
                    })
                i += 2
            else:
                issues.append({
                    'position': i,
                    'byte': f'0x{b:02X}',
                    'reason': 'Byte outside valid GBK range',
                })
                i += 1
        return issues


# ─── 转换引擎 ───────────────────────────────────────────────

class NovelConverter:
    """小说编码转换引擎"""

    @staticmethod
    def convert_to_gbk(data: bytes, source_encoding: str = None) -> tuple:
        """
        将任意编码的文本转为纯 GBK。
        返回: (gbk_bytes, stats_dict)
        """
        stats = {
            'source_encoding': source_encoding or 'auto-detect',
            'source_size': len(data),
            'non_gbk_chars_dropped': 0,
            'non_gbk_samples': [],
        }

        # 1. 解码为 Unicode 字符串
        text = NovelConverter._decode(data, source_encoding, stats)

        # 2. 清理：移除/替换 GBK 不支持的字符
        text = NovelConverter._clean_gbk_incompatible(text, stats)

        # 3. 编码为 GBK
        gbk_bytes = text.encode('gbk')
        stats['output_size'] = len(gbk_bytes)
        stats['compression_ratio'] = (
            f"{stats['output_size'] / stats['source_size'] * 100:.1f}%"
            if stats['source_size'] > 0 else '0%'
        )

        return gbk_bytes, stats

    @staticmethod
    def _decode(data: bytes, hint_encoding: str, stats: dict) -> str:
        """用多种策略尝试解码"""
        if hint_encoding:
            try:
                return data.decode(hint_encoding)
            except (UnicodeDecodeError, LookupError):
                stats['encoding_hint_failed'] = hint_encoding

        # 自动检测编码
        detect_result = EncodingDetector.detect(data)
        best = detect_result['best_encoding']

        if best:
            try:
                return data.decode(best)
            except UnicodeDecodeError:
                pass

        # 最终降级：GB18030 覆盖所有字节
        try:
            return data.decode('gb18030')
        except UnicodeDecodeError:
            return data.decode('gbk', errors='replace')

    @staticmethod
    def _clean_gbk_incompatible(text: str, stats: dict) -> str:
        """移除 GBK 不支持的 Unicode 字符"""
        cleaned = []
        dropped_count = 0
        dropped_samples = []

        for ch in text:
            try:
                ch.encode('gbk')
                cleaned.append(ch)
            except UnicodeEncodeError:
                dropped_count += 1
                if len(dropped_samples) < 20:
                    cp = ord(ch)
                    dropped_samples.append({
                        'char': ch,
                        'codepoint': f'U+{cp:04X}',
                        'name': _unicode_name(ch),
                    })

        stats['non_gbk_chars_dropped'] = dropped_count
        stats['non_gbk_samples'] = dropped_samples
        return ''.join(cleaned)


def _unicode_name(ch):
    """获取 Unicode 字符的友好名称"""
    try:
        import unicodedata
        return unicodedata.name(ch, 'UNKNOWN')
    except (ValueError, ImportError):
        return 'UNKNOWN'


# ─── 会话管理 ───────────────────────────────────────────────

class SessionManager:
    """管理上传/转换会话"""

    def __init__(self):
        self.sessions: dict = {}  # session_id -> session_data

    def create(self) -> str:
        sid = uuid.uuid4().hex[:12]
        session_dir = WORK_DIR / sid
        session_dir.mkdir(parents=True, exist_ok=True)
        self.sessions[sid] = {
            'id': sid,
            'dir': session_dir,
            'files': [],        # 上传文件列表
            'results': [],      # 转换结果
            'created_at': datetime.now().isoformat(),
        }
        return sid

    def get(self, sid: str):
        return self.sessions.get(sid)

    def add_file(self, sid: str, filename: str, data: bytes):
        session = self.get(sid)
        if not session:
            return None
        file_info = {
            'name': filename,
            'name_safe': secure_filename(filename),
            'size': len(data),
            'data': data,
            'detection': None,
            'converted': None,
            'status': 'pending',
        }
        session['files'].append(file_info)
        return file_info

    def process(self, sid: str):
        """批量处理所有文件"""
        session = self.get(sid)
        if not session:
            return None

        results = []
        for f in session['files']:
            try:
                # 检测
                detection = EncodingDetector.detect(f['data'])
                f['detection'] = detection

                # 转换
                if detection['action'] == 'none':
                    f['status'] = 'skipped'
                    f['converted'] = f['data']
                    stats = {'non_gbk_chars_dropped': 0, 'non_gbk_samples': [],
                             'output_size': len(f['data']),
                             'compression_ratio': '100%', 'source_encoding': detection['best_label']}
                else:
                    src_enc = detection['best_encoding']
                    f['converted'], stats = NovelConverter.convert_to_gbk(
                        f['data'], source_encoding=src_enc
                    )
                    f['status'] = 'converted'

                f['stats'] = stats
                results.append({
                    'name': f['name'],
                    'status': f['status'],
                    'encoding': {
                        'source': detection['best_label'],
                        'action': detection['action'],
                        'issues_count': len(detection['issues']),
                    },
                    'stats': stats,
                })
            except Exception as e:
                f['status'] = 'error'
                results.append({
                    'name': f['name'],
                    'status': 'error',
                    'error': str(e),
                })

        session['results'] = results
        return results

    def make_zip(self, sid: str) -> bytes:
        """将转换结果打包为 ZIP"""
        session = self.get(sid)
        if not session:
            return None

        buf = io.BytesIO()
        with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
            for f in session['files']:
                if f['status'] in ('converted', 'skipped') and f['converted']:
                    zf.writestr(f['name'], f['converted'])
        buf.seek(0)
        return buf.getvalue()

    def cleanup(self, sid: str):
        """清理会话"""
        session = self.get(sid)
        if session:
            import shutil
            shutil.rmtree(session['dir'], ignore_errors=True)
            del self.sessions[sid]


sessions = SessionManager()

# ─── 首页路由 ───────────────────────────────────────────────

@app.route('/')
def index():
    return send_from_directory('templates', 'index.html')

# ─── API 路由 ───────────────────────────────────────────────


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'time': datetime.now().isoformat()})


@app.route('/api/session', methods=['POST'])
def create_session():
    sid = sessions.create()
    return jsonify({'session_id': sid})


@app.route('/api/upload', methods=['POST'])
def upload_files():
    """上传文件并检测编码"""
    sid = request.form.get('session_id')
    if not sid or not sessions.get(sid):
        sid = sessions.create()

    if 'files' not in request.files:
        return jsonify({'error': '没有文件'}), 400

    uploaded = []
    for f in request.files.getlist('files'):
        if f.filename == '':
            continue
        filename = f.filename
        data = f.read()
        if len(data) == 0:
            continue
        if not filename.lower().endswith('.txt'):
            continue

        file_info = sessions.add_file(sid, filename, data)
        if file_info:
            # 立即检测编码
            detection = EncodingDetector.detect(data)
            file_info['detection'] = detection
            uploaded.append({
                'name': filename,
                'size': len(data),
                'size_display': _format_size(len(data)),
                'encoding': detection['best_label'],
                'action': detection['action'],
                'issues_count': len(detection['issues']),
                'gbk_clean': detection.get('gbk_clean', False),
            })

    return jsonify({
        'session_id': sid,
        'files': uploaded,
        'total': len(uploaded),
    })


@app.route('/api/convert', methods=['POST'])
def convert_files():
    """批量转换所有已上传文件"""
    data = request.get_json() or {}
    sid = data.get('session_id')
    if not sid or not sessions.get(sid):
        return jsonify({'error': '会话不存在'}), 400

    results = sessions.process(sid)

    # 统计
    summary = Counter(r['status'] for r in results)

    return jsonify({
        'session_id': sid,
        'results': results,
        'summary': {
            'converted': summary.get('converted', 0),
            'skipped': summary.get('skipped', 0),
            'error': summary.get('error', 0),
            'total': len(results),
        },
    })


@app.route('/api/download/<sid>', methods=['GET'])
def download_zip(sid):
    """下载转换结果 ZIP"""
    session = sessions.get(sid)
    if not session:
        return jsonify({'error': '会话不存在'}), 400

    zip_data = sessions.make_zip(sid)
    if not zip_data:
        return jsonify({'error': '没有可下载的文件'}), 400

    resp = make_response(zip_data)
    resp.headers['Content-Type'] = 'application/zip'
    resp.headers['Content-Disposition'] = (
        f'attachment; filename="novels_gbk_{sid}.zip"'
    )
    resp.headers['Content-Length'] = str(len(zip_data))
    return resp


@app.route('/api/detail/<sid>/<path:filename>', methods=['GET'])
def file_detail(sid, filename):
    """获取单个文件的详细信息"""
    from urllib.parse import unquote
    filename = unquote(filename)
    session = sessions.get(sid)
    if not session:
        return jsonify({'error': '会话不存在'}), 400

    for f in session['files']:
        if f['name'] == filename:
            return jsonify({
                'name': f['name'],
                'size': f['size'],
                'status': f['status'],
                'detection': f['detection'],
                'stats': f.get('stats'),
            })

    return jsonify({'error': '文件未找到'}), 404


def _format_size(size_bytes):
    """格式化文件大小"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024:
            return f'{size_bytes:.1f} {unit}'
        size_bytes /= 1024
    return f'{size_bytes:.1f} TB'


# ─── 启动 ──────────────────────────────────────────────────

def open_browser(port):
    """延迟打开浏览器"""
    def _open():
        import time
        time.sleep(1.5)
        webbrowser.open(f'http://localhost:{port}')
    threading.Thread(target=_open, daemon=True).start()


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    print('=' * 60)
    print('  小说编码批量转换工具')
    print(f'  服务地址: http://localhost:{port}')
    print('  按 Ctrl+C 停止服务')
    print('=' * 60)
    
    # 非调试模式下自动打开浏览器
    if not os.environ.get('FLASK_DEBUG'):
        open_browser(port)
    
    app.run(host='0.0.0.0', port=port, debug=False)
