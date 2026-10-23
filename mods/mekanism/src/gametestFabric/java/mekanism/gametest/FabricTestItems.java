/*
 * Mekanism 1.19.2 port - development-only Fabric GameTest fixtures.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ports.mekanism.fabric.api.ItemStorageLookup;
import ports.mekanism.fabric.api.MekanismChemicalLookups;

/**
 * Items another Fabric mod could ship, registered under this test mod's namespace so Mekanism treats them as foreign.
 * The gas tank answers on {@link MekanismChemicalLookups#GAS_ITEM} and the pouch on {@link ItemStorageLookup#ITEM}, both
 * keeping their contents in the stack's NBT. The wrench and the material carry only the c: tags in this mod's data.
 * None of them ship.
 */
public final class FabricTestItems {

    public static final long GAS_CAPACITY = 1_000;
    public static final long POUCH_CAPACITY = 64;
    private static final String GAS_KEY = "ForeignGas";
    private static final String POUCH_ITEM = "PouchItem";
    private static final String POUCH_COUNT = "PouchCount";

    public static Item FOREIGN_GAS_TANK;
    public static Item FOREIGN_POUCH;
    public static Item FOREIGN_WRENCH;
    public static Item FOREIGN_MATERIAL;

    private FabricTestItems() {
    }

    static void register() {
        FOREIGN_GAS_TANK = Registry.register(Registry.ITEM, id("foreign_gas_tank"), new Item(new Item.Properties().stacksTo(1)));
        FOREIGN_POUCH = Registry.register(Registry.ITEM, id("foreign_pouch"), new Item(new Item.Properties().stacksTo(1)));
        FOREIGN_WRENCH = Registry.register(Registry.ITEM, id("foreign_wrench"), new Item(new Item.Properties().stacksTo(1)));
        FOREIGN_MATERIAL = Registry.register(Registry.ITEM, id("foreign_material"), new Item(new Item.Properties()));
        MekanismChemicalLookups.GAS_ITEM.registerForItems((stack, context) -> new ForeignGasTank(stack), FOREIGN_GAS_TANK);
        ItemStorageLookup.ITEM.registerForItems((stack, context) -> new PouchStorage(context), FOREIGN_POUCH);
    }

    /**
     * The gas stored in a foreign gas tank's NBT, read without going through any lookup.
     */
    public static GasStack storedGas(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(GAS_KEY);
        return tag == null ? GasStack.EMPTY : GasStack.readFromNBT(tag);
    }

    /**
     * The items stored in a pouch's NBT, read without going through any lookup.
     */
    public static ItemStack pouchContents(ItemStack pouch) {
        CompoundTag tag = pouch.getTag();
        if (tag == null || !tag.contains(POUCH_ITEM)) {
            return ItemStack.EMPTY;
        }
        return ItemVariant.fromNbt(tag.getCompound(POUCH_ITEM)).toStack((int) tag.getLong(POUCH_COUNT));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("mekanism_gametest", path);
    }

    /**
     * One tank of any gas, written to the stack in place, as Mekanism's own item handlers do.
     */
    private record ForeignGasTank(ItemStack stack) implements IGasHandler {

        private void store(GasStack gas) {
            if (gas.isEmpty()) {
                stack.removeTagKey(GAS_KEY);
            } else {
                stack.addTagElement(GAS_KEY, gas.write(new CompoundTag()));
            }
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getChemicalInTank(int tank) {
            return tank == 0 ? storedGas(stack) : GasStack.EMPTY;
        }

        @Override
        public void setChemicalInTank(int tank, @NotNull GasStack gas) {
            if (tank == 0) {
                store(gas);
            }
        }

        @Override
        public long getTankCapacity(int tank) {
            return tank == 0 ? GAS_CAPACITY : 0;
        }

        @Override
        public boolean isValid(int tank, @NotNull GasStack gas) {
            return tank == 0;
        }

        @NotNull
        @Override
        public GasStack insertChemical(int tank, @NotNull GasStack gas, @NotNull Action action) {
            GasStack stored = storedGas(stack);
            if (tank != 0 || gas.isEmpty() || (!stored.isEmpty() && !stored.isTypeEqual(gas))) {
                return gas;
            }
            long accepted = Math.min(gas.getAmount(), GAS_CAPACITY - stored.getAmount());
            if (accepted <= 0) {
                return gas;
            }
            if (action.execute()) {
                store(new GasStack(gas, stored.getAmount() + accepted));
            }
            return accepted == gas.getAmount() ? GasStack.EMPTY : new GasStack(gas, gas.getAmount() - accepted);
        }

        @NotNull
        @Override
        public GasStack extractChemical(int tank, long amount, @NotNull Action action) {
            GasStack stored = storedGas(stack);
            if (tank != 0 || amount <= 0 || stored.isEmpty()) {
                return GasStack.EMPTY;
            }
            long taken = Math.min(amount, stored.getAmount());
            if (action.execute()) {
                store(new GasStack(stored, stored.getAmount() - taken));
            }
            return new GasStack(stored, taken);
        }
    }

    /**
     * One slot of up to {@link #POUCH_CAPACITY} items, kept in the pouch's NBT and changed through the context.
     */
    private static final class PouchStorage extends SingleVariantItemStorage<ItemVariant> {

        private PouchStorage(ContainerItemContext context) {
            super(context);
        }

        @Override
        protected ItemVariant getBlankResource() {
            return ItemVariant.blank();
        }

        @Override
        protected ItemVariant getResource(ItemVariant currentVariant) {
            CompoundTag tag = currentVariant.getNbt();
            return tag != null && tag.contains(POUCH_ITEM) ? ItemVariant.fromNbt(tag.getCompound(POUCH_ITEM)) : ItemVariant.blank();
        }

        @Override
        protected long getAmount(ItemVariant currentVariant) {
            CompoundTag tag = currentVariant.getNbt();
            return tag == null ? 0 : tag.getLong(POUCH_COUNT);
        }

        @Override
        protected long getCapacity(ItemVariant variant) {
            return POUCH_CAPACITY;
        }

        @Override
        protected boolean canInsert(ItemVariant resource) {
            return !resource.isOf(FOREIGN_POUCH);
        }

        @Override
        protected ItemVariant getUpdatedVariant(ItemVariant currentVariant, ItemVariant newResource, long newAmount) {
            CompoundTag tag = currentVariant.copyNbt();
            if (tag == null) {
                tag = new CompoundTag();
            }
            if (newResource.isBlank() || newAmount <= 0) {
                tag.remove(POUCH_ITEM);
                tag.remove(POUCH_COUNT);
            } else {
                tag.put(POUCH_ITEM, newResource.toNbt());
                tag.putLong(POUCH_COUNT, newAmount);
            }
            return ItemVariant.of(currentVariant.getItem(), tag.isEmpty() ? null : tag);
        }
    }
}
