"""
小说编码转换工具 - Android 启动器
使用 Kivy + WebView 包裹 Flask 服务器
"""

import os
import sys
import json
import uuid
import zipfile
import threading
import webbrowser
from pathlib import Path

# Flask 相关 - 确保路径正确
BASE_DIR = Path(__file__).parent
sys.path.insert(0, str(BASE_DIR))

# ===== 核心引擎 =====

class EncodingDetector:
    """编码检测器（精简版）"""
    
    ENCODINGS = [
        ('utf-8', 'UTF-8'),
        ('utf-8-sig', 'UTF-8 BOM'),
        ('gbk', 'GBK'),
        ('gb2312', 'GB2312'),
        ('gb18030', 'GB18030'),
        ('big5', 'BIG5'),
        ('utf-16', 'UTF-16'),
    ]
    
    @classmethod
    def detect(cls, data: bytes) -> dict:
        result = {'encoding': None, 'action': 'convert', 'issues': [], 'cjk': 0}
        sample = data[:100000]
        
        best_enc = None
        best_cjk = 0
        
        for codec, label in cls.ENCODINGS:
            try:
                text = sample.decode(codec)
                cjk = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
                if cjk > best_cjk:
                    best_cjk = cjk
                    best_enc = codec
                    result['encoding'] = label
            except:
                continue
        
        if best_enc:
            try:
                full = data.decode(best_enc)
                result['cjk'] = sum(1 for c in full if '\u4e00' <= c <= '\u9fff')
                result['action'] = 'none' if best_enc in ('gbk', 'gb2312', 'gb18030') else 'convert'
            except:
                pass
        
        return result


class Converter:
    """转换引擎"""
    
    @staticmethod
    def convert(data: bytes) -> tuple:
        text = data.decode('utf-8', errors='replace')
        # 也尝试 GBK 解码
        try:
            text = data.decode('gbk')
        except:
            try:
                text = data.decode('gb18030')
            except:
                text = data.decode('utf-8', errors='replace')
        
        clean = []
        dropped = 0
        for ch in text:
            try:
                ch.encode('gbk')
                clean.append(ch)
            except:
                dropped += 1
        
        return ''.join(clean).encode('gbk'), dropped


# ===== Flask 服务器 =====

from flask import Flask, request, jsonify, make_response, send_from_directory

app = Flask(__name__, 
    template_folder=str(BASE_DIR / 'templates'),
    static_folder=str(BASE_DIR / 'static')
)
app.config['MAX_CONTENT_LENGTH'] = 500 * 1024 * 1024

WORK_DIR = BASE_DIR / 'workspace'
WORK_DIR.mkdir(exist_ok=True)

sessions = {}

@app.route('/')
def index():
    return send_from_directory(str(BASE_DIR / 'templates'), 'index.html')

@app.route('/api/health')
def health():
    return jsonify({'status': 'ok'})

@app.route('/api/session', methods=['POST'])
def create_session():
    sid = uuid.uuid4().hex[:12]
    sessions[sid] = {'files': [], 'results': []}
    return jsonify({'session_id': sid})

@app.route('/api/upload', methods=['POST'])
def upload():
    sid = request.form.get('session_id') or uuid.uuid4().hex[:12]
    if sid not in sessions:
        sessions[sid] = {'files': [], 'results': []}
    
    files = []
    for f in request.files.getlist('files'):
        if not f.filename or not f.filename.lower().endswith('.txt'):
            continue
        data = f.read()
        if not data:
            continue
        detection = EncodingDetector.detect(data)
        files.append({
            'name': f.filename,
            'size': len(data),
            'action': detection['action'],
            'encoding': detection['encoding'],
            'issues': detection['issues'],
        })
        sessions[sid]['files'].append({
            'name': f.filename,
            'data': data,
            'detection': detection,
        })
    
    return jsonify({'session_id': sid, 'files': files, 'total': len(files)})

@app.route('/api/convert', methods=['POST'])
def convert():
    data = request.get_json() or {}
    sid = data.get('session_id', '')
    session = sessions.get(sid)
    if not session:
        return jsonify({'error': 'session not found'}), 400
    
    results = []
    converted = skipped = errors = 0
    
    for f in session['files']:
        try:
            detection = f['detection']
            if detection['action'] == 'none' and detection['encoding'] in ('GBK', 'GB2312', 'GB18030'):
                f['converted'] = f['data']
                f['status'] = 'skipped'
                skipped += 1
                results.append({
                    'name': f['name'],
                    'status': 'skipped',
                    'encoding': {'source': detection['encoding'], 'action': 'none'},
                })
            else:
                gbk_data, dropped = Converter.convert(f['data'])
                f['converted'] = gbk_data
                f['status'] = 'converted'
                converted += 1
                results.append({
                    'name': f['name'],
                    'status': 'converted',
                    'encoding': {'source': detection['encoding'], 'action': 'convert'},
                    'stats': {'non_gbk_chars_dropped': dropped},
                })
        except Exception as e:
            errors += 1
            results.append({'name': f['name'], 'status': 'error', 'error': str(e)})
    
    session['results'] = results
    return jsonify({
        'session_id': sid,
        'results': results,
        'summary': {'converted': converted, 'skipped': skipped, 'error': errors, 'total': len(results)},
    })

@app.route('/api/download/<sid>')
def download(sid):
    session = sessions.get(sid)
    if not session:
        return jsonify({'error': 'not found'}), 400
    
    buf = io.BytesIO()
    import zipfile
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in session['files']:
            if f.get('converted'):
                zf.writestr(f['name'], f['converted'])
    buf.seek(0)
    resp = make_response(buf.getvalue())
    resp.headers['Content-Type'] = 'application/zip'
    resp.headers['Content-Disposition'] = f'attachment; filename="novels_gbk.zip"'
    return resp


# ===== Kivy 启动器 =====

def start_server():
    """启动 Flask 服务器"""
    app.run(host='127.0.0.1', port=5000, debug=False, use_reloader=False)


if __name__ == '__main__':
    # 启动服务器线程
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()
    
    # Kivy Android 模式
    try:
        from kivy.app import App
        from kivy.uix.boxlayout import BoxLayout
        from kivy.clock import Clock
        from kivy.utils import platform
        import webbrowser
        
        class ConverterApp(App):
            def build(self):
                layout = BoxLayout()
                # 在 Android 上打开浏览器
                if platform == 'android':
                    from jnius import autoclass
                    PythonActivity = autoclass('org.kivy.android.PythonActivity')
                    WebView = autoclass('android.webkit.WebView')
                    activity = PythonActivity.mActivity
                    webview = WebView(activity)
                    webview.getSettings().setJavaScriptEnabled(True)
                    webview.loadUrl('http://127.0.0.1:5000')
                    activity.setContentView(webview)
                return layout
            
            def on_start(self):
                # 延迟打开浏览器
                Clock.schedule_once(lambda dt: webbrowser.open('http://127.0.0.1:5000'), 2)
        
        ConverterApp().run()
    except ImportError:
        # 非 Kivy 环境，直接启动
        print(f'Server started at http://127.0.0.1:5000')
        start_server()
