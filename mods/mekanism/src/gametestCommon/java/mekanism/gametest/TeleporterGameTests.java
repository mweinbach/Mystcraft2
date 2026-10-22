/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.math.FloatingLong;
import mekanism.api.security.SecurityMode;
import mekanism.api.text.EnumColor;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.lib.frequency.Frequency.FrequencyIdentity;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.frequency.IFrequencyItem;
import mekanism.common.lib.security.SecurityFrequency;
import mekanism.common.network.to_server.PacketPortableTeleporterTeleport;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A teleporter frame is 3 wide and 4 tall. The teleporter block is the bottom centre, the portal is the two blocks
 * above it, and the ninth frame block closes the top. Frequencies are public or private. Trusted is the security
 * mode on the block: the owner's security frequency lists who else may open it, and that list does not share a
 * private frequency.
 */
public class TeleporterGameTests {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final AtomicInteger NETHER_SLOTS = new AtomicInteger();
    private static final long ENERGY = 1_000;
    private static final long FLUID = 1_000;
    private static final long GAS = 500;
    private static final int ITEMS = 16;
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos DESTINATION = new BlockPos(1, 1, 6);

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void teleporterFrameForms(GameTestHelper helper) {
        helper.setBlock(SOURCE, MekanismBlocks.TELEPORTER.getBlock());
        int[] phase = {0};
        helper.succeedWhen(() -> {
            TileEntityTeleporter tile = teleporter(helper, SOURCE);
            if (phase[0] == 0) {
                check(tile.status == 2, "Bare teleporter status is " + tile.status);
                BlockPos absolute = helper.absolutePos(SOURCE);
                AABB bare = TestPlatform.renderBounds(tile);
                check(bare.equals(new AABB(absolute, absolute.offset(1, 1, 1))), "Bare render bounds are " + bare);
                placeFrameAround(helper, SOURCE);
                phase[0] = 1;
                throw new GameTestAssertException("frame placed");
            }
            if (phase[0] == 1) {
                check(tile.status == 3, "Framed teleporter with no link status is " + tile.status);
                check(!tile.shouldRender, "An unlinked teleporter is drawing a portal");
                BlockPos absolute = helper.absolutePos(SOURCE);
                AABB portal = new AABB(absolute.above(), absolute.offset(1, 3, 1));
                AABB bounds = TestPlatform.renderBounds(tile);
                check(bounds.equals(portal), "Portal render bounds " + bounds + " are not " + portal);
                String name = frequency("pub");
                UUID owner = UUID.randomUUID();
                placeFrame(helper, DESTINATION);
                link(teleporter(helper, SOURCE), name, true, owner);
                link(teleporter(helper, DESTINATION), name, true, owner);
                charge(teleporter(helper, SOURCE));
                charge(teleporter(helper, DESTINATION));
                phase[0] = 2;
                throw new GameTestAssertException("frequency set");
            }
            if (phase[0] == 2) {
                TileEntityTeleporter other = teleporter(helper, DESTINATION);
                check(tile.status == 1 && other.status == 1, "Linked status is " + tile.status + " and " + other.status);
                check(tile.shouldRender && other.shouldRender, "A ready teleporter is not drawing a portal");
                check(tile.getColor() == EnumColor.PURPLE && other.getColor() == EnumColor.PURPLE,
                      "Portal color is " + tile.getColor() + " and " + other.getColor());
                helper.setBlock(SOURCE.offset(1, 0, 0), Blocks.AIR);
                phase[0] = 3;
                throw new GameTestAssertException("frame broken");
            }
            check(tile.status == 2, "A broken frame stayed status " + tile.status);
            check(!tile.shouldRender, "A broken frame is still drawing a portal");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void teleporterMovesEntityOnPublicFrequency(GameTestHelper helper) {
        placeFrame(helper, SOURCE);
        placeFrame(helper, DESTINATION);
        String name = frequency("move");
        UUID owner = UUID.randomUUID();
        link(teleporter(helper, SOURCE), name, true, owner);
        link(teleporter(helper, DESTINATION), name, true, owner);
        charge(teleporter(helper, SOURCE));
        charge(teleporter(helper, DESTINATION));
        int[] phase = {0};
        Pig[] pig = {null};
        FloatingLong[] energy = {null};
        helper.succeedWhen(() -> {
            TileEntityTeleporter source = teleporter(helper, SOURCE);
            TileEntityTeleporter destination = teleporter(helper, DESTINATION);
            if (phase[0] == 0) {
                check(source.status == 1 && destination.status == 1, "Teleporters are not ready: " + source.status + " and " + destination.status);
                energy[0] = source.getEnergyContainer().getEnergy().copy();
                pig[0] = helper.spawn(EntityType.PIG, SOURCE.getX(), SOURCE.getY() + 1, SOURCE.getZ());
                pig[0].setNoAi(true);
                pig[0].setNoGravity(true);
                pig[0].setDeltaMovement(Vec3.ZERO);
                phase[0] = 1;
                throw new GameTestAssertException("pig placed");
            }
            BlockPos landed = helper.absolutePos(DESTINATION.above());
            Vec3 at = new Vec3(landed.getX() + 0.5, landed.getY(), landed.getZ() + 0.5);
            check(pig[0].distanceToSqr(at) < 0.05, "Pig is at " + pig[0].position() + ", expected " + at);
            check(pig[0].level == helper.getLevel(), "Pig left the dimension");
            check(source.getEnergyContainer().getEnergy().smallerThan(energy[0]), "Teleport spent no energy");
            check(destination.didTeleport.contains(pig[0].getUUID()), "Destination did not remember the arrival");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void privateFrequencyStaysWithItsOwner(GameTestHelper helper) {
        placeFrame(helper, SOURCE);
        placeFrame(helper, DESTINATION);
        String name = frequency("prv");
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        link(teleporter(helper, SOURCE), name, false, owner);
        link(teleporter(helper, DESTINATION), name, false, other);
        charge(teleporter(helper, SOURCE));
        charge(teleporter(helper, DESTINATION));
        int[] phase = {0};
        helper.succeedWhen(() -> {
            TileEntityTeleporter source = teleporter(helper, SOURCE);
            TileEntityTeleporter destination = teleporter(helper, DESTINATION);
            if (phase[0] == 0) {
                check(source.status == 3 && destination.status == 3,
                      "Different owners on one private name linked: " + source.status + " and " + destination.status);
                link(source, name, false, owner);
                link(destination, name, false, owner);
                phase[0] = 1;
                throw new GameTestAssertException("same owner");
            }
            check(source.status == 1 && destination.status == 1,
                  "One owner's private frequency did not link: " + source.status + " and " + destination.status);
            FrequencyIdentity identity = new FrequencyIdentity(name, false);
            TeleporterFrequency stored = FrequencyType.TELEPORTER.getFrequency(identity, owner);
            TeleporterFrequency others = FrequencyType.TELEPORTER.getFrequency(identity, other);
            check(stored != null && !stored.isPublic() && stored.ownerMatches(owner), "Private frequency was not stored for its owner");
            check(others != stored, "The other player received the owner's private frequency");
            check(others == null || others.getActiveCoords().isEmpty(),
                  "The other owner's private frequency still points at a teleporter");
            check(FrequencyType.TELEPORTER.getFrequency(identity, UUID.randomUUID()) == null,
                  "A player who never created it received that private frequency");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void trustedSecurityDoesNotSharePrivateFrequency(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), MekanismBlocks.TELEPORTER.getBlock());
        helper.setBlock(new BlockPos(4, 1, 1), MekanismBlocks.QUANTUM_ENTANGLOPORTER.getBlock());
        TileEntityTeleporter teleporter = teleporter(helper, new BlockPos(1, 1, 1));
        TileEntityQuantumEntangloporter entangloporter = entangloporter(helper, new BlockPos(4, 1, 1));
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        trust(teleporter, owner, trusted);
        trust(entangloporter, owner, trusted);
        String name = frequency("own");
        teleporter.setFrequency(FrequencyType.TELEPORTER, new FrequencyIdentity(name, false), owner);
        helper.succeedWhen(() -> {
            check(MekanismConfig.general.allowProtection.get(), "Security is disabled, so trusted mode cannot be told from public");
            check(MekanismAPI.getSecurityUtils().canAccess(owner, teleporter, false), "The owner was refused the teleporter");
            check(MekanismAPI.getSecurityUtils().canAccess(trusted, teleporter, false), "A trusted player was refused the teleporter");
            check(!MekanismAPI.getSecurityUtils().canAccess(stranger, teleporter, false), "A stranger opened a trusted teleporter");
            check(MekanismAPI.getSecurityUtils().canAccess(trusted, entangloporter, false), "A trusted player was refused the entangloporter");
            check(!MekanismAPI.getSecurityUtils().canAccess(stranger, entangloporter, false), "A stranger opened a trusted entangloporter");
            check(FrequencyType.TELEPORTER.getFrequency(new FrequencyIdentity(name, false), trusted) == null,
                  "Trusted access handed over the owner's private frequency");
            SecurityFrequency security = FrequencyType.SECURITY.getManager(null).getFrequency(owner);
            check(security != null && security.getTrustedUUIDs().contains(trusted), "The trusted player is not on the security frequency");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void teleporterCrossesDimensions(GameTestHelper helper) {
        placeFrame(helper, SOURCE);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        check(nether != null, "The nether is not loaded");
        BlockPos netherPos = new BlockPos(8 + NETHER_SLOTS.getAndIncrement() * 48, 80, 8);
        ChunkPos chunk = new ChunkPos(netherPos);
        nether.getChunk(chunk.x, chunk.z);
        nether.setChunkForced(chunk.x, chunk.z, true);
        clearNether(nether, netherPos);
        placeFrame(nether, netherPos);
        BlockEntity placed = nether.getBlockEntity(netherPos);
        check(placed instanceof TileEntityTeleporter, "The nether frame has no teleporter: " + placed);
        TileEntityTeleporter remote = (TileEntityTeleporter) placed;
        String name = frequency("dim");
        UUID owner = UUID.randomUUID();
        link(teleporter(helper, SOURCE), name, true, owner);
        link(remote, name, true, owner);
        charge(teleporter(helper, SOURCE));
        charge(remote);
        int[] phase = {0};
        Pig[] pig = {null};
        helper.succeedWhen(() -> {
            TileEntityTeleporter source = teleporter(helper, SOURCE);
            if (phase[0] == 0) {
                check(source.status == 1, "Cross-dimension teleporter status is " + source.status);
                pig[0] = helper.spawn(EntityType.PIG, SOURCE.getX(), SOURCE.getY() + 1, SOURCE.getZ());
                pig[0].setNoAi(true);
                pig[0].setNoGravity(true);
                pig[0].setDeltaMovement(Vec3.ZERO);
                pig[0].setCustomName(Component.literal(name));
                phase[0] = 1;
                throw new GameTestAssertException("pig placed");
            }
            BlockPos landed = netherPos.above();
            Vec3 at = new Vec3(landed.getX() + 0.5, landed.getY(), landed.getZ() + 0.5);
            List<Pig> arrived = nether.getEntitiesOfClass(Pig.class, new AABB(landed).inflate(2), entity -> named(entity, name));
            if (arrived.size() != 1) {
                List<Pig> farther = nether.getEntitiesOfClass(Pig.class, new AABB(netherPos).inflate(1024), entity -> named(entity, name));
                String where = farther.isEmpty() ? "none" : farther.get(0).position().toString();
                check(false, "Nether pigs at the portal: " + arrived.size() + "; named pig: " + where
                      + "; overworld alive=" + pig[0].isAlive() + " at " + pig[0].position());
            }
            check(arrived.get(0).distanceToSqr(at) < 1.0, "Nether pig is at " + arrived.get(0).position() + ", expected " + at);
            check(!pig[0].isAlive(), "The overworld pig is still present at " + pig[0].position());
            nether.setChunkForced(chunk.x, chunk.z, false);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void portableTeleporterMovesItsOwner(GameTestHelper helper) {
        placeFrame(helper, SOURCE);
        String name = frequency("port");
        UUID owner = UUID.randomUUID();
        link(teleporter(helper, SOURCE), name, true, owner);
        UUID stranger = UUID.randomUUID();
        String hidden = frequency("hide");
        FrequencyIdentity hiddenId = new FrequencyIdentity(hidden, false);
        FrequencyType.TELEPORTER.getManager(hiddenId, stranger).getOrCreateFrequency(hiddenId, stranger);
        ServerPlayer player = survival(helper, new BlockPos(6, 2, 4));
        int[] phase = {0};
        boolean[] reported = {false};
        boolean[] spent = {false};
        helper.succeedWhen(() -> {
            TileEntityTeleporter tile = teleporter(helper, SOURCE);
            if (phase[0] == 0) {
                check(tile.status == 3, "A lone framed teleporter status is " + tile.status);
                ItemStack locked = chargedPortable(FrequencyType.TELEPORTER.getFrequency(hiddenId, stranger));
                player.setItemInHand(InteractionHand.MAIN_HAND, locked);
                boolean moved = new PacketPortableTeleporterTeleport(InteractionHand.MAIN_HAND, hiddenId).teleport(player);
                check(!moved, "A private frequency owned by someone else teleported the player");
                check(stillAt(player, helper.absolutePos(new BlockPos(6, 2, 4))), "The refused teleport moved the player to " + player.position());
                TeleporterFrequency frequency = tile.getFrequency(FrequencyType.TELEPORTER);
                ItemStack stack = chargedPortable(frequency);
                IEnergyContainer energy = StorageUtils.getEnergyContainer(stack, 0);
                check(energy != null && energy.getEnergy().greaterThan(FloatingLong.ZERO), "The portable teleporter holds no energy");
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                phase[0] = 1;
                throw new GameTestAssertException("armed");
            }
            if (phase[0] == 1) {
                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                FloatingLong before = StorageUtils.getEnergyContainer(stack, 0).getEnergy().copy();
                FrequencyIdentity identity = ((IFrequencyItem) stack.getItem()).getFrequencyIdentity(stack);
                boolean moved = new PacketPortableTeleporterTeleport(InteractionHand.MAIN_HAND, identity).teleport(player);
                FloatingLong after = StorageUtils.getEnergyContainer(player.getItemInHand(InteractionHand.MAIN_HAND), 0).getEnergy();
                if (!moved && after.equals(before)) {
                    throw new GameTestAssertException("Portable destination is not ready; player is at " + player.position());
                }
                reported[0] = moved;
                spent[0] = after.smallerThan(before);
                phase[0] = 2;
            }
            BlockPos landed = helper.absolutePos(SOURCE.above());
            check(reported[0], "Portable teleporter did not report a teleport; player is at " + player.position());
            check(near(player, landed), "Portable teleporter left the player at " + player.position());
            check(spent[0], "Portable teleporter spent no energy");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void entangloporterSharesEnergyFluidGasAndItems(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), MekanismBlocks.QUANTUM_ENTANGLOPORTER.getBlock());
        helper.setBlock(new BlockPos(1, 1, 4), MekanismBlocks.QUANTUM_ENTANGLOPORTER.getBlock());
        TileEntityQuantumEntangloporter from = entangloporter(helper, new BlockPos(1, 1, 1));
        TileEntityQuantumEntangloporter to = entangloporter(helper, new BlockPos(1, 1, 4));
        String name = frequency("qe");
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        int[] phase = {0};
        long[] moved = new long[16];
        helper.succeedWhen(() -> {
            Direction input = inputSide(from);
            Direction output = outputSide(to);
            ItemStack cobble = new ItemStack(Items.COBBLESTONE, ITEMS);
            if (phase[0] == 0) {
                moved[0] = TestPlatform.insertEnergy(from, input, ENERGY);
                moved[1] = TestPlatform.insertBlockFluid(from, input, Fluids.WATER, FLUID, true);
                moved[2] = TestPlatform.insertBlockGas(from, input, MekanismGases.HYDROGEN.get(), GAS, true);
                moved[3] = TestPlatform.insertItems(from, input, cobble);
                from.setFrequency(FrequencyType.INVENTORY, new FrequencyIdentity(name, false), owner);
                to.setFrequency(FrequencyType.INVENTORY, new FrequencyIdentity(name, false), other);
                phase[0] = 1;
                throw new GameTestAssertException("separate private frequencies");
            }
            if (phase[0] == 1) {
                check(moved[0] == 0, "An unlinked entangloporter accepted energy");
                check(moved[1] == 0, "An unlinked entangloporter accepted water");
                check(moved[2] == 0, "An unlinked entangloporter accepted hydrogen");
                check(moved[3] == 0, "An unlinked entangloporter accepted items");
                check(from.hasFrequency() && to.hasFrequency(), "A private frequency was not selected");
                moved[4] = TestPlatform.insertEnergy(from, input, ENERGY);
                moved[5] = TestPlatform.extractEnergy(to, output, ENERGY);
                from.setFrequency(FrequencyType.INVENTORY, new FrequencyIdentity(name, true), owner);
                to.setFrequency(FrequencyType.INVENTORY, new FrequencyIdentity(name, true), other);
                phase[0] = 2;
                throw new GameTestAssertException("public frequency");
            }
            if (phase[0] == 2) {
                check(moved[4] == ENERGY, "The owner's entangloporter accepted " + moved[4] + " of " + ENERGY + " FE");
                check(moved[5] == 0, "Another owner's private frequency returned energy");
                moved[6] = TestPlatform.insertEnergy(from, outputSide(from), ENERGY);
                moved[7] = TestPlatform.insertEnergy(from, input, ENERGY);
                moved[8] = TestPlatform.extractEnergy(to, output, ENERGY);
                phase[0] = 3;
                throw new GameTestAssertException("shared energy");
            }
            if (phase[0] == 3) {
                check(moved[6] == 0, "The output side accepted energy");
                check(moved[7] == ENERGY, "Accepted " + moved[7] + " of " + ENERGY + " FE");
                check(moved[8] == ENERGY, "The other entangloporter returned " + moved[8] + " FE");
                moved[9] = TestPlatform.insertBlockFluid(from, input, Fluids.WATER, FLUID, false);
                moved[10] = TestPlatform.blockFluid(to, output);
                phase[0] = 4;
                throw new GameTestAssertException("simulated water");
            }
            if (phase[0] == 4) {
                check(moved[9] == FLUID, "A simulated fill offered less water than requested");
                check(moved[10] == 0, "A simulated fill stored water");
                moved[11] = TestPlatform.insertBlockFluid(from, input, Fluids.WATER, FLUID, true);
                moved[12] = TestPlatform.extractBlockFluid(to, output, Fluids.WATER, FLUID, true);
                phase[0] = 5;
                throw new GameTestAssertException("shared water");
            }
            if (phase[0] == 5) {
                check(moved[11] == FLUID, "The entangloporter accepted less water than requested");
                check(moved[12] == FLUID, "The other entangloporter returned less water than it stored");
                moved[13] = TestPlatform.insertBlockGas(from, input, MekanismGases.HYDROGEN.get(), GAS, false);
                moved[14] = TestPlatform.blockGas(to, output);
                phase[0] = 6;
                throw new GameTestAssertException("simulated hydrogen");
            }
            if (phase[0] == 6) {
                check(moved[13] == GAS, "A simulated fill offered less hydrogen than requested");
                check(moved[14] == 0, "A simulated fill stored hydrogen");
                moved[15] = TestPlatform.insertBlockGas(from, input, MekanismGases.HYDROGEN.get(), GAS, true);
                phase[0] = 7;
                throw new GameTestAssertException("stored hydrogen");
            }
            if (phase[0] == 7) {
                check(moved[15] == GAS, "The entangloporter accepted less hydrogen than requested");
                moved[0] = TestPlatform.extractGas(to, output, GAS, true).getAmount();
                phase[0] = 8;
                throw new GameTestAssertException("shared hydrogen");
            }
            if (phase[0] == 8) {
                check(moved[0] == GAS, "The other entangloporter returned " + moved[0] + " hydrogen");
                moved[1] = TestPlatform.insertItems(from, input, cobble);
                moved[2] = TestPlatform.extractItems(to, output, cobble);
                phase[0] = 9;
                throw new GameTestAssertException("shared items");
            }
            check(moved[1] == ITEMS, "The entangloporter accepted fewer items than requested");
            check(moved[2] == ITEMS, "The other entangloporter returned fewer items than it stored");
        });
    }

    private static boolean named(Pig pig, String name) {
        return pig.isAlive() && pig.hasCustomName() && pig.getCustomName() != null && name.equals(pig.getCustomName().getString());
    }

    private static String frequency(String prefix) {
        return prefix + Integer.toString(IDS.incrementAndGet(), 36);
    }

    private static void placeFrame(GameTestHelper helper, BlockPos origin) {
        helper.setBlock(origin, MekanismBlocks.TELEPORTER.getBlock());
        placeFrameAround(helper, origin);
    }

    private static void placeFrameAround(GameTestHelper helper, BlockPos origin) {
        Block frame = MekanismBlocks.TELEPORTER_FRAME.getBlock();
        for (int dy = 0; dy <= 3; dy++) {
            helper.setBlock(origin.offset(-1, dy, 0), frame);
            helper.setBlock(origin.offset(1, dy, 0), frame);
        }
        helper.setBlock(origin.offset(0, 3, 0), frame);
    }

    private static void placeFrame(ServerLevel level, BlockPos origin) {
        BlockState teleporter = MekanismBlocks.TELEPORTER.getBlock().defaultBlockState();
        BlockState frame = MekanismBlocks.TELEPORTER_FRAME.getBlock().defaultBlockState();
        level.setBlockAndUpdate(origin, teleporter);
        for (int dy = 0; dy <= 3; dy++) {
            level.setBlockAndUpdate(origin.offset(-1, dy, 0), frame);
            level.setBlockAndUpdate(origin.offset(1, dy, 0), frame);
        }
        level.setBlockAndUpdate(origin.offset(0, 3, 0), frame);
    }

    private static void clearNether(ServerLevel nether, BlockPos origin) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlockAndUpdate(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void link(TileEntityTeleporter tile, String name, boolean isPublic, UUID owner) {
        tile.setFrequency(FrequencyType.TELEPORTER, new FrequencyIdentity(name, isPublic), owner);
    }

    private static void charge(TileEntityTeleporter tile) {
        FloatingLong leftover = tile.getEnergyContainer().insert(FloatingLong.createConst(1_000_000), Action.EXECUTE, AutomationType.INTERNAL);
        check(leftover.smallerThan(FloatingLong.createConst(1_000_000)), "The teleporter accepted no energy");
    }

    private static void trust(TileEntityTeleporter tile, UUID owner, UUID trusted) {
        tile.setOwnerUUID(owner);
        tile.setSecurityMode(SecurityMode.TRUSTED);
        SecurityFrequency security = FrequencyType.SECURITY.getManager(null).getFrequency(owner);
        check(security != null, "Setting an owner did not create a security frequency");
        security.addTrusted(trusted, "trusted");
    }

    private static void trust(TileEntityQuantumEntangloporter tile, UUID owner, UUID trusted) {
        tile.setOwnerUUID(owner);
        tile.setSecurityMode(SecurityMode.TRUSTED);
    }

    private static TileEntityTeleporter teleporter(GameTestHelper helper, BlockPos pos) {
        BlockEntity block = helper.getBlockEntity(pos);
        check(block instanceof TileEntityTeleporter, "No teleporter at " + pos + ": " + block);
        return (TileEntityTeleporter) block;
    }

    private static TileEntityQuantumEntangloporter entangloporter(GameTestHelper helper, BlockPos pos) {
        BlockEntity block = helper.getBlockEntity(pos);
        check(block instanceof TileEntityQuantumEntangloporter, "No entangloporter at " + pos + ": " + block);
        return (TileEntityQuantumEntangloporter) block;
    }

    private static Direction inputSide(TileEntityQuantumEntangloporter tile) {
        return RelativeSide.BACK.getDirection(tile.getDirection());
    }

    private static Direction outputSide(TileEntityQuantumEntangloporter tile) {
        return RelativeSide.FRONT.getDirection(tile.getDirection());
    }

    private static ItemStack chargedPortable(TeleporterFrequency frequency) {
        ItemStack stack = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.PORTABLE_TELEPORTER.get()),
              MekanismConfig.gear.portableTeleporterMaxEnergy.get());
        ((IFrequencyItem) stack.getItem()).setFrequency(stack, frequency);
        return stack;
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "portable"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static boolean stillAt(ServerPlayer player, BlockPos absolute) {
        return Math.abs(player.getX() - (absolute.getX() + 0.5)) < 0.2
              && Math.abs(player.getY() - absolute.getY()) < 0.2
              && Math.abs(player.getZ() - (absolute.getZ() + 0.5)) < 0.2;
    }

    private static boolean near(ServerPlayer player, BlockPos absolute) {
        return Math.abs(player.getX() - (absolute.getX() + 0.5)) < 1.0
              && Math.abs(player.getY() - absolute.getY()) < 1.0
              && Math.abs(player.getZ() - (absolute.getZ() + 0.5)) < 1.0;
    }
}
