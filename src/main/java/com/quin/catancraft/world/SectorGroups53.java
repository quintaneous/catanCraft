package com.quin.catancraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.Path;

/** Recorded metadata selects the immutable group protocol, never the current catalog. */
final class SectorGroups53 {
    private SectorGroups53() {}
    private static boolean legacy(SectorService.Context context,CompoundTag group)throws IOException {
        if(!group.contains("schema",Tag.TAG_INT))throw new IOException("Missing wall group schema");
        if(group.getInt("schema")==1){
            if(!context.city().equals("a"))throw new IOException("Historical City A group cannot bind another city");
            return true;
        }
        if(group.getInt("schema")==2){
            SectorGroup53.supportHeader(group);
            if(!context.city().equals(group.getString("siteCity")))throw new IOException("Historical wall group belongs to another city");
            return false;
        }
        throw new IOException("Unknown wall group protocol; retain its original journal");
    }
    static CompoundTag inheritedSupportProof(SectorService.Context context,CompoundTag group)throws IOException {
        return legacy(context,group)?SectorCityAP3Group.inheritedSupportProof(context,group):SectorGroup53.inheritedSupportProof(context,group);
    }
    static void requireCurrent(SectorService.Context context,SectorContracts.Contract contract,CompoundTag frozen,Path file,boolean terminalReadOnly)throws IOException {
        if(legacy(context,contract.cityGroup()))SectorCityAP3Group.requireCurrent(context,contract,frozen,file,terminalReadOnly);
        else SectorGroup53.requireCurrent(context,contract,frozen,file,terminalReadOnly);
    }
    static boolean approvedWrite(SectorService.Context context,SectorContracts.Contract contract,BlockPos pos)throws IOException {
        return legacy(context,contract.cityGroup())?SectorCityAP3Group.approvedWrite(context,contract,pos):SectorGroup53.approvedWrite(context,contract,pos);
    }
    static void verifyCurrentAssembly(SectorService.Context context,CompoundTag group)throws IOException {
        if(legacy(context,group))SectorCityAP3Group.verifyCurrentAssembly(context);else SectorGroup53.verifyCurrentAssembly(context);
    }
}
