"""Offline contract tests on actual pinned P3 assets; these are not native acceptance."""
import gzip, importlib.util, itertools, json, os, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('export_sector_p3',ROOT/'tools/export_sector_p3.py');export=importlib.util.module_from_spec(spec);spec.loader.exec_module(export)
INPUT=Path(os.environ.get('CATAN_P3_INPUT','/workspace/catancraft-p3-inputs-20261018/CatanCraft_City_A_Sector_Walls_P3'))
ARCHIVE=Path(os.environ.get('CATAN_P3_ARCHIVE','/workspace/attachments/cf23bf43-3442-4f45-9c79-f71ed145dd64/CatanCraft_City_A_Sector_Walls_P3_Review_Assets.zip'))
SIDES=['north','east','south','west'];AIR='minecraft:air'
def cells(rows):return {tuple(r[:3]):tuple(r[3:]) for r in rows}
class P3Contracts(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.output=Path(cls.tmp.name)/'p3.json.gz'
  cls.data=export.run(INPUT,cls.output,ROOT,ARCHIVE)
  cls.assets={(a['side'],a['variant']):a for a in cls.data['assets']}
  cls.connections=cls.data['corners']['connections']
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def test_exact_sparse_counts_and_owned_guard_union(self):
  expected={'north':(5013,5576),'east':(3962,4034),'south':(3438,3510),'west':(4270,4342)}
  allowned={}
  for side,counts in expected.items():
   union=set()
   for tier,count in zip(['basic','reinforced'],counts):
    a=self.assets[side,tier];self.assertEqual(len(a['fresh']),count)
    fresh=cells(a['fresh']);self.assertEqual(len(fresh),count);self.assertTrue(all(before==AIR and after!=AIR for before,after in fresh.values()));union.update(fresh)
    self.assertEqual([p-a for p,a in zip(a['worldPasteAnchorXYZ'],a['localAnchorXYZ'])],a['worldMinXYZ'])
   own=cells(self.data['ownership'][side]);self.assertEqual(set(own),union)
   self.assertFalse(set(allowned)&union,'A world cell has two sector owners');allowned.update(own)
 def test_twelve_supplied_modes_exact_guards_and_changes(self):
  count={'east':2886,'south':2578,'west':3112}
  for m in self.data['upgrades']:
   if m['derived']:continue
   self.assertEqual(len(m['changes']),count[m['side']]-6*len(m['openCorners']))
   guard=cells(m['guards']);self.assertEqual(set(guard),set(cells(self.data['ownership'][m['side']])))
   self.assertEqual(cells(m['changes']),{p:v for p,v in guard.items() if v[0]!=v[1]})
   self.assertEqual(sum(v==(AIR,AIR) for v in guard.values()),6*len(m['openCorners']))
  self.assertEqual(sum(not m['derived'] for m in self.data['upgrades']),12)
 def test_north_open_modes_independently_preserve_original_assets_and_caps(self):
  north=[m for m in self.data['upgrades'] if m['derived']];self.assertEqual(len(north),4)
  for m in north:
   self.assertEqual(m['side'],'north');self.assertEqual(len(m['changes']),4539-6*len(m['openCorners']))
   guard=cells(m['guards'])
   for cap in self.data['northPins']['preservedEndPierCaps']:
    self.assertEqual(guard[tuple(cap['worldXYZ'])],(cap['basic'],cap['reinforced']))
  self.assertEqual(self.data['northPins']['archiveSHA256'],export.P2_SHA256)
 def assemble(self,levels):
  scene={}
  for side,level in zip(SIDES,levels):
   if level==0:continue
   for p,states in cells(self.data['ownership'][side]).items():scene[p]=states[level-1]
  levels=dict(zip(SIDES,levels))
  for c in self.connections:
   owner,neighbor=c['transactionOwner'],c['neighbor'];ot=1 if c['ownerTier']=='basic' else 2;nt=1 if c['neighborTier']=='basic' else 2
   if levels[owner]!=ot or levels[neighbor]!=nt:continue
   for edit in c['changes']:
    p=tuple(edit['worldXYZ']);self.assertEqual(scene[p],edit['before']);scene[p]=edit['after']
  return scene
 def test_all_81_city_level_configurations_caps_and_every_upgrade_reverse(self):
  checked=upgrades=0
  for levels in itertools.product(range(3),repeat=4):
   before=self.assemble(levels);checked+=1
   for i,side in enumerate(SIDES):
    if levels[i]!=1:continue
    opens=sorted({c['corner'] for c in self.connections if (c['transactionOwner']==side and levels[SIDES.index(c['neighbor'])]>0) or (c['neighbor']==side and levels[SIDES.index(c['transactionOwner'])]>0)})
    mode=next(m for m in self.data['upgrades'] if m['side']==side and sorted(m['openCorners'])==opens)
    guard=cells(mode['guards']);self.assertTrue(all(before[p]==v[0] for p,v in guard.items()))
    after=dict(before)
    for p,(old,new) in cells(mode['changes']).items():self.assertEqual(after[p],old);after[p]=new
    self.assertTrue(all(after[p]==v[1] for p,v in guard.items()))
    upgraded=list(levels);upgraded[i]=2;self.assertEqual(after,self.assemble(upgraded))
    for p,(old,new) in reversed(list(cells(mode['changes']).items())):self.assertEqual(after[p],new);after[p]=old
    self.assertEqual(after,before);upgrades+=1
  self.assertEqual(checked,81);self.assertEqual(upgrades,108)
 def test_16_seams_have_exact_6_cells_per_owner_and_all_four_tier_pairs(self):
  self.assertEqual(len(self.connections),16)
  for corner in ['NW','NE','SW','SE']:
   group=[c for c in self.connections if c['corner']==corner]
   self.assertEqual({(c['ownerTier'],c['neighborTier']) for c in group},set(itertools.product(['basic','reinforced'],repeat=2)))
   for c in group:
    self.assertEqual(c['transactionOwner'],'north' if corner.startswith('N') else 'south')
    self.assertEqual(len({tuple(e['worldXYZ']) for e in c['changes']}),12)
    for side in [c['transactionOwner'],c['neighbor']]:self.assertEqual(sum(e['cellOwner']==side for e in c['changes']),6)
 def test_no_owned_writes_in_roads_core_rear_corridor_or_current_building_envelopes(self):
  self.assertEqual(len(self.data['runtimeAudit']['envelopes']),19)
  for a in self.data['assets']:
   for x,y,z,_,_ in a['fresh']:
    self.assertFalse(-830<=x<-669 and -833<=z<-666)
    self.assertFalse(-770<=x<-729 and -666<=z<-653)
    for e in self.data['runtimeAudit']['envelopes']:
     self.assertFalse(all(lo<=v<=hi for lo,v,hi in zip(e['worldMinInclusive'],[x,y,z],e['worldMaxInclusive'])))
 def test_deterministic_gzip_has_zero_timestamp(self):
  raw=self.output.read_bytes();self.assertEqual(raw[4:8],bytes(4))
  again=Path(self.tmp.name)/'again.json.gz';export.run(INPUT,again,ROOT,ARCHIVE);self.assertEqual(raw,again.read_bytes())
 def test_wrong_archive_is_refused_before_export(self):
  wrong=Path(self.tmp.name)/'wrong.zip';wrong.write_bytes(b'not an approved asset')
  with self.assertRaises(AssertionError):export.run(INPUT,Path(self.tmp.name)/'bad.json.gz',ROOT,wrong)
if __name__=='__main__':unittest.main()
