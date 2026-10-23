package ports.buildcraft.builders.snapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.builders.snapshot.rules.JsonRule;
import ports.buildcraft.builders.snapshot.rules.NbtJson;
import ports.buildcraft.builders.snapshot.rules.RulesLoader;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code SchematicEntityDefault}: the saved data of an entity a capturing rule matches, its position
 * relative to the scanned box and, for paintings and item frames, the block they hang on and its side.
 */
public class SchematicEntityDefault implements SchematicEntity {
    private CompoundTag entityNbt;
    private Vec3 pos;
    private BlockPos hangingPos;
    private Direction hangingFacing;
    private Rotation entityRotation = Rotation.NONE;

    /**
     * Upstream {@code Entity.serializeNBT}: the saved data with the entity id, or null for entities that are
     * never saved; {@linkplain SchematicSafety#sanitizeEntityNbt sanitized}, so riders and loot tables are not recorded.
     */
    @Nullable
    static CompoundTag entityNbt(Entity entity) {
        EntityType<?> type = entity.getType();
        if (!type.canSerialize()) return null;
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(type).toString());
        return SchematicSafety.sanitizeEntityNbt(entity.saveWithoutId(tag));
    }

    public static boolean predicate(SchematicEntityContext context) {
        ResourceLocation registryName = Registry.ENTITY_TYPE.getKey(context.entity().getType());
        CompoundTag nbt = entityNbt(context.entity());
        return nbt != null && RulesLoader.readDomains().contains(registryName.getNamespace())
                && RulesLoader.getRules(registryName, nbt).stream().anyMatch(rule -> rule.capture);
    }

    @Override
    public void init(SchematicEntityContext context) {
        entityNbt = Objects.requireNonNull(entityNbt(context.entity()));
        pos = context.entity().position().subtract(Vec3.atLowerCornerOf(context.basePos()));
        if (context.entity() instanceof HangingEntity hanging) {
            hangingPos = hanging.getPos().subtract(context.basePos());
            hangingFacing = hanging.getDirection();
        } else {
            hangingPos = new BlockPos(pos);
            hangingFacing = Direction.NORTH;
        }
    }

    @Override
    public Vec3 getPos() {
        return pos;
    }

    private Set<JsonRule> rules() {
        return RulesLoader.getRules(new ResourceLocation(entityNbt.getString("id")), entityNbt);
    }

    @Override
    public List<ItemStack> computeRequiredItems() {
        Set<JsonRule> rules = rules();
        if (rules.isEmpty()) throw new IllegalArgumentException("Rules are empty");
        return rules.stream().map(rule -> rule.requiredExtractors).filter(Objects::nonNull).flatMap(List::stream)
                .flatMap(extractor -> extractor.extractItemsFromEntity(entityNbt).stream())
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    @Override
    public List<FluidAmount> computeRequiredFluids() {
        return rules().stream().map(rule -> rule.requiredExtractors).filter(Objects::nonNull).flatMap(List::stream)
                .flatMap(extractor -> extractor.extractFluidsFromEntity(entityNbt).stream())
                .filter(fluid -> !fluid.isEmpty())
                .toList();
    }

    @Override
    public SchematicEntityDefault getRotated(Rotation rotation) {
        SchematicEntityDefault schematicEntity = SchematicEntityManager.createCleanCopy(this);
        schematicEntity.entityNbt = entityNbt;
        schematicEntity.pos = rotate(pos, rotation);
        schematicEntity.hangingPos = hangingPos.rotate(rotation);
        schematicEntity.hangingFacing = rotation.rotate(hangingFacing);
        schematicEntity.entityRotation = entityRotation.getRotated(rotation);
        return schematicEntity;
    }

    /** Upstream {@code RotationUtil.rotateVec3d}: turns a point so it stays in the block {@link BlockPos#rotate} moves its block to. */
    private static Vec3 rotate(Vec3 vec, Rotation rotation) {
        return switch (rotation) {
            case NONE -> vec;
            case CLOCKWISE_90 -> new Vec3(1 - vec.z, vec.y, vec.x);
            case CLOCKWISE_180 -> new Vec3(1 - vec.x, vec.y, 1 - vec.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(vec.z, vec.y, 1 - vec.x);
        };
    }

    /**
     * Hanging entities are moved onto their rotated block and side. In 1.19 item frames store their side as
     * {@code Facing} and paintings as {@code facing}; 1.12 used {@code Facing} for both. Other entities turn
     * by the rotation; upstream applied the turn in reverse, which is fixed here.
     */
    @Nullable
    @Override
    public Entity build(Level level, BlockPos basePos) {
        Entity entity = createPreview(level, basePos);
        if (entity != null) level.addFreshEntity(entity);
        return entity;
    }

    @Nullable
    @Override
    public Entity createPreview(Level level, BlockPos basePos) {
        CompoundTag replaceNbt = rules().stream().map(rule -> rule.replaceNbt).filter(Objects::nonNull)
                .map(Tag.class::cast).reduce(NbtJson::merge).map(CompoundTag.class::cast).orElse(null);
        Vec3 placePos = Vec3.atLowerCornerOf(basePos).add(pos);
        BlockPos placeHangingPos = basePos.offset(hangingPos);
        CompoundTag newEntityNbt = entityNbt.copy();
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(placePos.x));
        posTag.add(DoubleTag.valueOf(placePos.y));
        posTag.add(DoubleTag.valueOf(placePos.z));
        newEntityNbt.put("Pos", posTag);
        newEntityNbt.putUUID("UUID", UUID.randomUUID());
        boolean rotate = false;
        if (newEntityNbt.contains("TileX") && newEntityNbt.contains("TileY") && newEntityNbt.contains("TileZ")
                && (newEntityNbt.contains("Facing") || newEntityNbt.contains("facing"))) {
            newEntityNbt.putInt("TileX", placeHangingPos.getX());
            newEntityNbt.putInt("TileY", placeHangingPos.getY());
            newEntityNbt.putInt("TileZ", placeHangingPos.getZ());
            if (newEntityNbt.contains("Facing")) newEntityNbt.putByte("Facing", (byte) hangingFacing.get3DDataValue());
            else newEntityNbt.putByte("facing", (byte) hangingFacing.get2DDataValue());
        } else {
            rotate = true;
        }
        CompoundTag finalNbt = replaceNbt != null ? (CompoundTag) NbtJson.merge(newEntityNbt, replaceNbt) : newEntityNbt;
        Optional<Entity> created = EntityType.create(finalNbt, level);
        if (created.isEmpty()) return null;
        Entity entity = created.get();
        if (rotate) entity.moveTo(placePos.x, placePos.y, placePos.z, entity.rotate(entityRotation), entity.getXRot());
        return entity;
    }

    @Nullable
    @Override
    public Entity buildWithoutChecks(Level level, BlockPos basePos) {
        return build(level, basePos);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("entityNbt", entityNbt);
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.x));
        posTag.add(DoubleTag.valueOf(pos.y));
        posTag.add(DoubleTag.valueOf(pos.z));
        tag.put("pos", posTag);
        tag.put("hangingPos", NbtUtils.writeBlockPos(hangingPos));
        tag.putString("hangingFacing", hangingFacing.name());
        tag.putString("entityRotation", entityRotation.name());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException {
        entityNbt = tag.getCompound("entityNbt");
        ListTag posTag = tag.getList("pos", Tag.TAG_DOUBLE);
        if (posTag.size() != 3) throw new InvalidSnapshotException("Expected 3 coordinates in " + posTag);
        pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
        hangingPos = NbtUtils.readBlockPos(tag.getCompound("hangingPos"));
        try {
            hangingFacing = Direction.valueOf(tag.getString("hangingFacing"));
            entityRotation = Rotation.valueOf(tag.getString("entityRotation"));
        } catch (IllegalArgumentException e) {
            throw new InvalidSnapshotException("Unknown facing or rotation in " + tag);
        }
    }

    /**
     * Not upstream: the entity must be of a saveable type from a recorded namespace that a capturing rule
     * accepts (so what it costs comes from that rule), at a finite position, and its data loses whatever
     * {@link SchematicSafety#sanitizeEntityNbt} removes.
     */
    @Override
    public SchematicEntity canonicalize() throws InvalidSnapshotException {
        ResourceLocation id = SchematicSafety.entityId(entityNbt);
        Optional<EntityType<?>> type = id == null ? Optional.empty() : Registry.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || !type.get().canSerialize() || !RulesLoader.readDomains().contains(id.getNamespace())) {
            throw new InvalidSnapshotException("A scan does not record the entity " + entityNbt.get("id"));
        }
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
            throw new InvalidSnapshotException("Entity position " + pos + " is not finite");
        }
        if (Vec3.atCenterOf(hangingPos).distanceTo(pos) > 4) {
            throw new InvalidSnapshotException("Entity at " + pos + " cannot hang on " + hangingPos.toShortString());
        }
        CompoundTag nbt = SchematicSafety.sanitizeEntityNbt(entityNbt);
        nbt.putString("id", id.toString());
        if (RulesLoader.getRules(id, nbt).stream().noneMatch(rule -> rule.capture)) {
            throw new InvalidSnapshotException("No rule captures the entity " + id);
        }
        SchematicEntityDefault schematicEntity = SchematicEntityManager.createCleanCopy(this);
        schematicEntity.entityNbt = nbt;
        schematicEntity.pos = pos;
        schematicEntity.hangingPos = hangingPos;
        schematicEntity.hangingFacing = hangingFacing;
        schematicEntity.entityRotation = entityRotation;
        return schematicEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicEntityDefault that = (SchematicEntityDefault) o;
        return entityNbt.equals(that.entityNbt) && pos.equals(that.pos) && hangingPos.equals(that.hangingPos)
                && hangingFacing == that.hangingFacing && entityRotation == that.entityRotation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityNbt, pos, hangingPos, hangingFacing, entityRotation);
    }
}
