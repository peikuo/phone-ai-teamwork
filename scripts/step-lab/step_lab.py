#!/usr/bin/env python3
"""Versioned, evidence-first step cases. Debug APK only; no arbitrary outbound text."""
from __future__ import annotations
import argparse
import json
import re
import sqlite3
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).with_name('cases.json')
DEFAULT_DB = ROOT / 'captures' / 'step-lab.sqlite'
ADB = Path.home() / 'Library/Android/sdk/platform-tools/adb'
LEVELS = {'small': 0, 'medium': 1, 'long': 2}
ALLOWED_PACKAGES = frozenset({
    'org.phoneai.teamwork', 'com.moonshot.kimichat', 'com.larus.nova', 'com.deepseek.chat',
})


def catalog():
    data = json.loads(CATALOG.read_text())
    assert data['schema'] == 1
    cases = data['cases']
    by_id = {c['id']: c for c in cases}
    assert len(by_id) == len(cases)
    for c in cases:
        assert re.fullmatch(r'[a-z0-9_.-]+', c['id'])
        assert c['level'] in LEVELS and c['kind'] in {'model_proposal', 'public_stage', 'public_draft_check', 'relay', 'capability_pending'}
        assert c['owner'] and c['setup'] and c['expect'] and c['capabilities']
        if c.get('with_image'):
            assert c['id'] == 'kimi.send.image.v1' and c['package'] == 'com.moonshot.kimichat' and c['objective'] == 'send'
        for dep in c['depends_on']:
            assert dep in by_id and LEVELS[by_id[dep]['level']] <= LEVELS[c['level']]
    visiting, visited = set(), set()
    def visit(case_id):
        assert case_id not in visiting, 'dependency cycle'
        if case_id in visited:
            return
        visiting.add(case_id)
        for dep in by_id[case_id]['depends_on']:
            visit(dep)
        visiting.remove(case_id)
        visited.add(case_id)
    for case_id in by_id:
        visit(case_id)
    return by_id


def db_connect(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path)
    db.execute('''CREATE TABLE IF NOT EXISTS runs (
        run_id TEXT PRIMARY KEY, case_id TEXT NOT NULL, level TEXT NOT NULL,
        owner TEXT NOT NULL, device TEXT, app_version TEXT,
        started_at TEXT NOT NULL, ended_at TEXT,
        status TEXT NOT NULL, reason TEXT NOT NULL,
        expected_json TEXT NOT NULL, actual_json TEXT NOT NULL,
        metrics_json TEXT NOT NULL, screenshot_count INTEGER NOT NULL DEFAULT 0)''')
    db.execute('''CREATE TABLE IF NOT EXISTS events (
        event_id INTEGER PRIMARY KEY AUTOINCREMENT, run_id TEXT NOT NULL,
        at TEXT NOT NULL, kind TEXT NOT NULL, detail_json TEXT NOT NULL,
        FOREIGN KEY(run_id) REFERENCES runs(run_id))''')
    db.commit()
    return db


def utc():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def event(db, run_id, kind, detail):
    db.execute('INSERT INTO events(run_id,at,kind,detail_json) VALUES(?,?,?,?)',
               (run_id, utc(), kind, json.dumps(detail, ensure_ascii=False, sort_keys=True)))
    db.commit()
    print(json.dumps({'event': kind, 'run_id': run_id, **detail}, ensure_ascii=False), flush=True)


def command(argv, timeout=30, binary=False):
    p = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if p.returncode:
        raise RuntimeError(f'COMMAND_FAILED:{p.returncode}:{p.stderr.decode(errors="replace")[:160]}')
    return p.stdout if binary else p.stdout.decode(errors='replace')


def adb(serial, *args, timeout=30, binary=False):
    return command([str(ADB), '-s', serial, *args], timeout=timeout, binary=binary)


def one_device(requested):
    lines = command([str(ADB), 'devices', '-l']).splitlines()[1:]
    serials = [line.split()[0] for line in lines if len(line.split()) > 1 and line.split()[1] == 'device']
    if requested:
        if requested not in serials:
            raise RuntimeError('DEVICE_NOT_CONNECTED')
        return requested
    if len(serials) != 1:
        raise RuntimeError('EXACTLY_ONE_DEVICE_REQUIRED')
    return serials[0]


def package_version(serial, package):
    out = adb(serial, 'shell', 'dumpsys', 'package', package)
    name = re.search(r'\bversionName=([^\s]+)', out)
    code = re.search(r'\bversionCode=(\d+)', out)
    if not name or not code:
        raise RuntimeError('APP_VERSION_UNAVAILABLE')
    return f'{name.group(1)} ({code.group(1)})'


def report(serial, filename):
    return adb(serial, 'shell', 'run-as', 'org.phoneai.teamwork', 'cat', f'files/{filename}')


