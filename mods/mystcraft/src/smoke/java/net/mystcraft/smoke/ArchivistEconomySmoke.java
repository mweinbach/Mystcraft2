package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.minecraft.exploration.*;
import net.mystcraft.minecraft.writing.PageData;

/** Actual loaded loot, native inventory payments, source stock clock and live menu validation. */
public final class ArchivistEconomySmoke {
    private ArchivistEconomySmoke(){}
    public static List<String> run(MinecraftServer server,ServerLevel level){
        check(server.isSameThread(),"archivist fixture must use server thread");var passed=new ArrayList<String>();
        int boosters=0,pages=0,leather=0,paper=0,vials=0,foreignOutputs=0;
        var loaded=LoadedTreasureFixture.load(server);
        for(int seed=0;seed<64;seed++){
            var context=new LootContext.Builder(level).withRandom(RandomSource.create(45000L+seed)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            var result=loaded.table().getRandomItems(context);
            var fullContext=new LootContext.Builder(level).withRandom(RandomSource.create(45000L+seed)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            foreignOutputs+=LoadedTreasureFixture.verifyWhole(loaded,result,loaded.whole().getRandomItems(fullContext),45000L+seed);
            if(result.size()<4||result.size()>8)throw new AssertionError("source treasure must have one4–8roll pool atzero luck; seed="+(45000L+seed)+" luck="+context.getLuck()+" stacks="+result.size()+" contents="+result.stream().map(stack->stack.save(new CompoundTag()).toString()).toList()+" loadedTable="+net.minecraft.world.level.storage.loot.LootTables.serialize(server.getLootTables().get(ClassicLibraryFeature.LOOT)));
            for(ItemStack stack:result){
                if(stack.is(MystcraftItems.BOOSTER.get())){boosters++;check(stack.getCount()==1,"one booster per treasure draw");}
                else if(stack.is(InkMixerRegistry.INK_VIAL.get())){vials++;check(stack.getCount()==1,"one vial per treasure draw");}
                else if(stack.is(Items.LEATHER)){leather++;check(stack.getCount()>=1&&stack.getCount()<=3,"source leather count1–3");}
                else if(stack.is(Items.PAPER)){paper++;check(stack.getCount()>=1&&stack.getCount()<=6,"source paper count1–6");}
                else{pages++;check(PageData.symbol(stack).isPresent()&&ExplorationPages.rank(stack,level.registryAccess())>=0,"treasure page must have executable ranked payload");}
            }
        }
        check(boosters>0&&pages>0,"actual loaded treasure must reach boosters and ranked pages");
        int[] boundaries={0,50,1050,1100,1150};
        for(int boundary:boundaries){
            var fixed=new net.minecraft.world.level.levelgen.LegacyRandomSource(1L){@Override public int nextInt(int bound){return bound>1150?boundary:bound-1;}};
            var fixedContext=new LootContext.Builder(level).withRandom(fixed).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            var items=loaded.table().getRandomItems(fixedContext);
            check(!items.isEmpty(),"source fixed-entry boundary must produce loot");
            for(ItemStack stack:items)check(switch(boundary){case 0->stack.is(InkMixerRegistry.INK_VIAL.get())&&stack.getCount()==1;case 50->stack.is(MystcraftItems.BOOSTER.get())&&stack.getCount()==1;case 1050->stack.is(Items.LEATHER)&&stack.getCount()==3;case 1100->stack.is(Items.PAPER)&&stack.getCount()==6;default->PageData.symbol(stack).isPresent();},"source exact50/1000/50/50 weight boundary or maximum count changed");
        }
        passed.add("Treasure: exact loaded source4–8roll pool/weight/count schema executes; whole table retains exact authored NBT plus "+foreignOutputs+" declared foreign outputs from "+loaded.foreignPools()+" appended pools");
        var luckContext=new LootContext.Builder(level).withRandom(RandomSource.create(99)).withLuck(2).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        int luckCount=loaded.table().getRandomItems(luckContext).size();check(luckCount>=6&&luckCount<=11,"source bonusrolls1–2 multiply native luck before flooring");
        passed.add("Treasure: source bonus rolls apply actual native luck rather than a separate supplies pool");
        Villager villager=EntityType.VILLAGER.create(level);check(villager!=null,"native villager fixture exists");
        villager.setVillagerData(villager.getVillagerData().setProfession(ExplorationRegistry.ARCHIVIST.get()));
        var data=new ArchivistStock();var state=data.inventory(villager);CompoundTag initial=state.save();initial.putInt("boostercount",5);initial.putLong("lastrestock",0);var initialPages=initial.getList("Inventory",10);for(int i=0;i<3;i++)initialPages.getCompound(i).putByte("Count",(byte)3);state.load(initial);
        for(int i=0;i<3;i++)check(state.page(villager,i).getCount()==3&&ExplorationPages.rank(state.page(villager,i),level.registryAccess())>=i+1,"three initial cards use minimum rank1/2/3 and source stock3");
        state.simulate(11999,RandomSource.create(1));check(state.lastRestock()==0&&state.boosters()==5,"no restock before12000worldticks");
        state.simulate(12000,RandomSource.create(1));check(state.lastRestock()==12000,"exact12000tick boundary advances one source restock step");
        state.load(initial);state.restockRoll(0);check(state.save().equals(initial),"source no-op restock branch changes no inventory");
        state.restockRoll(1);state.restockRoll(2);state.restockRoll(3);state.restockRoll(1);check(state.boosters()==8,"three booster outcomes grow byone capped8");
        state.restockRoll(4);state.restockRoll(4);state.restockRoll(4);check(state.page(villager,0).getCount()==5,"page restock grows byone capped5");
        state.load(initial);long catchupTime=ArchivistStock.STEP*2050;
        state.simulate(catchupTime,RandomSource.create(77));check(state.lastRestock()==ArchivistStock.STEP*1024&&state.catchingUp(catchupTime),"old-world catchup preserves pending intervals with bounded first tick");
        state.simulate(catchupTime,RandomSource.create(77));check(state.lastRestock()==ArchivistStock.STEP*1024,"multiple menu refreshes cannot reset same-tick catchup budget");
        state.simulate(catchupTime+1,RandomSource.create(77));state.simulate(catchupTime+2,RandomSource.create(77));check(state.lastRestock()==catchupTime&&!state.catchingUp(catchupTime+2),"catchup eventually processes every12000tick interval without skipping");
        var frozen=((net.minecraft.world.level.saveddata.SavedData)data).save(new CompoundTag());check(((net.minecraft.world.level.saveddata.SavedData)ArchivistStock.load(frozen)).save(new CompoundTag()).equals(frozen),"selected card payloads and stock survive saveddata roundtrip");
        CompoundTag longClock=state.save();longClock.putLong("lastrestock",Integer.MAX_VALUE+12000L);state.load(longClock);check(state.save().getLong("lastrestock")==Integer.MAX_VALUE+12000L,"restock clock retains long range");
        passed.add("Archivist stock: minimum ranks, initial counts,12000tick boundary, weighted branches/caps and exact NBT/long clock persistence");
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SmokeArchivist"),null);((Entity)player).setPos(40,220,40);villager.setPos(40,220,40);
        var inventory=player.getInventory();inventory.setItem(0,new ItemStack(Items.EMERALD_BLOCK,3));
        ItemStack output=state.page(villager,0);output.setCount(1);check(ArchivistPurchases.buy(inventory,output,20)&&ArchivistPurchases.emeralds(inventory)==7,"three emerald blocks pay20 with exact7emerald change");
        inventory.clearContent();for(int i=0;i<36;i++)inventory.setItem(i,new ItemStack(i==0?Items.EMERALD:Items.STONE,64));ListTag before=inventory.save(new ListTag());
        check(!ArchivistPurchases.buy(inventory,output,8)&&inventory.save(new ListTag()).equals(before),"full inventory rejects purchase atomically without consuming currency");
        passed.add("Archivist currency: native inventory accepts emerald blocks/exact change; full inventory rejection preserves all stacks");
        inventory.clearContent();inventory.setItem(0,new ItemStack(Items.EMERALD,64));
        ArchivistMenu menu=new ArchivistMenu(61,inventory,villager);check(((net.minecraft.world.inventory.AbstractContainerMenu)menu).stillValid(player),"nearby same-level adult archivist menu valid");int price=menu.price(0),count=menu.page(0).getCount();
        check(((net.minecraft.world.inventory.AbstractContainerMenu)menu).clickMenuButton(player,0)&&ArchivistPurchases.emeralds(inventory)==64-price&&menu.page(0).getCount()==count-1,"actual menu page purchase pays selected rank price and exactly one stock");
        int oldBoosters=menu.boosters();check(((net.minecraft.world.inventory.AbstractContainerMenu)menu).clickMenuButton(player,3)&&menu.boosters()==oldBoosters-1,"actual menu booster button consumes exactlyone stock");
        ListTag stable=inventory.save(new ListTag());check(!((net.minecraft.world.inventory.AbstractContainerMenu)menu).clickMenuButton(player,-1)&&!((net.minecraft.world.inventory.AbstractContainerMenu)menu).clickMenuButton(player,4)&&inventory.save(new ListTag()).equals(stable),"forged out-of-range menu indices cannot buy");
        villager.setPos(80,220,80);check(!((net.minecraft.world.inventory.AbstractContainerMenu)menu).stillValid(player)&&!((net.minecraft.world.inventory.AbstractContainerMenu)menu).clickMenuButton(player,0),"remote shop request rejects before stock/payment mutation");((net.minecraft.world.inventory.AbstractContainerMenu)menu).removed(player);
        passed.add("Archivist menu: actual page/booster purchases conserve stock/currency and reject forged or remote actions");
        return passed;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
