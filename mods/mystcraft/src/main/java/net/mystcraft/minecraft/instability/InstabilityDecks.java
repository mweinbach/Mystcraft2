package net.mystcraft.minecraft.instability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Source deck multiplicities, thresholds and prefix-cost selection. Black is intentionally not a source card. */
public final class InstabilityDecks {
    public static final Map<String,Integer> THRESHOLDS=Map.of("basic",0,"harsh",2500,"destructive",10000,"eating",15000,"death",20000);
    public static final Map<String,Integer> COSTS=new LinkedHashMap<>();
    private static final Map<String,List<String>> CARDS=new LinkedHashMap<>();
    static {
        for(String name:List.of("basic","harsh","destructive","eating","death")) CARDS.put(name,new ArrayList<>());
        add("blindness",1000,"eating",1); add("blindness,g",1500,"death",1);
        add("enemyregen,g",1000,"basic",5,"harsh",2); add("enemyresist,g",1000,"basic",2,"harsh",1);
        add("fatigue",500,"basic",5); add("fatigue,g",1000,"harsh",5);
        add("hunger",500,"basic",8,"harsh",2); add("hunger,g",1000,"harsh",5);
        add("nausea",1000,"eating",1); add("nausea,g",1500,"death",1);
        add("poison",500,"basic",9,"harsh",3); add("poison,g",1000,"harsh",5);
        add("slow",500,"basic",6,"harsh",1); add("slow,g",1000,"harsh",5);
        add("weakness",500,"basic",8,"harsh",2); add("weakness,g",1000,"harsh",5);
        add("wither",1000,"harsh",1,"destructive",1,"eating",2);
        add("wither,g",2000,"destructive",1,"eating",1,"death",1);
        add("burning",500,"harsh",1); add("crumble",2000,"destructive",6);
        add("decayblue",2000,"eating",2,"death",1); add("decaypurple",2000,"eating",2,"death",1);
        add("decayred",2000,"eating",2,"death",1); add("decaywhite",5000,"eating",1,"death",3);
        add("explosions",1000,"destructive",8); add("lightning",1000,"harsh",4,"destructive",4);
        add("meteors",1000,"destructive",4);
    }
    private InstabilityDecks() {}
    private static void add(String card,int cost,Object... decks) {
        COSTS.put(card,cost);
        for(int i=0;i<decks.length;i+=2) for(int n=0;n<(Integer)decks[i+1];n++) CARDS.get((String)decks[i]).add(card);
    }
    public static CompoundTag reconcile(long seed,CompoundTag saved) {
        CompoundTag result=new CompoundTag(); Random random=new Random(seed);
        for(var entry:CARDS.entrySet()) {
            List<String> missing=new ArrayList<>(entry.getValue()), order=new ArrayList<>();
            for(var value:saved.getList(entry.getKey(),8)) { String card=value.getAsString(); if(missing.remove(card)) order.add(card); }
            Collections.shuffle(missing,random); order.addAll(missing);
            ListTag cards=new ListTag(); order.forEach(card->cards.add(StringTag.valueOf(card))); result.put(entry.getKey(),cards);
        }
        return result;
    }
    public static Map<String,Integer> levels(CompoundTag decks,int score) {
        Map<String,Integer> result=new LinkedHashMap<>();
        // Source truncates by smallest cost (500), then each deck independently spends its budget.
        score-=score%500;
        for(String deck:CARDS.keySet()) {
            int remaining=score-THRESHOLDS.get(deck);
            for(var value:decks.getList(deck,8)) {
                String card=value.getAsString(); Integer cost=COSTS.get(card);
                if(cost==null || (remaining-=cost)<0) break;
                result.merge(card,1,Integer::sum);
            }
        }
        return result;
    }
    public static int score(boolean profileReady,int symbol,int base,int block,int bonus,int debug,int difficulty) {
        if(!profileReady) return 0;
        int score=symbol+base+block+bonus+debug;
        return switch(difficulty) { case 0->(int)(score*.25F); case 1->(int)(score*.5F); case 3->(int)(score*1.75F); default->score; };
    }
}
