"""Read the finished .schem independently, check geometry, and render the review plate."""
from collections import Counter, deque
from pathlib import Path
import gzip, hashlib, io, json, struct
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parent
class Reader:
    def __init__(self,b): self.f=io.BytesIO(b)
    def n(self,fmt): return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
    def string(self): return self.f.read(self.n('H')).decode('utf-8')
    def payload(self,t):
        if t in (1,2,3,4): return self.n({1:'b',2:'h',3:'i',4:'q'}[t])
        if t==7: return self.f.read(self.n('i'))
        if t==8:return self.string()
        if t==9:
            a,n=self.n('B'),self.n('i')
            return [self.payload(a) for _ in range(n)]
        if t==10:
            r={}
            while (k:=self.n('B')):
                key=self.string(); assert key not in r
                r[key]=self.payload(k)
            return r
        if t==11:return [self.n('i') for _ in range(self.n('i'))]
        raise ValueError(t)
def read(path):
    b=gzip.decompress(path.read_bytes());r=Reader(b)
    assert r.n('B')==10 and r.string()=='Schematic'
    s=r.payload(10)
    assert r.f.tell()==len(b)
    pal={v:k.removeprefix('minecraft:') for k,v in s['Palette'].items()}
    assert sorted(pal)==list(range(s['PaletteMax']))
    values=[];v=shift=0
    for byte in s['BlockData']:
        v|=(byte&127)<<shift
        if byte&128: shift+=7; continue
        values.append(pal[v]);v=shift=0
    assert shift==0
    w,h,l=[s[k] for k in ('Width','Height','Length')]
    assert len(values)==w*h*l
    return s,values,w,h,l
schem=ROOT/'brinewake.schem'
s,blocks,W,H,L=read(schem)
assert (W,H,L)==(74,39,48) and s['Version']==2 and s['DataVersion']==3465 and s['Offset']==[0,0,0]
assert s['Entities']==s['BlockEntities']==[]
def b(x,y,z):
    if not (0<=x<W and 0<=y<H and 0<=z<L): return 'air'
    return blocks[(y*L+z)*W+x]
def bare(state): return state.split('[')[0]
def path(start,target,neighbors):
    back={start:None};q=deque([start])
    while q:
        node=q.popleft()
        if node==target:
            chain=[node]
            while back[node] is not None:
                node=back[node];chain.append(node)
            return chain[::-1]
        for after in neighbors(node):
            if after not in back: back[after]=node;q.append(after)
    raise ValueError(f'No path {start} -> {target}')
D=[(0,-1),(1,0),(0,1),(-1,0)]
def clear(p):
    x,y,z=p
    floor=bare(b(x,y-1,z))
    return floor in {'spruce_stairs','spruce_planks','dark_oak_planks','stripped_dark_oak_log','chiseled_stone_bricks','waxed_cut_copper','smooth_quartz','gray_concrete','light_gray_concrete','red_terracotta','terracotta','cut_copper'} and b(x,y,z)==b(x,y+1,z)=='air'
def walker(p):
    x,y,z=p
    for dx,dz in D:
        for new in [y-1,y,y+1]:
            t=(x+dx,new,z+dz)
            if clear(t): yield t
def waterclear(x,z):
    return all(b(i,j,k)=='water[level=0]' for i in range(x-1,x+2) for k in range(z-1,z+2) for j in (13,14,15)) and all(b(i,j,k)=='air' for i in range(x-1,x+2) for k in range(z-1,z+2) for j in (16,17))
def rower(p):
    x,z=p
    for dx,dz in D:
        nx,nz=x+dx,z+dz
        if waterclear(nx,nz):yield nx,nz
