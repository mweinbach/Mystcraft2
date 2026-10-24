package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ports.gridwright.block.WardKind;
import ports.gridwright.registry.ModUtilities;
import ports.gridwright.storage.WardBlockEntity;
import ports.gridwright.storage.Wards;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.grid;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.platform;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/**
 * Wards. Every test runs in its own batch and removes its wards before it succeeds: a ward reaches
 * well past its own arena, and GameTests in one batch share a level.
 */
public class WardGameTests {

    /** A mob made but never added to the level, standing {@code east} blocks east of {@code pos}'s centre. */
    private static <T extends Mob> T unspawned(GameTestHelper helper, EntityType<T> type, BlockPos pos, double east) {
        T mob = type.create(helper.getLevel());
        check(mob != null, "Cannot create " + type);
        BlockPos at = helper.absolutePos(pos);
        mob.moveTo(at.getX() + 0.5 + east, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        return mob;
    }

    private static WardBlockEntity ward(GameTestHelper helper, WardKind kind, BlockPos pos) {
        return place(helper, ModUtilities.WARDS.get(kind), pos);
    }

    @GameTest(template = TEMPLATE, batch = "gridwright_ward_spawn")
    public static void spawnWardStopsHostileNaturalSpawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wardPos = new BlockPos(1, 1, 1);
        BlockPos standing = new BlockPos(5, 2, 5);
        // Slimes are hostile and, unlike most monsters, pass vanilla's instance spawn rules anywhere.
        Slime slime = unspawned(helper, EntityType.SLIME, standing, 0);
        Slime outside = unspawned(helper, EntityType.SLIME, standing, WardKind.SPAWN.radius() + 4);
        Cow cow = unspawned(helper, EntityType.COW, standing, 0);
        check(platform().naturalSpawnAllowed(level, slime), "Without a ward the slime may spawn");

        WardBlockEntity ward = ward(helper, WardKind.SPAWN, wardPos);
        check(ward.working() && ward.status() == WardBlockEntity.Status.ACTIVE, "A spawn ward needs no power");
        check(Wards.deniesNaturalSpawn(level, slime), "The ward covers the slime");
        check(!platform().naturalSpawnAllowed(level, slime), "The loader's spawn check must refuse the slime");
        check(!Wards.deniesNaturalSpawn(level, outside), "The ward reaches only " + WardKind.SPAWN.radius() + " blocks");
        check(!Wards.deniesNaturalSpawn(level, cow), "Passive creatures still spawn");

        helper.setBlock(wardPos, Blocks.AIR);
        check(!Wards.deniesNaturalSpawn(level, slime), "Breaking the ward ends its effect");
        check(platform().naturalSpawnAllowed(level, slime), "The slime may spawn again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "gridwright_ward_greater")
    public static void greaterSpawnWardRunsOnGridPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Owner owner = Owner.random("gwWd");
        AtomicBoolean powered = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (powered.get()) grid(helper).addProduction(owner.gridKey(), "test", 64.0);
        });
        BlockPos wardPos = new BlockPos(1, 1, 1);
        BlockPos orphanPos = new BlockPos(6, 1, 6);
        WardBlockEntity ward = owned(ward(helper, WardKind.GREATER_SPAWN, wardPos), owner);
        WardBlockEntity orphan = ward(helper, WardKind.GREATER_SPAWN, orphanPos);
        // Thirty blocks out: beyond a plain spawn ward, inside a greater one.
        Slime distant = unspawned(helper, EntityType.SLIME, new BlockPos(1, 2, 1), 30);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    check(!ward.working() && ward.status() == WardBlockEntity.Status.UNPOWERED,
                            "A greater ward on an empty grid must not work: " + ward.status());
                    check(!orphan.working() && orphan.status() == WardBlockEntity.Status.NO_OWNER,
                            "A ward without an owner has no grid: " + orphan.status());
                    check(!Wards.deniesNaturalSpawn(level, distant), "Unpowered wards deny nothing");
                    powered.set(true);
                })
                .thenExecuteAfter(3, () -> {
                    check(ward.working() && ward.status() == WardBlockEntity.Status.ACTIVE, "A powered greater ward works");
                    check(snapshot(helper, owner.gridKey()).demand() >= WardKind.GREATER_SPAWN.gp() - 1e-6,
                            "The ward should draw " + WardKind.GREATER_SPAWN.gp() + " GP: " + snapshot(helper, owner.gridKey()));
                    check(Wards.deniesNaturalSpawn(level, distant), "A greater ward reaches " + WardKind.GREATER_SPAWN.radius() + " blocks");
                    check(!platform().naturalSpawnAllowed(level, distant), "The loader's spawn check must refuse the slime");
                    powered.set(false);
                })
                .thenExecuteAfter(3, () -> {
                    check(!ward.working() && ward.status() == WardBlockEntity.Status.UNPOWERED, "The grid lost its power");
                    check(!Wards.deniesNaturalSpawn(level, distant), "An unpowered ward stops working");
                    helper.setBlock(wardPos, Blocks.AIR);
                    helper.setBlock(orphanPos, Blocks.AIR);
                })
                .thenSucceed();
    }

    private static boolean teleport(EnderMan enderman, BlockPos target) {
        try {
            Method teleport = EnderMan.class.getDeclaredMethod("teleport", double.class, double.class, double.class);
            teleport.setAccessible(true);
            return (boolean) teleport.invoke(enderman, target.getX() + 0.5, (double) target.getY(), target.getZ() + 0.5);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot call EnderMan.teleport", e);
        }
    }

    @GameTest(template = TEMPLATE, batch = "gridwright_ward_ender")
    public static void enderWardStopsEndermanTeleports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        BlockPos start = new BlockPos(1, 1, 1);
        BlockPos target = new BlockPos(6, 1, 6);
        BlockPos wardPos = new BlockPos(3, 1, 6);
        EnderMan enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, start);
        BlockPos absoluteTarget = helper.absolutePos(target);
        BlockPos absoluteStart = helper.absolutePos(start);

        check(teleport(enderman, absoluteTarget), "Without a ward the enderman teleports");
        check(enderman.blockPosition().equals(absoluteTarget), "Enderman ended at " + enderman.blockPosition());

        WardBlockEntity ward = ward(helper, WardKind.ENDER, wardPos);
        check(ward.working(), "An ender ward needs no power");
        Vec3 before = enderman.position();
        check(!teleport(enderman, absoluteStart), "A ward stops the enderman teleporting");
        check(enderman.position().equals(before), "The enderman moved to " + enderman.position());
        check(Wards.deniesTeleport(level, enderman, before.x + 100, before.y, before.z), "Leaving the area is refused too");
        Cow cow = unspawned(helper, EntityType.COW, start, 0);
        check(!Wards.deniesTeleport(level, cow, before.x, before.y, before.z), "Only endermen are held");

        helper.setBlock(wardPos, Blocks.AIR);
        check(teleport(enderman, absoluteStart), "Breaking the ward frees the enderman");
        enderman.discard();
        helper.succeed();
    }
}
