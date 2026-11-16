import java.nio.file.*;
import java.util.*;

/** Standalone Java 17 allocation/lookup benchmark, not a native Minecraft test.
 * Position mirrors the verified Vec3i hash, equality and ordering; input is the
 * exact audited wall guard-coordinate inventory. No world or save is opened. */
final class SpatialMapBenchmark {
    private record Position(int x,int y,int z) implements Comparable<Position> {
        @Override public int hashCode(){return (y+z*31)*31+x;}
        @Override public int compareTo(Position p){int c=Integer.compare(y,p.y);if(c==0)c=Integer.compare(z,p.z);return c==0?Integer.compare(x,p.x):c;}
    }
    private static volatile long sink;
    private static long lookup(Map<Position,Integer> map,Collection<Position> keys){long sum=0;for(Position p:keys)sum+=map.get(p);sink=sum;return sum;}
    private static void immutable(Map<Position,Integer> map,Position p){try{map.put(p,2);throw new AssertionError("Published map was mutable");}catch(UnsupportedOperationException expected){}}
    public static void main(String[] args)throws Exception {
        if(Runtime.version().feature()!=17)throw new IllegalStateException("This evidence requires Java 17");
        Map<Position,Integer> source=new HashMap<>();
        for(String line:Files.readAllLines(Path.of(args[0]))){String[] a=line.split(" ");if(a.length!=3)throw new IllegalArgumentException("Invalid coordinate row");Position p=new Position(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));if(source.putIfAbsent(p,1)!=null)throw new IllegalArgumentException("Duplicate coordinate row");}
        List<Position> keys=List.copyOf(source.keySet());List<String> rows=new ArrayList<>();
        for(int attempt=0;attempt<6;attempt++){
            long a=System.nanoTime();Map<Position,Integer> hash=Collections.unmodifiableMap(new HashMap<>(source));long b=System.nanoTime();
            long fast=lookup(hash,keys);long c=System.nanoTime();Map<Position,Integer> copy=Map.copyOf(source);long d=System.nanoTime();
            long slow=lookup(copy,keys);long e=System.nanoTime();
            if(fast!=source.size()||slow!=source.size()||!copy.equals(hash))throw new AssertionError("Spatial contents changed");
            Position probe=new Position(123456,123,654321);source.put(probe,1);
            if(hash.containsKey(probe)||copy.containsKey(probe))throw new AssertionError("Defensive copy was aliased");source.remove(probe);immutable(hash,probe);immutable(copy,probe);
            if(attempt!=0)rows.add(String.format(Locale.ROOT,"{\"iteration\":%d,\"hash_copy_ms\":%.6f,\"hash_lookup_ms\":%.6f,\"map_copyof_ms\":%.6f,\"map_copyof_lookup_ms\":%.6f}",attempt,(b-a)/1e6,(c-b)/1e6,(d-c)/1e6,(e-d)/1e6));
        }
        System.out.println("{\"coordinate_count\":"+source.size()+",\"checksum\":"+sink+",\"equal_contents\":true,\"immutable\":true,\"defensive_copy\":true,\"warmup_iterations\":1,\"measurements\":["+String.join(",",rows)+"]}");
    }
}
