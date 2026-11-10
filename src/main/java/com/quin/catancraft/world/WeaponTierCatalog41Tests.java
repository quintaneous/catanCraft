package com.quin.catancraft.world;

import com.google.gson.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises shipped reference parsing and exclusion policy, not weapon combat or pack acceptance. */
@GameTestHolder("catancraft_rewards41") @PrefixGameTestTemplate(false)
public final class WeaponTierCatalog41Tests {
    @GameTest(template="townhall_state_empty",batch="tiers41_01")
    public static void full_reference_covers_both_namespaces_and_preserves_reserves(GameTestHelper h)throws Exception {
        var entries=WeaponTierCatalog41.entries();Set<String> ids=new HashSet<>();
        for(var e:entries)h.assertTrue(ids.add(e.id()),"A configured reference gun appears twice");
        h.assertTrue(entries.size()==113&&entries.stream().filter(e->e.provider().equals("tacz")).count()==70
                &&entries.stream().filter(e->e.provider().equals("superbwarfare")).count()==43,"Reference coverage changed");
        h.assertTrue(entries.stream().filter(e->e.disposition()==WeaponTierCatalog41.Disposition.DROP).count()==28
                &&entries.stream().filter(e->e.disposition()==WeaponTierCatalog41.Disposition.HOLD).count()==2,"Reserved/held guns were lost");
        h.assertTrue(!ids.contains("superbwarfare:empty_gun")&&!ids.contains("superbwarfare:vehicle_gun"),"Internal weapon registrations entered the shop reference");
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_02")
    public static void drop_and_hold_guns_are_denied_at_every_tier_and_profile(GameTestHelper h)throws Exception {
        for(var e:WeaponTierCatalog41.entries())if(e.disposition()==WeaponTierCatalog41.Disposition.DROP||e.disposition()==WeaponTierCatalog41.Disposition.HOLD)
            for(int level=0;level<=5;level++) {
                int tier=level;refuse(()->WeaponTierCatalog41.requireShop(e.id(),tier,WeaponTierCatalog41.CANDIDATE_REVISION));
                refuse(()->WeaponTierCatalog41.requireExistingOffer(e.id(),tier));
            }
        for(String id:List.of("superbwarfare:empty_gun","superbwarfare:vehicle_gun","tacz:unclassified_new_gun","minecraft:diamond","ak47","TACZ:ak47")) {
            refuse(()->WeaponTierCatalog41.entry(id));refuse(()->WeaponTierCatalog41.requireExistingOffer(id,5));
        }
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_03")
    public static void legacy_stock_and_ak_gate_remain_separate_from_candidate_progression(GameTestHelper h)throws Exception {
        var levels=Map.of("tacz:glock_17",0,"tacz:ak47",1,"tacz:9mm",0,"tacz:762x39",1);
        for(var e:levels.entrySet()) {
            WeaponTierCatalog41.requireExistingOffer(e.getKey(),e.getValue());
            refuse(()->WeaponTierCatalog41.requireExistingOffer(e.getKey(),e.getValue()+1));
        }
        h.assertTrue(WeaponShopCatalog40.offers().size()==4,"Reference policy silently activated new stock");
        h.assertTrue(WeaponShopCatalog40.offer("ak47").requiredLevel()==1
                &&WeaponShopCatalog40.offer("ak47").price().money()==1200,"Existing AK stock or price was silently changed");
        h.assertTrue(WeaponTierCatalog41.requireShop("tacz:ak47",2,WeaponTierCatalog41.CANDIDATE_REVISION).proposedLevel()==2,"Candidate AK level lost");
        refuse(()->WeaponTierCatalog41.requireShop("tacz:ak47",1,WeaponTierCatalog41.CANDIDATE_REVISION));
        refuse(()->WeaponTierCatalog41.requireShop("tacz:ak47",1,WeaponTierCatalog41.LEGACY_REVISION));
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_04")
    public static void candidate_levels_and_special_native_requirements_cannot_be_bypassed(GameTestHelper h)throws Exception {
        int ordinary=0,special=0;
        for(var e:WeaponTierCatalog41.entries())if(e.disposition()==WeaponTierCatalog41.Disposition.STARTER||e.disposition()==WeaponTierCatalog41.Disposition.SHOP) {
            if(e.requiresSpecialQualification()){special++;refuse(()->WeaponTierCatalog41.requireShop(e.id(),e.proposedLevel(),WeaponTierCatalog41.CANDIDATE_REVISION));}
            else {ordinary++;h.assertTrue(WeaponTierCatalog41.requireShop(e.id(),e.proposedLevel(),WeaponTierCatalog41.CANDIDATE_REVISION).equals(e),"Eligible reference policy entry changed");}
            refuse(()->WeaponTierCatalog41.requireShop(e.id(),e.proposedLevel()+1,WeaponTierCatalog41.CANDIDATE_REVISION));
            refuse(()->WeaponTierCatalog41.requireShop(e.id(),e.proposedLevel(),"unknown_future_revision"));
            refuse(()->WeaponTierCatalog41.requireShop(e.id(),e.proposedLevel(),null));
        }
        h.assertTrue(ordinary>0&&special>0&&ordinary+special==83,"Conditional special behavior was flattened into ordinary stock");h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_05")
    public static void forged_offer_cannot_send_reserved_weapon_to_native_builder(GameTestHelper h)throws Exception {
        var price=WeaponShopCatalog40.offer("pistol").price();
        for(String id:List.of("tacz:minigun","tacz:m95","superbwarfare:vehicle_gun","tacz:unknown_variant")) {
            var forged=new WeaponShopCatalog40.Offer("pistol","Forged alias",WeaponShopCatalog40.Kind.GUN,id,"AUTO",1,5,price);
            try {WeaponShopCatalog40.build(forged);throw new AssertionError("Forged reserved offer reached delivery");}
            catch(IOException expected){h.assertTrue(expected.getMessage().contains("040"),"The central policy guard did not run before native mod lookup");}
        }
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_06")
    public static void shipped_reference_is_immutable_and_changed_inputs_refuse(GameTestHelper h)throws Exception {
        byte[] bytes=bytes();h.assertTrue(WeaponTierCatalog41.validate(bytes).equals(WeaponTierCatalog41.entries()),"Runtime and explicit parser disagree");
        refuse(()->WeaponTierCatalog41.validate(null));refuse(()->WeaponTierCatalog41.validate(new byte[0]));refuse(()->WeaponTierCatalog41.validate(new byte[131073]));
        byte[] changed=Arrays.copyOf(bytes,bytes.length+1);changed[changed.length-1]=' ';refuse(()->WeaponTierCatalog41.validate(changed));
        try{WeaponTierCatalog41.entries().clear();throw new AssertionError("Reference list is mutable");}catch(UnsupportedOperationException expected){}
        var entry=WeaponTierCatalog41.entry("tacz:ak47");
        try{entry.qualificationFlags().clear();throw new AssertionError("Qualification flags are mutable");}catch(UnsupportedOperationException expected){}
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_07")
    public static void duplicate_missing_unknown_and_wrong_type_reference_fields_refuse(GameTestHelper h)throws Exception {
        String original=new String(bytes(),StandardCharsets.UTF_8);
        refuse(()->WeaponTierCatalog41.validate(original.replaceFirst("\\{","{\"schema\":1,").getBytes(StandardCharsets.UTF_8)));
        for(String edit:List.of("missing","unknown","duplicate","fractional","string_level")) {
            JsonObject json=JsonParser.parseString(original).getAsJsonObject();var entries=json.getAsJsonArray("entries");var first=entries.get(0).getAsJsonObject();
            switch(edit) {
                case "missing"->entries.remove(0);
                case "unknown"->first.addProperty("price",0);
                case "duplicate"->entries.set(1,first.deepCopy());
                case "fractional"->first.addProperty("proposedLevel",1.5);
                case "string_level"->first.addProperty("proposedLevel","1");
            }
            refuse(()->WeaponTierCatalog41.validate(json.toString().getBytes(StandardCharsets.UTF_8)));
        }
        h.succeed();
    }
    @GameTest(template="townhall_state_empty",batch="tiers41_08")
    public static void native_ammo_and_special_consumer_shapes_remain_distinct(GameTestHelper h)throws Exception {
        h.assertTrue(WeaponTierCatalog41.entry("tacz:ak47").ammoJson().equals("\"tacz:762x39\""),"TaCZ ammo identity changed");
        h.assertTrue(WeaponTierCatalog41.entry("superbwarfare:ak_47").ammoJson().contains("@RifleAmmo"),"SBW player-ammo consumer was converted to a guessed registry ID");
        h.assertTrue(WeaponTierCatalog41.entries().stream().anyMatch(e->e.ammoJson().startsWith("[")),"Native alternate ammo structure lost");
        h.assertTrue(WeaponTierCatalog41.entries().stream().anyMatch(e->e.qualificationFlags().contains(WeaponTierCatalog41.Qualification.ENERGY_CONSUMER)),"Energy consumers lost qualification hold");
        h.assertTrue(!WeaponTierCatalog41.entry("tacz:ak47").equals(WeaponTierCatalog41.entry("superbwarfare:ak_47")),"Same-named guns collapsed across providers");
        h.succeed();
    }
    private static byte[] bytes()throws IOException {try(var in=WeaponTierCatalog41Tests.class.getResourceAsStream("/data/catancraft/economy/weapon_tiers41.json")){if(in==null)throw new IOException("Missing shipped reference");return in.readAllBytes();}}
    private interface Attempt {void run()throws Exception;}
    private static void refuse(Attempt attempt)throws Exception {try{attempt.run();}catch(IOException expected){return;}throw new AssertionError("Expected strict catalog refusal");}
}