obj=(35,20,15)
landings=[(33,17,6),(20,17,38)]
paths=[path(start,obj,walker) for start in landings]
aft=path(obj,(58,19,28),walker)
channels=[path((1,z),(x,z),rower) for x,z in ((33,4),(20,40))]
# Both boat centerlines reach the bank with a water block between the boat center and landing edge.
assert all(clear(p) for p in landings+[obj])
assert [b(x,15,z) for x,z in ((33,5),(20,39))]==['water[level=0]']*2
assert all(get=='ladder[facing=south,waterlogged=false]' for get in [b(62,y,31) for y in range(19,24)])
# Hull rib in contact with rock, verified as a direct vertical support stack to tile base.
contacts=[(x,z) for x in range(W) for z in range(L) if bare(b(x,10,z)) in {'red_terracotta','terracotta','cut_copper'} and all(b(x,y,z) not in ('air','water[level=0]') for y in range(10))]
assert len(contacts)>30
print(json.dumps({'sha256':hashlib.sha256(schem.read_bytes()).hexdigest(),'dimensionsXYZ':[W,H,L],'voxelCount':len(blocks),'dataVersion':s['DataVersion'],'spongeVersion':s['Version'],'paletteStates':s['PaletteMax'],'entities':0,'blockEntities':0,'waterBlockLocalY':15,'waterBlockWorldY':75,'pivot':[37,15,24],'standingObjectiveLocalXYZ':obj,'boardableLandingFeetLocalXYZ':landings,'walksFromLandingsInCardinalMoves':[len(p)-1 for p in paths],'boatApproachMovesFromLocalX1AlongNorthSouthRows':[len(p)-1 for p in channels],'checkedBoatCorridorWidthBlocks':3,'minimumBoatSourceWaterDepthBlocks':3,'checkedBoatAirClearanceBlocks':2,'mainDeckToLowerCabinCardinalMoves':len(aft)-1,'groundedKeelColumns':len(contacts),'limitations':'Static block geometry and conservative cardinal floor/clearance searches. No connected boat, dismount, swimming, lighting, capture or paste was tested.'},indent=2))

# Geometry render. Materials are schematic-derived colors; non-cube blocks have simplified meshes.
colors={'spruce_planks':(153,115,78),'dark_oak_planks':(113,84,60),'stripped_dark_oak_log':(103,79,56),'gray_concrete':(72,85,91),'light_gray_concrete':(159,162,151),'red_terracotta':(166,84,56),'brown_terracotta':(113,64,47),'terracotta':(176,107,72),'yellow_terracotta':(211,157,64),'orange_terracotta':(180,115,54),'cyan_terracotta':(69,116,123),'waxed_weathered_cut_copper':(78,145,133),'waxed_exposed_cut_copper':(157,115,86),'waxed_exposed_copper':(147,105,79),'waxed_cut_copper':(185,126,82),'cut_copper':(187,126,85),'smooth_sandstone':(220,205,168),'smooth_quartz':(236,230,210),'light_blue_stained_glass':(101,156,164),'polished_andesite':(126,132,127),'polished_deepslate':(68,76,78),'polished_blackstone':(58,62,66),'chiseled_stone_bricks':(149,144,123),'iron_bars':(151,155,146),'dark_oak_fence':(122,88,62),'chain':(85,91,90),'lightning_rod':(169,115,78),'lantern':(242,177,78),'spruce_stairs':(153,115,78),'ladder':(149,106,72),'stone_button':(161,162,152),'polished_blackstone_wall':(76,79,80),'gravel':(125,128,119),'andesite':(127,132,126),'stone':(122,126,123),'tuff':(114,126,113)}
def tone(c,f): return tuple(max(0,min(255,round(v*f))) for v in c)
def lerp(c,d,a): return tuple(round(v+(j-v)*a) for v,j in zip(c,d))
S=2
img=Image.new('RGB',(1200*S,770*S),(247,245,238)); d=ImageDraw.Draw(img)
def text(x,y,msg,size,color,bold=False):
    path='/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf'
    d.text((x*S,y*S),msg,font=ImageFont.truetype(path,size*S),fill=color)
def polygon(points,c): d.polygon([(round(u*S),round(v*S)) for u,v in points],fill=c)
def line(points,c,width=1):d.line([(round(u*S),round(v*S)) for u,v in points],fill=c,width=width*S)
ink=(38,58,62)
text(42,25,'BRINEWAKE',42,ink,True)
text(45,84,'Grounded coastal freighter   /   Offshore monument study',19,(71,96,94))
text(880,51,'CATANCRAFT   /   R9',18,ink,True)
line([(44,123),(1156,123)],(177,190,180))
# Local water tile and dry hull. Orthographic with bow in the foreground on left.
def project(x,y,z):
    return 88 + 12.2*(.955*x+.296*z), 349 + 12.2*((-.296*x+.955*z)*.46-(y-16)*.886)
