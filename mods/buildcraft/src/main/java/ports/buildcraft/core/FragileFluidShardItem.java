package ports.buildcraft.core;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Fluids2;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code ItemFragileFluidContainer}: up to half a bucket of the fluid a broken machine held. It can
 * only be emptied, and is used up once empty. It is not listed in the creative menu.
 */
public class FragileFluidShardItem extends Item {
    public static final int MAX_FLUID_HELD = 500;

    public FragileFluidShardItem(Properties properties) {
        super(properties);
    }

    public static FluidAmount getFluid(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("fluid");
        if (tag == null) return FluidAmount.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("FluidName"));
        return new FluidAmount(id == null ? Fluids.EMPTY : Registry.FLUID.get(id), tag.getInt("Amount"));
    }

    public static void setFluid(ItemStack stack, FluidAmount fluid) {
        if (fluid.isEmpty()) {
            stack.removeTagKey("fluid");
            return;
        }
        CompoundTag tag = stack.getOrCreateTagElement("fluid");
        tag.putString("FluidName", Registry.FLUID.getKey(Fluids2.still(fluid.fluid())).toString());
        tag.putInt("Amount", (int) fluid.amount());
    }

    public static ItemStack of(FluidAmount fluid) {
        ItemStack stack = new ItemStack(BCCore.FRAGILE_FLUID_SHARD.get());
        setFluid(stack, fluid);
        return stack;
    }

    /** Upstream {@code addFluidDrops}: full 500 mB shards, then one for the remainder. */
    public static void addFluidDrops(List<ItemStack> drops, FluidAmount fluid) {
        long amount = fluid.amount();
        for (; amount >= MAX_FLUID_HELD; amount -= MAX_FLUID_HELD) drops.add(of(fluid.withAmount(MAX_FLUID_HELD)));
        if (amount > 0) drops.add(of(fluid.withAmount(amount)));
    }

    /** Upstream {@code TankManager.addDrops} followed by {@code InventoryUtil.dropAll}. */
    public static void dropTanks(Level level, BlockPos pos, Tank... tanks) {
        for (Tank tank : tanks) dropFluid(level, pos, tank.contents());
    }

    public static void dropFluid(Level level, BlockPos pos, FluidAmount fluid) {
        if (fluid.isEmpty()) return;
        List<ItemStack> drops = new ArrayList<>();
        addFluidDrops(drops, fluid);
        for (ItemStack stack : drops) InventoryUtil.drop(level, pos, stack);
    }

    /**
     * Upstream {@code FragileFluidHandler.drain}: takes up to {@code max} mB of {@code filter} (any fluid when
     * null) out of the shard, which is emptied of its fluid tag when drained dry.
     */
    public static FluidAmount drain(ItemStack stack, @Nullable Fluid filter, long max, boolean simulate) {
        FluidAmount held = getFluid(stack);
        if (held.isEmpty() || max <= 0 || filter != null && Fluids2.still(filter) != held.fluid()) return FluidAmount.EMPTY;
        FluidAmount drained = held.withAmount(Math.min(max, held.amount()));
        if (!simulate) setFluid(stack, held.withAmount(held.amount() - drained.amount()));
        return drained;
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidAmount fluid = getFluid(stack);
        if (fluid.isEmpty()) return Component.translatable(getDescriptionId(stack), "ERROR! NULL FLUID!");
        if (fluid.fluid() instanceof BCFluid bc) {
            Component bare = Component.translatable("fluid_family.buildcraft." + bc.definition().family.name);
            return Component.translatable("buildcraft.fluid.heat_" + bc.definition().heat, Component.translatable(getDescriptionId(stack), bare));
        }
        return Component.translatable(getDescriptionId(stack), Platform.get().fluidName(fluid.fluid()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        FluidAmount fluid = getFluid(stack);
        if (!fluid.isEmpty()) tooltip.add(Component.translatable("gui.buildcraft.fluid_amount", fluid.amount(), MAX_FLUID_HELD));
    }
}
