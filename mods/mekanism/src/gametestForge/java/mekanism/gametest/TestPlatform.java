/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import it.unimi.dsi.fastutil.longs.LongSet;
import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * What the shared tests do through the loader's APIs: Forge Energy, Forge's item and fluid capabilities, and
 * Mekanism's chemical and heat capabilities. {@link #DROPLETS_PER_MB} is the Transfer API ratio the shared fluid
 * assertion uses; Forge itself counts millibuckets. Chemicals are Mekanism units.
 */
public final class TestPlatform {

    public static final long DROPLETS_PER_MB = 81;
    private static final String ANCHOR_TICKET = "forge:block";

    private TestPlatform() {
    }

    public static long insertEnergy(BlockEntity blockEntity, Direction side, long amount) {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY, side).map(storage -> storage.receiveEnergy((int) amount, false)).orElse(0);
    }

    public static long insertItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return insertThroughItemCapability(blockEntity, side, stack);
    }

    public static int insertThroughItemCapability(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .map(handler -> stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount())
              .orElse(0);
    }

    public static long insertItemEnergy(ItemStack stack, long amount, boolean commit) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(ForgeCapabilities.ENERGY)
              .map(storage -> (long) storage.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), !commit))
              .orElse(0L);
    }

    public static long extractItemEnergy(ItemStack stack, long amount, boolean commit) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(ForgeCapabilities.ENERGY)
              .map(storage -> (long) storage.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), !commit))
              .orElse(0L);
    }

    public static long itemEnergy(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> (long) storage.getEnergyStored()).orElse(0L);
    }

    public static long itemEnergyCapacity(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> (long) storage.getMaxEnergyStored()).orElse(0L);
    }

    public static long insertItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return fluidHandler(stack).map(handler -> {
            int moved = handler.fill(new FluidStack(fluid, (int) Math.min(milliBuckets, Integer.MAX_VALUE)), action(commit));
            keepStack(stack, handler);
            return (long) moved;
        }).orElse(0L);
    }

    public static long extractItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return fluidHandler(stack).map(handler -> {
            FluidStack drained = handler.drain(new FluidStack(fluid, (int) Math.min(milliBuckets, Integer.MAX_VALUE)), action(commit));
            keepStack(stack, handler);
            return drained.isEmpty() ? 0L : drained.getAmount();
        }).orElse(0L);
    }

    public static long itemFluidDroplets(ItemStack stack) {
        return fluidStored(stack) * DROPLETS_PER_MB;
    }

    public static long itemFluidCapacity(ItemStack stack) {
        return fluidHandler(stack).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getTankCapacity(tank);
            }
            return total;
        }).orElse(0L);
    }

    private static Optional<IFluidHandlerItem> fluidHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
    }

    private static long fluidStored(ItemStack stack) {
        return fluidHandler(stack).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getFluidInTank(tank).getAmount();
            }
            return total;
        }).orElse(0L);
    }

    /**
     * The teleporter's anchor upgrade has a saved non-ticking block ticket, and the chunk source is holding that ticket.
     */
    public static boolean anchorChunkLoaded(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        return savedAnchorTicket(level, owner, chunk)
              && ChunkLoadingGameTests.namesTicket(ChunkLoadingGameTests.chunkTicketDebug(level, chunk), ANCHOR_TICKET);
    }

    /**
     * Drops the vanilla tickets, then asks Forge to load the saved ones again. Null when the anchor ticket is back and
     * was gone in between.
     */
    public static String anchorChunkRestored(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        if (!anchorChunkLoaded(level, owner, chunk)) {
            return "Anchor ticket was not loaded before the restart: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk);
        }
        dropRuntimeTickets(level, chunk);
        if (ChunkLoadingGameTests.namesTicket(ChunkLoadingGameTests.chunkTicketDebug(level, chunk), ANCHOR_TICKET)) {
            return "Runtime chunk ticket was still present after it was dropped: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk);
        }
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
        if (data == null) {
            return "Saved chunk tickets were missing after the runtime ticket was dropped";
        }
        ForgeChunkManager.reinstatePersistentChunks(level, data);
        if (!anchorChunkLoaded(level, owner, chunk)) {
            return "Anchor ticket was not restored from the level save: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk);
        }
        return null;
    }

    private static boolean savedAnchorTicket(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
        if (data == null) {
            return false;
        }
        long id = chunk.toLong();
        for (Map.Entry<ForgeChunkManager.TicketOwner<BlockPos>, LongSet> entry : data.getBlockForcedChunks().getChunks().entrySet()) {
            if (entry.getValue().contains(id) && ownedBy(entry.getKey(), owner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownedBy(ForgeChunkManager.TicketOwner<?> ticket, BlockPos owner) {
        try {
            Field modId = ForgeChunkManager.TicketOwner.class.getDeclaredField("modId");
            Field owned = ForgeChunkManager.TicketOwner.class.getDeclaredField("owner");
            modId.setAccessible(true);
            owned.setAccessible(true);
            return Mekanism.MODID.equals(modId.get(ticket)) && owner.equals(owned.get(ticket));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void dropRuntimeTickets(ServerLevel level, ChunkPos chunk) {
        try {
            Field managerField = ServerChunkCache.class.getDeclaredField("distanceManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(level.getChunkSource());
            Field ticketsField = DistanceManager.class.getDeclaredField("tickets");
            ticketsField.setAccessible(true);
            Object map = ticketsField.get(manager);
            Object set = map.getClass().getMethod("get", long.class).invoke(map, chunk.toLong());
            List<Ticket<?>> matches = new ArrayList<>();
            if (set instanceof Iterable<?> tickets) {
                for (Object ticket : tickets) {
                    if (ticket instanceof Ticket<?> typed && ChunkLoadingGameTests.namesTicket(String.valueOf(typed.getType()), ANCHOR_TICKET)) {
                        matches.add(typed);
                    }
                }
            }
            Field keyField = Ticket.class.getDeclaredField("key");
            keyField.setAccessible(true);
            for (Ticket<?> ticket : matches) {
                int distance = 33 - ticket.getTicketLevel();
                Object key = keyField.get(ticket);
                boolean forceTicks = ticket.getClass().getMethod("isForceTicks").invoke(ticket) == Boolean.TRUE;
                level.getChunkSource().removeRegionTicket((TicketType) ticket.getType(), chunk, distance, key, forceTicks);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IFluidHandler.FluidAction action(boolean commit) {
        return commit ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
    }

    public static GasStack extractGas(BlockEntity blockEntity, Direction side, long amount, boolean execute) {
        return blockEntity.getCapability(Capabilities.GAS_HANDLER, side)
              .map(handler -> handler.extractChemical(amount, chemicalAction(execute)))
              .orElse(GasStack.EMPTY);
    }

    public static int gasTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.GAS_HANDLER, side).map(IGasHandler::getTanks).orElse(0);
    }

    public static int infusionTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.INFUSION_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int pigmentTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.PIGMENT_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int slurryTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.SLURRY_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int heatCapacitors(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.HEAT_HANDLER, side).map(IHeatHandler::getHeatCapacitorCount).orElse(0);
    }

    public static int itemGasTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).map(IGasHandler::getTanks).orElse(0);
    }

    public static int itemInfusionTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.INFUSION_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int itemPigmentTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.PIGMENT_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int itemSlurryTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.SLURRY_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static long insertItemGas(ItemStack stack, Gas gas, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(Capabilities.GAS_HANDLER).map(handler -> {
            GasStack remainder = handler.insertChemical(new GasStack(gas, amount), chemicalAction(execute));
            return amount - remainder.getAmount();
        }).orElse(0L);
    }

    public static long extractItemGas(ItemStack stack, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(Capabilities.GAS_HANDLER)
              .map(handler -> handler.extractChemical(amount, chemicalAction(execute)).getAmount())
              .orElse(0L);
    }

    public static long itemGas(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getChemicalInTank(tank).getAmount();
            }
            return total;
        }).orElse(0L);
    }

    private static Action chemicalAction(boolean execute) {
        return execute ? Action.EXECUTE : Action.SIMULATE;
    }

    public static void fillBlockFluid(BlockEntity blockEntity, Fluid fluid, int milliBuckets) {
        if (blockEntity instanceof TileEntityFluidTank tank) {
            tank.fluidTank.setStack(new FluidStack(fluid, milliBuckets));
        }
    }

    private static void keepStack(ItemStack stack, IFluidHandlerItem handler) {
        ItemStack container = handler.getContainer();
        if (container != stack && container.is(stack.getItem())) {
            stack.setTag(container.getTag());
            stack.setCount(container.getCount());
        }
    }
}
