package ports.jurassicraft.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class JeepEntity extends VehicleEntity {
    private static final double[][] SEATS = {{.563, .45, 0}, {-.563, .45, 0}, {.5, .7, -2.2}, {-.5, .7, -2.2}};
    public JeepEntity(EntityType<? extends JeepEntity> type, Level level) { super(type, level); }
    @Override protected Item vehicleItem() { return VehicleRegistry.JEEP_ITEM.get(); }
    @Override protected double[][] seatOffsets() { return SEATS; }
}
