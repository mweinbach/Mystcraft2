package ports.jurassicraft.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
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

/** A wall-mounted sign that retains its artwork on breaking, save, and respawn. */
public final class ParkSignEntity extends HangingEntity {
    private static final EntityDataAccessor<String> ARTWORK = SynchedEntityData.defineId(ParkSignEntity.class, EntityDataSerializers.STRING);
    public ParkSignEntity(EntityType<? extends ParkSignEntity> type, Level level) { super(type, level); }
    public ParkSignEntity(EntityType<? extends ParkSignEntity> type, Level level, BlockPos pos, Direction side) {
        super(type, level, pos);
        setDirection(side);
    }
    public boolean attraction() { return getType() == DecorRegistry.ATTRACTION_SIGN_ENTITY.get(); }
    public String artwork() { return ParkSigns.validate(attraction(), entityData.get(ARTWORK)); }
    public void setArtwork(String artwork) { entityData.set(ARTWORK, ParkSigns.validate(attraction(), artwork)); }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ARTWORK, ""); }
    @Override public int getWidth() { return attraction() ? 128 : 16; }
    @Override public int getHeight() { return attraction() ? 64 : 16; }
    @Override protected void recalculateBoundingBox() {
        if (!attraction()) { super.recalculateBoundingBox(); return; }
        if (direction == null || pos == null) return;
        double x = pos.getX() + .5 - direction.getStepX() * .46875;
        double y = pos.getY() + 1;
        double z = pos.getZ() + .5 - direction.getStepZ() * .46875;
        Direction across = direction.getCounterClockWise();
        x += .5 * across.getStepX(); z += .5 * across.getStepZ();
        setPosRaw(x, y, z);
        double halfX = direction.getAxis() == Direction.Axis.Z ? 4 : 1.0 / 32;
        double halfZ = direction.getAxis() == Direction.Axis.X ? 4 : 1.0 / 32;
        setBoundingBox(new AABB(x - halfX, y - 4, z - halfZ, x + halfX, y, z + halfZ));
    }
    @Override public boolean survives() {
        if (!attraction()) return super.survives();
        if (direction == null || pos == null || level.getBlockCollisions(this, getBoundingBox()).iterator().hasNext()) return false;
        Direction across = direction.getCounterClockWise();
        BlockPos first = pos.relative(direction.getOpposite()).relative(across, -3);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 4; y++) {
            BlockPos support = first.relative(across, x).below(y);
            var state = level.getBlockState(support);
            if (!state.getMaterial().isSolid() || !state.isFaceSturdy(level, support, direction)) return false;
        }
        return level.getEntities(this, getBoundingBox(), entity -> entity instanceof ParkSignEntity sign && sign.attraction()).isEmpty();
    }
    @Override public void playPlacementSound() { }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Artwork", artwork());
        tag.putByte("Facing", (byte) direction.get2DDataValue());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setArtwork(tag.getString("Artwork"));
        // Older port saves retained the entity yaw but omitted a dedicated facing field.
        setDirection(tag.contains("Facing", Tag.TAG_ANY_NUMERIC)
                ? Direction.from2DDataValue(tag.getByte("Facing")) : Direction.fromYRot(getYRot()));
    }
    @Override public ItemStack getPickResult() {
        ItemStack stack = new ItemStack(attraction() ? DecorRegistry.ATTRACTION_SIGN.get() : DecorRegistry.PADDOCK_SIGN.get());
        stack.getOrCreateTag().putString("Artwork", artwork());
        return stack;
    }
    @Override public void dropItem(Entity breaker) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && !(breaker instanceof Player player && player.getAbilities().instabuild)) spawnAtLocation(getPickResult());
    }
    // The rendered position is not the wall anchor; ignore movement interpolation.
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {}
    @Override public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, direction.get3DDataValue(), getPos());
    }
    @Override public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }
}
