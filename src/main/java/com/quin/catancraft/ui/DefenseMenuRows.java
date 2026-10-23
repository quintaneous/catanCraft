package com.quin.catancraft.ui;

import com.quin.catancraft.data.NationData;
import com.quin.catancraft.economy.EconomyCost;
import com.quin.catancraft.world.SectorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Server-authored dashboard rows. The client never supplies prices or purchased levels. */
public final class DefenseMenuRows {
    private DefenseMenuRows() {}

    public static List<String> rows(String city, boolean leader, NationData nation,
                                    List<SectorService.MenuState> states) {
        List<String> rows=new ArrayList<>();
        long built=states.stream().filter(s->s.level()>0).count();
        rows.add("N|"+city+"|defenses|DEFENSES - "+built+"/4 sides built");
        rows.add("D|Buy one complete compass side. Gates, guns and ammo are not included.");
        for(var state:states) {
            String side=label(state.side().name());
            String level=switch(state.level()){case 0->"Not built";case 1->"Basic";case 2->"Reinforced (maximum level)";default->"Needs review";};
            rows.add("P|"+side+" - "+level+(state.pending()?" - operation pending":""));
            if(state.cost()!=null&&state.recovery().isEmpty())rows.add("D|Cost: "+state.cost().describe());
            if(leader&&!state.recovery().isEmpty()) {
                rows.add("Y|An interrupted operation is retained. Continue it; do not buy again.");
                rows.add("A|nation defense "+state.recovery()+" "+city+" "+state.side().name().toLowerCase(Locale.ROOT)+" confirm|"+(state.recovery().equals("resume")?"Resume existing construction - no repeat charge":"Finish existing rollback"));
                rows.add("");continue;
            }
            String unavailable=state.unavailable();
            if(!leader)unavailable="Only the owning nation's leader may purchase defenses.";
            String shortage=state.cost()==null?"":missing(nation,state.cost());
            if(!unavailable.isEmpty())rows.add("Y|Unavailable: "+safe(unavailable));
            if(!shortage.isEmpty())rows.add("Y|Missing: "+shortage);
            if(leader&&state.level()>=0&&state.level()<2&&!state.pending()
                    &&unavailable.isEmpty()&&shortage.isEmpty()) {
                rows.add("A|nation defense review "+city+" "+state.side().name().toLowerCase(Locale.ROOT)
                        +"|"+(state.level()==0?"Build Basic ":"Upgrade to Reinforced - ")+side);
            }
            rows.add("");
        }
        if(states.isEmpty())rows.add("Y|No owned defense site is available.");
        rows.add("A|nation|Refresh defense status");
        rows.add("Q|"+city+"|defenses");
        return List.copyOf(rows);
    }

    public static List<String> confirmation(String cityName, SectorService.MenuQuote quote) {
        String side=label(quote.side().name());
        return List.of("V|defense_confirmation", "H|Confirm wall purchase",
                "Y|City: "+safe(cityName)+" ("+quote.city()+")", "P|Side: "+side,
                "B|"+(quote.sourceLevel()==0?"Build Basic":"Basic -> Reinforced"),
                "G|Cost: "+quote.cost().describe(),
                "D|One complete side; national treasury and stockpile.",
                "D|No gates, guns or ammunition included. Provisional playtest prices.",
                "D|Quote expires after 1,200 server ticks. Placement is rechecked on confirmation.",
                "A|nation defense confirm "+quote.city()+" "+quote.side().name().toLowerCase(Locale.ROOT)+" "+quote.token()+"|Confirm purchase",
                "A|nation defense cancel "+quote.city()+" "+quote.side().name().toLowerCase(Locale.ROOT)+" "+quote.token()+"|Cancel");
    }
    public static String missing(NationData nation, EconomyCost cost) {
        StringJoiner result=new StringJoiner(" + ");
        if(nation.treasury()<cost.money())result.add("$"+(cost.money()-nation.treasury()));
        cost.resources().forEach((resource,amount)->{
            long have=nation.resource(resource);
            if(have<amount)result.add((amount-have)+" "+label(resource.id().replace('_',' ')));
        });
        return result.toString();
    }
    public static String safe(String text) {
        if(text==null)return "Unknown reason";
        String cleaned=text.replace('|','/').replace('\n',' ').replace('\r',' ');
        return cleaned.length()>440?cleaned.substring(0,437)+"...":cleaned;
    }
    private static String label(String value) {
        String lower=value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0))+lower.substring(1);
    }
}
