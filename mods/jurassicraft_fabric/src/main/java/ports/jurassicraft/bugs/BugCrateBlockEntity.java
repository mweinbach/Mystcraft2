package ports.jurassicraft.bugs;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BugCrateBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    private static final int[] INPUTS={0,1,2,3,4,5}, OUTPUTS={6,7,8};
    private NonNullList<ItemStack> items=NonNullList.withSize(9,ItemStack.EMPTY);
    private int progress,activeBug=-1,activeFood=-1;
    private ItemStack activeBugStack=ItemStack.EMPTY,activeFoodStack=ItemStack.EMPTY;
    @Nullable private Component customName;
    private final ContainerData data=new ContainerData() {
        @Override public int get(int index) { return index==0 ? progress : index==1 ? totalTime() : 0; }
        @Override public void set(int index,int value) { if (index==0) progress=value; }
        @Override public int getCount() { return 2; }
    };
    public BugCrateBlockEntity(BlockPos pos,BlockState state) { super(BugRegistry.ENTITY.get(),pos,state); }
    private record Recipe(int bug,int food,int count) { int ticks() { return Math.max(1,count/3)*400; } }
    @Nullable private Recipe recipe() {
        for (int bug=0;bug<3;bug++) {
            ItemStack insect=items.get(bug);
            if (!BugFoods.isBug(insect)) continue;
            int best=-1,count=0;
            for (int food=3;food<6;food++) {
                int candidate=BugFoods.offspring(insect,items.get(food));
                if (candidate>count) { count=candidate; best=food; }
            }
            if (best>=0) return new Recipe(bug,best,count);
        }
        return null;
    }
    private int totalTime() { Recipe recipe=recipe(); return recipe==null ? 0 : recipe.ticks(); }
    public static void tick(Level level,BlockPos pos,BlockState state,BugCrateBlockEntity crate) {
        if (level.isClientSide) return;
        Recipe recipe=crate.recipe();
        if (recipe==null) {
            if (crate.progress!=0 || crate.activeBug!=-1) { crate.progress=0; crate.activeBug=-1; crate.activeFood=-1; crate.activeBugStack=ItemStack.EMPTY; crate.activeFoodStack=ItemStack.EMPTY; crate.setChanged(); }
            return;
        }
        ItemStack bug=crate.items.get(recipe.bug),food=crate.items.get(recipe.food);
        if (crate.activeBug!=recipe.bug || crate.activeFood!=recipe.food || !same(crate.activeBugStack,bug) || !same(crate.activeFoodStack,food)) {
            crate.progress=0; crate.activeBug=recipe.bug; crate.activeFood=recipe.food; crate.activeBugStack=one(bug); crate.activeFoodStack=one(food);
        }
        if (++crate.progress>=recipe.ticks()) {
            ItemStack offspring=new ItemStack(bug.getItem(),recipe.count);
            crate.removeItem(recipe.food,1);
            int target=crate.outputFor(offspring);
            if (target<0) Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,offspring);
            else if (crate.items.get(target).isEmpty()) crate.items.set(target,offspring);
            else crate.items.get(target).grow(offspring.getCount());
            crate.progress=0;
        }
        crate.setChanged();
    }
    private int outputFor(ItemStack stack) {
        for (int slot : OUTPUTS) {
            ItemStack existing=items.get(slot);
            if (existing.isEmpty() || ItemStack.isSameItemSameTags(existing,stack) && existing.getCount()+stack.getCount()<=existing.getMaxStackSize()) return slot;
        }
        return -1;
    }
    private static boolean same(ItemStack a,ItemStack b) { return a.isEmpty() && b.isEmpty() || !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a,b); }
    private static ItemStack one(ItemStack stack) { if (stack.isEmpty()) return ItemStack.EMPTY; ItemStack result=stack.copy(); result.setCount(1); return result; }
    @Override public int getContainerSize() { return 9; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int count) { ItemStack result=ContainerHelper.removeItem(items,slot,count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); if (stack.getCount()>getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return level!=null && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64; }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot>=0 && slot<3 ? BugFoods.isBug(stack) : slot<6 && slot>=3 && BugFoods.isFood(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return side==Direction.DOWN ? OUTPUTS : INPUTS; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return slot>=6; }
    @Override public Component getDisplayName() { return customName!=null ? customName : Component.translatable("container.jurassicraft.bug_crate"); }
    public void setCustomName(Component name) { customName=name; setChanged(); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new BugCrateMenu(id,inventory,this,data); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag,items); tag.putInt("Progress",progress); tag.putInt("ActiveBug",activeBug); tag.putInt("ActiveFood",activeFood);
        if (!activeBugStack.isEmpty()) tag.put("Bug",activeBugStack.save(new CompoundTag()));
        if (!activeFoodStack.isEmpty()) tag.put("Food",activeFoodStack.save(new CompoundTag()));
        if (customName!=null) tag.putString("CustomName",Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items=NonNullList.withSize(9,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,items);
        progress=Math.max(0,Math.min(3600,tag.getInt("Progress"))); activeBug=tag.contains("ActiveBug") ? tag.getInt("ActiveBug") : -1; activeFood=tag.contains("ActiveFood") ? tag.getInt("ActiveFood") : -1;
        activeBugStack=tag.contains("Bug",10) ? ItemStack.of(tag.getCompound("Bug")) : ItemStack.EMPTY; activeFoodStack=tag.contains("Food",10) ? ItemStack.of(tag.getCompound("Food")) : ItemStack.EMPTY;
        customName=tag.contains("CustomName",8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }
}
