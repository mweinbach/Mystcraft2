package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.registry.MeteorRegistry;
import net.mystcraft.minecraft.entity.MeteorEntity;

/** Actual server entity flight and impact, with every fixture block restored afterwards. */
public final class MeteorSmoke {
    private MeteorSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        var passed = new ArrayList<String>();
        var meteor = new MeteorEntity(MeteorRegistry.METEOR.get(), level);
        Entity entity = meteor;
        meteor.setScale(2, 0);
        require(meteor.getScale() == 2 && entity.getBbWidth() == 2 && !entity.shouldBeSaved(),
                "Meteor scale/dimensions/source transient lifetime differ");
        boolean rejected = false;
        try { meteor.setScale(Float.NaN, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && meteor.getScale() == 2, "Invalid meteor scale mutated entity");
        meteor.setScale(1, 0);
        entity.setPos(3384.5, 260, 3384.5);
        entity.setDeltaMovement(0.125, -3, -0.25);
        entity.tick();
        require(entity.getX() == 3384.625 && entity.getY() == 257 && entity.getZ() == 3384.25
                && entity.getDeltaMovement().y == -3, "Meteor flight gained gravity/drag or lost source velocity");
        passed.add("meteor: synchronized scale, source transient lifetime, bounded dimensions and actual constant-velocity flight");
        BlockPos center = new BlockPos(3384, 200, 3384);
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> data = new LinkedHashMap<>();
        var area = new AABB(center).inflate(11);
        int before = level.getEntitiesOfClass(ItemEntity.class, area).size();
        try {
            for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-10, -8, -10), center.offset(10, 10, 10))) {
                BlockPos pos = cursor.immutable(); states.put(pos, level.getBlockState(pos));
                BlockEntity be = level.getBlockEntity(pos); if (be != null) data.put(pos, be.saveWithFullMetadata());
                // Prevent container onRemove from ejecting the inventory which we later restore.
                level.removeBlockEntity(pos);
                level.setBlock(pos, pos.getY() <= 200 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            entity.setPos(3384.5, 202, 3384.5);
            entity.setDeltaMovement(0, -3, 0);
            level.addFreshEntity(entity);
            entity.tick();
            require(entity.isRemoved(), "Zero-penetration meteor survived real ground impact");
            require(!level.getBlockState(center).is(Blocks.STONE), "Meteor impact failed to clear source penetration column");
            require(level.getEntitiesOfClass(ItemEntity.class, area).size() == before,
                    "Source no-drop meteor explosion spawned block drops");
            passed.add("meteor: real ground collision executes source penetration/eight-burst crater and removes meteor without block-item drops");
        } finally {
            entity.discard();
            for (var entry : states.entrySet()) {
                level.removeBlockEntity(entry.getKey());
                level.setBlock(entry.getKey(), entry.getValue(), 18);
            }
            for (var entry : data.entrySet()) {
                BlockEntity be = level.getBlockEntity(entry.getKey());
                if (be != null) { be.load(entry.getValue()); be.setChanged(); }
            }
        }
        return passed;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
