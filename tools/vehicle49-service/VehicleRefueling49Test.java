import com.quin.catancraft.world.VehicleRefueling49;
import com.quin.catancraft.world.VehicleFuel49;
import com.quin.catancraft.world.VehicleRefueling49.*;
import com.quin.catancraft.world.VehicleRefueling49.Record;
import java.io.IOException;
import java.util.UUID;

public class VehicleRefueling49Test {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    static VehicleFuel49.Meter empty() { return VehicleFuel49.emptyMeter(VehicleFuel49.PICKUP,100); }
    static Intent intent() { return new Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "world", "factory", VehicleFuel49.PICKUP, VehicleFuel49.Mode.ADD_QUARTER, empty(), VehicleFuel49.quote(VehicleFuel49.PICKUP,100,10,VehicleFuel49.Mode.ADD_QUARTER,empty())); }
    static class Fake implements Environment {
        VehicleFuel49.Meter meter=empty();
        Record record; long energy=10, fuel=80; int debits, adds; boolean authorized=true;
        Proof paid=Proof.ABSENT, added=Proof.ABSENT; String failure="";
        public void guard(Intent i) throws IOException { if (!authorized) throw new IOException("authority changed"); }
        public Record read(UUID id) { return record; }
        public void save(Record expected, Record next) throws IOException {
            if (!java.util.Objects.equals(expected, record)) throw new IOException("CAS"); record=next;
        }
        public VehicleFuel49.Meter currentMeter(Intent i) { return meter; }
        public Proof debitProof(Intent i) { return paid; }
        public Proof energyProof(Intent i) { return added; }
        public long currentEnergy(Intent i) { return energy; }
        public void debitAndSave(Intent i) throws IOException {
            if (failure.equals("beforeDebit")) throw new IOException("interrupted");
            if (fuel<i.fuelCost()) throw new IOException("insufficient Fuel");
            fuel-=i.fuelCost(); meter=i.quote().nextMeter(); debits++; paid=Proof.EXACT;
            if (failure.equals("afterDebit")) throw new IOException("interrupted");
        }
        public void addEnergyAndSave(Intent i) throws IOException {
            if (failure.equals("beforeEnergy")) throw new IOException("interrupted");
            if (energy!=i.beforeEnergy()) throw new IOException("stale");
            energy+=i.addedEnergy(); adds++; added=Proof.EXACT;
            if (failure.equals("afterEnergy")) throw new IOException("interrupted");
        }
    }
    static void interrupted(Fake f, Intent i) throws Exception {
        try { VehicleRefueling49.execute(f,i); throw new AssertionError("expected interruption"); }
        catch(IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        Intent i=intent(); Fake f=new Fake();
        check(VehicleRefueling49.execute(f,i)==Result.COMPLETE); check(f.fuel==72 && f.energy==35);
        f.energy=2; check(VehicleRefueling49.execute(f,i)==Result.COMPLETE); check(f.adds==1 && f.debits==1 && f.energy==2);
        for(String point:new String[]{"beforeDebit","afterDebit","beforeEnergy","afterEnergy"}) {
            f=new Fake(); f.failure=point; i=intent(); interrupted(f,i); f.failure="";
            if(point.equals("afterEnergy")) f.energy=0;
            Result r=VehicleRefueling49.execute(f,i);
            check(r==(point.equals("beforeDebit")||point.equals("beforeEnergy")?Result.HELD:Result.COMPLETE));
            check(f.debits<=1 && f.adds<=1);
            if(point.equals("afterEnergy")) check(f.energy==0);
        }
        f=new Fake(); f.authorized=false; interrupted(f,intent()); check(f.record==null && f.debits==0);
        f=new Fake(); f.energy=11; check(VehicleRefueling49.execute(f,intent())==Result.HELD); check(f.debits==0);
        f=new Fake(); i=intent(); f.failure="afterDebit"; interrupted(f,i); f.energy=9; f.failure="";
        check(VehicleRefueling49.execute(f,i)==Result.HELD); check(f.debits==1 && f.adds==0);
        f=new Fake(); i=intent(); f.failure="afterEnergy"; interrupted(f,i); f.added=Proof.UNKNOWN;
        check(VehicleRefueling49.execute(f,i)==Result.HELD); check(f.adds==1);
        f=new Fake(); i=intent(); f.failure="beforeDebit"; interrupted(f,i); f.paid=Proof.CONFLICT;
        check(VehicleRefueling49.execute(f,i)==Result.HELD); check(f.debits==0);
        f=new Fake(); f.fuel=0; i=intent(); interrupted(f,i); f.fuel=80;
        check(VehicleRefueling49.execute(f,i)==Result.HELD); check(f.debits==0);
        f=new Fake(); i=intent(); VehicleRefueling49.execute(f,i);
        Intent changed=new Intent(i.operation(),i.actor(),i.nation(),i.vehicle(),i.world(),"foreign-factory",i.tariff(),i.mode(),i.beforeMeter(),i.quote());
        interrupted(f,changed); check(f.adds==1);
        f=new Fake(); i=intent(); VehicleRefueling49.execute(f,i); f.added=Proof.UNKNOWN;
        check(VehicleRefueling49.execute(f,i)==Result.HELD); check(f.adds==1);
        f=new Fake(); f.meter=new VehicleFuel49.Meter(VehicleFuel49.CATALOG,"pickup",100,32,1);
        check(VehicleRefueling49.execute(f,intent())==Result.HELD); check(f.debits==0);
        System.out.println("PASS " + checks + " transactional assertions (in-memory fault model; not native persistence)");
    }
}
