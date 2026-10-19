package ports.jurassicraft.mural;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A mural is anchored at its top edge, as in the 1.12.2 original. */
public final class MuralEntity extends HangingEntity {
    private static final EntityDataAccessor<Integer> ART = SynchedEntityData.defineId(MuralEntity.class, EntityDataSerializers.INT);
    public MuralEntity(EntityType<? extends MuralEntity> type, Level level) { super(type, level); }
    private MuralEntity(Level level, BlockPos pos, Direction side) {
        super(MuralRegistry.ENTITY.get(), level, pos);
        setDirection(side);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ART, 0); }
    public MuralType artwork() { return MuralType.byOrdinal(entityData.get(ART)); }
    private void setArtwork(MuralType artwork) {
        entityData.set(ART, artwork.ordinal());
        if (pos != null) recalculateBoundingBox();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ART.equals(key) && pos != null) recalculateBoundingBox();
    }
    public static Optional<MuralEntity> create(Level level, BlockPos pos, Direction side) {
        if (!side.getAxis().isHorizontal()) return Optional.empty();
        MuralEntity mural = new MuralEntity(level, pos, side);
        List<MuralType> possible = new ArrayList<>();
        for (MuralType type : MuralType.values()) {
            mural.setArtwork(type);
            if (mural.survives()) possible.add(type);
        }
        if (possible.isEmpty()) return Optional.empty();
        mural.setArtwork(possible.get(level.random.nextInt(possible.size())));
        return Optional.of(mural);
    }
    @Override public int getWidth() { return artwork().width; }
    @Override public int getHeight() { return artwork().height; }
    @Override protected void recalculateBoundingBox() {
        if (direction == null || pos == null) return;
        double x = pos.getX() + .5 - direction.getStepX() * .46875;
        double y = pos.getY() + .5 + (getHeight() % 32 == 0 ? .5 : 0);
        double z = pos.getZ() + .5 - direction.getStepZ() * .46875;
        Direction across = direction.getCounterClockWise();
        double offset = getWidth() % 32 == 0 ? .5 : 0;
        x += offset * across.getStepX(); z += offset * across.getStepZ();
        setPosRaw(x, y, z);
        double halfWidth = getWidth() / 32.0;
        double halfX = direction.getAxis() == Direction.Axis.Z ? halfWidth : 1.0 / 32;
        double halfZ = direction.getAxis() == Direction.Axis.X ? halfWidth : 1.0 / 32;
        setBoundingBox(new AABB(x - halfX, y - getHeight() / 16.0, z - halfZ, x + halfX, y, z + halfZ));
    }
    @Override public boolean survives() {
        if (level.getBlockCollisions(this, getBoundingBox()).iterator().hasNext()) return false;
        int width = getWidth() / 16, height = getHeight() / 16;
        Direction across = direction.getCounterClockWise();
        BlockPos first = pos.relative(direction.getOpposite()).relative(across, -(width / 2) + 1);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            BlockPos support = first.relative(across, x).below(y);
            var state = level.getBlockState(support);
            if (!state.getMaterial().isSolid() || !state.isFaceSturdy(level, support, direction)) return false;
        }
        return level.getEntities(this, getBoundingBox(), entity -> entity instanceof MuralEntity).isEmpty();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Artwork", artwork().id());
        tag.putByte("Facing", (byte) direction.get2DDataValue());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        setArtwork(MuralType.byId(tag.getString("Artwork")));
        super.readAdditionalSaveData(tag);
        setDirection(Direction.from2DDataValue(tag.getByte("Facing")));
    }
    @Override public void dropItem(Entity breaker) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && !(breaker instanceof Player player && player.getAbilities().instabuild)) spawnAtLocation(new ItemStack(MuralRegistry.MURAL.get()));
    }
    @Override public ItemStack getPickResult() { return new ItemStack(MuralRegistry.MURAL.get()); }
    @Override public void playPlacementSound() {}
    // Movement packets contain the rendered position, not the hanging anchor.
    // Like vanilla paintings, a stationary mural must ignore interpolation.
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {}
    @Override public Packet<?> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this, direction.get3DDataValue(), getPos());
    }
    @Override public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }
}
