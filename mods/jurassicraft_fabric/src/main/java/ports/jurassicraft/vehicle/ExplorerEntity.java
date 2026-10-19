package ports.jurassicraft.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import ports.jurassicraft.tourrail.TourRailTravel;

public final class ExplorerEntity extends VehicleEntity {
    private static final EntityDataAccessor<Boolean> ON_TOUR_RAIL = SynchedEntityData.defineId(ExplorerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double[][] SEATS = {{.563, .45, .4}, {-.563, .45, .4}, {.563, .45, -1}, {-.563, .45, -1}};
    private final TourRailTravel tourRail = new TourRailTravel(this);
    public ExplorerEntity(EntityType<? extends ExplorerEntity> type, Level level) { super(type, level); }
    @Override protected Item vehicleItem() { return VehicleRegistry.EXPLORER_ITEM.get(); }
    @Override protected double[][] seatOffsets() { return SEATS; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ON_TOUR_RAIL, false); }
    @Override public EntityDimensions getDimensions(Pose pose) {
        return entityData.get(ON_TOUR_RAIL) ? EntityDimensions.scalable(.75F, .25F) : super.getDimensions(pose);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ON_TOUR_RAIL.equals(key)) refreshDimensions();
    }
    public void setOnTourRail(boolean value) { entityData.set(ON_TOUR_RAIL, value); }
    public boolean isOnTourRail() { return entityData.get(ON_TOUR_RAIL); }
    @Override protected boolean tickSpecialMovement(int controls) { return tourRail.tick(controls); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tourRail.save(tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); tourRail.load(tag); }
}
