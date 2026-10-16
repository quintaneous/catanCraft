"""Extract reviewed sparse defense contracts only; never imports worlds or screenshots."""
import gzip,hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]); result=[]
def read(p):return json.loads(p.read_text())
r4=root/'r4a';r6=root/'r6';selected=read(r4/'validation/rotation-and-socket-validation.json')['nonoverlapping_review_assembly'];locals={s['socket_id']:s for s in read(r4/'contracts/settlement-local-sockets.json')}
for row in read(r4/'contracts/territory-socket-survey.json')['rows']:
 city=row['territory_id'];sid=row['socket_id']
 if sid not in selected[city]:continue
 assert row['condition']=='PASS' and row['module_id'].startswith('r4a_')
 d=read(r4/f'contracts/placement-deltas/{city}_{sid}.json');a=row['world_anchor_xyz'];writes=[[*[a[i]+w['relative_xyz'][i] for i in range(3)],w['expected_before'],w['after']] for w in d['writes']];wp={tuple(w[:3]) for w in writes};reads={}
 for s in d['support_preconditions']:
  for y,state in s['profile']:reads[(s['world_xz'][0],y,s['world_xz'][1])]=state
 for key in ['public_road_surface_read_only_conditions','public_road_empty_clearance_read_only_conditions']:
  for p in d.get(key,[]):reads[tuple(p['world_xyz'])]=p['expected_before']
 lo=row['world_bounds_inclusive']['min'];hi=row['world_bounds_inclusive']['max']
 for y in range(max(a[1],lo[1]),hi[1]+1):
  for z in range(lo[2],hi[2]+1):
   for x in range(lo[0],hi[0]+1):
    if (x,y,z) not in wp:reads[(x,y,z)]='minecraft:air'
 result.append(dict(city=city.lower(),socket=sid,module=row['module_id'],dependencies=locals[sid]['depends_on'],excludes=locals[sid]['excludes'],writes=writes,reads=[[*p,s] for p,s in sorted(reads.items())]))
for row in read(r6/'contracts/r6-socket-survey.json')['rows']:
 assert row['condition']=='PASS_STATIC';d=read(r6/row['delta_file']);reads={}
 for key in ['support_read_only','clearance_read_only']:
  for p in d[key]:reads[tuple(p['world_xyz'])]=p['expected_before']
 result.append(dict(city=row['territory_id'].lower(),socket=row['socket_id'],module=row['module_id'],dependencies=[],excludes=row['excludes_r4a']+row['excludes_r6'],writes=[[*w['world_xyz'],w['expected_before'],w['after']] for w in d['writes']],reads=[[*p,s] for p,s in sorted(reads.items())]))
assert len(result)==864
for p in result:
 assert len({tuple(w[:3]) for w in p['writes']})==len(p['writes']);assert all(w[3]=='minecraft:air' for w in p['writes'])
raw=json.dumps(result,separators=(',',':'),sort_keys=True).encode();out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(gzip.compress(raw,mtime=0));print(len(result),hashlib.sha256(out.read_bytes()).hexdigest())
