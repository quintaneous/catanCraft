"""Artifact qualification regressions; these are not native gameplay tests."""
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location('walls53_audit',Path(__file__).with_name('audit_sites.py'))
audit=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(audit)


class WallSourceCatalog(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(ROOT/'src/main/resources/assets/catancraft/sector53/sites.json.gz').read_bytes()
        cls.catalog=json.loads(gzip.decompress(cls.raw))
        cls.report=json.loads((ROOT/'docs/evidence/walls53_source_audit.json').read_bytes())
        cls.map=json.loads((ROOT/'src/main/resources/data/catancraft/maps/season1_river_bridges_v3.json').read_bytes())
        cls.template=json.loads(gzip.decompress((ROOT/'src/main/resources/assets/catancraft/sector_p3/city_a.json.gz').read_bytes()))
        cls.baseline={tuple(r[:3]):audit.state(r[3]) for a in cls.template['assets'] for r in a['reads']+a['fresh']}
        cls.owned={tuple(r[:3]):side for side,rows in cls.template['ownership'].items() for r in rows}

    def test_complete_actual_site_inventory_and_input_provenance(self):
        self.assertEqual(set(self.catalog['cities']),set('abcdefghijklmnop'))
        self.assertEqual(self.catalog['mapSHA256'],audit.MAP_SHA)
        self.assertEqual(self.catalog['inputManifestSHA256'],audit.INPUT_SHA)
        self.assertEqual(self.catalog['templateResourceSHA256'],audit.P3_SHA)
        self.assertEqual(self.report['inputFilesReverifiedUnchanged'],40)
        self.assertEqual(self.report['qualifiedCities'],16)
        self.assertEqual(self.report['regionsRead'],16)
        self.assertFalse(self.report['nativeExecutionClaimed'])
        self.assertEqual(sum(c['guardCells'] for c in self.report['cities']),1075280)
        for definition in self.map['territories']:
            row=self.catalog['cities'][definition['id']];center=definition['cityCenter']
            self.assertTrue(row['qualified'])
            self.assertEqual(row['center'],[center['x'],center['y'],center['z']])
            self.assertEqual(row['rotation'],center['rotation'])
            self.assertEqual(row['guardCount'],67205)
            self.assertEqual(row['ownedCount'],17462)

    def test_read_overrides_are_exact_guard_context_without_owned_or_road_writes(self):
        for definition in self.map['territories']:
            row=self.catalog['cities'][definition['id']];center=definition['cityCenter']
            transformed={audit.transform(p,center):audit.state(s,center['rotation']==180) for p,s in self.baseline.items()}
            owned={audit.transform(p,center) for p in self.owned}
            seen=set()
            for x,y,z,state in row['sourceOverrides']:
                p=(x,y,z)
                self.assertNotIn(p,seen);seen.add(p)
                self.assertIn(p,transformed)
                self.assertNotIn(p,owned)
                self.assertNotEqual(state,transformed[p])
            roads={tuple(p) for p in row['protectedRoads']}
            self.assertFalse(roads&owned)
            self.assertTrue(roads<=transformed.keys())

    def test_only_observed_twenty_six_owned_soil_tuples_and_exact_targets(self):
        expected={'f':10,'g':8,'i':1,'j':2,'l':5}
        total=0
        for name,row in self.catalog['cities'].items():
            edits=row['ownedSourceOverrides'];self.assertEqual(len(edits),expected.get(name,0));total+=len(edits)
            for x,y,z,before,basic,reinforced in edits:
                self.assertEqual(y,81)
                self.assertEqual(before,'minecraft:grass_block[snowy=false]')
                self.assertEqual(basic,'minecraft:stone_bricks')
                self.assertEqual(reinforced,'minecraft:light_gray_concrete')
                self.assertNotIn([x,y,z],row['protectedRoads'])
        self.assertEqual(total,26)
        self.assertEqual(self.catalog['cities']['a']['sourceOverrides'],[])
        self.assertEqual(self.catalog['cities']['a']['ownedSourceOverrides'],[])

    def test_all_existing_progression_envelopes_and_routes_are_preserved(self):
        self.assertEqual(sum(c['factoryTierPlacementsReviewed'] for c in self.report['cities']),2160)
        self.assertEqual(sum(len(c['civicAndSupportContracts']) for c in self.report['cities']),80)
        for row in self.report['cities']:
            for field in ['blockedOwnedSources','guardBlockEntities','houseRouteOverlap','buildingEnvelopeOverlap',
                          'factoryFullEnvelopeGuardOverlap','declaredRouteObstructions','readLeavesAdjacentToWallWrites']:
                self.assertEqual(row[field],[],(row['city'],field))
            self.assertTrue(all(not c['guardOverlap'] for c in row['civicAndSupportContracts']))
            self.assertEqual(row['levelConfigurations'],81)
            self.assertEqual(row['supportedCornerHistories'],433)
        self.assertEqual(self.report['templateGeometry']['nextTierTransitions'],984)

    def test_world_compass_ownership_and_transformed_masks_are_bijective(self):
        for definition in self.map['territories']:
            center=definition['cityCenter'];points=set()
            for p,side in self.owned.items():
                q=audit.transform(p,center)
                self.assertNotIn(q,points);points.add(q)
                worldside=audit.OPPOSITE[side] if center['rotation']==180 else side
                actual={'north':q[2]<center['z'],'east':q[0]>center['x'],
                        'south':q[2]>center['z'],'west':q[0]<center['x']}
                self.assertTrue(actual[worldside])

    def test_changed_input_manifest_refuses_without_touching_existing_outputs(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);(root/'INPUT_MANIFEST.json').write_text('{}')
            report=root/'report.json';catalog=root/'catalog.gz'
            report.write_bytes(b'prior evidence');catalog.write_bytes(b'prior artifact')
            with self.assertRaisesRegex(ValueError,'Input hash changed'):
                audit.run(ROOT,root,report,catalog)
            self.assertEqual(report.read_bytes(),b'prior evidence')
            self.assertEqual(catalog.read_bytes(),b'prior artifact')

    def test_catalog_is_deterministic_and_matches_native_loader_pin(self):
        self.assertEqual(self.raw[4:8],bytes(4))
        again=gzip.compress((json.dumps(self.catalog,sort_keys=True,separators=(',',':'))+'\n').encode(),mtime=0)
        self.assertEqual(self.raw,again)
        source=(ROOT/'src/main/java/com/quin/catancraft/world/SectorSites53.java').read_text()
        self.assertIn('RESOURCE_SHA = "'+audit.sha(self.raw)+'"',source)


if __name__=='__main__':unittest.main()
