"""Focused regressions for segmented evidence, independent of the native wall run."""
import copy
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/authored53'))
from verify_wall_segments import CITIES, WALL, PASS, check_events, check_native, validate_plan


def first_events():
    rows = [dict(kind='EXPLICIT_DEVELOPER_FIXTURE_GRANT', nation=str(i),
                 money=1_000_000, eachResource=100_000) for i in range(4)]
    rows.extend(dict(kind='normal_founding_and_automatic_base', city=c,
                     route='nation_start' if c in 'admp' else 'nation_claim') for c in CITIES)
    rows.append(dict(kind='wall_purchase', city='a', side='north', partialAtSeed=False,
                     groupSchema=1, money=1500, observedWritten=5, exactChanges=5,
                     townHallLevel=1, id='unique', operation='catancraft_sector_a_north_'+'a'*36+'_v1.dat',
                     immutableHistorySHA256='0'*64, journalSHA256='1'*64, **{'from':0, 'to':1}))
    return rows


class SegmentedEvidence(unittest.TestCase):
    def test_no_gaps_or_split_partials(self):
        validate_plan([[0,0],[0,64],[64,126],[126,128]])
        for ranges in ([[0,1],[2,128]], [[0,126],[126,127],[127,128]],
                       [[0,126]], [[0,2],[1,128]], [[0,0],[0,0],[0,128]]):
            with self.subTest(ranges=ranges), self.assertRaises(ValueError):
                validate_plan(ranges)

    def test_events_match_actual_scope(self):
        rows = first_events()
        check_events(rows[:-1], 0)
        check_events(rows, 1)
        for changes in ({'side':'east'}, {'groupSchema':2}, {'townHallLevel':5},
                        {'observedWritten':3}, {'operation':'other.dat'}, {'partialAtSeed':True}):
            changed = copy.deepcopy(rows)
            changed[-1].update(changes)
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                check_events(changed, 1)

    def test_native_log_source_exit_and_predecessor(self):
        source, root, predecessor = 'a'*40, '/worlds/fresh', 'f'*64
        args = ['runGameTestServer','-I','tools/authored53/validate.gradle',
                '-I','tools/authored53/combined.gradle', '-PgameTestNamespace='+WALL,
                '-PauthoredSource='+source,'-PauthoredRunRoot='+root,'-PauthoredPhase=seed',
                '-Pwall53RangeStart=2','-Pwall53RangeEnd=4','-Pwall53Predecessor='+predecessor]
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'native.log'
            log.write_bytes(b"Running test batch 'sector53_authored:1' (1 tests)\n=== 1 GAME TESTS COMPLETE ===\nAll 1 required tests passed\n")
            proc = dict(source=source, sourceUnchanged=True, status=PASS, exitCode=0, expectedTests=1,
                        reportedCompleted=[1], requiredTestsPassedMessage=True, command=args,
                        startedUnix=2.0, endedUnix=3.0, nativeLogSHA256=hashlib.sha256(log.read_bytes()).hexdigest())
            def check(**updates):
                return check_native(dict(proc, **updates), log, source, root, 'seed', [2,4], predecessor, 1.0)
            self.assertEqual(3.0, check())
            for changes in ({'source':'b'*40}, {'exitCode':1}, {'startedUnix':0.5},
                            {'command':args[:-1]}, {'command':args+['-Pwall53RangeEnd=4']}):
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    check(**changes)
            log.write_bytes(log.read_bytes()+b'changed')
            with self.assertRaises(ValueError):
                check()


if __name__ == '__main__':
    unittest.main()
