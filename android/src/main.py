import os, io, sys, json, uuid, zipfile, threading
from pathlib import Path

APP_DIR = Path(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, str(APP_DIR))

# ── 核心引擎 ──
ENCODINGS = ['utf-8-sig','utf-8','gb18030','gbk','gb2312','big5','big5hkscs','utf-16','iso-8859-1']

def try_all_decodings(data):
    best = ('', 'iso-8859-1', 0)
    for enc in ENCODINGS:
        try:
            t = data.decode(enc)
            c = sum(1 for c in t if '\u4e00' <= c <= '\u9fff')
            if c > best[2]: best = (t, enc, c)
        except: continue
    return best

def extract_clean_gbk(text):
    clean, lost = [], []
    for ch in text:
        try:
            ch.encode('gbk')
            clean.append(ch)
        except: lost.append(ch)
    return ''.join(clean).encode('gbk'), {'total': len(text), 'kept': len(clean), 'dropped': len(lost)}

def compact_text(text):
    lines = text.splitlines()
    out, blank = [], False
    for l in lines:
        s = l.strip()
        if not s:
            if not blank: out.append(''); blank = True
        else: blank = False; out.append(s)
    return '\r\n'.join(out)

# ── Flask ──
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
    if sid not in sessions: sessions[sid] = {'files': []}
    files = []
    for f in request.files.getlist('files'):
        if not f.filename or not f.filename.lower().endswith('.txt'): continue
        data = f.read()
        if not data: continue
        text, enc, cjk = try_all_decodings(data)
        sessions[sid]['files'].append({'name': f.filename, 'data': data, 'encoding': enc})
        files.append({'name': f.filename, 'size': len(data), 'encoding': enc})
    return jsonify({'session_id': sid, 'files': files, 'total': len(files)})

@app.route('/api/convert', methods=['POST'])
def convert():
    data = request.get_json() or {}
    sid = data.get('session_id', '')
    compact = data.get('compact', True)
    split_mb = int(data.get('split_mb', 0) or 0)
    s = sessions.get(sid)
    if not s: return jsonify({'error': 'not found'}), 400
    results = []
    for f in s['files']:
        try:
            text, enc, _ = try_all_decodings(f['data'])
            gbk, stats = extract_clean_gbk(text)
            clean = gbk.decode('gbk')
            if compact: clean = compact_text(clean)
            parts = []
            max_bytes = split_mb * 1048576 if split_mb > 0 else 0
            if max_bytes > 0 and len(clean.encode('gbk')) > max_bytes:
                paras = clean.split('\r\n')
                chunk, chunks, cur = [], [], 0
                for p in paras:
                    sz = len(p.encode('gbk')) + 2
                    if cur + sz > max_bytes and chunk:
                        chunks.append('\r\n'.join(chunk)); chunk = [p]; cur = sz
                    else: chunk.append(p); cur += sz
                if chunk: chunks.append('\r\n'.join(chunk))
                for i, p in enumerate(chunks, 1): parts.append({'name': f'part_{i}.txt', 'data': p.encode('gbk')})
            else: parts.append({'name': f['name'], 'data': clean.encode('gbk')})
            f['output'] = parts
            results.append({'name': f['name'], 'encoding': enc, 'stats': stats, 'parts': len(parts)})
        except Exception as e: results.append({'name': f['name'], 'error': str(e)})
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
                for p in f['output']: zf.writestr(f'{base}_{p["name"]}', p['data'])
    buf.seek(0)
    resp = make_response(buf.getvalue())
    resp.headers['Content-Type'] = 'application/zip'
    resp.headers['Content-Disposition'] = 'attachment; filename="novels_gbk.zip"'
    return resp

def start_flask():
    app.run(host='127.0.0.1', port=5000, debug=False, use_reloader=False)

# ── Android 启动 ──
if __name__ == '__main__':
    threading.Thread(target=start_flask, daemon=True).start()
    import time
    from jnius import autoclass
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    activity = PythonActivity.mActivity
    wv = autoclass('android.webkit.WebView')(activity)
    settings = wv.getSettings()
    settings.setJavaScriptEnabled(True)
    settings.setDomStorageEnabled(True)
    wv.setWebChromeClient(autoclass('android.webkit.WebChromeClient')())
    activity.setContentView(wv)
    time.sleep(2)
    wv.loadUrl('http://127.0.0.1:5000')
    try:
        while True: time.sleep(1)
    except: pass
