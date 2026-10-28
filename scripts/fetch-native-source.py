#!/usr/bin/env python3
"""Fetch the pinned CPU/JNI source set; verify every file against GitHub Git blob IDs.
No third-party code is executed by this downloader. GPU backends and vocab test fixtures are excluded.
"""
import concurrent.futures, hashlib, json, os, pathlib, threading, time, urllib.request
REVISION = '7ab4ee7baad2d920464cbacfad4f4b07cf111fd2'
ROOT = pathlib.Path(__file__).resolve().parents[1]
DEST = ROOT / '.native' / ('llama.cpp-' + REVISION)
TREE_URL = f'https://api.github.com/repos/ggml-org/llama.cpp/git/trees/{REVISION}?recursive=1'
local_tree = ROOT / '.native' / ('source-tree-' + REVISION + '.json')
if local_tree.exists():
    tree = json.loads(local_tree.read_text())
else:
    with urllib.request.urlopen(TREE_URL, timeout=30) as response: tree = json.load(response)
    local_tree.parent.mkdir(parents=True, exist_ok=True)
    local_tree.write_text(json.dumps(tree))
assert not tree.get('truncated')

def needed(p):
    if '/' not in p: return True
    if p.startswith(('cmake/', 'src/', 'include/', 'vendor/', 'tools/mtmd/')): return True
    if p.startswith(('ggml/include/', 'ggml/cmake/', 'ggml/src/ggml-cpu/')): return True
    if p.startswith('ggml/src/') and p.count('/') == 2: return True
    if p.startswith('ggml/') and p.count('/') == 1: return True
    return False

files = [f for f in tree['tree'] if f['type'] == 'blob' and needed(f['path'])]
assert all('..' not in pathlib.PurePosixPath(f['path']).parts for f in files)

def git_hash(data): return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def fetch(f):
    out = DEST / f['path']
    if out.is_file() and git_hash(out.read_bytes()) == f['sha']: return
    url = f"https://raw.githubusercontent.com/ggml-org/llama.cpp/{REVISION}/{f['path']}"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=35) as response: data = response.read()
            if git_hash(data) != f['sha']: raise ValueError('blob hash mismatch: '+f['path'])
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
            out.chmod(0o755 if f['mode'] == '100755' else 0o644)
            return
        except Exception:
            if attempt == 3: raise
            time.sleep(attempt + 1)

print(f'Fetching {len(files)} pinned files ({sum(f.get("size",0) for f in files)} bytes)', flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
    futures = [pool.submit(fetch, f) for f in files]
    completed = 0
    for future in concurrent.futures.as_completed(futures):
        future.result(); completed += 1
        if completed % 100 == 0: print(f'Verified {completed}/{len(files)} files', flush=True)
(DEST/'.phone-source-ready').write_text(REVISION+'\n')
print('All selected Git blobs verified. Native source ready.', flush=True)
