/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mekanism.api.RelativeSide;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.FloatingLong;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.item.gear.ItemAtomicDisassembler;
import mekanism.common.item.gear.ItemAtomicDisassembler.DisassemblerMode;
import mekanism.common.item.gear.ItemFlamethrower;
import mekanism.common.item.gear.ItemFlamethrower.FlamethrowerMode;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.network.to_server.PacketDropperUse;
import mekanism.common.network.to_server.PacketDropperUse.DropperAction;
import mekanism.common.network.to_server.PacketDropperUse.TankType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.resource.ResourceType;
import mekanism.common.resource.ore.OreType;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Hand tools do the work upstream gives them. Module ticking is a later slice: a bare Meka-Tool still mines.
 */
public class HandToolGameTests {

    private static final String TEMPLATE = "mekanism:empty";
    private static final int DROPPER_FLUID = 1_000;

    @GameTest(template = TEMPLATE)
    public static void configuratorChangesAMachineSide(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MekanismBlocks.ENRICHMENT_CHAMBER.getBlock());
        TileEntityConfigurableMachine machine = (TileEntityConfigurableMachine) helper.getBlockEntity(pos);
        ConfigInfo items = machine.getConfig().getConfig(TransmissionType.ITEM);
        Direction facing = machine.getDirection();
        RelativeSide top = RelativeSide.fromDirections(facing, Direction.UP);
        ServerPlayer player = survival(helper, new BlockPos(2, 3, 1), "configurator");
        ItemStack configurator = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.CONFIGURATOR.get()),
              MekanismConfig.gear.configuratorMaxEnergy);
        player.setItemInHand(InteractionHand.MAIN_HAND, configurator);
        FloatingLong charged = energy(configurator);

        check(items.getDataType(top) == DataType.INPUT, "The enrichment chamber's top started as " + items.getDataType(top));
        player.setShiftKeyDown(false);
        InteractionResult viewed = use(helper, player, pos, Direction.UP);
        check(viewed.consumesAction(), "Looking at the side did not use the configurator: " + viewed);
        check(items.getDataType(top) == DataType.INPUT, "Viewing the side changed it to " + items.getDataType(top));
        check(energy(player.getMainHandItem()).equals(charged), "Viewing a side spent energy");

        player.setShiftKeyDown(true);
        DataType next = DataType.INPUT.getNext(items.getSupportedDataTypes()::contains);
        InteractionResult changed = use(helper, player, pos, Direction.UP);
        check(changed.consumesAction(), "Configuring the side did not use the configurator: " + changed);
        FloatingLong spent = charged.subtract(energy(player.getMainHandItem()));
        helper.succeedWhen(() -> {
            check(items.getDataType(top) == next, "The top stayed " + items.getDataType(top) + " instead of " + next);
            check(spent.equals(MekanismConfig.gear.configuratorEnergyPerConfigure.get()), "Configuring spent " + spent);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void gaugeDropperFillsAndEmptiesATank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
        TileEntityFluidTank tank = (TileEntityFluidTank) helper.getBlockEntity(pos);
        TestPlatform.fillBlockFluid(tank, Fluids.WATER, DROPPER_FLUID);
        ServerPlayer player = survival(helper, new BlockPos(2, 3, 1), "dropper");
        ItemStack dropper = new ItemStack(MekanismItems.GAUGE_DROPPER.get());
        int accepted = FluidUtil.getFluidHandler(dropper).map(handler -> handler.fill(FluidStack.EMPTY, FluidAction.SIMULATE)).orElse(-1);
        check(accepted == 0, "An empty gauge dropper simulated a fill of " + accepted);
        player.containerMenu.setCarried(dropper);

        applyDropper(player, helper.absolutePos(pos), DropperAction.FILL_DROPPER);
        check(tank.fluidTank.isEmpty(), "Filling the dropper left " + tank.fluidTank.getFluidAmount() + " in the tank");
        check(fluid(player.containerMenu.getCarried()) == DROPPER_FLUID, "The dropper holds " + fluid(player.containerMenu.getCarried()));

        applyDropper(player, helper.absolutePos(pos), DropperAction.DRAIN_DROPPER);
        check(tank.fluidTank.getFluidAmount() == DROPPER_FLUID && tank.fluidTank.getFluid().getFluid() == Fluids.WATER,
              "Draining the dropper left " + tank.fluidTank.getFluid());
        check(fluid(player.containerMenu.getCarried()) == 0, "The dropper still holds " + fluid(player.containerMenu.getCarried()));

        applyDropper(player, helper.absolutePos(pos), DropperAction.DUMP_TANK);
        helper.succeedWhen(() -> {
            check(tank.fluidTank.isEmpty(), "Dumping left " + tank.fluidTank.getFluidAmount() + " in the tank");
            check(fluid(player.containerMenu.getCarried()) == 0, "Dumping the tank filled the dropper");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void atomicDisassemblerVeinsAnOre(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 2, 2);
        BlockPos west = new BlockPos(2, 2, 2);
        BlockPos east = new BlockPos(4, 2, 2);
        BlockPos beside = new BlockPos(3, 2, 3);
        helper.setBlock(west, MekanismBlocks.ORES.get(OreType.OSMIUM).stone().getBlock());
        helper.setBlock(center, MekanismBlocks.ORES.get(OreType.OSMIUM).stone().getBlock());
        helper.setBlock(east, MekanismBlocks.ORES.get(OreType.OSMIUM).stone().getBlock());
        helper.setBlock(beside, Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(3, 4, 2), "disassembler");
        ItemStack disassembler = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.ATOMIC_DISASSEMBLER.get()),
              MekanismConfig.gear.disassemblerMaxEnergy);
        boolean previous = MekanismConfig.gear.disassemblerVeinMining.get();
        MekanismConfig.gear.disassemblerVeinMining.set(true);
        try {
            ((ItemAtomicDisassembler) disassembler.getItem()).setMode(disassembler, player, DisassemblerMode.VEIN);
            check(((ItemAtomicDisassembler) disassembler.getItem()).getMode(disassembler) == DisassemblerMode.VEIN,
                  "Vein mode stayed disabled");
            player.setItemInHand(InteractionHand.MAIN_HAND, disassembler);
            check(player.gameMode.destroyBlock(helper.absolutePos(center)), "The atomic disassembler did not break the ore");
        } finally {
            MekanismConfig.gear.disassemblerVeinMining.set(previous);
        }
        Item rawOsmium = MekanismItems.PROCESSED_RESOURCES.get(ResourceType.RAW, PrimaryResource.OSMIUM).get();
        helper.succeedWhen(() -> {
            check(helper.getBlockState(center).isAir(), "The mined ore is still there");
            check(helper.getBlockState(west).isAir(), "Vein mining left the west ore");
            check(helper.getBlockState(east).isAir(), "Vein mining left the east ore");
            check(helper.getBlockState(beside).is(Blocks.STONE), "Vein mining broke the stone beside the vein");
            AABB box = new AABB(helper.absolutePos(west)).minmax(new AABB(helper.absolutePos(east))).inflate(1);
            int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                  .filter(entity -> entity.getItem().is(rawOsmium) && !entity.isRemoved())
                  .mapToInt(entity -> entity.getItem().getCount()).sum();
            check(dropped >= 3, "The vein dropped " + dropped + " raw osmium");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void flamethrowerSmeltsADroppedItem(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(2, 2, 2), "flamethrower");
        player.setYRot(0);
        player.setXRot(0);
        ItemStack flamethrower = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.FLAMETHROWER.get()),
              MekanismConfig.gear.flamethrowerMaxGas, MekanismGases.HYDROGEN);
        ((ItemFlamethrower) flamethrower.getItem()).setMode(flamethrower, FlamethrowerMode.HEAT);
        player.setItemInHand(InteractionHand.MAIN_HAND, flamethrower);
        long hydrogen = gas(player.getMainHandItem());
        check(hydrogen > 1, "The flamethrower has no hydrogen");
        // The flame spawns just below eye level and flies along the look vector. The item's box has to contain that ray.
        Vec3 spot = player.getEyePosition().add(player.getLookAngle().scale(2)).add(0, -0.25, 0);
        ItemEntity dropped = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setNoGravity(true);
        dropped.setPickUpDelay(Short.MAX_VALUE);
        dropped.tickCount = 101;
        check(helper.getLevel().addFreshEntity(dropped), "The cobblestone did not spawn");
        Mekanism.playerState.setFlamethrowerState(player.getUUID(), true, false);
        boolean[] fired = {false};
        helper.succeedWhen(() -> {
            if (!fired[0]) {
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                fired[0] = true;
                check(false, "Waiting for the flame to reach the cobblestone");
            }
            check(dropped.getItem().is(Items.STONE), "The dropped item is " + dropped.getItem());
            check(gas(player.getMainHandItem()) < hydrogen, "The flamethrower did not spend hydrogen");
            Mekanism.playerState.setFlamethrowerState(player.getUUID(), false, false);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void bareMekaToolMines(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(2, 3, 1), "meka-tool");
        ItemStack tool = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismConfig.gear.mekaToolBaseEnergyCapacity);
        check(((IModuleContainerItem) tool.getItem()).getModules(tool).isEmpty(), "The bare Meka-Tool had modules");
        check(((ItemMekaTool) tool.getItem()).canPerformAction(tool, ToolActions.PICKAXE_DIG), "The bare Meka-Tool cannot dig like a pickaxe");
        check(!((ItemMekaTool) tool.getItem()).canPerformAction(tool, ToolActions.AXE_STRIP), "The bare Meka-Tool strips logs");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        FloatingLong charged = energy(tool);
        check(player.gameMode.destroyBlock(helper.absolutePos(pos)), "The Meka-Tool did not break the stone");
        FloatingLong spent = charged.subtract(energy(player.getMainHandItem()));
        FloatingLong expected = MekanismConfig.gear.mekaToolEnergyUsage.get().multiply(MekanismConfig.gear.mekaToolBaseEfficiency.get());
        helper.succeedWhen(() -> {
            check(helper.getBlockState(pos).isAir(), "The stone is still there");
            check(spent.equals(expected), "The Meka-Tool spent " + spent + " instead of " + expected);
            AABB box = new AABB(helper.absolutePos(pos)).inflate(2);
            int cobble = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                  .filter(entity -> entity.getItem().is(Items.COBBLESTONE) && !entity.isRemoved())
                  .mapToInt(entity -> entity.getItem().getCount()).sum();
            check(cobble >= 1, "The stone dropped " + cobble + " cobblestone");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void networkReaderReportsACable(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2);
        BlockPos second = new BlockPos(3, 2, 2);
        helper.setBlock(first, MekanismBlocks.BASIC_UNIVERSAL_CABLE.getBlock());
        helper.setBlock(second, MekanismBlocks.BASIC_UNIVERSAL_CABLE.getBlock());
        ListeningPlayer player = listening(helper, new BlockPos(2, 3, 1));
        ItemStack reader = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.NETWORK_READER.get()),
              MekanismConfig.gear.networkReaderMaxEnergy);
        player.setItemInHand(InteractionHand.MAIN_HAND, reader);
        FloatingLong charged = energy(reader);
        boolean[] read = {false};
        helper.succeedWhen(() -> {
            TileEntityTransmitter cable = (TileEntityTransmitter) helper.getBlockEntity(first);
            check(cable.getTransmitter().hasTransmitterNetwork(), "The cable has no network yet");
            check(cable.getTransmitter().getTransmitterNetwork().transmittersSize() == 2,
                  "The cable network has " + cable.getTransmitter().getTransmitterNetwork().transmittersSize() + " transmitters");
            if (!read[0]) {
                InteractionResult result = use(helper, player, first, Direction.UP);
                check(result.consumesAction(), "The network reader did not read the cable: " + result);
                read[0] = true;
            }
            String text = String.join("\n", player.messages).toLowerCase(Locale.ROOT);
            check(text.contains("transmitter"), "The network reader said: " + text);
            check(charged.subtract(energy(player.getMainHandItem())).equals(MekanismConfig.gear.networkReaderEnergyUsage.get()),
                  "The network reader spent " + charged.subtract(energy(player.getMainHandItem())));
        });
    }

    private static void applyDropper(ServerPlayer player, BlockPos absolute, DropperAction action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new PacketDropperUse(absolute, action, TankType.FLUID_TANK, 0).encode(buffer);
        PacketDropperUse decoded = PacketDropperUse.decode(buffer);
        check(buffer.readableBytes() == 0, "The dropper packet left unread bytes");
        decoded.handle(context(player));
    }

    private static NetworkEvent.Context context(ServerPlayer sender) {
        Class<?> type = NetworkEvent.Context.class;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            try {
                if (params.length == 2 && params[0] == NetworkDirection.class && params[1] == ServerPlayer.class) {
                    constructor.setAccessible(true);
                    return (NetworkEvent.Context) constructor.newInstance(NetworkDirection.PLAY_TO_SERVER, sender);
                }
                if (params.length == 3 && params[0] == Connection.class && params[1] == NetworkDirection.class && params[2] == int.class) {
                    constructor.setAccessible(true);
                    return (NetworkEvent.Context) constructor.newInstance(sender.connection.connection, NetworkDirection.PLAY_TO_SERVER, 0);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new GameTestAssertException("Could not build a dropper-packet context: " + e.getMessage());
            }
        }
        throw new GameTestAssertException("No dropper-packet context constructor");
    }

    private static int fluid(ItemStack stack) {
        return FluidUtil.getFluidHandler(stack).map(handler -> handler.getFluidInTank(0).getAmount()).orElse(0);
    }

    private static long gas(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).resolve()
              .map(handler -> handler.getTanks() > 0 ? handler.getChemicalInTank(0).getAmount() : 0L)
              .orElse(-1L);
    }

    private static FloatingLong energy(ItemStack stack) {
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        check(container != null, "The tool has no energy container");
        return container.getEnergy().copy();
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name), null);
        connect(helper, player, relative);
        return player;
    }

    private static ListeningPlayer listening(GameTestHelper helper, BlockPos relative) {
        ListeningPlayer player = new ListeningPlayer(helper, new GameProfile(UUID.randomUUID(), "network-reader"));
        connect(helper, player, relative);
        return player;
    }

    private static void connect(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class ListeningPlayer extends ServerPlayer {

        private final List<String> messages = new ArrayList<>();

        private ListeningPlayer(GameTestHelper helper, GameProfile profile) {
            super(helper.getLevel().getServer(), helper.getLevel(), profile, null);
        }

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component.getString());
        }
    }
}
