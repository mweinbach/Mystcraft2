/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.block.BlockObsidianTNT;
import mekanism.additions.common.block.ExtendedFluidLogType;
import mekanism.additions.common.block.IStateExtendedFluidLoggable;
import mekanism.additions.common.block.plastic.BlockPlasticFence;
import mekanism.additions.common.block.plastic.BlockPlasticRoad;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.additions.common.entity.EntityObsidianTNT;
import mekanism.additions.common.registries.AdditionsBlocks;
import mekanism.additions.common.registries.AdditionsEntityTypes;
import mekanism.api.text.EnumColor;
import mekanism.common.registries.MekanismItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mekanism Additions registers its plastic blocks and obsidian TNT, crafts them, and runs the block behaviour the
 * block classes implement: extended fluid logging, glow panels, slick friction, reinforced blast resistance, roads
 * and the obsidian TNT entity.
 */
public class AdditionsModuleGameTests {

    public static final String TEMPLATE = "mekanism:empty";

    private static final int PLASTIC_BLOCK_COUNT = 271;

    @GameTest(template = TEMPLATE)
    public static void additionsRegistersBlocksAndObsidianTnt(GameTestHelper helper) {
        int blocks = 0;
        int items = 0;
        for (Block block : Registry.BLOCK) {
            if (MekanismAdditions.MODID.equals(Registry.BLOCK.getKey(block).getNamespace())) {
                blocks++;
            }
        }
        for (Item item : Registry.ITEM) {
            ResourceLocation id = Registry.ITEM.getKey(item);
            if (MekanismAdditions.MODID.equals(id.getNamespace())) {
                items++;
            }
        }
        check(blocks == PLASTIC_BLOCK_COUNT, "Additions registered " + blocks + " blocks");
        check(items == PLASTIC_BLOCK_COUNT, "Additions registered " + items + " items");
        ResourceLocation tnt = new ResourceLocation(MekanismAdditions.MODID, "obsidian_tnt");
        check(Registry.ENTITY_TYPE.getKey(AdditionsEntityTypes.OBSIDIAN_TNT.get()).equals(tnt), "Obsidian TNT entity is not registered");
        check(Registry.BLOCK.getKey(AdditionsBlocks.OBSIDIAN_TNT.getBlock()).equals(tnt), "Obsidian TNT block is not registered");
        check(AdditionsBlocks.PLASTIC_BLOCKS.get(EnumColor.DARK_RED).getBlock() != null, "Dark red plastic is missing");
        check(AdditionsBlocks.PLASTIC_BLOCKS.get(EnumColor.AQUA).getBlock() != null, "Aqua plastic is missing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsCraftsPlasticSlabAndFence(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack sheet = new ItemStack(MekanismItems.HDPE_SHEET.get());
        ItemStack dye = new ItemStack(Items.RED_DYE);
        ItemStack empty = ItemStack.EMPTY;
        ItemStack plastic = craft(helper, player, new BlockPos(1, 1, 1),
              empty, sheet.copy(), empty,
              sheet.copy(), dye, sheet.copy(),
              empty, sheet.copy(), empty);
        check(plastic.is(AdditionsBlocks.PLASTIC_BLOCKS.get(EnumColor.RED).asItem()) && plastic.getCount() == 4,
              "Red plastic recipe made " + name(plastic) + " x" + plastic.getCount());
        ItemStack slab = craft(helper, player, new BlockPos(3, 1, 1),
              plastic.copy(), plastic.copy(), plastic.copy(),
              empty, empty, empty,
              empty, empty, empty);
        check(slab.is(AdditionsBlocks.PLASTIC_SLABS.get(EnumColor.RED).asItem()) && slab.getCount() == 6,
              "Plastic slab recipe made " + name(slab) + " x" + slab.getCount());
        ItemStack stick = new ItemStack(MekanismItems.HDPE_STICK.get());
        ItemStack fence = craft(helper, player, new BlockPos(5, 1, 1),
              plastic.copy(), stick, plastic.copy(),
              plastic.copy(), stick.copy(), plastic.copy(),
              empty, empty, empty);
        check(fence.is(AdditionsBlocks.PLASTIC_FENCES.get(EnumColor.RED).asItem()) && fence.getCount() == 3,
              "Plastic fence recipe made " + name(fence) + " x" + fence.getCount());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsFenceLogsWaterThenLava(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, AdditionsBlocks.PLASTIC_FENCES.get(EnumColor.RED).getBlock().defaultBlockState());
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);
        BlockPlasticFence fence = (BlockPlasticFence) state.getBlock();
        check(fence.placeLiquid(helper.getLevel(), absolute, state, Fluids.WATER.getSource(false)), "Water did not log the fence");
        state = helper.getBlockState(pos);
        check(state.getValue(BlockStateProperties.WATERLOGGED), "Waterlogged fence is not waterlogged");
        check(state.getValue(IStateExtendedFluidLoggable.FLUID_LOGGED) == ExtendedFluidLogType.EMPTY, "Water used the lava property");
        ItemStack water = fence.pickupBlock(helper.getLevel(), absolute, state);
        check(water.is(Items.WATER_BUCKET), "Picking up water gave " + name(water));
        state = helper.getBlockState(pos);
        check(!state.getValue(BlockStateProperties.WATERLOGGED), "Water stayed in the fence");
        check(fence.placeLiquid(helper.getLevel(), absolute, state, Fluids.LAVA.getSource(false)), "Lava did not log the fence");
        state = helper.getBlockState(pos);
        check(!state.getValue(BlockStateProperties.WATERLOGGED), "Lava set the waterlogged flag");
        check(state.getValue(IStateExtendedFluidLoggable.FLUID_LOGGED) == ExtendedFluidLogType.LAVA, "Fence fluid is " + state.getValue(IStateExtendedFluidLoggable.FLUID_LOGGED));
        ItemStack lava = fence.pickupBlock(helper.getLevel(), absolute, state);
        check(lava.is(Items.LAVA_BUCKET), "Picking up lava gave " + name(lava));
        state = helper.getBlockState(pos);
        check(state.getValue(IStateExtendedFluidLoggable.FLUID_LOGGED) == ExtendedFluidLogType.EMPTY, "Lava stayed in the fence");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsGlowPanelNeedsSupportAndLights(GameTestHelper helper) {
        BlockPos support = new BlockPos(1, 1, 1);
        BlockPos panelPos = new BlockPos(1, 2, 1);
        helper.setBlock(support, Blocks.STONE);
        BlockState panel = AdditionsBlocks.GLOW_PANELS.get(EnumColor.ORANGE).getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        helper.setBlock(panelPos, panel);
        BlockState placed = helper.getBlockState(panelPos);
        check(placed.getLightEmission() == 15, "Glow panel light is " + placed.getLightEmission());
        check(placed.canSurvive(helper.getLevel(), helper.absolutePos(panelPos)), "Glow panel does not survive on stone");
        helper.setBlock(support, Blocks.AIR);
        BlockState updated = placed.updateShape(Direction.DOWN, Blocks.AIR.defaultBlockState(), helper.getLevel(), helper.absolutePos(panelPos),
              helper.absolutePos(support));
        check(updated.isAir(), "Unsupported glow panel became " + updated);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsSlickFrictionAndReinforcedResistance(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, AdditionsBlocks.SLICK_PLASTIC_BLOCKS.get(EnumColor.RED).getBlock().defaultBlockState());
        float friction = helper.getBlockState(pos).getFriction(helper.getLevel(), helper.absolutePos(pos), null);
        check(friction == 0.98F, "Slick plastic friction is " + friction);
        Block reinforced = AdditionsBlocks.REINFORCED_PLASTIC_BLOCKS.get(EnumColor.RED).getBlock();
        check(reinforced.getExplosionResistance() == 1_200F, "Reinforced plastic resistance is " + reinforced.getExplosionResistance());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsRoadBoostsMotion(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, AdditionsBlocks.PLASTIC_ROADS.get(EnumColor.RED).getBlock().defaultBlockState());
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);
        Pig pig = new Pig(EntityType.PIG, helper.getLevel());
        pig.setDeltaMovement(0.1D, 0, 0);
        ((BlockPlasticRoad) state.getBlock()).stepOn(helper.getLevel(), absolute, state, pig);
        float friction = state.getFriction(helper.getLevel(), absolute, pig);
        double expected = 0.1D + Math.sin(Math.atan2(0.1D, 0)) * 1.6D * friction;
        check(Math.abs(pig.getDeltaMovement().x - expected) < 1.0E-4, "Road motion is " + pig.getDeltaMovement().x + ", expected " + expected);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsObsidianTntPrimesItsOwnEntity(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 3, 1));
        BlockPos primed = new BlockPos(1, 1, 1);
        helper.setBlock(primed, AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState());
        BlockPos absolute = helper.absolutePos(primed);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        InteractionResult used = helper.getBlockState(primed).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(used.consumesAction(), "Flint and steel did not prime obsidian TNT: " + used);
        check(helper.getBlockState(primed).isAir(), "Primed obsidian TNT left " + helper.getBlockState(primed));
        EntityObsidianTNT entity = onlyObsidian(helper, absolute);
        int delay = MekanismAdditionsConfig.additions.obsidianTNTDelay.get();
        check(entity.getFuse() == delay, "Obsidian TNT fuse is " + entity.getFuse());
        check(entity.owner == player, "Obsidian TNT igniter is " + entity.owner);
        entity.discard();

