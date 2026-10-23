from pathlib import Path

p=Path('src/main/java/com/quin/catancraft/world/BaseCityLifecycleTests.java')
s=p.read_text()
if 'private static void readyPublic' not in s:
    insert='''    @FunctionalInterface private interface FixtureAction { void run() throws Exception; }
    // Stage historical fixtures only after their complete read boundary is resident.
    // The production queue still acquires and releases its own independent lease.
    private static void readyPublic(GameTestHelper h,ChunkLifecycleTests.PublicFixture f,FixtureAction action)throws Exception {
        net.minecraft.world.phys.AABB bounds=null;
        for(var c:f.plan.cells()){var b=new net.minecraft.world.phys.AABB(c.pos()).inflate(2);bounds=bounds==null?b:bounds.minmax(b);}
        for(var b:f.plan.boxes())bounds=bounds==null?b:bounds.minmax(b);
        waitFixture(h,SectorChunkLease.acquire(f.level(),bounds),0,action);
    }
    private static void waitFixture(GameTestHelper h,SectorChunkLease lease,int ticks,FixtureAction action) {
        h.runAfterDelay(1,()->{
            try {
                h.assertTrue(ticks<8000,"Public fixture failed to load before history staging");
                lease.keepAlive();
                if(!lease.ready()){waitFixture(h,lease,ticks+1,action);return;}
                try{action.run();}finally{lease.close();}
            }catch(Exception e){lease.close();throw new RuntimeException(e);}
        });
    }
'''
    marker='    @GameTest(template="townhall_state_empty",batch="base01",timeoutTicks=10000)'
    assert marker in s
    s=s.replace(marker,insert+marker,1)
    cases=[('interrupted_public_journal_is_resumed_not_skipped',32000),('intentional_public_rollback_is_not_reapplied',34000),('committed_public_verification_does_not_rewrite_receipt',38000)]
    for name,offset in cases:
        start=s.index('    public static void '+name)
        end=s.index('    @GameTest',start)
        part=s[start:end]
        needle=f'var f=pub(h,{offset});'
        assert needle in part
        part=part.replace(needle,needle+'readyPublic(h,f,()->{',1)
        last=part.rfind('    }')
        part=part[:last]+'        });\n'+part[last:]
        s=s[:start]+part+s[end:]
    p.write_text(s)
p=Path('src/main/java/com/quin/catancraft/world/CombinedIntegrationTests.java')
s=p.read_text()
if 'COMBINED_FIXTURE_GENERATED_MOBS_REMOVED' not in s:
    needle='            int enclosureCells=0,unspecifiedFluids=0;'
    replacement='''            // This is an artificial GameTest scene, not an authored user save.
            // Remove generated mobs before the deliberate occupancy-test actors exist.
            if(!(level.getServer() instanceof GameTestServer))throw new IOException("Fixture isolation outside GameTest server");
            var artificialScene=new net.minecraft.world.phys.AABB(isolationMinX-1,isolationMinY-1,isolationMinZ-1,isolationMaxX+2,isolationMaxY+2,isolationMaxZ+2);
            int generatedMobs=0;
            for(var entity:new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity)null,artificialScene)))
                if(entity instanceof net.minecraft.world.entity.Mob){entity.discard();generatedMobs++;}
            CatanCraft.LOGGER.info("COMBINED_FIXTURE_GENERATED_MOBS_REMOVED city={} count={} beforeTestEntities=true",city,generatedMobs);
            int enclosureCells=0,unspecifiedFluids=0;'''
    assert needle in s
    p.write_text(s.replace(needle,replacement,1))
# Remove the superseded, never-applied transport file. Source edits above are explicit.
Path('tools/reliability-fixture-ready.patch.b64').unlink(missing_ok=True)
print('Only two GameTest fixture classes changed; production guards are unmodified.')
