/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import mekanism.common.Mekanism;
import mekanism.common.integration.curios.AccessoryInventory;
import mekanism.common.integration.curios.AccessoryItemHandler;
import mekanism.common.integration.curios.AccessoryResult;
import mekanism.common.integration.curios.CuriosIntegration;
import mekanism.common.integration.gender.GenderArmorSettings;
import mekanism.common.integration.gender.GenderCapabilityHelper;
import mekanism.common.item.gear.ItemJetpack;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.network.to_server.PacketModeChangeCurios;
import mekanism.common.registries.MekanismItems;
import mekanism.tools.common.integration.gender.ToolsGenderCapabilityHelper;
import mekanism.tools.common.registries.ToolsItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Accessory lookups run without Curios or Trinkets installed. A stand-in inventory is what those mods would return.
 * The Gender Mod flag stays false: loading the adapter needs that mod on the classpath.
 */
public class AccessoryGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void accessorySlotIsFoundByMekanismLookups(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        boolean curios = Mekanism.hooks.CuriosLoaded;
        ItemStack dashboard = new ItemStack(MekanismItems.PORTABLE_QIO_DASHBOARD);
        ItemStack chestJetpack = new ItemStack(MekanismItems.JETPACK);
        ItemStack wornJetpack = new ItemStack(MekanismItems.JETPACK);
        ((ItemJetpack) wornJetpack.getItem()).setMode(wornJetpack, JetpackMode.HOVER);
        try {
            CuriosIntegration.install(single("body", 0, dashboard));
            Mekanism.hooks.CuriosLoaded = false;
            check(CuriosIntegration.getCuriosInventory(player).isEmpty(), "A lookup ran with Curios and Trinkets absent");
            check(CuriosIntegration.findFirstCurio(player, stack -> true).isEmpty(), "findFirst ran with the mods absent");
            check(IJetpackItem.getPrimaryJetpack(player).isEmpty(), "An empty chest found a jetpack with the mods absent");

            Mekanism.hooks.CuriosLoaded = true;
            IItemHandler slots = CuriosIntegration.getCuriosInventory(player).orElse(null);
            check(slots != null && slots.getSlots() == 1 && slots.getStackInSlot(0) == dashboard,
                  "The portable QIO dashboard was not in the accessory inventory");
            Optional<AccessoryResult> found = CuriosIntegration.findFirstCurioAsResult(player,
                  stack -> stack.getItem() == MekanismItems.PORTABLE_QIO_DASHBOARD.asItem());
            check(found.isPresent() && found.get().slotType().equals("body") && found.get().index() == 0 && found.get().stack() == dashboard,
                  "The dashboard lookup did not report the body slot");

            CuriosIntegration.install(single("body", 0, wornJetpack));
            player.setItemSlot(EquipmentSlot.CHEST, chestJetpack);
            check(IJetpackItem.getPrimaryJetpack(player) == chestJetpack, "The chest jetpack lost to the accessory jetpack");
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ItemStack primary = IJetpackItem.getPrimaryJetpack(player);
            check(primary == wornJetpack, "The accessory jetpack was not the primary jetpack");
            check(((ItemJetpack) primary.getItem()).getJetpackMode(primary) == JetpackMode.HOVER,
                  "The accessory jetpack mode was " + ((ItemJetpack) primary.getItem()).getJetpackMode(primary));
            Mekanism.hooks.CuriosLoaded = false;
            check(IJetpackItem.getPrimaryJetpack(player).isEmpty(), "Clearing the flag still found the accessory jetpack");
            helper.succeed();
        } finally {
            restore(curios);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void accessoryModePacketChangesTheJetpack(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        boolean curios = Mekanism.hooks.CuriosLoaded;
        ItemStack jetpack = new ItemStack(MekanismItems.JETPACK);
        try {
            check(mode(jetpack) == JetpackMode.NORMAL, "The jetpack did not start on normal");
            CuriosIntegration.install(single("body", 0, jetpack));
            Mekanism.hooks.CuriosLoaded = true;
            apply(player, "body", 0, 1);
            check(mode(jetpack) == JetpackMode.HOVER, "The accessory mode packet left the jetpack on " + mode(jetpack));

            Mekanism.hooks.CuriosLoaded = false;
            apply(player, "body", 0, 1);
            check(mode(jetpack) == JetpackMode.HOVER, "A packet with the mods absent changed the jetpack");
            new PacketModeChangeCurios("body", 0, 1).handle(context(null));
            check(mode(jetpack) == JetpackMode.HOVER, "A packet with no sender changed the jetpack");
            helper.succeed();
        } finally {
            restore(curios);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void genderSettingsMatchUpstream(GameTestHelper helper) {
        boolean gender = Mekanism.hooks.WildfireGenderModLoaded;
        try {
            Mekanism.hooks.WildfireGenderModLoaded = false;
            check(GenderArmorSettings.OPEN_FRONT.equals(new GenderArmorSettings(false, false, 0, 0)), "Open-front settings changed");
            check(GenderArmorSettings.HIDES_BREASTS.equals(new GenderArmorSettings(true, true, 0, 0)), "Hides-breasts settings changed");
            check(covers(GenderArmorSettings.HAZMAT, 0.5F, 0.25F), "Hazmat settings changed");
            check(covers(GenderArmorSettings.BRONZE, 0.9F, 0), "Bronze settings changed");
            check(covers(GenderArmorSettings.LAPIS_LAZULI, 0.6F, 0.1F), "Lapis lazuli settings changed");
            check(covers(GenderArmorSettings.OSMIUM, 1, 0), "Osmium settings changed");
            check(covers(GenderArmorSettings.REFINED_GLOWSTONE, 0.95F, 0), "Refined glowstone settings changed");
            check(covers(GenderArmorSettings.REFINED_OBSIDIAN, 1, 0), "Refined obsidian settings changed");
            check(covers(GenderArmorSettings.STEEL, 1, 0), "Steel settings changed");
            boolean rejected = false;
            try {
                new GenderArmorSettings(true, false, 1.1F, 0);
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            check(rejected, "A resistance above one was accepted");

            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.HAZMAT_GOWN.asItem())) == GenderArmorSettings.HAZMAT, "Hazmat gown settings");
            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.JETPACK.asItem())) == GenderArmorSettings.OPEN_FRONT, "Jetpack settings");
            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.SCUBA_TANK.asItem())) == GenderArmorSettings.OPEN_FRONT, "Scuba tank settings");
            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.ARMORED_JETPACK.asItem())) == GenderArmorSettings.HIDES_BREASTS, "Armored jetpack settings");
            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.MEKASUIT_BODYARMOR.asItem())) == GenderArmorSettings.HIDES_BREASTS, "MekaSuit bodyarmor settings");
            check(GenderCapabilityHelper.coreChest(armor(MekanismItems.MEKASUIT_HELMET.asItem())) == null, "A helmet was given chest settings");

            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.BRONZE_CHESTPLATE.asItem()) == GenderArmorSettings.BRONZE, "Bronze chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.LAPIS_LAZULI_CHESTPLATE.asItem()) == GenderArmorSettings.LAPIS_LAZULI, "Lapis chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.OSMIUM_CHESTPLATE.asItem()) == GenderArmorSettings.OSMIUM, "Osmium chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.REFINED_GLOWSTONE_CHESTPLATE.asItem()) == GenderArmorSettings.REFINED_GLOWSTONE, "Glowstone chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.REFINED_OBSIDIAN_CHESTPLATE.asItem()) == GenderArmorSettings.REFINED_OBSIDIAN, "Obsidian chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.STEEL_CHESTPLATE.asItem()) == GenderArmorSettings.STEEL, "Steel chestplate");
            check(ToolsGenderCapabilityHelper.chestSettings(ToolsItems.BRONZE_HELMET.asItem()) == null, "A tool helmet was given chest settings");

            boolean[] called = {false};
            GenderCapabilityHelper.addGenderCapability(armor(MekanismItems.HAZMAT_GOWN.asItem()), capability -> called[0] = true);
            check(!called[0], "Gender capability was attached while the mod was absent");
            helper.succeed();
        } finally {
            Mekanism.hooks.WildfireGenderModLoaded = gender;
        }
    }

    private static void restore(boolean curiosLoaded) {
        Mekanism.hooks.CuriosLoaded = curiosLoaded;
        if (curiosLoaded) {
            CuriosIntegration.installNative();
        } else {
            CuriosIntegration.install(AccessoryInventory.NONE);
        }
    }

    private static boolean covers(GenderArmorSettings settings, float resistance, float tightness) {
        return settings.coversBreasts() && !settings.alwaysHidesBreasts()
              && settings.physicsResistance() == resistance && settings.tightness() == tightness;
    }

    private static ArmorItem armor(Item item) {
        check(item instanceof ArmorItem, item + " is not armor");
        return (ArmorItem) item;
    }

    private static JetpackMode mode(ItemStack stack) {
        return ((ItemJetpack) stack.getItem()).getJetpackMode(stack);
    }

    private static AccessoryInventory single(String slotType, int index, ItemStack stack) {
        return new AccessoryInventory() {
            @Override
            public Optional<? extends IItemHandler> inventory(LivingEntity entity) {
                return Optional.of(new AccessoryItemHandler(List.of(stack)));
            }

            @Override
            public Optional<AccessoryResult> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
                return filter.test(stack) ? Optional.of(new AccessoryResult(slotType, index, stack)) : Optional.empty();
            }

            @Override
            public ItemStack stack(LivingEntity entity, String type, int slot) {
                return type.equals(slotType) && slot == index ? stack : ItemStack.EMPTY;
            }
        };
    }

    private static void apply(ServerPlayer player, String slotType, int slot, int shift) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new PacketModeChangeCurios(slotType, slot, shift).encode(buffer);
        PacketModeChangeCurios decoded = PacketModeChangeCurios.decode(buffer);
        check(buffer.readableBytes() == 0, "The accessory mode packet left unread bytes");
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
                    Connection connection = sender == null ? new Connection(PacketFlow.SERVERBOUND) : sender.connection.connection;
                    return (NetworkEvent.Context) constructor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new GameTestAssertException("Could not build an accessory mode-packet context: " + e.getMessage());
            }
        }
        throw new GameTestAssertException("No accessory mode-packet context constructor");
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "accessory"), null);
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
