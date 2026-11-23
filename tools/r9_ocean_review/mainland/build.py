"""Read the R7 map and the R9 plan; draw an offline mainland design/route sheet."""
from collections import Counter, deque
from pathlib import Path
import json
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patheffects as effects
from matplotlib.patches import Rectangle
import numpy as np

HERE = Path(__file__).resolve().parent
WORK = HERE.parents[1]
R7 = WORK/"inputs/CatanCraft_Clean_R7_Input"
BASE = R7/"source_world/CatanCraft_Season1_R7_Development"
sys.dont_write_bytecode = True
sys.path[:0] = [str(R7), str(WORK/"resource-ui/tools/walls53"), str(WORK/"receipts/ocean-prototype/atlas")]
from audit_sites import World
from nbt_check import NBT
from build_atlas import AIR, raster, digest, MANIFEST_PIN, MAP_PIN

# Published R8-B review extents, inclusive. The supplied brief links the source report.
R8 = {"Foxwood Basin": [-995,-490,-835,-330], "Splitstone Ravine":[508,28,652,172], "Alder Valley":[770,330,930,490]}
PLANS = {
    "a":("Orchard foothills","Broken field hedges and orchard pockets outside the northwest settlement. Leave long views from the road junction."),
    "b":("Pine terraces","Group tall pines on upland shelves and open the lower verge. From the north bridge approach, keep the crossing visible."),
    "c":("Limestone benches","Use pale stepped rock and sparse pines away from the riverbank. Preserve the river profile down to its northern mouth."),
    "d":("Coastal pasture","Ease low farm ridges into open grass above the northeastern shore. Retain a clear route for the first pickup."),
    "e":("Wooded quarry","Use Foxwood Basin as the transition reference. Preserve its two infantry approaches and southern vehicle bypass."),
    "f":("Ironwood gullies","Add shallow gullies outside the city and its works. Keep openings between gullies on routes toward the objective court."),
    "g":("Copper scree","Build weathered copper-colored stone spurs above open lower ground. Preserve B2 and its court approach."),
    "h":("Eastern forest","Use broad woodland islands divided by crossings. Open sightlines at the eastern shoreline for a view toward future offshore traffic."),
    "i":("Oak ridgelines","Run oak groves along low ridges with two gaps per long grove. Thin vegetation toward the west."),
    "j":("Coal outcrops","Use dark rock bands and short sheltered cuts. Keep the public court's southwestern approach broad and readable."),
    "k":("Iron terraces","Step gentle slopes around modest ironstone outcrops. Keep the B3 road leg visible from both directions."),
    "l":("Ravine & valley","Use Splitstone and Alder Valley as the paired reference. Maintain the low and high routes and both proposed vehicle approaches."),
    "m":("Farm shelterbelts","Break open fields with irregular tree belts. Keep long stretches open around the southwest founding route."),
    "n":("Southern escarpment","Place small stone faces on the upland side of the existing road. Leave the southern river mouth's west bank open."),
    "o":("Pine floodplain","Raise pine islands away from the southern river; grade their edges into alder flats and protect the waterline."),
    "p":("Windbreak meadows","Use grass terraces, orchard clusters and gaps through windbreaks. Retain clear exits from the southeast starter.")
}
def measure_route(world):
    def name(x,y,z):
        return world.at((x,y,z)).partition("[")[0].removeprefix("minecraft:")
    # The bridge road surface at Y80 uses andesite, gravel and stone bricks. Require
    # three blocks of air over each measured 5-wide slice and a continuous projected center.
    road_states = {}
    for x in range(-400,-15):
        cells={z for z in range(-589,-546) if name(x,80,z) in ('andesite','gravel','stone_bricks')
               and all(name(x,y,z)=='air' for y in range(81,84))}
        centers=[z for z in sorted(cells) if all(z+k in cells for k in range(-2,3))]
        assert centers,(x,'no road')
        if road_states:
            prev=road_states[x-1]
            entries={z:min((prev[v][0]+abs(z-v),v) for v in (z-1,z,z+1) if v in prev)
                     for z in centers if any(v in prev for v in (z-1,z,z+1))}
        else:
            entries={z:(abs(z+565),None) for z in centers}
        assert entries,(x,'no connected road')
        road_states[x]=entries
    last_row=road_states[-16]
    end=min(last_row,key=lambda z:(abs(z+561),last_row[z][0]))
    road=[]
    for x in reversed(road_states):
        road.append([x,end]);end=road_states[x][end][1]
    road.reverse()
    channel=[]
    for z in range(-570,-1025,-1):
        wet={x for x in range(-50,65) if all(name(x,y,z)=='water' for y in range(73,76))
             and all(name(x,y,z)=='air' for y in range(76,80))}
        centers=[x for x in sorted(wet) if all(x+k in wet for k in range(-3,5))]
        target=channel[-1][0] if channel else -16
        assert centers,(z,'no channel')
        # Prefer middle of the feasible interval while maintaining a contiguous 8-wide ribbon.
        x=min(centers,key=lambda x:(abs(x-(centers[0]+centers[-1])/2),abs(x-target)))
        if channel and abs(x-target)>1:
            x=min(centers,key=lambda x:abs(x-target))
        assert not channel or abs(x-target)<=1,(z,x,target)
        channel.append([x,z])
    return road,channel

