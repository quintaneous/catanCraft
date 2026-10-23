package com.quin.catancraft.world;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/** Shared durable city reservation check across historical sockets and grouped sectors. */
final class DefenseCityReservations {
    private DefenseCityReservations() {}
    static void requireAvailable(Path root,String city,Path ownJournal)throws IOException {
        if(!city.matches("[a-p]"))throw new IOException("Unknown reservation city");
        Path dir=root.resolve("data");
        if(Files.isSymbolicLink(dir))throw new IOException("Defense history directory is a symlink");
        if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return;
        int checked=0;
        for(String kind:new String[]{"defense","sector","connection"}) {
            String suffix=kind.equals("defense")?"_r4a_r6_1.dat":"_v1.dat";
            Set<String> phases=kind.equals("defense")?Set.of("PREPARED","RESERVED","APPLYING","COMMITTED","ROLLING_BACK","ROLLED_BACK"):
                Set.of("PREPARED","APPLYING","PHYSICAL_COMPLETE","COMMITTED","ROLLING_BACK","ROLLED_BACK");
            try(var paths=Files.newDirectoryStream(dir,"catancraft_"+kind+"_"+city+"_*"+suffix)) {
                for(Path file:paths) {
                    if(++checked>4096)throw new IOException("Excess defense histories require review");
                    var journal=ProducerJournal.read(file);var identity=journal.getCompound("identity");String phase=journal.getString("phase");
                    if(!root.toString().equals(identity.getString("world"))||!city.equals(identity.getString("city"))||!journal.hasUUID("id")||!phases.contains(phase))
                        throw new IOException("Unknown or foreign defense city history: "+file.getFileName());
                    if(!file.equals(ownJournal)&&!Set.of("COMMITTED","ROLLED_BACK").contains(phase))
                        throw new PreparationDeferred("Pending "+kind+" city operation; recover retained history "+file.getFileName()+" first");
                }
            }
        }
    }
}
