package ports.jurassicraft.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.mural.MuralType;

/** Places template entities after the blocks, each mural only in its anchor chunk. */
final class ParkHangingEntities {
    private ParkHangingEntities() {}
    static void place(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
                      BoundingBox bounds, ServerLevelAccessor level) {
        for (var source : template.entityInfoList) {
            CompoundTag tag = source.nbt.copy();
            String id = tag.getString("id");
            if (!id.equals("jurassicraft:mural") && !id.equals("minecraft:painting")) {
                placeOther(source, tag, origin, settings, bounds, level);
                continue;
            }
            BlockPos anchor = new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"));
            anchor = StructureTemplate.calculateRelativePosition(settings, anchor).offset(origin);
            String key = id.equals("minecraft:painting") ? "facing" : "Facing";
            Direction original = Direction.from2DDataValue(tag.getByte(key));
            Direction facing = settings.getRotation().rotate(settings.getMirror().mirror(original));
            if (id.equals("jurassicraft:mural") && settings.getMirror() != Mirror.NONE
                    && MuralType.byId(tag.getString("Artwork")).width % 32 == 0)
                anchor = anchor.relative(facing.getCounterClockWise().getOpposite());
            if (!bounds.isInside(anchor)) continue;
            tag.putInt("TileX", anchor.getX()); tag.putInt("TileY", anchor.getY()); tag.putInt("TileZ", anchor.getZ());
            tag.putByte(key, (byte) facing.get2DDataValue());
            tag.remove("UUID");
            Vec3 position = Vec3.atCenterOf(anchor);
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(position.x)); pos.add(DoubleTag.valueOf(position.y)); pos.add(DoubleTag.valueOf(position.z));
            tag.put("Pos", pos);
            Entity entity = EntityType.loadEntityRecursive(tag, level.getLevel(), e -> {
                e.moveTo(position.x, position.y, position.z, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity != null) level.addFreshEntityWithPassengers(entity);
        }
    }

    // A datapack may replace the original templates with ones containing other entities.
    // Retain vanilla's transform and finalization rules for those entities.
    private static void placeOther(StructureTemplate.StructureEntityInfo source, CompoundTag tag,
                                   BlockPos origin, StructurePlaceSettings settings,
                                   BoundingBox bounds, ServerLevelAccessor level) {
        BlockPos clipped = StructureTemplate.transform(source.blockPos, settings.getMirror(),
                settings.getRotation(), settings.getRotationPivot()).offset(origin);
        if (!bounds.isInside(clipped)) return;
        Vec3 local = StructureTemplate.transform(source.pos, settings.getMirror(), settings.getRotation(), settings.getRotationPivot());
        Vec3 pos = local.add(origin.getX(), origin.getY(), origin.getZ());
        ListTag coordinates = new ListTag();
        coordinates.add(DoubleTag.valueOf(pos.x)); coordinates.add(DoubleTag.valueOf(pos.y)); coordinates.add(DoubleTag.valueOf(pos.z));
        tag.put("Pos", coordinates);
        tag.remove("UUID");
        EntityType.create(tag, level.getLevel()).ifPresent(entity -> {
            float yaw = entity.rotate(settings.getRotation()) + entity.mirror(settings.getMirror()) - entity.getYRot();
            entity.moveTo(pos.x, pos.y, pos.z, yaw, entity.getXRot());
            if (settings.shouldFinalizeEntities() && entity instanceof Mob mob)
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(pos)), MobSpawnType.STRUCTURE, null, tag);
            level.addFreshEntityWithPassengers(entity);
        });
    }

}