def plot(config,image,road,channel,proposed,atlas):
    colors={"ink":"#253b35","paper":"#f7f5ec","road":"#8d4313","river":"#066a8b","sea":"#513781","sample":"#693b64"}
    fig=plt.figure(figsize=(14,10),facecolor=colors["paper"])
    title=fig.text; title(.055,.94,"From mainland to open water",fontsize=33,weight="bold",color=colors["ink"])
    title(.056,.905,"Season 1 terrain study   •   R7 surface with proposed landscapes and an R9 test route",fontsize=14,color="#4d6256")
    ax=fig.add_axes((.055,.12,.59,.75)); ax.set_xlim(-1540,1050); ax.set_ylim(1050,-1320); ax.set_aspect("equal");ax.set_facecolor("#ddebf0")
    ax.imshow(image,extent=[-1024,1024,1024,-1024],origin="upper",interpolation="nearest")
    ax.add_patch(Rectangle((-1024,-1024),2048,2048,fill=False,lw=1.2,edgecolor="#67776b"))
    for i in (-1000,-500,0,500,1000):
        ax.plot([i,i],[-1000,1000],lw=.4,color="#536b5e",alpha=.7)
        ax.plot([-1000,1000],[i,i],lw=.4,color="#536b5e",alpha=.7)
    for t in config["territories"]:
        x,z=t['cityCenter']['x'],t['cityCenter']['z']
        ax.text(x,z,t['id'].upper(),ha='center',va='center',weight='bold',fontsize=10,color=colors["ink"],
                bbox=dict(boxstyle="round,pad=.15",fc="#fff9e8",ec="#6d7866",lw=.5))
    for label,(lo,top,hi,bot) in R8.items():
        ax.add_patch(Rectangle((lo,top),hi-lo+1,bot-top+1,fill=False,linewidth=1.7,edgecolor=colors['sample']))
    segs=[(road,colors['road'],'-'),(channel,colors['river'],'-'),(proposed,colors['sea'],'--')]
    for coords,color,ls in segs:
        xy=np.array(coords)
        line,=ax.plot(xy[:,0],xy[:,1],ls,color=color,lw=2.6,zorder=5)
        line.set_path_effects([effects.Stroke(linewidth=4,foreground='#fffbf4'),effects.Normal()])
    for point in [road[0],road[-1],channel[-1],proposed[-1]]:
        ax.plot(*point,'o',ms=4.5,mec='#fffef8',mew=.6,mfc=colors['ink'],zorder=6)
    for pt,label,offset in [(road[0],"Road start",(-18,-14)),(road[-1],"B1 bridge",(7,6)),(channel[-1],"North mouth",(8,-5)),(proposed[-1],"Harbor light",(2,11))]:
        ax.annotate(label,pt,xytext=offset,textcoords='offset points',fontsize=8,fontweight='bold',color=colors['ink'],path_effects=[effects.withStroke(linewidth=3,foreground=colors['paper'])])
    ax.set_xlabel("X  /  blocks east");ax.set_ylabel("Z  /  blocks south");ax.tick_params(labelsize=8)
    ax.spines[:].set_color("#9aa99c")
    key=fig.add_axes((.675,.12,.29,.75));key.axis('off')
    def txt(x,y,s,size=11,**kw):key.text(x,y,s,fontsize=size,color=colors['ink'],va='top',transform=key.transAxes,**kw)
    txt(0,.99,"Proposed mainland treatments",16,weight="bold")
    for i,(city,(name,_)) in enumerate(PLANS.items()):
        row,col=i%8,i//8
        txt(col*.53,.923-row*.047,city.upper(),10,weight='bold')
        txt(.06+col*.53,.923-row*.047,name,9.5)
    txt(0,.51,"Three existing R8-B review areas",14,weight="bold")
    txt(0,.459,"Outlined in plum. E holds Foxwood Basin.\nL holds Splitstone Ravine and Alder Valley.",10.5,linespacing=1.4)
    txt(0,.35,"First traversal",15,weight='bold')
    items=[("Road to B1","#8d4313","Five clear blocks across at each sampled X."),("River to north mouth","#066a8b","Eight water blocks across at each river row."),("Future sea leg","#513781","Follow the proposed west lane to Harbor light.")]
    for i,(n,c,detail) in enumerate(items):
        y=.292-i*.092
        key.plot([0,.075],[y-.006,y-.006],color=c,lw=2.7,ls='--' if i==2 else '-',transform=key.transAxes)
        txt(.095,y,n,10.5,weight='bold');txt(.095,y-.036,detail,9)
    title(.055,.047,"Source: pinned R7 surface, registered city / bridge anchors and R8-B review bounds. Colored routes are measured on land and river; dashed water is proposed.",fontsize=9.2,color="#536b5e")
    fig.savefig(HERE/"mainland-coast.png",dpi=145,facecolor=fig.get_facecolor());plt.close(fig)

