/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.block.BlockObsidianTNT;
import mekanism.additions.common.block.ExtendedFluidLogType;
import mekanism.additions.common.block.IStateExtendedFluidLoggable;
import mekanism.additions.common.block.plastic.BlockPlasticFence;
import mekanism.additions.common.block.plastic.BlockPlasticRoad;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.additions.common.entity.EntityBalloon;
import mekanism.additions.common.entity.EntityObsidianTNT;
import mekanism.additions.common.entity.baby.BabyType;
import mekanism.additions.common.entity.baby.EntityBabyCreeper;
import mekanism.additions.common.entity.baby.EntityBabySkeleton;
import mekanism.additions.common.registries.AdditionsBlocks;
import mekanism.additions.common.registries.AdditionsEntityTypes;
import mekanism.additions.common.item.ItemWalkieTalkie;
import mekanism.additions.common.registries.AdditionsItems;
import mekanism.additions.common.voice.VoiceConnection;
import mekanism.additions.common.voice.VoiceServerManager;
import mekanism.additions.common.world.modifier.BabyEntitySpawnBiomeModifier;
import mekanism.api.text.EnumColor;
import mekanism.common.config.value.CachedIntValue;
import mekanism.common.registries.MekanismItems;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.resource.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/**
 * Mekanism Additions registers its plastic blocks, obsidian TNT, baby mobs, balloons and the walkie-talkie, crafts
 * them, and runs the block and entity behaviour: extended fluid logging, glow panels, slick friction, reinforced blast
 * resistance, roads, the obsidian TNT entity, baby spawns and drops, balloons that latch and pop, walkie channel
 * selection, and the voice server starting only when its config is on. A voice socket claims a one-time session
 * token, and one recipient's audio frames are written whole.
 */
public class AdditionsModuleGameTests {

    public static final String TEMPLATE = "mekanism:empty";

