package net.mystcraft.minecraft.ink;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.MystcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Classic virtual result: a basin and paper are consumed only when the result is taken. */
public final class InkMixerBlockEntity extends BlockEntity implements MenuProvider {
    private boolean ink;
    private final Map<String, Float> probabilities = new HashMap<>();
    private long nextSeed = ThreadLocalRandom.current().nextLong();
    public final SimpleContainer inventory = new SimpleContainer(4) {
        @Override public void setChanged() { super.setChanged(); InkMixerBlockEntity.this.setChanged(); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0 ? net.mystcraft.minecraft.fluid.InkContainerAccess.isInkContainer(stack) : slot == 1 && stack.is(Items.PAPER);
        }
    };
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            if (index == 0) return ink ? 1 : 0;
            return index > 0 && index <= InkAdditives.PROPERTIES.size() ? Math.round(probabilities.getOrDefault(InkAdditives.PROPERTIES.get(index - 1), 0F) * 10000) : 0;
        }
        @Override public void set(int index, int value) {
            if (index == 0) { ink = value != 0; if (!ink) probabilities.clear(); }
        }
        @Override public int getCount() { return 1 + InkAdditives.PROPERTIES.size(); }
    };

    public InkMixerBlockEntity(BlockPos pos, BlockState state) {
        super(InkMixerRegistry.INK_MIXER_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InkMixerBlockEntity mixer) {
        if (level.isClientSide) return;
        mixer.process();
    }

    private void process() {
        ItemStack vial = inventory.getItem(0);
        ItemStack bottle = inventory.getItem(2);
        if(ink)return;
        var transfer=net.mystcraft.minecraft.fluid.InkContainerAccess.drainOne(vial,1000,true);
        if(transfer.isEmpty()||!net.mystcraft.minecraft.fluid.InkContainerAccess.fits(bottle,transfer.get().remainder()))return;
        vial.shrink(1);inventory.setItem(2,net.mystcraft.minecraft.fluid.InkContainerAccess.merge(bottle,transfer.get().remainder()));ink=true;setChanged();
    }

    /** Preview never consumes paper, changes the random seed, or modifies the mixture. */
    public ItemStack preview() {
        if (!inventory.getItem(3).isEmpty()) return inventory.getItem(3).copy();
        if (!ink || !inventory.getItem(1).is(Items.PAPER)) return ItemStack.EMPTY;
        ItemStack panel = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        InkAdditives.apply(panel, probabilities, nextSeed);
        return panel;
    }

    public ItemStack takePanel(int amount) {
        if (amount <= 0 || level == null || level.isClientSide) return ItemStack.EMPTY;
        // Keep already-paid output from development saves/recovery transactions; never charge twice.
        if (!inventory.getItem(3).isEmpty()) return inventory.removeItem(3, amount);
        if (!ink || !inventory.getItem(1).is(Items.PAPER)) return ItemStack.EMPTY;
        ItemStack panel = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        nextSeed = InkAdditives.apply(panel, probabilities, nextSeed);
        inventory.getItem(1).shrink(1);
        ink = false;
        probabilities.clear();
        setChanged();
        return panel;
    }

    public Map<String, Float> probabilities() { return Map.copyOf(probabilities); }

    /** The classic basin consumes held additives, with right click using only one. No remainders. */
    public int addItems(ItemStack held, boolean single) {
        if (level == null || level.isClientSide || !ink || held.isEmpty()) return 0;
        Map<String, Float> effects = InkAdditives.effects(held);
        if (effects.isEmpty()) return 0;
        int count = single ? 1 : held.getCount();
        for (int i = 0; i < count; i++) InkAdditives.mix(probabilities, effects);
        held.shrink(count);
        setChanged();
        return count;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level, worldPosition, inventory);
        inventory.clearContent();
        ink = false;
        probabilities.clear();
        setChanged();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.mystcraft.ink_mixer"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new InkMixerMenu(id, playerInventory, inventory, data, worldPosition);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Ink", ink);
        tag.put("Probabilities", InkAdditives.save(probabilities));
        tag.putLong("NextSeed", nextSeed);
        NonNullList<ItemStack> stacks = NonNullList.withSize(4, ItemStack.EMPTY);
        for (int i = 0; i < 4; i++) stacks.set(i, inventory.getItem(i));
        ContainerHelper.saveAllItems(tag, stacks);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        ink = tag.getBoolean("Ink");
        probabilities.clear();
        if (ink) probabilities.putAll(InkAdditives.load(tag.getCompound("Probabilities")));
        if (tag.contains("NextSeed")) nextSeed = tag.getLong("NextSeed");
        NonNullList<ItemStack> stacks = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, stacks);
        for (int i = 0; i < 4; i++) inventory.setItem(i, stacks.get(i));
        // Migrate early development interaction-only mixer inventory.
        if (tag.contains("Paper") && inventory.getItem(1).isEmpty()) {
            ItemStack paper = ItemStack.of(tag.getCompound("Paper"));
            if (paper.is(Items.PAPER)) inventory.setItem(1, paper);
        }
    }
}