def fields(raw):
    return dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)


def matching_draft_report(actual, run_id):
    return actual.get('run_id') == run_id and actual.get('state') in {'VERIFIED', 'ERROR'}


def previous_pass(db, case_id, serial):
    dependency = catalog()[case_id]
    required = 3 if dependency['level'] == 'small' else 1
    version = dependency['version']
    rows = db.execute('SELECT status,ended_at FROM runs WHERE case_id=? AND device=? AND app_version=? ORDER BY started_at DESC LIMIT ?',
                      (case_id, serial, version, required)).fetchall()
    return len(rows) == required and all(
        status == 'PASS' and ended_at and
        (datetime.now(timezone.utc) - datetime.fromisoformat(ended_at)).total_seconds() <= 3600
        for status, ended_at in rows)


def front_package(serial, package):
    if package not in ALLOWED_PACKAGES:
        raise ValueError('PACKAGE_OUTSIDE_PHONE_LAB')
    out = adb(serial, 'shell', 'dumpsys', 'window', timeout=10)
    focus = re.search(r'mCurrentFocus=([^\n]+)', out)
    return bool(focus and f'u0 {package}/' in focus.group(1))


def wait_report(serial, filename, run_id, timeout):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            raw = report(serial, filename)
            f = fields(raw)
            if f.get('run_id') == run_id and f.get('state') in {'FINISHED', 'ERROR'}:
                return f
        except Exception:
            pass
        time.sleep(0.7)
    raise RuntimeError('REPORT_TIMEOUT')


def arm_and_wait(serial, case):
    if not front_package(serial, case['package']):
        raise RuntimeError('SETUP_APP_NOT_FOREGROUND')
    started_ms = int(adb(serial, 'shell', 'date', '+%s%3N').strip())
    adb(serial, 'shell', 'am', 'broadcast', '--receiver-foreground', '-a', 'org.phoneai.teamwork.ARM_INSPECTION',
        '-n', 'org.phoneai.teamwork/.model.ExperimentReceiver', '--es', 'participant', case['participant'],
        '--ez', 'with_image', 'true' if case.get('with_image') else 'false', timeout=12)
    end = time.monotonic() + 12
    while time.monotonic() < end:
        status = adb(serial, 'shell', 'dumpsys', 'activity', 'service',
                     'org.phoneai.teamwork/.phone.InspectionService', timeout=10)
        observed = re.search(r'phoneai\.observedAt=(\d+)', status)
        if (f'phoneai.package={case["package"]}' in status and 'phoneai.truncated=false' in status and
                observed and int(observed.group(1)) >= started_ms):
            input_lengths = [int(x) for x in re.findall(r'phoneai\.input [^\n]*textLength=(\d+)', status)]
            if case['id'] == 'kimi.voice.pause.v1' and input_lengths:
                raise Blocked('SETUP_MISMATCH:TEXT_INPUT_PRESENT')
            if case['id'] == 'ds.input.visible.v1' and input_lengths != [0]:
                raise Blocked('SETUP_MISMATCH:ONE_EMPTY_INPUT_REQUIRED')
            if case['id'] == 'ds.send.visible.v1' and (len(input_lengths) != 1 or input_lengths[0] == 0):
                raise Blocked('SETUP_MISMATCH:ONE_NONEMPTY_DRAFT_REQUIRED')
            if case['id'] == 'kimi.send.visible.v1' and (input_lengths != [277] or
                    status.count('phoneai.publicSend path=') != 1):
                raise Blocked('SETUP_MISMATCH:PUBLIC_KIMI_DRAFT_AND_SEND_REQUIRED')
            if case['id'] == 'kimi.send.image.v1':
                if input_lengths != [277] or status.count('phoneai.publicSend path=') != 1:
                    raise Blocked('SETUP_MISMATCH:PUBLIC_KIMI_DRAFT_AND_SEND_REQUIRED')
                if 'phoneai.screenshot=当前窗口截图，仅内存保存' not in status:
                    if 'phoneai.screenshot=截图中' in status:
                        time.sleep(0.5)
                        continue
                    raise Blocked('WINDOW_SCREENSHOT_UNAVAILABLE')
            return
        time.sleep(0.5)
    raise RuntimeError('OBSERVATION_UNAVAILABLE')


def evaluate(expected, actual):
    mismatches = {key: {'expected': value, 'actual': actual.get(key)}
                  for key, value in expected.items() if actual.get(key) != value}
    if actual.get('state') == 'ERROR':
        return 'FAIL', actual.get('reason', 'CASE_EXECUTION_ERROR'), mismatches
    if actual.get('parse') in {'BACKEND_UNAVAILABLE_OR_ERROR', 'INVALID_OUTPUT_NEEDS_USER'} or 'error' in actual:
        return 'FAIL', 'MODEL_OR_BACKEND_ERROR', mismatches
    return ('FAIL', 'EXPECTATION_MISMATCH', mismatches) if mismatches else ('PASS', 'EXPECTED_RESULT', {})


