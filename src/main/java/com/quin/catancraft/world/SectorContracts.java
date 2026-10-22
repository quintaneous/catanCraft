package com.quin.catancraft.world;

import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.data.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.io.IOException;
import java.util.*;

/** Policy and quote snapshots only. No geometry, corner allocation or prices are supplied here. */
public final class SectorContracts {
    private SectorContracts() {}
    public enum Direction {
        NORTH(0,-1),EAST(1,0),SOUTH(0,1),WEST(-1,0);
        public final int dx,dz;
        Direction(int dx,int dz){this.dx=dx;this.dz=dz;}
        public static Direction parse(String text)throws IOException {
            try{return valueOf(text.toUpperCase(Locale.ROOT));}
            catch(RuntimeException ex){throw new IOException("Unknown absolute compass sector",ex);}
        }
        public Set<Direction> adjacent(){return switch(this){
            case NORTH,SOUTH -> Set.of(EAST,WEST);case EAST,WEST -> Set.of(NORTH,SOUTH);
        };}
    }
    public enum Corner {
        NORTH_WEST(Direction.NORTH,Direction.WEST),NORTH_EAST(Direction.NORTH,Direction.EAST),
        SOUTH_EAST(Direction.SOUTH,Direction.EAST),SOUTH_WEST(Direction.SOUTH,Direction.WEST);
        final Set<Direction> incident;
        Corner(Direction a,Direction b){incident=Set.of(a,b);}
    }
    public record Neighbor(int level,String receipt) {
        public Neighbor {
            if(level<0||receipt==null||(level>0&&receipt.isBlank()))throw new IllegalArgumentException("Invalid durable adjacent level/receipt");
        }
    }
    /** Immutable entire-side plan and explicit global corner table, including road read masks. */
    public static final class Contract {
        private final String world,city;
        private final Direction sector;
        private final SectorTransaction.Plan plan;
        private final Map<Corner,Direction> corners;
        private final Map<Direction,Neighbor> neighbors;
        private final Set<BlockPos> roads;
        private final EconomyCost cost;
        private final CompoundTag cityGroup;
        public Contract(String world,String city,Direction sector,SectorTransaction.Plan plan,
                Map<Corner,Direction> corners,Map<Direction,Neighbor> neighbors,Set<BlockPos> roads,EconomyCost cost)throws IOException {
            this(world,city,sector,plan,corners,neighbors,roads,cost,new CompoundTag());
        }
        public Contract(String world,String city,Direction sector,SectorTransaction.Plan plan,
                Map<Corner,Direction> corners,Map<Direction,Neighbor> neighbors,Set<BlockPos> roads,EconomyCost cost,CompoundTag cityGroup)throws IOException {
            if(world==null||world.isBlank()||city==null||!city.matches("[a-p]")||sector==null||plan==null||cost==null)
                throw new IOException("Incomplete sector contract or unapproved aggregate price");
            if(plan.cells()==null||plan.reads()==null)throw new IOException("Missing authored sector union/read conditions");
            if(!plan.sector().equalsIgnoreCase(sector.name())||plan.sourceRevision()==null||plan.sourceRevision().isBlank()
                ||plan.targetRevision()==null||plan.targetRevision().isBlank()||plan.sourceLevel()<0||plan.sourceLevel()==Integer.MAX_VALUE||plan.targetLevel()<0||plan.targetLevel()!=plan.sourceLevel()+1)
                throw new IOException("Invalid immutable sector revision/level transition");
            if(corners==null)throw new IOException("Explicit incident corner ownership required");
            for(Corner corner:Corner.values()) {
                if(corner.incident.contains(sector)&&!corners.containsKey(corner))throw new IOException("Missing incident corner ownership: "+corner);
                if(corners.containsKey(corner)&&(corners.get(corner)==null||!corner.incident.contains(corners.get(corner))))throw new IOException("Corner owner is not an incident sector: "+corner);
            }
            if(corners.keySet().stream().anyMatch(Objects::isNull))throw new IOException("Unknown corner");
            if(neighbors==null||!neighbors.keySet().equals(sector.adjacent())||neighbors.values().stream().anyMatch(Objects::isNull))
                throw new IOException("Exact adjacent level/receipt snapshots required");
            if(roads==null)throw new IOException("Explicit road read mask required");
            Set<BlockPos> roadCopy=new HashSet<>();for(BlockPos p:roads){if(p==null)throw new IOException("Null road position");roadCopy.add(p.immutable());}
            Set<BlockPos> occupied=new HashSet<>(),readPositions=new HashSet<>();List<SectorTransaction.Cell> cells=new ArrayList<>();
            boolean changed=false;
            for(var c:plan.cells()){
                if(c==null||c.pos()==null||c.before()==null||c.after()==null||!occupied.add(c.pos()))throw new IOException("Duplicate/shared or invalid sector cell");
                if(!c.before().equals(c.after())){changed=true;if(roadCopy.contains(c.pos()))throw new IOException("Sector write enters protected road mask");}
                cells.add(new SectorTransaction.Cell(c.pos().immutable(),c.before(),c.after()));
            }
            if(!changed)throw new IOException("Sector purchase requires an authored nonempty change plan");
            List<SectorTransaction.Read> reads=new ArrayList<>();
            for(var r:plan.reads()){
                if(r==null||r.pos()==null||r.state()==null||!readPositions.add(r.pos())||occupied.contains(r.pos()))throw new IOException("Duplicate/shared sector read cell");
                reads.add(new SectorTransaction.Read(r.pos().immutable(),r.state()));
            }
            // A road may be a preserved union cell or a separate read, never an unchecked mask entry.
            for(BlockPos p:roadCopy)if(!occupied.contains(p)&&!readPositions.contains(p))throw new IOException("Road mask lacks an exact read condition");
            if(plan.naturalSupport().grassSupports().stream().anyMatch(roadCopy::contains))throw new IOException("Natural substrate evolution cannot relax protected road reads");
            this.world=world;this.city=city;this.sector=sector;
            this.plan=new SectorTransaction.Plan(sector.name().toLowerCase(Locale.ROOT),plan.sourceRevision(),plan.targetRevision(),plan.sourceLevel(),plan.targetLevel(),List.copyOf(cells),List.copyOf(reads),plan.naturalSupport());
            this.corners=Map.copyOf(corners);this.neighbors=Map.copyOf(neighbors);this.roads=Set.copyOf(roadCopy);
            this.cost=new EconomyCost(cost.money(),cost.resources());
            if(cityGroup==null)throw new IOException("Missing explicit city group metadata");this.cityGroup=cityGroup.copy();
        }
        public String world(){return world;}
        public String city(){return city;}
        public Direction sector(){return sector;}
        public SectorTransaction.Plan plan(){return plan;}
        public Map<Corner,Direction> corners(){return corners;}
        public Map<Direction,Neighbor> neighbors(){return neighbors;}
        public Set<BlockPos> roads(){return roads;}
        public EconomyCost cost(){return cost;}
        public CompoundTag cityGroup(){return cityGroup.copy();}
        private boolean same(Contract other){return other!=null&&world.equals(other.world)&&city.equals(other.city)&&sector==other.sector
            &&plan.equals(other.plan)&&corners.equals(other.corners)&&neighbors.equals(other.neighbors)&&roads.equals(other.roads)
            &&cost.money()==other.cost.money()&&cost.resources().equals(other.cost.resources())&&cityGroup.equals(other.cityGroup);}
    }
    public record Quote(UUID owner,Contract contract) {
        public Quote {Objects.requireNonNull(owner);Objects.requireNonNull(contract);}
        public void requireCurrent(UUID currentOwner,Contract current)throws IOException {
            if(!owner.equals(currentOwner)||!contract.same(current))throw new IOException("Stale sector quote: owner, revision, geometry, roads, price or adjacent receipt changed");
        }
    }
    /** Conservative four-side partition check, not a geometry/corner or level-transition certificate.
     * Cross-side read/write compatibility needs a separate reviewed contract; none is inferred here.
     */
    public static void requireDisjoint(Collection<Contract> contracts)throws IOException {
        if(contracts==null||contracts.isEmpty())throw new IOException("Missing sector partition");
        Set<Direction> sides=new HashSet<>();Map<BlockPos,Direction> writes=new HashMap<>();Map<BlockPos,Set<Direction>> observations=new HashMap<>();Set<BlockPos> roads=new HashSet<>();Map<Corner,Direction> owners=new EnumMap<>(Corner.class);Contract first=null;
        for(Contract c:contracts){
            if(c==null)throw new IOException("Missing sector contract");
            if(first==null)first=c;
            if(!first.world.equals(c.world)||!first.city.equals(c.city)||!sides.add(c.sector))throw new IOException("Inconsistent city sector partition");
            for(var entry:c.corners.entrySet()){Direction prior=owners.putIfAbsent(entry.getKey(),entry.getValue());if(prior!=null&&prior!=entry.getValue())throw new IOException("Conflicting corner owners");}
            roads.addAll(c.roads);
            for(var cell:c.plan.cells()){
                observations.computeIfAbsent(cell.pos(),p->new HashSet<>()).add(c.sector);
                if(!cell.before().equals(cell.after())&&writes.putIfAbsent(cell.pos(),c.sector)!=null)
                    throw new IOException("Shared sector write requires one explicit owner at "+cell.pos());
            }
            for(var read:c.plan.reads())observations.computeIfAbsent(read.pos(),p->new HashSet<>()).add(c.sector);
        }
        if(sides.size()!=Direction.values().length)throw new IOException("All four absolute compass sectors required for partition validation");
        for(var write:writes.entrySet())for(Direction observer:observations.get(write.getKey()))if(observer!=write.getValue())
            throw new IOException("Sector write overlaps another sector's read/union; explicit compatibility contract required at "+write.getKey());
        for(BlockPos p:roads)if(writes.containsKey(p))throw new IOException("Sector partition writes another sector's protected road");
    }
    /** Checked aggregation for an explicitly approved list; no default/free unknown module entry. */
    public static EconomyCost aggregate(Collection<EconomyCost> approved)throws IOException {
        if(approved==null||approved.isEmpty())throw new IOException("No approved sector price components");
        long money=0;Map<ResourceType,Long> resources=new EnumMap<>(ResourceType.class);
        try {for(EconomyCost c:approved){if(c==null)throw new IOException("Unapproved sector price component");money=Math.addExact(money,c.money());
            for(var entry:c.resources().entrySet())resources.put(entry.getKey(),Math.addExact(resources.getOrDefault(entry.getKey(),0L),entry.getValue()));}}
        catch(ArithmeticException ex){throw new IOException("Sector aggregate price overflow",ex);}
        return new EconomyCost(money,resources);
    }
}
