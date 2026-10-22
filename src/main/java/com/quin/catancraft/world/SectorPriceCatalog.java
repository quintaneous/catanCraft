package com.quin.catancraft.world;

import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.data.ResourceType;
import java.io.IOException;
import java.util.Map;

/** Owner-approved iteration-one whole-compass prices. Price availability never approves geometry.
 * Basic and Reinforced are the two current iteration tiers, not a final maximum-tier declaration.
 * Historical operations retain their sealed original cost/version; never reprice recovery here.
 */
final class SectorPriceCatalog {
    static final String VERSION="DEF04_SECTOR_PRICES_V1_20261018";
    private static final EconomyCost BASIC=new EconomyCost(1500,Map.of(ResourceType.WOOD,15L,ResourceType.STONE,30L));
    private static final EconomyCost REINFORCED_INCREMENT=new EconomyCost(4000,Map.of(ResourceType.CONCRETE,50L,ResourceType.STEEL,15L));
    private SectorPriceCatalog(){}
    record Price(String version,SectorContracts.Direction side,int sourceLevel,int targetLevel,EconomyCost cost){}
    static Price quote(SectorContracts.Direction side,int sourceLevel)throws IOException {
        if(side==null)throw new IOException("Unknown compass side has no approved sector price");
        EconomyCost cost=switch(sourceLevel){case 0->BASIC;case 1->REINFORCED_INCREMENT;default->throw new IOException("No approved sector price for source level "+sourceLevel+"; current iteration supports Basic and Reinforced only");};
        return new Price(VERSION,side,sourceLevel,sourceLevel+1,cost);
    }
    static EconomyCost cost(SectorContracts.Direction side,int sourceLevel)throws IOException {return quote(side,sourceLevel).cost();}
    /** Compatibility entrypoint for the registered City A north geometry (independently gated). */
    static EconomyCost northP2(int sourceLevel)throws IOException {return cost(SectorContracts.Direction.NORTH,sourceLevel);}
}