        BlockPos signal = new BlockPos(4, 1, 1);
        helper.setBlock(signal, AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState());
        BlockPos signalAbsolute = helper.absolutePos(signal);
        helper.setBlock(signal.north(), Blocks.REDSTONE_BLOCK);
        if (helper.getBlockState(signal).is(AdditionsBlocks.OBSIDIAN_TNT.getBlock())) {
            helper.getBlockState(signal).neighborChanged(helper.getLevel(), signalAbsolute, Blocks.REDSTONE_BLOCK, helper.absolutePos(signal.north()), false);
        }
        check(helper.getBlockState(signal).isAir(), "Redstone left " + helper.getBlockState(signal));
        onlyObsidian(helper, signalAbsolute).discard();

        BlockPos blasted = new BlockPos(1, 1, 4);
        helper.setBlock(blasted, AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState());
        BlockPos blastedAbsolute = helper.absolutePos(blasted);
        Explosion explosion = new Explosion(helper.getLevel(), null, blastedAbsolute.getX(), blastedAbsolute.getY(), blastedAbsolute.getZ(), 1);
        AdditionsBlocks.OBSIDIAN_TNT.getBlock().wasExploded(helper.getLevel(), blastedAbsolute, explosion);
        EntityObsidianTNT blastedEntity = onlyObsidian(helper, blastedAbsolute);
        check(blastedEntity.getFuse() < delay, "Exploded obsidian TNT kept a full fuse of " + blastedEntity.getFuse());
        blastedEntity.discard();

