package com.quin.catancraft.world;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
/** Observes the real queued base initializer; never force-loads or changes readiness. */
@GameTestHolder("catancraft_chunk_lifecycle") @PrefixGameTestTemplate(false)
public final class ReadinessProbeTests {
 @GameTest(template="townhall_state_empty",batch="load_05",timeoutTicks=1200)
 public static void report_base_readiness_without_changing_chunk_state(GameTestHelper h){probe(h,0);}
 private static void probe(GameTestHelper h,int tick){h.runAfterDelay(1,()->{
  if(tick%100==20)try{
   var field=CityBasePreparation.class.getDeclaredField("pending");field.setAccessible(true);Object work=field.get(null);
   if(work!=null){var accessor=work.getClass().getDeclaredMethod("lease");accessor.setAccessible(true);var lease=(SectorChunkLease)accessor.invoke(work);int blocks=0,entities=0;String first="";
    for(var p:lease.positions()){boolean b=h.getLevel().getChunkSource().getChunkNow(p.x,p.z)==null,e=!h.getLevel().areEntitiesLoaded(p.toLong());if(b)blocks++;if(e)entities++;if(first.isEmpty()&&(b||e))first=p+" blocks="+(!b)+" entities="+(!e);}
    System.out.println("BASE_LEASE_PROBE tick="+tick+" count="+lease.chunkCount()+" blocksMissing="+blocks+" entitiesMissing="+entities+" first="+first);
   }
  }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
  if(tick<800)probe(h,tick+1);else h.succeed();
 });}
}
