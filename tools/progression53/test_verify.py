"""Synthetic rejection tests for the evidence checker; these are not native passes."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('progression53_verify', Path(__file__).with_name('verify.py'))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)
SOURCE = '1' * 40


def report(layout, phase):
    rows = []
    for city in verify.CITIES:
        for tier in (range(1, 6) if phase == 'seed' else [5]):
            rows.append({'city': city, 'townHallLevel': tier, 'producerLevel': tier,
                         'rotation': 0 if city <= 'h' else 180,
                         'managementBookPreserved': True, 'automaticPublicAndHousesReady': True,
                         'immutableEarlierHistories': True, 'savedDataRoundtrip': True,
                         'earlierEligibleFacilitiesRechecked': True,
                         'checkedPhysicalCells': 100, 'unpaidWallSourceCellsChecked': 100,
                         'compassWallQuotes': 4})
    sites = {f'{city}:{plot}:{verify.INDUSTRIES[((ord(city)-ord("a"))*3+slot+layout)%9]}': [1, 2, 3, 4, 5]
             for city in verify.CITIES for slot, plot in enumerate(verify.PLOTS)}
    return {'schema': 1, 'purpose': 'verified-clean-r7-progression53', 'source': SOURCE,
            'layout': layout, 'phase': phase, 'result': 'PASS' if phase == 'seed' else 'RESTART_PASS',
            'worldId': f'00000000-0000-0000-0000-{layout:012d}',
            'artificialMoneyOrMaterialGrants': 0, 'directPaidLevelOrBuildingSeeding': False,
            'manualTargetBlockWrites': 0, 'connectedClientAcceptance': False,
            'realTimePacingAcceptance': False, 'mapSHA256': '2' * 64,
            'authoredWorldSHA256': '3' * 64, 'checkpoints': rows,
            'completedHistorySHA256': {'an_original.dat': '4' * 64},
            'factoryPurchasedLevels': sites, 'factorySiteCumulativeChecks': dict.fromkeys(sites, 3),
            'actions': [{'exactDebit': True}], 'acceleratedScheduledCycles': 10,
            'simulatedActiveMinutes': 150, 'actualNativeTicks': 50}


class EvidenceRefusalTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.paths = []
        for layout in range(9):
            for phase in ('seed', 'verify'):
                path = Path(self.tmp.name) / f'{layout}-{phase}.json'
                path.write_text(json.dumps(report(layout, phase)))
                self.paths.append(path)

    def change(self, index, mutation):
        data = json.loads(self.paths[index].read_text())
        mutation(data)
        self.paths[index].write_text(json.dumps(data))

    def test_complete_synthetic_inventory_stays_labeled_report_check_only(self):
        result = verify.verify(self.paths, SOURCE)
        self.assertEqual(432, result['industrySiteCombinations'])
        self.assertEqual(2160, result['industrySiteTierChecks'])
        self.assertEqual('NATIVE_REPORTS_COMPLETE_REQUIRES_LOG_REVIEW', result['result'])
        self.assertTrue(result['requiresOriginalNativeLogAndExitReview'])
        self.assertFalse(result['connectedClientAcceptance'])

    def test_missing_earlier_city_checkpoint_refuses_even_with_pass_label(self):
        self.change(0, lambda row: row['checkpoints'].pop(0))
        with self.assertRaisesRegex(ValueError, 'Missing earlier'):
            verify.verify(self.paths, SOURCE)

    def test_money_grant_cannot_be_used_as_normal_economy_pass(self):
        self.change(0, lambda row: row.update(artificialMoneyOrMaterialGrants=1))
        with self.assertRaisesRegex(ValueError, 'provenance'):
            verify.verify(self.paths, SOURCE)

    def test_reusing_prior_world_as_fresh_layout_refuses(self):
        self.change(2, lambda row: row.update(worldId=report(0, 'seed')['worldId']))
        with self.assertRaisesRegex(ValueError, 'reused'):
            verify.verify(self.paths, SOURCE)

    def test_missing_level1_purchase_is_not_cumulative_coverage(self):
        self.change(0, lambda row: row['factoryPurchasedLevels'].__setitem__(
            next(iter(row['factoryPurchasedLevels'])), [2, 3, 4, 5]))
        with self.assertRaisesRegex(ValueError, 'purchase/upgrade level'):
            verify.verify(self.paths, SOURCE)

    def test_restart_changed_original_history_refuses(self):
        self.change(1, lambda row: row['completedHistorySHA256'].update(an_original='5' * 64))
        with self.assertRaisesRegex(ValueError, 'histories changed'):
            verify.verify(self.paths, SOURCE)

    def test_foreign_source_and_repeated_phase_refuse(self):
        self.change(0, lambda row: row.update(source='9' * 40))
        with self.assertRaisesRegex(ValueError, 'Foreign schema/source'):
            verify.verify(self.paths, SOURCE)
        self.paths[0].write_text(json.dumps(report(0, 'seed')))
        self.paths[1].write_text(json.dumps(report(0, 'seed')))
        with self.assertRaisesRegex(ValueError, 'Repeated layout/phase'):
            verify.verify(self.paths, SOURCE)

    def test_failed_native_status_never_becomes_pass(self):
        self.change(0, lambda row: row.update(result='FAIL'))
        with self.assertRaisesRegex(ValueError, 'Failed/incomplete'):
            verify.verify(self.paths, SOURCE)


if __name__ == '__main__':
    unittest.main()
