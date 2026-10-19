package ports.jurassicraft.cultivator;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.hatching.HatcheryRegistry;

public final class CultivatorBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    public static final int TICKS=2000, MAX_NUTRIENTS=3000, REQUIRED=1500;
    private static final int[] INPUTS={0,1,2}, OUTPUTS={0,1,3};
    private NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private int progress,water,proximates,minerals,vitamins,lipids,temperature;
    private ItemStack active=ItemStack.EMPTY;
    @Nullable private Component customName;
    private LazyOptional<IItemHandler> input=LazyOptional.empty(),output=LazyOptional.empty(),all=LazyOptional.empty();
    private final ContainerData data=new ContainerData() {
        @Override public int get(int id) { return switch(id) {case 0 -> progress; case 1 -> TICKS; case 2 -> water; case 3 -> proximates; case 4 -> minerals; case 5 -> vitamins; case 6 -> lipids; case 7 -> temperature; default -> 0;}; }
        @Override public void set(int id,int value) { switch(id) {case 0 -> progress=value; case 2 -> water=value; case 3 -> proximates=value; case 4 -> minerals=value; case 5 -> vitamins=value; case 6 -> lipids=value; case 7 -> temperature=value;} }
        @Override public int getCount() { return 8; }
    };
    public CultivatorBlockEntity(BlockPos pos,BlockState state) { super(CultivatorRegistry.ENTITY.get(),pos,state); createCapabilities(); }
    public static void tick(Level level,BlockPos pos,BlockState state,CultivatorBlockEntity entity) {
        if (level.isClientSide) return;
        entity.tickServer(level.random);
    }
    private void tickServer(RandomSource random) {
        boolean dirty=false;
        ItemStack bucket=items.get(3);
        if (water<2 && items.get(2).is(Items.WATER_BUCKET) && (bucket.isEmpty() || bucket.is(Items.BUCKET) && bucket.getCount()<16)) {
            items.get(2).shrink(1);
            if (bucket.isEmpty()) items.set(3,new ItemStack(Items.BUCKET)); else bucket.grow(1);
            water++; dirty=true;
        }
        var food=CultivatorNutrition.get(items.get(1));
        if (food!=null && (proximates<MAX_NUTRIENTS || minerals<MAX_NUTRIENTS || vitamins<MAX_NUTRIENTS || lipids<MAX_NUTRIENTS)) {
            if (items.get(1).is(Items.MILK_BUCKET)) items.set(1,new ItemStack(Items.BUCKET)); else items.get(1).shrink(1);
            proximates=Math.min(MAX_NUTRIENTS,proximates+(int)((800+random.nextInt(201))*food.proximates()));
            minerals=Math.min(MAX_NUTRIENTS,minerals+(int)((900+random.nextInt(101))*food.minerals()));
            vitamins=Math.min(MAX_NUTRIENTS,vitamins+(int)((900+random.nextInt(101))*food.vitamins()));
            lipids=Math.min(MAX_NUTRIENTS,lipids+(int)((980+random.nextInt(101))*food.lipids()));
            dirty=true;
        }
        ItemStack syringe=items.get(0);
        if (!same(syringe,active)) { active=one(syringe); progress=0; dirty=true; }
        if (validSyringe(syringe) && water==2 && proximates>=REQUIRED && minerals>=REQUIRED && vitamins>=REQUIRED && lipids>=REQUIRED) {
            progress++; dirty=true;
            if (progress>=TICKS) {
                ItemStack result=new ItemStack(HatcheryRegistry.hatchedEgg(Species.COELACANTH));
                result.setTag(syringe.getTag().copy()); result.getTag().putBoolean("Gender",temperature>50);
                items.set(0,result); active=one(result); progress=0;
                proximates-=REQUIRED; minerals-=REQUIRED; vitamins-=REQUIRED; lipids-=REQUIRED; water--;
            }
        } else if (progress>0) { progress=0; dirty=true; }
        if (dirty) setChanged();
    }
    public static boolean validSyringe(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(HatcheryRegistry.syringe(Species.COELACANTH))) return false;
        return Genetics.read(stack.getTag()).filter(dna -> dna.dinosaur()==Species.COELACANTH).isPresent();
    }
    private static boolean same(ItemStack a,ItemStack b) { return a.isEmpty() && b.isEmpty() || !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a,b); }
    private static ItemStack one(ItemStack stack) { if (stack.isEmpty()) return ItemStack.EMPTY; ItemStack copy=stack.copy(); copy.setCount(1); return copy; }
    public void setTemperature(int value) { temperature=Math.max(0,Math.min(100,value)); setChanged(); }
    public static boolean accepts(int slot,ItemStack stack) { return switch(slot) {
        case 0 -> validSyringe(stack); case 1 -> CultivatorNutrition.get(stack)!=null; case 2 -> stack.is(Items.WATER_BUCKET); default -> false;
    }; }
    @Override public int getContainerSize() { return 4; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int count) { ItemStack removed=ContainerHelper.removeItem(items,slot,count); if (!removed.isEmpty()) setChanged(); return removed; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items,slot); }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); if (stack.getCount()>getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return level!=null && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64; }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return accepts(slot,stack); }
    @Override public int[] getSlotsForFace(Direction side) { return side==Direction.DOWN ? OUTPUTS : INPUTS; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) {
        return slot==0 ? stack.is(HatcheryRegistry.hatchedEgg(Species.COELACANTH)) : (slot==1 || slot==3) && stack.is(Items.BUCKET);
    }
    @Override public Component getDisplayName() { return customName!=null ? customName : Component.translatable("container.jurassicraft.cultivator"); }
    public void setCustomName(Component name) { customName=name; setChanged(); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new CultivatorMenu(id,inventory,this,data); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag,items);
        tag.putInt("Progress",progress); tag.putInt("Water",water); tag.putInt("Proximates",proximates);
        tag.putInt("Minerals",minerals); tag.putInt("Vitamins",vitamins); tag.putInt("Lipids",lipids); tag.putInt("Temperature",temperature);
        if (!active.isEmpty()) tag.put("Active",active.save(new CompoundTag()));
        if (customName!=null) tag.putString("CustomName",Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items=NonNullList.withSize(4,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,items);
        progress=Math.max(0,Math.min(TICKS-1,tag.getInt("Progress"))); water=Math.max(0,Math.min(2,tag.getInt("Water")));
        proximates=nutrient(tag,"Proximates"); minerals=nutrient(tag,"Minerals"); vitamins=nutrient(tag,"Vitamins"); lipids=nutrient(tag,"Lipids");
        temperature=Math.max(0,Math.min(100,tag.getInt("Temperature")));
        active=tag.contains("Active",10) ? ItemStack.of(tag.getCompound("Active")) : ItemStack.EMPTY;
        customName=tag.contains("CustomName",8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }
    private static int nutrient(CompoundTag tag,String key) { return Math.max(0,Math.min(MAX_NUTRIENTS,tag.getInt(key))); }
    private void createCapabilities() {
        input=LazyOptional.of(() -> new SidedInvWrapper(this,Direction.UP));
        output=LazyOptional.of(() -> new SidedInvWrapper(this,Direction.DOWN));
        all=LazyOptional.of(() -> new CombinedInvWrapper(new SidedInvWrapper(this,Direction.UP),new SidedInvWrapper(this,Direction.DOWN)));
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        if (capability==ForgeCapabilities.ITEM_HANDLER) return (side==null ? all : side==Direction.DOWN ? output : input).cast();
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); input.invalidate(); output.invalidate(); all.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); createCapabilities(); }
}
