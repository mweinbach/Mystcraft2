/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.util.UUID;
import mekanism.api.MekanismAPI;
import mekanism.api.gear.IModule;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.item.gear.ItemAtomicDisassembler;
import mekanism.common.item.gear.ItemAtomicDisassembler.DisassemblerMode;
import mekanism.common.network.to_server.PacketModeChange;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Shift-scroll sends {@link PacketModeChange}. The server applies that shift to the stack in the named hand.
 */
public class ModeChangeGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void modeChangePacketStepsTheHeldItem(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack configurator = new ItemStack(MekanismItems.CONFIGURATOR);
        ItemStack disassembler = new ItemStack(MekanismItems.ATOMIC_DISASSEMBLER);
        ItemStack mekaTool = new ItemStack(MekanismItems.MEKA_TOOL);
        ((IModuleContainerItem) mekaTool.getItem()).addModule(mekaTool, MekanismModules.EXCAVATION_ESCALATION_UNIT.get());
        player.setItemSlot(EquipmentSlot.MAINHAND, configurator);

        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_ITEMS, "The configurator did not start on items");
        apply(player, EquipmentSlot.MAINHAND, 0);
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_ITEMS, "A zero shift changed the configurator");
        apply(player, EquipmentSlot.MAINHAND, 1);
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_FLUIDS, "Scrolling forward left the configurator on " + configuratorMode(player));
        apply(player, EquipmentSlot.MAINHAND, -1);
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_ITEMS, "Scrolling back left the configurator on " + configuratorMode(player));
        apply(player, EquipmentSlot.MAINHAND, -1);
        check(configuratorMode(player) == ConfiguratorMode.WRENCH, "Scrolling past the first mode left the configurator on " + configuratorMode(player));

        player.setItemSlot(EquipmentSlot.MAINHAND, disassembler);
        check(disassemblerMode(player) == DisassemblerMode.NORMAL, "The atomic disassembler did not start on normal");
        apply(player, EquipmentSlot.MAINHAND, 1);
        check(disassemblerMode(player) == DisassemblerMode.SLOW, "Scrolling forward left the atomic disassembler on " + disassemblerMode(player));
        apply(player, EquipmentSlot.MAINHAND, -1);
        check(disassemblerMode(player) == DisassemblerMode.NORMAL, "Scrolling back left the atomic disassembler on " + disassemblerMode(player));
        apply(player, EquipmentSlot.MAINHAND, -1);
        check(disassemblerMode(player) == DisassemblerMode.OFF, "Scrolling past normal left the atomic disassembler on " + disassemblerMode(player));

        player.setItemSlot(EquipmentSlot.MAINHAND, mekaTool);
        check(excavationEfficiency(player) == 16F, "The Meka-Tool excavation module did not start at normal efficiency");
        apply(player, EquipmentSlot.MAINHAND, -1);
        check(excavationEfficiency(player) == 4F, "Scrolling back left the Meka-Tool at efficiency " + excavationEfficiency(player));
        apply(player, EquipmentSlot.MAINHAND, 1);
        check(excavationEfficiency(player) == 16F, "Scrolling forward left the Meka-Tool at efficiency " + excavationEfficiency(player));

        ItemStack bareTool = new ItemStack(MekanismItems.MEKA_TOOL);
        player.setItemSlot(EquipmentSlot.MAINHAND, bareTool);
        apply(player, EquipmentSlot.MAINHAND, 1);
        check(MekanismAPI.getModuleHelper().load(player.getItemBySlot(EquipmentSlot.MAINHAND), MekanismModules.EXCAVATION_ESCALATION_UNIT) == null,
              "A Meka-Tool with no module gained an excavation mode");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        apply(player, EquipmentSlot.MAINHAND, 1);
        ItemStack stick = player.getItemBySlot(EquipmentSlot.MAINHAND);
        check(stick.is(Items.STICK) && stick.getCount() == 1 && !stick.hasTag(), "A plain item was changed by the mode packet");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MekanismItems.CONFIGURATOR));
        applyWithoutSender();
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_ITEMS, "A packet with no sender changed the configurator");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void modeChangePacketStaysInTheNamedHand(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MekanismItems.CONFIGURATOR));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MekanismItems.ATOMIC_DISASSEMBLER));
        apply(player, EquipmentSlot.MAINHAND, 1);
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_FLUIDS, "The main hand did not change");
        check(disassemblerMode(player, EquipmentSlot.OFFHAND) == DisassemblerMode.NORMAL, "The off hand changed with the main-hand packet");
        apply(player, EquipmentSlot.OFFHAND, 1);
        check(configuratorMode(player) == ConfiguratorMode.CONFIGURATE_FLUIDS, "The main hand changed with the off-hand packet");
        check(disassemblerMode(player, EquipmentSlot.OFFHAND) == DisassemblerMode.SLOW, "The off hand did not change");
        helper.succeed();
    }

    private static void apply(ServerPlayer player, EquipmentSlot slot, int shift) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new PacketModeChange(slot, shift).encode(buffer);
        PacketModeChange decoded = PacketModeChange.decode(buffer);
        check(buffer.readableBytes() == 0, "The mode packet left unread bytes");
        decoded.handle(context(player));
    }

    private static void applyWithoutSender() {
        new PacketModeChange(EquipmentSlot.MAINHAND, 1).handle(context(null));
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
                    Connection connection = sender == null ? new Connection(PacketFlow.SERVERBOUND) : sender.connection.connection;
                    return (NetworkEvent.Context) constructor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new GameTestAssertException("Could not build a mode-packet context: " + e.getMessage());
            }
        }
        throw new GameTestAssertException("No mode-packet context constructor");
    }

    private static ConfiguratorMode configuratorMode(ServerPlayer player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        check(stack.getItem() instanceof ItemConfigurator, "The main hand is not a configurator");
        return ((ItemConfigurator) stack.getItem()).getMode(stack);
    }

    private static DisassemblerMode disassemblerMode(ServerPlayer player) {
        return disassemblerMode(player, EquipmentSlot.MAINHAND);
    }

    private static DisassemblerMode disassemblerMode(ServerPlayer player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        check(stack.getItem() instanceof ItemAtomicDisassembler, slot + " is not an atomic disassembler");
        return ((ItemAtomicDisassembler) stack.getItem()).getMode(stack);
    }

    private static float excavationEfficiency(ServerPlayer player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        IModule<ModuleExcavationEscalationUnit> module = MekanismAPI.getModuleHelper().load(stack, MekanismModules.EXCAVATION_ESCALATION_UNIT);
        check(module != null, "The Meka-Tool has no excavation module");
        return module.getCustomInstance().getEfficiency();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "mode-change"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        check(player.connection != null && player.connection.connection != null, "The player has no connection");
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
