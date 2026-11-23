"""Draw depth and access sections from the independently decoded schematics."""
from pathlib import Path
import sys
import numpy as np
from PIL import Image,ImageDraw,ImageFont
sys.dont_write_bytecode=True
from verify import audit, WATER_Y, BASE

def draw_maps(workspace, tiles, output):
    sys.path.insert(0,str(workspace/"inputs/CatanCraft_Clean_R7_Input"))
    from nbt_check import NBT
    img=Image.new("RGB",(1160,844),"#f4f2e9")
    pen=ImageDraw.Draw(img)
    def font(size, bold=False):
        file="DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
        return ImageFont.truetype(file,size)
    ink="#193534"
    pen.text((36,21),"Coastal navigation studies",font=font(30,True),fill=ink)
    pen.text((37,64),"R7 water at block Y75  /  112 × 96 block windows  /  north and open water at top",font=font(16),fill="#365651")
    for idx,(kind,title,section_z) in enumerate([("sheltered_cove","Sheltered cove",35),("headland_estuary","Headland estuary",82)]):
        left=38+idx*568
        r,depth,height,path=audit(tiles/("r9_"+kind+".schem"),NBT)
        pen.text((left,116),title,font=font(21,True),fill=ink)
        scale=4
        x0,y0=left+37,163
        dz,dx=np.gradient(height.astype(float))
        pixels=np.zeros((*height.shape,3),dtype=np.uint8)
        for z,x in np.ndindex(height.shape):
            alt=height[z,x]+BASE-WATER_Y
            d=depth[z,x]
            if d:
                shallow=np.array([122,207,184]); deep=np.array([26,85,111])
                t=min(1,d/14)
                rgb=shallow*(1-t)+deep*t
            elif alt<=3: rgb=np.array([222,204,158])
            else: rgb=np.array([156,163,127]) if alt<14 else np.array([144,145,135])
            if not d: rgb=rgb*(1+np.clip(-dx[z,x]*.07-dz[z,x]*.06,-.24,.15))
            pixels[z,x]=np.clip(rgb,0,255)
        img.paste(Image.fromarray(pixels).resize((112*scale,96*scale),Image.Resampling.NEAREST),(x0,y0))
        for z in [0,24,48,72,95]:
            pen.text((x0-8,y0+z*scale),str(z),font=font(12),fill=ink,anchor="rm")
        for x in [0,28,56,84,111]:
            pen.text((x0+x*scale,y0+96*scale+5),str(x),font=font(12),fill=ink,anchor="mt")
        coords=[(x0+int(x*scale+scale/2),y0+int(z*scale+scale/2)) for x,z in path]
        pen.line(coords,fill="#e97429",width=4)
        x,y=coords[-1]
        pen.ellipse((x-5,y-5,x+5,y+5),outline="#fff7c8",width=2)
        ys=y0+section_z*scale+2
        for xx in range(x0,x0+448,12): pen.line((xx,ys,xx+5,ys),fill="#f5f1db",width=1)
        pen.text((left,586),f"Cross-section at local Z {section_z}",font=font(16,True),fill=ink)
        bottom=750
        yy=lambda h:bottom-int((h-57)*3.7)
        top=yy(WATER_Y)
        pts=[(x0+x*4+2,yy(h+BASE)) for x,h in enumerate(height[section_z])]
        pen.rectangle((x0,top,x0+447,bottom),fill="#85bdc6")
        pen.polygon([(x0,bottom),*pts,(x0+447,bottom)],fill="#827965")
        for h in [60,75,90]:
            pen.text((x0-8,yy(h)),str(h),font=font(12),fill=ink,anchor="rm")
        pen.text((left,773),f"Route  {r['route']['lengthInHorizontalSteps']} steps   ·   Depth at center  ≥{r['route']['leastDepthAtRouteCenter']} blocks",font=font(15),fill=ink)
    pen.text((36,813),"Orange shows a 5-block-wide route with ≥3 source-water blocks and 2 clear air blocks. Dashed white marks each section.",font=font(15),fill=ink)
    img.save(output)

if __name__=="__main__":
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("workspace",type=Path);p.add_argument("tiles",type=Path);p.add_argument("output",type=Path)
    a=p.parse_args()
    draw_maps(a.workspace.resolve(),a.tiles.resolve(),a.output)
