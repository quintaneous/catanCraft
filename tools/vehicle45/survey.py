"""Read-only authored factory survey; never opens a world or modifies assets."""
import base64,zipfile,io,gzip,struct,hashlib,collections
from pathlib import Path
b=base64.b64decode(Path('src/main/resources/data/catancraft/basic_construction/assets.b64').read_bytes());z=zipfile.ZipFile(io.BytesIO(b));s=z.read('vehicle_factory.schem');print('sha',hashlib.sha256(s).hexdigest());f=io.BytesIO(gzip.decompress(s))
def n(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
def st():return f.read(n('H')).decode()
def payload(t):
 if t in [1,2,3,4,5,6]:return n({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
 if t==7:return f.read(n('i'))
 if t==8:return st()
 if t==9:
  k=n('B');return [payload(k) for _ in range(n('i'))]
 if t==10:
  d={}
  while (k:=n('B')):key=st();d[key]=payload(k)
  return d
 if t in [11,12]:return [n('i' if t==11 else 'q') for _ in range(n('i'))]
 raise ValueError(t)
t=n('B');st();d=payload(t);print('size',d['Width'],d['Height'],d['Length'], 'offset',d['Offset']);pal={v:k for k,v in d['Palette'].items()};a=[];v=sh=0
for b in d['BlockData']:
 v|=(b&127)<<sh
 if b&128:sh+=7
 else:a.append(pal[v]);v=sh=0
W,H,L=d['Width'],d['Height'],d['Length']
def block(x,y,z):return a[(y*L+z)*W+x] if 0<=x<W and 0<=y<H and 0<=z<L else 'outside'

assert hashlib.sha256(s).hexdigest() == '793e2a277fcb7287ba76298d2080808e315bcf626f76e9fe8f9ce5143e731649'
assert (W,H,L)==(31,47,31) and d['Offset']==[-29,-2,-15]
assert block(18,3,9)=='minecraft:crafting_table'
route=[(x,2,15) for x in range(18,30)]+[(18,2,z) for z in range(10,15)]
solid={'minecraft:gravel','minecraft:smooth_stone','minecraft:stone_bricks','minecraft:black_concrete','minecraft:yellow_concrete'}
assert len(route)==17
for x,y,z in route:
 assert block(x,y,z)==block(x,y+1,z)=='minecraft:air'
 assert block(x,y-1,z) in solid
for x in range(10,30):
 for z in range(12,19):
  assert block(x,1,z) in solid
  for y in range(2,7):assert block(x,y,z)=='minecraft:air'
print('VEHICLE45_STATIC_SURVEY_PASS routeSteps=16 laneAirCells=700 nativeGeometryQualified=false')