def run_case(args):
    cases = catalog()
    case = cases[args.case_id]
    db = db_connect(args.db)
    run_id = uuid.uuid4().hex[:24]
    device = None
    version = None
    db.execute('INSERT INTO runs(run_id,case_id,level,owner,started_at,status,reason,expected_json,actual_json,metrics_json) VALUES(?,?,?,?,?,?,?,?,?,?)',
               (run_id, case['id'], case['level'], case['owner'], utc(), 'RUNNING', 'PREFLIGHT',
                json.dumps(case['expect'], ensure_ascii=False), '{}', '{}'))
    db.commit()
    event(db, run_id, 'STEP_STARTED', {'case_id': case['id'], 'level': case['level'], 'owner': case['owner']})
    actual = {}
    mismatches = {}
    try:
        if case['kind'] in {'relay', 'capability_pending'}:
            raise Blocked('CAPABILITY_PENDING:' + ','.join(case.get('blocked_until', [])))
        device = one_device(args.serial)
        version = package_version(device, case['package'])
        if version != case['version']:
            raise Blocked(f'APP_VERSION_MISMATCH:{version}')
        for dep in case['depends_on']:
            if not previous_pass(db, dep, device):
                raise Blocked(f'DEPENDENCY_NOT_PASSED:{dep}')
        if case['kind'] == 'model_proposal':
            if case['id'] in {'kimi.send.visible.v1', 'kimi.send.image.v1'}:
                adb(device, 'shell', 'am', 'start', '-n', 'org.phoneai.teamwork/.ui.MainActivity')
                adb(device, 'shell', 'am', 'start', '-n', 'com.moonshot.kimichat/.MainActivity')
                time.sleep(1.5)
            arm_and_wait(device, case)
            adb(device, 'shell', 'am', 'broadcast', '--receiver-foreground', '-a', 'org.phoneai.teamwork.RUN_EXPERIMENT',
                '-n', 'org.phoneai.teamwork/.model.ExperimentReceiver',
                '--es', 'case_id', case['objective'], '--es', 'run_id', run_id,
                '--ez', 'with_image', 'true' if case.get('with_image') else 'false', timeout=12)
            actual = wait_report(device, 'model-experiment-last.txt', run_id, args.timeout)
        elif case['kind'] == 'public_stage':
            previous = fields(report(device, 'single-exchange-last.txt')).get('taskId')
            adb(device, 'shell', 'am', 'broadcast', '-a', 'org.phoneai.teamwork.STAGE_PUBLIC_PROBE',
                '-n', 'org.phoneai.teamwork/.model.ExperimentReceiver', timeout=12)
            end = time.monotonic() + args.timeout
            while time.monotonic() < end:
                actual = fields(report(device, 'single-exchange-last.txt'))
                if actual.get('taskId') != previous and actual.get('state') not in {None, 'RUNNING'}:
                    break
                time.sleep(0.7)
            else:
                raise RuntimeError('REPORT_TIMEOUT')
        elif case['kind'] == 'public_draft_check':
            if case['participant'] != 'kimi' or not front_package(device, case['package']):
                raise Blocked('KIMI_STAGED_PAGE_NOT_FOREGROUND')
            # Xiaomi can freeze a cached receiver even while its accessibility service is bound.
            # Rewarm only our app, return to the already selected Kimi app, then wait for a stable window.
            adb(device, 'shell', 'am', 'start', '-n', 'org.phoneai.teamwork/.ui.MainActivity')
            adb(device, 'shell', 'am', 'start', '-n', 'com.moonshot.kimichat/.MainActivity')
            time.sleep(1.5)
            if not front_package(device, case['package']):
                raise Blocked('KIMI_STAGED_PAGE_NOT_FOREGROUND')
            adb(device, 'shell', 'run-as', 'org.phoneai.teamwork', 'rm', '-f',
                'files/kimi-draft-check-last.txt')
            adb(device, 'shell', 'am', 'broadcast', '--receiver-foreground', '-a', 'org.phoneai.teamwork.CHECK_KIMI_PUBLIC_DRAFT',
                '-n', 'org.phoneai.teamwork/.model.ExperimentReceiver', '--es', 'run_id', run_id, timeout=12)
            end = time.monotonic() + args.timeout
            while time.monotonic() < end:
                try:
                    actual = fields(report(device, 'kimi-draft-check-last.txt'))
                except Exception:
                    time.sleep(0.5)
                    continue
                if matching_draft_report(actual, run_id):
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError('REPORT_TIMEOUT')
        status, reason, mismatches = evaluate(case['expect'], actual)
    except Blocked as e:
        status, reason = 'BLOCKED', str(e)
    except Exception as e:
        status, reason = 'ERROR', str(e).splitlines()[0][:180]
    metrics = {k: actual.get(k) for k in ('backend', 'load_ms', 'step_ms', 'sampled_peak_pss_kib', 'image',
                                         'image_width', 'image_height', 'parse') if k in actual}
    db.execute('UPDATE runs SET device=?,app_version=?,ended_at=?,status=?,reason=?,actual_json=?,metrics_json=?,screenshot_count=? WHERE run_id=?',
               (device, version, utc(), status, reason, json.dumps({'fields': actual, 'mismatches': mismatches}, ensure_ascii=False),
                json.dumps(metrics, ensure_ascii=False), 0, run_id))
    db.commit()
    event(db, run_id, 'STEP_FINISHED', {'case_id': case['id'], 'status': status, 'reason': reason,
                                       'screenshots': 0, 'actual': {k: actual.get(k) for k in case['expect']}})
    if status == 'PASS' and case['level'] == 'long':
        event(db, run_id, 'SCENARIO_COMPLETED', {'case_id': case['id']})
    if args.notify:
        subprocess.run(['osascript', '-e',
            f'display notification "{status}: {reason}" with title "Phone AI Step Lab"'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5, check=False)
    return 0 if status in {'PASS', 'BLOCKED'} else 1


class Blocked(Exception):
    pass


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--db', type=Path, default=DEFAULT_DB)
    sub = p.add_subparsers(dest='command', required=True)
    sub.add_parser('validate')
    sub.add_parser('list')
    history = sub.add_parser('history')
    history.add_argument('--limit', type=int, default=20)
    detail = sub.add_parser('report')
    detail.add_argument('run_id')
    sub.add_parser('summary')
    run = sub.add_parser('run')
    run.add_argument('case_id')
    run.add_argument('--serial')
    run.add_argument('--timeout', type=int, default=150)
    run.add_argument('--notify', action='store_true', help='Also show a local macOS completion notification')
    run.add_argument('--screenshot-every', type=int, default=0, help='Temporarily disabled; only 0 is accepted')
    args = p.parse_args()
    cases = catalog()
    if args.command == 'validate':
        print(f'VALID {len(cases)} cases')
    elif args.command == 'list':
        for c in cases.values():
            print(f'{c["id"]:28} {c["level"]:6} {c["owner"]:36} dependencies={",".join(c["depends_on"])}')
    elif args.command == 'history':
        db = db_connect(args.db)
        for row in db.execute('SELECT run_id,case_id,started_at,status,reason,screenshot_count FROM runs ORDER BY started_at DESC LIMIT ?', (args.limit,)):
            print('\t'.join(map(str, row)))
    elif args.command == 'report':
        db = db_connect(args.db)
        db.row_factory = sqlite3.Row
        row = db.execute('SELECT * FROM runs WHERE run_id=?', (args.run_id,)).fetchone()
        if not row:
            p.error('unknown run id')
        data = dict(row)
        for key in ('expected_json', 'actual_json', 'metrics_json'):
            data[key[:-5]] = json.loads(data.pop(key))
        data['events'] = [dict(r) for r in db.execute('SELECT at,kind,detail_json FROM events WHERE run_id=? ORDER BY event_id', (args.run_id,))]
        for e in data['events']:
            e['detail'] = json.loads(e.pop('detail_json'))
        print(json.dumps(data, ensure_ascii=False, indent=2))
    elif args.command == 'summary':
        db = db_connect(args.db)
        rows = db.execute('SELECT case_id,status,reason,metrics_json FROM runs ORDER BY started_at').fetchall()
        result = {}
        for case_id, status, reason, metrics_json in rows:
            group = result.setdefault(case_id, {'PASS': 0, 'FAIL': 0, 'BLOCKED': 0, 'ERROR': 0, 'step_ms': [], 'reasons': {}})
            group[status] += 1
            group['reasons'][reason] = group['reasons'].get(reason, 0) + 1
            step = re.match(r'\d+', str(json.loads(metrics_json).get('step_ms', '')))
            if step:
                group['step_ms'].append(int(step.group()))
        for group in result.values():
            times = group['step_ms']
            group['avg_step_ms'] = round(sum(times) / len(times)) if times else None
            group['max_step_ms'] = max(times) if times else None
            del group['step_ms']
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        if args.case_id not in cases:
            p.error('unknown case id')
        if args.timeout < 10 or args.timeout > 600 or args.screenshot_every != 0:
            p.error('timeout must be 10..600 seconds; screenshots require an isolated target-window path and are disabled')
        return run_case(args)
    return 0


if __name__ == '__main__':
    sys.exit(main())