def main():
    manifest=(R7/'INPUT_MANIFEST.json').read_bytes(); assert digest(manifest)==MANIFEST_PIN
    pins={v['path']:v['sha256'] for v in json.loads(manifest)['files']}
    data=(R7/'bootstrap_reference/config/catancraft/map.json').read_bytes();assert digest(data)==MAP_PIN
    config=json.loads(data)
    atlas=json.loads((WORK/"receipts/ocean-prototype/atlas/atlas.json").read_text())
    assert atlas['mapSHA256']==MAP_PIN
    w=World(BASE,NBT)
    road,channel=measure_route(w)
    start_x=atlas['measuredR7FringeBoatChannel']['rows']['north'][-1]['centerX']
    assert abs(start_x-channel[-1][0])<=1
    site=next(x for x in atlas['sites'] if x['id']=='lighthouse_west')
    berth=site['prototypeBerthsXYZ']['primary']
    proposed=[channel[-1], [start_x,-1220],[-1220,-1220],[-1220,-430],[site['boundsXZ'][2],-430],[berth[0],berth[2]]]
    data={"source":atlas["sourceCommit"],"coordinateOrder":"X,Z","designOnly":True,
          "r8bReviewBoxesInclusive":R8,"proposedTreatments":{t['id']:{"specialties":t['resources'],"label":PLANS[t['id']][0],"treatment":PLANS[t['id']][1]} for t in config['territories']},
          "survey":{"roadRows":len(road),"roadCenterXZ":road,"roadSurfaceY":80,"roadClearWidth":5,"roadClearHeight":3,
                    "bridgeAnchorXZ":[-16,-561],"riverRows":len(channel),"riverCenterXZ":channel,"waterTopBlockY":75,"riverClearWidth":8,"riverMinDepth":3,"riverClearAir":4},
          "proposedSeaWaypointsXZ":proposed,"berthXYZ":berth}
    (HERE/'mainland-coast.json').write_text(json.dumps(data,separators=(',',':'))+'\n')
    image=raster(BASE,R7,pins,NBT)
    plot(config,image,road,channel,proposed,atlas)
if __name__=='__main__':
    main()
