"""Replay, reread and restore the Pine Reach spans against original NBT without disk writes."""
from pathlib import Path
import json
import math
import sys
import mainland

sys.dont_write_bytecode = True
src=mainland.WORK/"inputs/CatanCraft_Clean_R7_Input"
sys.path[:0]=[str(src),str(mainland.WORK/"resource-ui/tools/walls53")]
from nbt_check import NBT
from audit_sites import World

class Overlay:
    def __init__(self, reader):
        self.source=reader
        self.changed={}
    def get(self,x,y,z):
        if (x,y,z) in self.changed:
            return self.changed[x,y,z]
        return self.source.at((x,y,z))
    def set(self,x,y,z,state):
        if self.source.at((x,y,z))==state:
            self.changed.pop((x,y,z),None)
        else:
            self.changed[x,y,z]=state

def verify():
    w=Overlay(World(src/"source_world/CatanCraft_Season1_R7_Development",NBT))
    result=mainland.build(w)
    assert len(w.changed)==result["changedBlocks"]
    changed_xz={(x,z) for x,y,z in w.changed}
    assert len(changed_xz)==result["changedColumns"] and all(mainland.allowed(*p) for p in changed_xz)
    info=mainland._plan()
    compared=0
    for x,z,spans in info["columns"]:
        for lo,hi,a,b in spans:
            for y in range(lo,hi+1):
                assert w.source.at((x,y,z))==info["palette"][a]
                assert w.get(x,y,z)==info["palette"][b]
                compared += 1
    assert compared==len(w.changed)
    road=json.loads((mainland.WORK/"receipts/mainland-coast-plan/mainland-coast.json").read_bytes())["survey"]["roadCenterXZ"]
    road_center_separation=min(max(abs(x-i),abs(z-j)) for x,z in changed_xz for i,j in road)
    bridge_separation=min(max(abs(x+16),abs(z+561)) for x,z in changed_xz)
    # The first occupied cell nearest each marked land-transfer road reference is separate.
    restore=mainland.restore(w)
    assert not w.changed and restore["changedBlocks"]==compared
    x,z,spans=info["columns"][-1]
    y=spans[-1][0]
    w.set(x,y,z,"minecraft:gold_block")
    try:
        mainland.build(w)
    except ValueError as e:
        assert 'expected' in str(e) and w.changed=={(x,y,z):"minecraft:gold_block"}
    else:
        raise AssertionError("Preflight failed to reject modified original")
    return {"source":info["source"],"verifiedBeforeAndAfterVoxels":compared,
            "restoredVoxels":compared,"restoredToZeroDifferences":True,"beforeImageMismatchRefusedBeforeWrites":True,
            "minimumChessboardDistanceTo385RoadCenterlinePositions":road_center_separation,
            "minimumChessboardDistanceToBridgeB1Anchor":bridge_separation,
            "measure":"Maximum absolute X or Z difference between two block coordinates; 1 means adjacent.",
            "mode":"R7 original-region read plus in-memory overlay; no Minecraft server launched."}
if __name__=="__main__":
    print(json.dumps(verify(),indent=2))
