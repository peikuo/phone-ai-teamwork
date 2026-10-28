import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('step_lab', HERE / 'step_lab.py')
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)

class StepLabTest(unittest.TestCase):
    def test_front_package_rejects_outside_allowlist_before_adb(self):
        with self.assertRaisesRegex(ValueError, 'PACKAGE_OUTSIDE_PHONE_LAB'):
            lab.front_package('test-phone', 'com.example.other')

    def test_catalog_has_progressive_acyclic_cases(self):
        cases = lab.catalog()
        self.assertEqual(12, len(cases))
        self.assertEqual('small', cases['ds.send.visible.v1']['level'])
        self.assertEqual('long', cases['kimi.answer_to_ds.v1']['level'])
        self.assertIn('verified_kimi_message_in_store', cases['kimi.answer_to_ds.v1']['blocked_until'])

    def test_oracle_separates_model_failure_from_setup_and_success(self):
        expected = {'proposal': 'FOCUS_INPUT', 'selected_editable': 'true'}
        self.assertEqual(('PASS', 'EXPECTED_RESULT', {}), lab.evaluate(expected, expected))
        self.assertEqual('EXPECTATION_MISMATCH', lab.evaluate(expected, {'proposal': 'PAUSE'})[1])
        self.assertEqual('MODEL_OR_BACKEND_ERROR', lab.evaluate(expected, {'parse': 'INVALID_OUTPUT_NEEDS_USER'})[1])
        self.assertEqual('EMPTY_PAGE_ANCHORS_REQUIRED', lab.evaluate(expected, {'state': 'ERROR', 'reason': 'EMPTY_PAGE_ANCHORS_REQUIRED'})[1])

    def test_three_recent_passes_required_for_small_dependency(self):
        with tempfile.TemporaryDirectory() as d:
            db = lab.db_connect(Path(d) / 'runs.sqlite')
            for i in range(3):
                rid = f'run{i}'
                db.execute('INSERT INTO runs(run_id,case_id,level,owner,device,app_version,started_at,ended_at,status,reason,expected_json,actual_json,metrics_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',
                           (rid, 'ds.input.visible.v1', 'small', 'model', 'test-phone', '2.5.2 (273)', lab.utc(), lab.utc(), 'PASS', '', '{}', '{}', '{}'))
                db.commit()
                self.assertEqual(i == 2, lab.previous_pass(db, 'ds.input.visible.v1', 'test-phone', '2.5.2 (273)'))
            db.execute("UPDATE runs SET status='FAIL' WHERE run_id='run2'")
            db.commit()
            self.assertFalse(lab.previous_pass(db, 'ds.input.visible.v1', 'test-phone', '2.5.2 (273)'))

    def test_events_and_runs_are_persisted(self):
        with tempfile.TemporaryDirectory() as d:
            db = lab.db_connect(Path(d) / 'runs.sqlite')
            db.execute("INSERT INTO runs(run_id,case_id,level,owner,started_at,status,reason,expected_json,actual_json,metrics_json) VALUES('r','c','small','model','now','RUNNING','', '{}','{}','{}')")
            db.commit()
            lab.event(db, 'r', 'STEP_FINISHED', {'status': 'PASS'})
            rows = db.execute('SELECT kind,detail_json FROM events').fetchall()
            self.assertEqual('STEP_FINISHED', rows[0][0])
            self.assertEqual({'status': 'PASS'}, json.loads(rows[0][1]))

if __name__ == '__main__':
    unittest.main()