        BlockPos vanilla = new BlockPos(4, 1, 4);
        helper.setBlock(vanilla, Blocks.TNT);
        BlockPos vanillaAbsolute = helper.absolutePos(vanilla);
        BlockHitResult vanillaHit = new BlockHitResult(Vec3.atCenterOf(vanillaAbsolute), Direction.UP, vanillaAbsolute, false);
        helper.getBlockState(vanilla).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, vanillaHit);
        List<PrimedTnt> primedTnt = helper.getLevel().getEntitiesOfClass(PrimedTnt.class, new AABB(vanillaAbsolute).inflate(1));
        check(primedTnt.size() == 1 && !(primedTnt.get(0) instanceof EntityObsidianTNT), "Vanilla TNT primed " + primedTnt);
        primedTnt.get(0).discard();

        BlockState obsidian = AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState();
        check(obsidian.getFlammability(helper.getLevel(), absolute, Direction.UP) == 75, "Obsidian TNT flammability is not 75");
        check(BlockObsidianTNT.createAndAddEntity(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 1)), null), "Dispenser helper did not prime");
        onlyObsidian(helper, helper.absolutePos(new BlockPos(6, 1, 1))).discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void additionsTransparentPlasticTintsBeacon(GameTestHelper helper) {
        float[] plastic = tint(helper, new BlockPos(1, 1, 1), AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS.get(EnumColor.RED).getBlock().defaultBlockState());
        float[] expected = EnumColor.RED.getRgbCodeFloat();
        check(colorsMatch(plastic, expected), "Red transparent plastic tinted the beacon " + format(plastic));
        float[] glass = tint(helper, new BlockPos(4, 1, 1), Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        check(colorsMatch(glass, DyeColor.WHITE.getTextureDiffuseColors()), "White stained glass tinted the beacon " + format(glass));
        helper.succeed();
    }

    private static float[] tint(GameTestHelper helper, BlockPos beaconPos, BlockState tint) {
        helper.setBlock(beaconPos, Blocks.BEACON);
        helper.setBlock(beaconPos.above(), tint);
        // The beacon scans upward only while the column is at or below WORLD_SURFACE, then commits the beam.
        // A structure placed above the terrain never enters that loop, so pin the surface to the tint block.
        pinSurface(helper, beaconPos.above());
        BlockPos absolute = helper.absolutePos(beaconPos);
        BeaconBlockEntity beacon = (BeaconBlockEntity) helper.getBlockEntity(beaconPos);
        BlockState state = helper.getBlockState(beaconPos);
        BeaconBlockEntity.tick(helper.getLevel(), absolute, state, beacon);
        // getBeamSections is empty until the beacon has a pyramid level. The tint is stored before that.
        try {
            Field levels = BeaconBlockEntity.class.getDeclaredField("levels");
            levels.setAccessible(true);
            levels.setInt(beacon, 1);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not read the beacon beam: " + e);
        }
        List<BeaconBlockEntity.BeaconBeamSection> sections = beacon.getBeamSections();
        check(sections.size() >= 2, "Beacon under " + tint.getBlock() + " recorded " + sections.size()
              + " beam sections at surface " + helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, absolute.getX(), absolute.getZ()));
        // The beacon block itself is a beam and occupies section 0. The block above is section 1.
        return sections.get(1).getColor();
    }

    private static void pinSurface(GameTestHelper helper, BlockPos relativeTop) {
        BlockPos top = helper.absolutePos(relativeTop);
        LevelChunk chunk = helper.getLevel().getChunkAt(top);
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE);
        int localX = top.getX() & 15;
        int localZ = top.getZ() & 15;
        // Level.getHeight returns the heightmap's first free Y. Raising uses the tint block; lowering pretends
        // each current top is air so the column stops on the tint instead of the terrain above the structure.
        heightmap.update(localX, top.getY(), localZ, helper.getLevel().getBlockState(top));
        int guard = 0;
        while (helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, top.getX(), top.getZ()) > top.getY() + 1 && guard++ < 512) {
            int surface = helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, top.getX(), top.getZ());
            if (!heightmap.update(localX, surface - 1, localZ, Blocks.AIR.defaultBlockState())) {
                break;
            }
        }
        int surface = helper.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, top.getX(), top.getZ());
        check(surface == top.getY() + 1, "Beacon surface stayed at " + surface + " for tint Y " + top.getY());
    }

    private static EntityObsidianTNT onlyObsidian(GameTestHelper helper, BlockPos absolute) {
        List<EntityObsidianTNT> found = helper.getLevel().getEntitiesOfClass(EntityObsidianTNT.class, new AABB(absolute).inflate(1));
        check(found.size() == 1, "Expected one obsidian TNT entity, found " + found.size());
        Entity entity = found.get(0);
        check(entity.getType() == AdditionsEntityTypes.OBSIDIAN_TNT.get(), "Primed entity type is " + Registry.ENTITY_TYPE.getKey(entity.getType()));
        return found.get(0);
    }

    private static ItemStack craft(GameTestHelper helper, ServerPlayer player, BlockPos table, ItemStack... grid) {
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        for (int i = 0; i < grid.length; i++) {
            menu.getSlot(i + 1).set(grid[i]);
        }
        return menu.getSlot(0).getItem().copy();
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "additions-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static boolean colorsMatch(float[] actual, float[] expected) {
        if (actual == null || expected == null || actual.length < 3 || expected.length < 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (Math.abs(actual[i] - expected[i]) > 0.001F) {
                return false;
            }
        }
        return true;
    }

    private static String format(float[] color) {
        if (color == null) {
            return "nothing";
        }
        return color[0] + "," + color[1] + "," + color[2];
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getItem().getName(stack).getString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