    private static final int PLASTIC_BLOCK_COUNT = 271;
    private static final int BALLOON_COUNT = 18;
    private static final int BABY_SPAWN_EGG_COUNT = 5;
    private static final int WALKIE_COUNT = 1;
    private static final int ADDITIONS_ITEM_COUNT = PLASTIC_BLOCK_COUNT + BALLOON_COUNT + BABY_SPAWN_EGG_COUNT + WALKIE_COUNT;

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
        check(items == ADDITIONS_ITEM_COUNT, "Additions registered " + items + " items");
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
        // The fuse length is one shared config, and a failed run can leave it at 0.
        CachedIntValue configuredDelay = MekanismAdditionsConfig.additions.obsidianTNTDelay;
        int original = configuredDelay.get();
        configuredDelay.set(100);
        try {
            primeObsidianTnt(helper, configuredDelay);
        } finally {
            configuredDelay.set(original);
        }
    }

    private static void primeObsidianTnt(GameTestHelper helper, CachedIntValue configuredDelay) {
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
        int delay = configuredDelay.get();
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

        // A delay of 0–3 used to call nextInt(0). The caller restores the shared delay.
        configuredDelay.set(0);
        BlockPos shortFuse = new BlockPos(6, 1, 4);
        helper.setBlock(shortFuse, AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState());
        BlockPos shortAbsolute = helper.absolutePos(shortFuse);
        AdditionsBlocks.OBSIDIAN_TNT.getBlock().wasExploded(helper.getLevel(), shortAbsolute,
              new Explosion(helper.getLevel(), null, shortAbsolute.getX(), shortAbsolute.getY(), shortAbsolute.getZ(), 1));
        EntityObsidianTNT shortEntity = onlyObsidian(helper, shortAbsolute);
        check(shortEntity.getFuse() == 0, "Zero delay fuse is " + shortEntity.getFuse());
        shortEntity.discard();
        configuredDelay.set(delay);

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

    @GameTest(template = TEMPLATE)
    public static void additionsFireSpreadPrimesObsidianTnt(GameTestHelper helper) {
        BlockPos obsidian = new BlockPos(1, 1, 1);
        helper.setBlock(obsidian, AdditionsBlocks.OBSIDIAN_TNT.getBlock().defaultBlockState());
        burnFromFire(helper, helper.absolutePos(obsidian));
        onlyObsidian(helper, helper.absolutePos(obsidian)).discard();

        BlockPos vanilla = new BlockPos(4, 1, 1);
        helper.setBlock(vanilla, Blocks.TNT);
        BlockPos vanillaAbsolute = helper.absolutePos(vanilla);
        burnFromFire(helper, vanillaAbsolute);
        List<PrimedTnt> primed = helper.getLevel().getEntitiesOfClass(PrimedTnt.class, new AABB(vanillaAbsolute).inflate(1));
        check(primed.size() == 1 && !(primed.get(0) instanceof EntityObsidianTNT), "Fire primed vanilla TNT as " + primed);
        primed.get(0).discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsBabyMobsRegisterSpawnAndDrop(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 3, 1));
        EntityBabyCreeper creeper = spawnBaby(helper, AdditionsEntityTypes.BABY_CREEPER.get(), new BlockPos(1, 2, 1));
        EntityBabySkeleton skeleton = spawnBaby(helper, AdditionsEntityTypes.BABY_SKELETON.get(), new BlockPos(3, 2, 1));
        spawnBaby(helper, AdditionsEntityTypes.BABY_ENDERMAN.get(), new BlockPos(5, 2, 1)).discard();
        spawnBaby(helper, AdditionsEntityTypes.BABY_STRAY.get(), new BlockPos(1, 2, 3)).discard();
        spawnBaby(helper, AdditionsEntityTypes.BABY_WITHER_SKELETON.get(), new BlockPos(3, 2, 3)).discard();
        check(Registry.ENTITY_TYPE.getKey(AdditionsEntityTypes.BALLOON.get()).getPath().equals("balloon"), "Balloon entity is not registered");
        MobSpawnSettings.SpawnerData scaled = MekanismAdditionsConfig.additions.getConfig(BabyType.CREEPER)
              .getSpawner(new MobSpawnSettings.SpawnerData(EntityType.CREEPER, 100, 4, 4));
        check(scaled.type == AdditionsEntityTypes.BABY_CREEPER.get() && scaled.getWeight().asInt() == 50 && scaled.minCount == 2 && scaled.maxCount == 2,
              "Baby creeper spawn is weight " + scaled.getWeight().asInt() + " size " + scaled.minCount + "-" + scaled.maxCount);
        check(lootContains(helper, creeper, player, Items.GUNPOWDER), "Baby creeper loot never dropped gunpowder");
        check(lootContains(helper, skeleton, player, Items.BONE) && lootContains(helper, skeleton, player, Items.ARROW),
              "Baby skeleton loot missed bones or arrows");
        creeper.hurt(DamageSource.playerAttack(player), 40);
        skeleton.hurt(DamageSource.playerAttack(player), 40);
        check(!creeper.isAlive() && !skeleton.isAlive(), "Baby mobs survived a lethal hit");
        creeper.discard();
        skeleton.discard();

        ServerLevel level = helper.getLevel();
        Holder<Biome> plains = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS);
        WeightedRandomList<MobSpawnSettings.SpawnerData> mobs = level.getChunkSource().getGenerator()
              .getMobsAt(plains, level.structureManager(), MobCategory.MONSTER, helper.absolutePos(new BlockPos(8, 1, 8)));
        int parent = spawnWeight(mobs, EntityType.CREEPER);
        int baby = spawnWeight(mobs, AdditionsEntityTypes.BABY_CREEPER.get());
        check(parent > 0, "Plains has no creeper spawns");
        int expected = (int) Math.ceil(parent * MekanismAdditionsConfig.additions.getConfig(BabyType.CREEPER).weightPercentage.get());
        check(baby == expected, "Baby creeper weight is " + baby + ", expected " + expected + " from parent " + parent);
        check(spawnWeight(mobs, AdditionsEntityTypes.BABY_WITHER_SKELETON.get()) == 0, "Baby wither skeletons spawned in plains");

        BlockPos eggPos = new BlockPos(5, 1, 3);
        helper.setBlock(eggPos, Blocks.STONE);
        BlockPos eggAbsolute = helper.absolutePos(eggPos);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AdditionsItems.BABY_CREEPER_SPAWN_EGG.get()));
        UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
              new BlockHitResult(Vec3.atCenterOf(eggAbsolute), Direction.UP, eggAbsolute, false));
        InteractionResult hatched = AdditionsItems.BABY_CREEPER_SPAWN_EGG.get().useOn(context);
        check(hatched.consumesAction(), "Baby creeper spawn egg did not hatch: " + hatched);
        List<EntityBabyCreeper> hatchedCreepers = level.getEntitiesOfClass(EntityBabyCreeper.class, new AABB(eggAbsolute.above()).inflate(1));
        check(hatchedCreepers.size() == 1 && hatchedCreepers.get(0).isBaby(), "Spawn egg produced " + hatchedCreepers);
        hatchedCreepers.get(0).discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsBalloonsCraftLatchAndPop(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 3, 1));
        ItemStack empty = ItemStack.EMPTY;
        ItemStack crafted = craft(helper, player, new BlockPos(1, 1, 1),
              new ItemStack(Items.LEATHER), new ItemStack(Items.STRING), new ItemStack(Items.RED_DYE),
              empty, empty, empty,
              empty, empty, empty);
        check(crafted.is(AdditionsItems.BALLOONS.get(EnumColor.RED).get()) && crafted.getCount() == 2,
              "Red balloon recipe made " + name(crafted) + " x" + crafted.getCount());

        Pig pig = new Pig(EntityType.PIG, helper.getLevel());
        BlockPos pigPos = helper.absolutePos(new BlockPos(3, 2, 1));
        pig.moveTo(pigPos.getX() + 0.5, pigPos.getY(), pigPos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(pig);
        EntityBalloon latched = EntityBalloon.create(pig, EnumColor.RED);
        check(latched != null, "Could not create a balloon");
        helper.getLevel().addFreshEntity(latched);
        check(latched.isLatched() && latched.isLatchedToEntity(), "Balloon did not latch to the pig");
        check(latched.skipAttackInteraction(player), "Balloon attack was not consumed");
        check(latched.isRemoved(), "Attacked balloon is still in the world");
        check(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pigPos).inflate(4)).stream()
              .noneMatch(item -> item.getItem().is(AdditionsItems.BALLOONS.get(EnumColor.RED).get())), "Popped balloon dropped itself");

        EntityBalloon popped = EntityBalloon.create(pig, EnumColor.WHITE);
        check(popped != null, "Could not create a second balloon");
        helper.getLevel().addFreshEntity(popped);
        check(popped.hurt(DamageSource.playerAttack(player), 1), "Balloon ignored damage");
        check(popped.isRemoved(), "Damaged balloon is still in the world");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsBabyBiomeBlacklistUsesHolderKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        Holder<Biome> plains = biomes.getHolderOrThrow(Biomes.PLAINS);
        // A world biome can carry this key while its biome instance is absent from the built-in registry.
        ResourceKey<Biome> blacklisted = ResourceKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(MekanismAdditions.MODID, "not_plains"));
        Holder.Reference<Biome> worldBiome = Holder.Reference.createStandAlone(biomes, blacklisted);
        try {
            Method bind = Holder.Reference.class.getDeclaredMethod("bind", ResourceKey.class, Object.class);
            bind.setAccessible(true);
            bind.invoke(worldBiome, blacklisted, plains.value());
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not bind the biome holder: " + e);
        }
        var config = MekanismAdditionsConfig.additions.getConfig(BabyType.CREEPER);
        List<ResourceLocation> previous = new ArrayList<>(config.biomeBlackList.get());
        config.biomeBlackList.set(List.of(blacklisted.location()));
        try {
            check(babyWeight(worldBiome) == 0, "Blacklisted biome holder still added a baby creeper");
        } finally {
            config.biomeBlackList.set(previous);
        }
        check(babyWeight(plains) > 0, "Plains without a blacklist did not add a baby creeper");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void additionsBalloonHoversBelowZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int stoneY = level.getMinBuildHeight();
        check(stoneY < 0, "Overworld minimum build height is " + stoneY);
        BlockPos column = helper.absolutePos(new BlockPos(7, 1, 7));
        BlockPos stone = new BlockPos(column.getX(), stoneY, column.getZ());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        for (int above = 1; above <= 4; above++) {
            level.setBlockAndUpdate(stone.above(above), Blocks.AIR.defaultBlockState());
        }
        Pig pig = new Pig(EntityType.PIG, level);
        pig.setNoAi(true);
        pig.moveTo(stone.getX() + 0.5, stoneY + 2.0, stone.getZ() + 0.5, 0, 0);
        pig.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(pig);
        EntityBalloon latched = EntityBalloon.create(pig, EnumColor.RED);
        check(latched != null, "Could not latch a balloon underground");
        level.addFreshEntity(latched);
        latched.tick();
        check(Math.abs(pig.getDeltaMovement().y) < 1.0E-4,
              "Balloon lifted the pig from Y " + pig.getY() + ": " + pig.getDeltaMovement().y);
        latched.discard();

        for (int above = 0; above <= 4; above++) {
            level.setBlockAndUpdate(stone.above(above), Blocks.AIR.defaultBlockState());
        }
        pig.moveTo(stone.getX() + 0.5, stoneY + 2.0, stone.getZ() + 0.5, 0, 0);
        pig.setDeltaMovement(Vec3.ZERO);
        EntityBalloon unfloored = EntityBalloon.create(pig, EnumColor.WHITE);
        check(unfloored != null, "Could not latch a balloon with no floor");
        level.addFreshEntity(unfloored);
        unfloored.tick();
        check(Math.abs(pig.getDeltaMovement().y) < 1.0E-4,
              "Balloon with no floor pushed the pig: " + pig.getDeltaMovement().y);
        unfloored.discard();
        pig.discard();
        helper.succeed();
    }

    private static int babyWeight(Holder<Biome> biome) {
        MobSpawnSettingsBuilder spawns = new MobSpawnSettingsBuilder(MobSpawnSettings.EMPTY);
        spawns.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.CREEPER, 100, 4, 4));
        try {
            new BabyEntitySpawnBiomeModifier(BabyType.CREEPER).modify(biome, BiomeModifier.Phase.REMOVE, biomeBuilder(spawns));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not build biome spawns: " + e);
        }
        int weight = 0;
        for (MobSpawnSettings.SpawnerData data : spawns.getSpawner(MobCategory.MONSTER)) {
            if (data.type == AdditionsEntityTypes.BABY_CREEPER.get()) {
                weight += data.getWeight().asInt();
            }
        }
        return weight;
    }

    /**
     * Forge's biome builder constructor is private and takes the whole biome. Fabric's takes the spawn builder.
     * The baby modifier only reads mob spawns, so either constructor is enough.
     */
    private static ModifiableBiomeInfo.BiomeInfo.Builder biomeBuilder(MobSpawnSettingsBuilder spawns) throws ReflectiveOperationException {
        for (Constructor<?> constructor : ModifiableBiomeInfo.BiomeInfo.Builder.class.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            constructor.setAccessible(true);
            if (parameters.length == 1 && parameters[0] == MobSpawnSettingsBuilder.class) {
                return (ModifiableBiomeInfo.BiomeInfo.Builder) constructor.newInstance(spawns);
            }
            if (parameters.length == 4 && parameters[3] == MobSpawnSettingsBuilder.class) {
                return (ModifiableBiomeInfo.BiomeInfo.Builder) constructor.newInstance(null, null, null, spawns);
            }
        }
        throw new NoSuchMethodException("No mob-spawn biome builder");
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

    /**
     * Fire consumes a neighbour only when a random roll beats its burn odds. A bound of one makes that roll zero, so a
     * flammable block always burns and a block with no odds never does. Forge renamed the method and passes the face.
     */
    private static void burnFromFire(GameTestHelper helper, BlockPos absolute) {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        Level level = helper.getLevel();
        try {
            Method forge = FireBlock.class.getDeclaredMethod("tryCatchFire", Level.class, BlockPos.class, int.class, RandomSource.class, int.class,
                  Direction.class);
            forge.setAccessible(true);
            forge.invoke(fire, level, absolute, 1, level.getRandom(), 0, Direction.UP);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fabric still has vanilla checkBurnOut. The burn-odds call there reads getFlammability.
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not spread fire: " + e);
        }
        try {
            Method vanilla = FireBlock.class.getDeclaredMethod("checkBurnOut", Level.class, BlockPos.class, int.class, RandomSource.class, int.class);
            vanilla.setAccessible(true);
            vanilla.invoke(fire, level, absolute, 1, level.getRandom(), 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not spread fire: " + e);
        }
    }

    private static <T extends Mob> T spawnBaby(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        T mob = type.create(helper.getLevel());
        check(mob != null, "Could not create " + Registry.ENTITY_TYPE.getKey(type));
        BlockPos absolute = helper.absolutePos(relative);
        mob.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(mob);
        check(mob.isBaby(), Registry.ENTITY_TYPE.getKey(type) + " is not a baby");
        return mob;
    }

    private static boolean lootContains(GameTestHelper helper, LivingEntity entity, ServerPlayer player, Item item) {
        LootTable table = helper.getLevel().getServer().getLootTables().get(entity.getLootTable());
        for (long seed = 0; seed < 48; seed++) {
            LootContext context = new LootContext.Builder(helper.getLevel())
                  .withRandom(RandomSource.create(seed))
                  .withParameter(LootContextParams.THIS_ENTITY, entity)
                  .withParameter(LootContextParams.ORIGIN, entity.position())
                  .withParameter(LootContextParams.DAMAGE_SOURCE, DamageSource.playerAttack(player))
                  .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                  .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                  .create(LootContextParamSets.ENTITY);
            for (ItemStack stack : table.getRandomItems(context)) {
                if (stack.is(item) && stack.getCount() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int spawnWeight(WeightedRandomList<MobSpawnSettings.SpawnerData> mobs, EntityType<?> type) {
        int weight = 0;
        for (MobSpawnSettings.SpawnerData data : mobs.unwrap()) {
            if (data.type == type) {
                weight += data.getWeight().asInt();
            }
        }
        return weight;
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

    @GameTest(template = TEMPLATE)
    public static void additionsWalkieSelectsChannelAndCrafts(GameTestHelper helper) throws Exception {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemWalkieTalkie walkie = AdditionsItems.WALKIE_TALKIE.get();
        ItemStack stack = new ItemStack(walkie);
        check(!walkie.getOn(stack), "A new walkie is on");
        check(walkie.getChannel(stack) == 1, "An empty channel stayed " + walkie.getChannel(stack));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(false);
        InteractionResultHolder<ItemStack> passed = walkie.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(passed.getResult() == InteractionResult.PASS, "Using a walkie without sneaking consumed it");
        check(!walkie.getOn(stack), "Using a walkie without sneaking turned it on");
        player.setShiftKeyDown(true);
        walkie.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        check(walkie.getOn(held), "Sneaking did not turn the walkie on");
        walkie.setOn(held, false);
        walkie.setChannel(held, 3);
        walkie.changeMode(player, held, 1, false);
        check(walkie.getChannel(held) == 3, "An off walkie changed channel to " + walkie.getChannel(held));
        walkie.setOn(held, true);
        walkie.setChannel(held, 1);
        int channel = 1;
        for (int step = 0; step < 8; step++) {
            walkie.changeMode(player, held, 1, false);
            channel = channel == 8 ? 1 : channel + 1;
            check(walkie.getChannel(held) == channel, "Channel after step " + step + " is " + walkie.getChannel(held));
        }
        walkie.changeMode(player, held, -1, false);
        check(walkie.getChannel(held) == 8, "A backward step landed on " + walkie.getChannel(held));
        walkie.setChannel(held, 7);
        walkie.changeMode(player, held, 3, false);
        check(walkie.getChannel(held) == 2, "A shift of three from 7 landed on " + walkie.getChannel(held));
        String text = walkie.getScrollTextComponent(held).getString();
        check(text.contains("2"), "Scroll text is " + text);

        boolean wasEnabled = MekanismAdditionsConfig.additions.voiceServerEnabled.get();
        try {
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(false);
            String disabled = tooltip(walkie, held, helper.getLevel());
            check(disabled.contains("Voice server disabled.") || disabled.contains("walkie.mekanismadditions.disabled"),
                  "Disabled tooltip is " + disabled);
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(true);
            String enabled = tooltip(walkie, held, helper.getLevel());
            check(!enabled.contains("Voice server disabled.") && !enabled.contains("walkie.mekanismadditions.disabled"),
                  "Enabled tooltip is " + enabled);
            check(enabled.toLowerCase(Locale.ROOT).contains("channel"), "Channel tooltip is " + enabled);
        } finally {
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(wasEnabled);
        }

        ItemStack empty = ItemStack.EMPTY;
        ItemStack osmium = new ItemStack(MekanismItems.PROCESSED_RESOURCES.get(ResourceType.INGOT, PrimaryResource.OSMIUM).get());
        ItemStack steel = new ItemStack(MekanismItems.STEEL_INGOT.get());
        ItemStack circuit = new ItemStack(MekanismItems.BASIC_CONTROL_CIRCUIT.get());
        ItemStack crafted = craft(helper, player, new BlockPos(1, 1, 1),
              empty, empty, osmium,
              steel, circuit, steel.copy(),
              empty, steel.copy(), empty);
        check(crafted.is(walkie) && crafted.getCount() == 1, "Walkie recipe made " + name(crafted) + " x" + crafted.getCount());
        voiceServer(helper);
        helper.succeed();
    }

    private static void voiceServer(GameTestHelper helper) throws Exception {
        boolean wasEnabled = MekanismAdditionsConfig.additions.voiceServerEnabled.get();
        int wasPort = MekanismAdditionsConfig.additions.voicePort.get();
        List<ServerPlayer> tracked = new ArrayList<>();
        try {
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(false);
            voiceEvent(helper, "serverStarting", ServerStartingEvent.class);
            check(MekanismAdditions.voiceManager == null, "The voice server started while disabled");

            int port = freePort();
            MekanismAdditionsConfig.additions.voicePort.set(port);
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(true);
            voiceEvent(helper, "serverStarting", ServerStartingEvent.class);
            VoiceServerManager manager = MekanismAdditions.voiceManager;
            check(manager != null, "The voice server did not start");
            ServerSocket socket = (ServerSocket) field(manager, "serverSocket");
            check(socket != null && socket.isBound() && !socket.isClosed() && socket.getLocalPort() == port,
                  "Voice server socket is " + (socket == null ? "missing" : socket.getLocalPort()));
            relay(helper, manager, tracked);
            sessions(helper, manager);
            frames(helper);

            voiceEvent(helper, "serverStopping", ServerStoppingEvent.class);
            check(MekanismAdditions.voiceManager == null, "Stopping left the voice server running");
            check(socket.isClosed(), "Stopping left the voice socket open");

            MekanismAdditionsConfig.additions.voiceServerEnabled.set(false);
            voiceEvent(helper, "serverStarting", ServerStartingEvent.class);
            check(MekanismAdditions.voiceManager == null, "The voice server started again while disabled");
        } finally {
            untrack(helper, tracked);
            if (MekanismAdditions.voiceManager != null) {
                MekanismAdditions.voiceManager.stop();
                MekanismAdditions.voiceManager = null;
            }
            MekanismAdditionsConfig.additions.voiceServerEnabled.set(wasEnabled);
            MekanismAdditionsConfig.additions.voicePort.set(wasPort);
        }
    }

    private static void relay(GameTestHelper helper, VoiceServerManager manager, List<ServerPlayer> tracked) throws Exception {
        ServerPlayer sender = survival(helper, new BlockPos(2, 2, 1));
        ServerPlayer same = survival(helper, new BlockPos(3, 2, 1));
        ServerPlayer other = survival(helper, new BlockPos(4, 2, 1));
        track(helper, tracked, sender, same, other);
        ItemWalkieTalkie walkie = AdditionsItems.WALKIE_TALKIE.get();
        arm(sender, walkie, 4, true, true);
        arm(same, walkie, 4, true, false);
        arm(other, walkie, 5, true, true);
        ByteArrayOutputStream sameBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream otherBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream senderBytes = new ByteArrayOutputStream();
        VoiceConnection senderConn = connection(sender, senderBytes);
        VoiceConnection sameConn = connection(same, sameBytes);
        VoiceConnection otherConn = connection(other, otherBytes);
        @SuppressWarnings("unchecked")
        Set<VoiceConnection> connections = (Set<VoiceConnection>) field(manager, "connections");
        connections.add(senderConn);
        connections.add(sameConn);
        connections.add(otherConn);
        byte[] audio = new byte[]{1, 2, 3, 4};
        manager.sendToPlayers((short) audio.length, audio, senderConn);
        check(heard(sameBytes, audio), "A walkie on the same channel heard " + sameBytes.size() + " bytes");
        check(sameBytes.size() > 0 && otherBytes.size() == 0, "A walkie on another channel heard " + otherBytes.size() + " bytes");
        check(senderBytes.size() == 0, "The sender heard its own audio");

        sameBytes.reset();
        walkie.setOn(same.getInventory().getItem(1), false);
        manager.sendToPlayers((short) audio.length, audio, senderConn);
        check(sameBytes.size() == 0, "An off walkie heard audio");

        ItemStack offhand = new ItemStack(walkie);
        walkie.setOn(offhand, true);
        walkie.setChannel(offhand, 4);
        same.getInventory().offhand.set(0, offhand);
        manager.sendToPlayers((short) audio.length, audio, senderConn);
        check(heard(sameBytes, audio), "An offhand walkie on the channel heard " + sameBytes.size() + " bytes");

        sameBytes.reset();
        walkie.setOn(sender.getInventory().getSelected(), false);
        manager.sendToPlayers((short) audio.length, audio, senderConn);
        check(sameBytes.size() == 0, "An off sender still relayed audio");
        connections.remove(senderConn);
        connections.remove(sameConn);
        connections.remove(otherConn);
    }

    private static void arm(ServerPlayer player, ItemWalkieTalkie walkie, int channel, boolean on, boolean selected) {
        ItemStack stack = new ItemStack(walkie);
        walkie.setOn(stack, on);
        walkie.setChannel(stack, channel);
        player.getInventory().selected = 0;
        player.getInventory().setItem(selected ? 0 : 1, stack);
    }

    private static void sessions(GameTestHelper helper, VoiceServerManager manager) throws Exception {
        ServerPlayer first = survival(helper, new BlockPos(2, 2, 2));
        ServerPlayer second = survival(helper, new BlockPos(3, 2, 2));
        UUID firstToken = manager.issueToken(first.getUUID());
        UUID secondToken = manager.issueToken(second.getUUID());
        check(first.getUUID().equals(manager.claimToken(firstToken)), "The first voice token bound the wrong player");
        check(manager.claimToken(firstToken) == null, "A voice token was accepted twice");
        check(second.getUUID().equals(manager.claimToken(secondToken)), "The second voice token bound the wrong player");

        UUID live = manager.issueToken(first.getUUID());
        try (ServerSocket opened = new ServerSocket(0); Socket client = new Socket()) {
            opened.setSoTimeout(2_000);
            Thread connector = new Thread(() -> {
                try {
                    client.connect(new InetSocketAddress("127.0.0.1", opened.getLocalPort()), 2_000);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }, "walkie-session-client");
            connector.start();
            try (Socket accepted = opened.accept()) {
                connector.join(2_000L);
                check(client.isConnected(), "Voice test client did not connect");
                VoiceConnection connection = new VoiceConnection(accepted);
                connection.start();
                DataOutputStream handshake = new DataOutputStream(client.getOutputStream());
                handshake.writeLong(live.getMostSignificantBits());
                handshake.writeLong(live.getLeastSignificantBits());
                handshake.flush();
                UUID bound = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (System.nanoTime() < deadline && bound == null) {
                    bound = (UUID) field(connection, "uuid");
                    if (bound == null) {
                        Thread.sleep(10L);
                    }
                }
                check(first.getUUID().equals(bound), "Voice socket bound " + bound);
                check(manager.claimToken(live) == null, "The socket left the voice token reusable");
                connection.interrupt();
            }
        }
    }

    private static void frames(GameTestHelper helper) throws Exception {
        ServerPlayer listener = survival(helper, new BlockPos(4, 2, 2));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HoldFirstByte held = new HoldFirstByte(entered, release);
        VoiceConnection recipient = connection(listener, held);
        byte[] first = {1, 2, 3, 4};
        byte[] second = {5, 6, 7, 8};
        Thread leading = new Thread(() -> recipient.sendToPlayer((short) first.length, first, recipient), "walkie-frame-a");
        Thread following = new Thread(() -> recipient.sendToPlayer((short) second.length, second, recipient), "walkie-frame-b");
        leading.start();
        check(entered.await(2, TimeUnit.SECONDS), "The first voice frame never started writing");
        following.start();
        Thread.sleep(100L);
        release.countDown();
        leading.join(2_000L);
        following.join(2_000L);
        check(!leading.isAlive() && !following.isAlive(), "Voice frame writers did not finish");
        byte[] bytes = held.toByteArray();
        byte[] expected = {0, 4, 1, 2, 3, 4, 0, 4, 5, 6, 7, 8};
        check(bytes.length == expected.length, "Combined voice frames are " + bytes.length + " bytes");
        for (int i = 0; i < expected.length; i++) {
            check(bytes[i] == expected[i], "Voice frame byte " + i + " is " + bytes[i]);
        }
    }

    private static VoiceConnection connection(ServerPlayer player, OutputStream captured) throws Exception {
        VoiceConnection connection = new VoiceConnection(new Socket());
        Field uuid = VoiceConnection.class.getDeclaredField("uuid");
        uuid.setAccessible(true);
        uuid.set(connection, player.getUUID());
        Field output = VoiceConnection.class.getDeclaredField("output");
        output.setAccessible(true);
        output.set(connection, new DataOutputStream(captured));
        return connection;
    }

    private static final class HoldFirstByte extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private boolean holding = true;

        private HoldFirstByte(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void write(int b) throws IOException {
            if (holding) {
                holding = false;
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IOException("Voice frame hold was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            bytes.write(b);
        }

        private byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    private static boolean heard(ByteArrayOutputStream captured, byte[] audio) {
        byte[] bytes = captured.toByteArray();
        if (bytes.length != audio.length + 2 || bytes[0] != 0 || bytes[1] != audio.length) {
            return false;
        }
        for (int i = 0; i < audio.length; i++) {
            if (bytes[i + 2] != audio[i]) {
                return false;
            }
        }
        return true;
    }

    private static void track(GameTestHelper helper, List<ServerPlayer> tracked, ServerPlayer... players) throws Exception {
        @SuppressWarnings("unchecked")
        Map<UUID, ServerPlayer> byId = (Map<UUID, ServerPlayer>) field(helper.getLevel().getServer().getPlayerList(), "playersByUUID");
        for (ServerPlayer player : players) {
            byId.put(player.getUUID(), player);
            tracked.add(player);
        }
    }

    private static void untrack(GameTestHelper helper, List<ServerPlayer> tracked) {
        try {
            @SuppressWarnings("unchecked")
            Map<UUID, ServerPlayer> byId = (Map<UUID, ServerPlayer>) field(helper.getLevel().getServer().getPlayerList(), "playersByUUID");
            for (ServerPlayer player : tracked) {
                byId.remove(player.getUUID(), player);
            }
        } catch (Exception ignored) {
        }
    }

    private static void voiceEvent(GameTestHelper helper, String name, Class<?> eventType) throws Exception {
        Method method = MekanismAdditions.class.getDeclaredMethod(name, eventType);
        method.setAccessible(true);
        Object event = eventType.getConstructor(MinecraftServer.class).newInstance(helper.getLevel().getServer());
        method.invoke(MekanismAdditions.instance, event);
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String tooltip(ItemWalkieTalkie walkie, ItemStack stack, Level level) {
        List<Component> lines = new ArrayList<>();
        walkie.appendHoverText(stack, level, lines, TooltipFlag.Default.NORMAL);
        StringBuilder text = new StringBuilder();
        for (Component line : lines) {
            text.append(line.getString()).append('\n');
        }
        return text.toString();
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
