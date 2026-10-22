/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.api.MekanismAPI;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Break speed and item crafted reach the same Mekanism listeners Forge calls.
 */
public class BreakSpeedGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void replacedBreakSpeedIsWhatMiningUses(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        player.setOnGround(true);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BreakSpeed.class, event -> {
            if (event.getEntity() == player) {
                event.setNewSpeed(7.5F);
            }
        });
        helper.succeedWhen(() -> {
            near(player.getDestroySpeed(stone), 7.5F, "A break-speed listener did not replace destroy speed");
            int divisor = player.hasCorrectToolForDrops(stone) ? 30 : 100;
            float progress = stone.getDestroyProgress(player, helper.getLevel(), absolute);
            near(progress, 7.5F / stone.getDestroySpeed(helper.getLevel(), absolute) / divisor,
                  "Destroy progress ignored the replacement speed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cancelledBreakSpeedIsNegative(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        player.setOnGround(true);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BreakSpeed.class, event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        });
        helper.succeedWhen(() -> {
            float speed = player.getDestroySpeed(stone);
            near(speed, -1, "Cancelling break speed returned " + speed);
            float progress = stone.getDestroyProgress(player, helper.getLevel(), absolute);
            check(progress < 0, "Cancelled break speed still advanced mining, progress " + progress);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void mekaToolSlowsForAHarderBlast(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(1, 2, 1);
        BlockPos blastPos = new BlockPos(4, 3, 4);
        helper.setBlock(plainPos, Blocks.STONE);
        helper.setBlock(blastPos, Blocks.STONE);
        // Horizontal neighbors, so whichever face the ray hits is in the blast.
        helper.setBlock(new BlockPos(3, 3, 4), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(5, 3, 4), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(4, 3, 3), Blocks.OBSIDIAN);

        ServerPlayer plain = lookSouth(helper, new BlockPos(1, 1, 0));
        plain.setItemInHand(InteractionHand.MAIN_HAND, chargedMekaTool());
        plain.setOnGround(true);
        ServerPlayer blaster = lookSouth(helper, new BlockPos(4, 2, 2));
        blaster.setItemInHand(InteractionHand.MAIN_HAND, chargedMekaTool());
        blaster.setOnGround(true);
        blaster.setShiftKeyDown(false);

        BlockState stone = helper.getBlockState(blastPos);
        BlockPos plainAbsolute = helper.absolutePos(plainPos);
        BlockPos blastAbsolute = helper.absolutePos(blastPos);
        float stoneHardness = stone.getDestroySpeed(helper.getLevel(), blastAbsolute);
        float obsidianHardness = helper.getBlockState(new BlockPos(3, 3, 4)).getDestroySpeed(helper.getLevel(), helper.absolutePos(new BlockPos(3, 3, 4)));
        helper.succeedWhen(() -> {
            check(obsidianHardness > stoneHardness, "Obsidian was not harder than stone");
            float direct = blaster.getDestroySpeed(stone);
            check(direct > 1, "The Meka-Tool destroy speed was " + direct);
            near(direct, plain.getDestroySpeed(helper.getBlockState(plainPos)), "A direct destroy speed used a blast position");
            float plainProgress = helper.getBlockState(plainPos).getDestroyProgress(plain, helper.getLevel(), plainAbsolute);
            float blastProgress = stone.getDestroyProgress(blaster, helper.getLevel(), blastAbsolute);
            check(plainProgress > 0, "The Meka-Tool made no destroy progress");
            near(blastProgress, plainProgress * (stoneHardness / obsidianHardness),
                  "Meka-Tool blast did not slow for the harder neighbor");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void disassemblerKeepsSpeedWithGyroscopicStabilization(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        ServerPlayer miner = survival(helper, new BlockPos(1, 2, 1));
        miner.setItemInHand(InteractionHand.MAIN_HAND, chargedDisassembler());
        miner.setOnGround(true);
        float ground = miner.getDestroySpeed(stone);
        miner.setOnGround(false);
        float air = miner.getDestroySpeed(stone);

        miner.setItemSlot(EquipmentSlot.LEGS, stabilizationPants());
        miner.setOnGround(false);
        float stabilizedAir = miner.getDestroySpeed(stone);

        helper.setBlock(new BlockPos(5, 2, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(5, 3, 2), Blocks.WATER);
        ServerPlayer diver = survival(helper, new BlockPos(5, 2, 2));
        diver.setItemInHand(InteractionHand.MAIN_HAND, chargedDisassembler());
        diver.setItemSlot(EquipmentSlot.LEGS, stabilizationPants());
        diver.setNoGravity(true);
        helper.succeedWhen(() -> {
            check(ground > 1, "The atomic disassembler destroy speed was " + ground);
            near(air, ground / 5, "Air did not slow the atomic disassembler");
            near(stabilizedAir, ground, "Gyroscopic stabilization did not restore the disassembler's air speed");
            diver.doTick();
            check(diver.isEyeInFluid(FluidTags.WATER), "The disassembler's owner was not eye-deep in water");
            diver.setOnGround(true);
            diver.setDeltaMovement(0, 0, 0);
            near(diver.getDestroySpeed(stone), ground, "Underwater gyroscopic stabilization did not restore the disassembler's speed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void craftedEventFiresWhenTheResultIsTaken(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        WitnessRecipe witness = new WitnessRecipe();
        Runnable restore = prependCraftingRecipe(helper.getLevel().getRecipeManager(), witness);
        AtomicBoolean sawCraft = new AtomicBoolean();
        AtomicBoolean gridIntact = new AtomicBoolean();
        AtomicBoolean afterCraftedBy = new AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemCraftedEvent.class, event -> {
            if (event.getEntity() != player) {
                return;
            }
            sawCraft.set(event.getCrafting().is(Items.DIRT) && event.getCrafting().getCount() == 1);
            gridIntact.set(event.getInventory().getContainerSize() == 9 && event.getInventory().getItem(0).is(Items.BEDROCK));
            afterCraftedBy.set(player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.DIRT)) == 1);
        });
        try {
            CraftingMenu menu = new CraftingMenu(0, player.getInventory(),
                  ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
            menu.getSlot(1).set(new ItemStack(Items.BEDROCK));
            ItemStack result = menu.getSlot(0).getItem();
            check(result.is(Items.DIRT), "The witness recipe produced " + result);
            ItemStack taken = menu.getSlot(0).remove(result.getCount());
            menu.getSlot(0).onTake(player, taken);
        } finally {
            restore.run();
        }
        Player duringRemainder = witness.duringRemainder;
        Player leaked = ForgeHooks.getCraftingPlayer();
        helper.succeedWhen(() -> {
            check(sawCraft.get(), "Taking the result did not fire the crafted event");
            check(afterCraftedBy.get(), "The crafted event ran before the stack was crafted");
            check(gridIntact.get(), "The crafting grid was already consumed when the crafted event fired");
            check(duringRemainder == player, "The remainder lookup did not see the crafting player");
            check(leaked == null, "The crafting player was still set after the result was taken");
        });
    }

    private static ItemStack chargedMekaTool() {
        ItemStack tool = new ItemStack(MekanismItems.MEKA_TOOL.get());
        ((IModuleContainerItem) tool.getItem()).addModule(tool, MekanismModules.BLASTING_UNIT.get());
        check(MekanismAPI.getModuleHelper().isEnabled(tool, MekanismModules.BLASTING_UNIT), "The blasting module was disabled");
        return StorageUtils.getFilledEnergyVariant(tool, MekanismConfig.gear.mekaToolBaseEnergyCapacity);
    }

    private static ItemStack chargedDisassembler() {
        return StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.ATOMIC_DISASSEMBLER.get()), MekanismConfig.gear.disassemblerMaxEnergy);
    }

    private static ItemStack stabilizationPants() {
        ItemStack pants = new ItemStack(MekanismItems.MEKASUIT_PANTS.get());
        ((IModuleContainerItem) pants.getItem()).addModule(pants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT.get());
        check(MekanismAPI.getModuleHelper().isEnabled(pants, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT), "The gyroscopic module was disabled");
        return pants;
    }

    /**
     * Puts {@code recipe} ahead of the other crafting recipes. The returned action puts the previous map back.
     */
    @SuppressWarnings("unchecked")
    private static Runnable prependCraftingRecipe(RecipeManager manager, Recipe<?> recipe) {
        Field field = recipeField(manager);
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> original;
        try {
            original = (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) field.get(manager);
        } catch (IllegalAccessException e) {
            throw new GameTestAssertException("Could not read recipes: " + e.getMessage());
        }
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> copy = new LinkedHashMap<>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : original.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        Map<ResourceLocation, Recipe<?>> crafting = new LinkedHashMap<>();
        crafting.put(recipe.getId(), recipe);
        Map<ResourceLocation, Recipe<?>> existing = copy.get(RecipeType.CRAFTING);
        if (existing != null) {
            crafting.putAll(existing);
        }
        copy.put(RecipeType.CRAFTING, crafting);
        try {
            field.set(manager, copy);
        } catch (IllegalAccessException e) {
            throw new GameTestAssertException("Could not install the witness recipe: " + e.getMessage());
        }
        return () -> {
            try {
                field.set(manager, original);
            } catch (IllegalAccessException e) {
                throw new GameTestAssertException("Could not restore recipes: " + e.getMessage());
            }
        };
    }

    private static Field recipeField(RecipeManager manager) {
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(manager);
                if (value instanceof Map && ((Map<?, ?>) value).containsKey(RecipeType.CRAFTING)) {
                    return field;
                }
            } catch (IllegalAccessException e) {
                throw new GameTestAssertException("Could not inspect " + field.getName() + ": " + e.getMessage());
            }
        }
        throw new GameTestAssertException("Recipe manager has no crafting recipe map");
    }

    private static ServerPlayer lookSouth(GameTestHelper helper, BlockPos feet) {
        // Yaw 0 looks south.
        ServerPlayer player = survival(helper, feet);
        player.setYRot(0);
        player.setXRot(0);
        player.setYHeadRot(0);
        return player;
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "break-speed"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void near(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > 0.001F) {
            throw new GameTestAssertException(message + " (expected " + expected + ", was " + actual + ")");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class WitnessRecipe extends CustomRecipe {

        private Player duringRemainder;

        private WitnessRecipe() {
            super(new ResourceLocation("mekanism", "gametest_crafted_event"));
        }

        @Override
        public boolean matches(CraftingContainer inv, Level level) {
            boolean found = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (found || !stack.is(Items.BEDROCK)) {
                    return false;
                }
                found = true;
            }
            return found;
        }

        @Override
        public ItemStack assemble(CraftingContainer inv) {
            return new ItemStack(Items.DIRT);
        }

        @Override
        public ItemStack getResultItem() {
            return new ItemStack(Items.DIRT);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 1;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
            duringRemainder = ForgeHooks.getCraftingPlayer();
            return super.getRemainingItems(inv);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }
    }
}
