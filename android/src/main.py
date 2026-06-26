#!/usr/bin/env python3
"""
小说编码转换工具 - Android 版
启动本地 Flask 服务，用 WebView 加载前端界面。
"""

import os
import io
import sys
import json
import uuid
import zipfile
import threading
import webbrowser
from pathlib import Path

# 设置工作目录
APP_DIR = Path(os.path.dirname(os.path.abspath(__file__))) if '__file__' in dir() else Path(os.getcwd())
sys.path.insert(0, str(APP_DIR))

# ============================================================
#  核心引擎（精简自 backend.py，避免额外依赖）
# ============================================================

CANDIDATE_ENCODINGS = [
    'utf-8-sig', 'utf-8',
    'gb18030', 'gbk', 'gb2312',
    'big5', 'big5hkscs',
    'utf-16', 'utf-16-le', 'utf-16-be',
    'iso-8859-1',
]

def try_all_decodings(data):
    best = ('', 'iso-8859-1', 0)
    for enc in CANDIDATE_ENCODINGS:
        try:
            t = data.decode(enc)
            c = sum(1 for c in t if '\u4e00' <= c <= '\u9fff')
            if c > best[2]:
                best = (t, enc, c)
        except:
            continue
    return best

def extract_clean_gbk(text):
    clean, lost = [], []
    for ch in text:
        try:
            ch.encode('gbk')
            clean.append(ch)
        except:
            lost.append(ch)
    stats = {'total': len(text), 'kept': len(clean), 'dropped': len(lost)}
    return ''.join(clean).encode('gbk'), stats

def compact_text(text):
    lines = text.splitlines()
    out, blank = [], False
    for line in lines:
        s = line.strip()
        if not s:
            if not blank:
                out.append('')
                blank = True
            continue
        blank = False
        out.append(s)
    return '\r\n'.join(out)

# ============================================================
#  Flask 服务
# ============================================================

from flask import Flask, request, jsonify, make_response, send_from_directory

app = Flask(__name__, template_folder=str(APP_DIR / 'templates'))
app.config['MAX_CONTENT_LENGTH'] = 300 * 1024 * 1024

sessions = {}

@app.route('/')
def index():
    return send_from_directory(str(APP_DIR / 'templates'), 'index.html')

@app.route('/api/health')
def health():
    return jsonify({'status': 'ok'})

@app.route('/api/session', methods=['POST'])
def create_session():
    sid = uuid.uuid4().hex[:12]
    sessions[sid] = {'files': []}
    return jsonify({'session_id': sid})

@app.route('/api/upload', methods=['POST'])
def upload():
    sid = request.form.get('session_id', uuid.uuid4().hex[:12])
    if sid not in sessions:
        sessions[sid] = {'files': []}
    files = []
    for f in request.files.getlist('files'):
        if not f.filename or not f.filename.lower().endswith('.txt'):
            continue
        data = f.read()
        if not data: continue
        text, enc, cjk = try_all_decodings(data)
        info = {'name': f.filename, 'data': data, 'encoding': enc, 'cjk': cjk, 'size': len(data)}
        sessions[sid]['files'].append(info)
        files.append({'name': f.filename, 'size': len(data), 'encoding': enc, 'cjk': cjk})
    return jsonify({'session_id': sid, 'files': files, 'total': len(files)})

@app.route('/api/convert', methods=['POST'])
def convert():
    data = request.get_json() or {}
    sid = data.get('session_id', '')
    compact = data.get('compact', True)
    split_mb = int(data.get('split_mb', 0) or 0)
    s = sessions.get(sid)
    if not s: return jsonify({'error': 'session not found'}), 400
    results = []
    for f in s['files']:
        try:
            text, enc, _ = try_all_decodings(f['data'])
            gbk_bytes, stats = extract_clean_gbk(text)
            clean_text = gbk_bytes.decode('gbk')
            if compact:
                clean_text = compact_text(clean_text)
            parts_data = []
            max_bytes = split_mb * 1048576 if split_mb > 0 else 0
            if max_bytes > 0 and len(clean_text.encode('gbk')) > max_bytes:
                paras = clean_text.split('\r\n')
                chunk, chunks = [], []
                cur = 0
                for p in paras:
                    sz = len(p.encode('gbk')) + 2
                    if cur + sz > max_bytes and chunk:
                        chunks.append('\r\n'.join(chunk))
                        chunk = [p]; cur = sz
                    else:
                        chunk.append(p); cur += sz
                if chunk: chunks.append('\r\n'.join(chunk))
                for i, part in enumerate(chunks, 1):
                    parts_data.append({'name': f'part_{i}.txt', 'data': part.encode('gbk')})
            else:
                parts_data.append({'name': f['name'], 'data': clean_text.encode('gbk')})
            f['output'] = parts_data
            results.append({
                'name': f['name'], 'encoding': enc, 'stats': stats,
                'parts': len(parts_data),
                'final_size': sum(p['data'].__len__() for p in parts_data),
                'split': len(parts_data) > 1,
            })
        except Exception as e:
            results.append({'name': f['name'], 'error': str(e)})
    return jsonify({'session_id': sid, 'results': results, 'summary': {'total': len(results)}})

@app.route('/api/download/<sid>')
def download(sid):
    s = sessions.get(sid)
    if not s: return jsonify({'error': 'not found'}), 400
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in s['files']:
            if f.get('output'):
                base = f['name'].rsplit('.', 1)[0]
                for p in f['output']:
                    zf.writestr(f'{base}_{p["name"]}', p['data'])
    buf.seek(0)
    resp = make_response(buf.getvalue())
    resp.headers['Content-Type'] = 'application/zip'
    resp.headers['Content-Disposition'] = 'attachment; filename="novels_gbk.zip"'
    return resp


def start_flask():
    app.run(host='127.0.0.1', port=5000, debug=False, use_reloader=False)


# ============================================================
#  Kivy 启动器
# ============================================================

if __name__ == '__main__':
    # 启动 Flask
    t = threading.Thread(target=start_flask, daemon=True)
    t.start()

    try:
        from kivy.app import App
        from kivy.uix.boxlayout import BoxLayout
        from kivy.utils import platform
        from kivy.clock import Clock

        class NovelApp(App):
            def build(self):
                if platform == 'android':
                    from jnius import autoclass
                    act = autoclass('org.kivy.android.PythonActivity').mActivity
                    wv = autoclass('android.webkit.WebView')(act)
                    wv.getSettings().setJavaScriptEnabled(True)
                    wv.getSettings().setDomStorageEnabled(True)
                    wv.getSettings().setAllowFileAccess(True)
                    wv.setWebChromeClient(autoclass('android.webkit.WebChromeClient')())
                    self.wv = wv
                    def load(_):
                        wv.loadUrl('http://127.0.0.1:5000')
                    Clock.schedule_once(load, 3)
                    act.setContentView(wv)
                return BoxLayout()
        NovelApp().run()
    except ImportError:
        print('Server running on http://127.0.0.1:5000')
        webbrowser.open('http://127.0.0.1:5000')
        import time
        try:
            while True: time.sleep(1)
        except KeyboardInterrupt:
            pass
