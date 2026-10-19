package net.mystcraft.minecraft.exploration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Source InventoryVillager economy, independent of modern work schedules and reputation discounts. */
public final class ArchivistStock extends SavedData {
    public static final String FILE="mystcraft_archivist_stock";
    public static final long STEP=12000;
    private final Map<UUID,State> inventories=new HashMap<>();
    public static ArchivistStock get(Villager villager){return villager.getServer().overworld().getDataStorage().computeIfAbsent(ArchivistStock::load,ArchivistStock::new,FILE);}
    public State inventory(Villager villager){State state=inventories.computeIfAbsent(villager.getUUID(),id->{setDirty();return new State();});for(int i=0;i<3;i++)state.page(villager,i);state.simulate(villager);return state;}
    public final class State {
        private final ItemStack[] pages={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
        private int boosters=5;
        private long lastRestock;
        private final net.minecraft.util.RandomSource restockRandom=net.minecraft.util.RandomSource.create();
        private long budgetTick=Long.MIN_VALUE;
        private int remainingBudget;
        public boolean catchingUp(long now){return now-lastRestock>=STEP;}
        public int boosters(){return boosters;}
        public long lastRestock(){return lastRestock;}
        public ItemStack page(Villager villager,int index){
            if(index<0||index>=3)return ItemStack.EMPTY;
            if(pages[index].isEmpty()){
                pages[index]=ExplorationPages.sampleRank(villager.level.registryAccess(),villager.getRandom(),index+1,Integer.MAX_VALUE);
                if(!pages[index].isEmpty())pages[index].setCount(3);
                setDirty();
            }
            return pages[index].copy();
        }
        public int price(Villager villager,int index){int rank=ExplorationPages.rank(page(villager,index),villager.level.registryAccess());return rank<0?100:4*(1+rank);}
        public void consume(int index){if(index==3){if(boosters>0)boosters--;}else if(index>=0&&index<3&&!pages[index].isEmpty())pages[index].shrink(1);setDirty();}
        public void simulate(Villager villager){
            simulate(villager.level.getGameTime(),restockRandom);
        }
        public void simulate(long now,net.minecraft.util.RandomSource random){
            // Source uses total world ticks, not daytime; moving the sun does not accelerate restocks.
            if(now!=budgetTick){budgetTick=now;remainingBudget=1024;}
            while(catchingUp(now)&&remainingBudget>0){remainingBudget--;lastRestock+=STEP;restockRoll(random.nextInt(7));setDirty();}
        }
        /** Exactly one of seven equiprobable outcomes: no-op, booster x3, each page x1. */
        public void restockRoll(int roll){
            if(roll<0||roll>=7)throw new IllegalArgumentException("Invalid archivist restock roll");
            if(roll==0)return;
            if(roll<=3){if(boosters<8)boosters++;}
            else if(!pages[roll-4].isEmpty()&&pages[roll-4].getCount()<5)pages[roll-4].grow(1);
            setDirty();
        }
        public CompoundTag save(){CompoundTag tag=new CompoundTag();ListTag list=new ListTag();for(ItemStack page:pages)list.add(page.save(new CompoundTag()));tag.put("Inventory",list);tag.putInt("boostercount",boosters);tag.putLong("lastrestock",lastRestock);return tag;}
        public void load(CompoundTag tag){budgetTick=Long.MIN_VALUE;remainingBudget=0;var list=tag.getList("Inventory",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(3,list.size());i++){pages[i]=ItemStack.of(list.getCompound(i));if(pages[i].getCount()>5)pages[i].setCount(5);}boosters=Math.max(0,Math.min(8,tag.contains("boostercount")?tag.getInt("boostercount"):5));lastRestock=Math.max(0,tag.getLong("lastrestock"));}
    }
    @Override public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();inventories.forEach((id,state)->{CompoundTag entry=state.save();entry.putUUID("Villager",id);list.add(entry);});tag.put("Stocks",list);return tag;}
    public static ArchivistStock load(CompoundTag tag){ArchivistStock data=new ArchivistStock();var list=tag.getList("Stocks",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){CompoundTag entry=list.getCompound(i);if(entry.hasUUID("Villager")){State state=data.new State();state.load(entry);data.inventories.put(entry.getUUID("Villager"),state);}}return data;}
}
