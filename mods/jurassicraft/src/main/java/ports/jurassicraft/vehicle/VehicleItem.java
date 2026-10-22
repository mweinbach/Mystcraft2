package ports.jurassicraft.vehicle;

import java.util.function.Supplier;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Place a vehicle only if its whole collision box is clear. */
public final class VehicleItem extends Item {
    private final Supplier<? extends EntityType<? extends VehicleEntity>> type;
    public VehicleItem(Supplier<? extends EntityType<? extends VehicleEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jurassicraft.vehicle_drive").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jurassicraft.vehicle_radio", Component.keybind("key.jurassicraft.vehicle_radio")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jurassicraft.vehicle_seats", Component.keybind("key.jurassicraft.vehicle_seat_1")).withStyle(ChatFormatting.GRAY));
        if (type.get() == VehicleRegistry.HELICOPTER.get()) {
            tooltip.add(Component.translatable("tooltip.jurassicraft.helicopter_altitude",
                    Component.keybind("key.jurassicraft.helicopter_ascend"),
                    Component.keybind("key.jurassicraft.helicopter_descend")).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.jurassicraft.helicopter_steering",
                    Component.keybind("key.jurassicraft.helicopter_rotate_left"),
                    Component.keybind("key.jurassicraft.helicopter_rotate_right")).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.jurassicraft.helicopter_assists",
                    Component.keybind("key.jurassicraft.helicopter_autopilot"),
                    Component.keybind("key.jurassicraft.helicopter_rotation_lock")).withStyle(ChatFormatting.GRAY));
        }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        VehicleEntity vehicle = type.get().create(level);
        if (vehicle == null) return InteractionResult.FAIL;
        vehicle.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), 0);
        if (!level.getWorldBorder().isWithinBounds(vehicle.getBoundingBox()) || !level.noCollision(vehicle) || !level.addFreshEntity(vehicle)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