def cuboid(x0,y0,z0,x1,y1,z1,c,occupancy=(True,True,True)):
    top=[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)]
    west=[(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)]
    south=[(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
    for keep,pts,f in zip(occupancy,[west,south,top],[.69,.84,1.07]):
        if keep:polygon([project(*v) for v in pts],tone(c,f))
for x in range(W):
    for z in range(L):
        y=15
        while y>0 and b(x,y,z)=='water[level=0]':y-=1
        depth=15-y
        c=lerp((82,151,146),(38,102,116),min(1,depth/14))
        c=tone(c,1+((x*11+z*13)%7-3)*.003)
        polygon([project(x,16,z),project(x+1,16,z),project(x+1,16,z+1),project(x,16,z+1)],c)
pieces=[]
for y in range(16,H):
    for z in range(L):
        for x in range(W):
            s0=b(x,y,z);mat=bare(s0)
            if mat=='air' or mat=='water': continue
            c=tone(colors[mat],1+((x*17+y*11+z*3)%11-5)*(.009 if 'plank' in mat else .0045))
            depth=-.296*x+.955*z+.52*y
            pieces.append((depth,x,y,z,mat,s0,c))
for _,x,y,z,mat,st,c in sorted(pieces):
    if mat in ('iron_bars','dark_oak_fence'):
        cuboid(x+.39,y,z+.39,x+.61,y+.95,z+.61,c)
        for side,limits in [('north',(.445,0,.555,.5)),('south',(.445,.5,.555,1)),('west',(0,.445,.5,.555)),('east',(.5,.445,1,.555))]:
            if side+'=true' in st:
                a,g,e,j=limits
                for h0 in (.34,.72):cuboid(x+a,y+h0,z+g,x+e,y+h0+.10,z+j,c)
    elif mat in ('chain','lightning_rod','polished_blackstone_wall','lantern','stone_button'):
        width={'chain':.16,'lightning_rod':.24,'polished_blackstone_wall':.64,'lantern':.46,'stone_button':.30}[mat]
        k=(1-width)/2
        cuboid(x+k,y,z+k,x+1-k,y+(.22 if mat=='stone_button' else 1),z+1-k,c)
    elif mat=='ladder':
        for a in (.18,.79):cuboid(x+a,y,z,x+a+.11,y+1,z+.14,c)
        for h0 in (.20,.68):cuboid(x+.18,y+h0,z,x+.9,y+h0+.11,z+.14,c)
    elif mat=='spruce_stairs':
        cuboid(x,y,z,x+1,y+.5,z+1,c)
        za,zb=(0,.5) if 'facing=north' in st else (.5,1)
        cuboid(x,y+.5,z+za,x+1,y+1,z+zb,c)
    else:
        cube=lambda n:bare(n) not in ('air','water','chain','iron_bars','ladder','spruce_stairs','dark_oak_fence','lightning_rod','polished_blackstone_wall','lantern','stone_button')
        cuboid(x,y,z,x+1,y+1,z+1,c,tuple(not cube(b(x+dx,y+dy,z+dz)) for dx,dy,dz in [(-1,0,0),(0,0,1),(0,1,0)]))
line([(44,608),(1156,608)],(177,190,180))
text(46,630,'TWO BOARDING ROUTES',19,ink,True)
text(46,664,'Approach around the bow. Either timber landing',17,ink)
text(46,690,'leads to the copper-edged marker on the main deck.',17,ink)
text(645,630,'WRECK TILE',19,ink,True)
text(645,664,'74 × 48 blocks   ·   39 blocks tall',17,ink)
text(645,690,'Top water block Y75   ·   source water in the tile',17,ink)
text(46,734,'Orthographic schematic render  /  Water shading shows depth.  /  Local material colors and simplified rail geometry.',13,(82,103,99))
img.resize((1200,770),Image.Resampling.LANCZOS).save(ROOT/'Brinewake_preview.png',optimize=True)
