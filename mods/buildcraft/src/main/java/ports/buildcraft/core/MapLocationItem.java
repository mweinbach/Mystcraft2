package ports.buildcraft.core;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import ports.buildcraft.lib.AreaProvider;
import ports.buildcraft.robotics.zone.ZonePlan;

/**
 * Upstream {@code ItemMapLocation}. The six types were item damage; here the type is {@code mapType} in the
 * item's tag, and a clean stack holds 16 while any recorded one holds one. There is no recipe.
 */
public class MapLocationItem extends Item {
    public static final String TYPE = "mapType";
    private static final String[] STORAGE = {"x", "y", "z", "side", "xMin", "xMax", "yMin", "yMax", "zMin", "zMax", "path", "chunkMapping", "name"};

    /**
     * Upstream {@code IMapLocation.MapLocationType}, in its ordinal order. {@link #ZONE} is before
     * {@link #PATH_REPEATING}.
     */
    public enum Type {
        CLEAN, SPOT, AREA, PATH, ZONE, PATH_REPEATING;

        public static Type of(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            int ordinal = tag == null ? 0 : tag.getInt(TYPE);
            Type[] values = values();
            return ordinal < 0 || ordinal >= values.length ? CLEAN : values[ordinal];
        }

        public void set(ItemStack stack) {
            stack.getOrCreateTag().putInt(TYPE, ordinal());
        }
    }

    public MapLocationItem(Properties properties) {
        super(properties);
    }

    public static int maxStack(ItemStack stack) {
        return Type.of(stack) == Type.CLEAN ? 16 : 1;
    }

    /** Upstream {@code onItemRightClick}: sneaking in the air wipes a recorded location. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (Type.of(stack) == Type.CLEAN) return InteractionResultHolder.pass(stack);
        clear(stack);
        return InteractionResultHolder.success(stack);
    }

    /**
     * Upstream {@code onItemUseFirst}, before the clicked block's own use. A clean map records a path mark, an
     * area provider, or the clicked block; anything already recorded refuses the click.
     */
    public static InteractionResult useFirst(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MapLocationItem) || player.isSpectator()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (Type.of(stack) != Type.CLEAN) return InteractionResult.FAIL;
        ItemStack recorded = stack.getCount() == 1 ? stack : stack.split(1);
        record(recorded, level, hit);
        if (recorded != stack && !player.getInventory().add(recorded)) player.drop(recorded, false);
        return InteractionResult.SUCCESS;
    }

    /** Upstream writes the zone into the tag it already has and marks the stack as a zone. */
    public static void setZone(ItemStack stack, ZonePlan plan) {
        Type.ZONE.set(stack);
        plan.write(stack.getOrCreateTag());
    }

    @Nullable
    public static ZonePlan zone(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("chunkMapping")) return null;
        ZonePlan plan = new ZonePlan();
        plan.read(tag);
        return plan;
    }

    private static void record(ItemStack stack, Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        CompoundTag tag = stack.getOrCreateTag();
        if (level.getBlockEntity(pos) instanceof MarkerPathBlockEntity path) {
            List<BlockPos> points = path.getPath();
            (points.size() > 1 && points.get(0).equals(points.get(points.size() - 1)) ? Type.PATH_REPEATING : Type.PATH).set(stack);
            ListTag list = new ListTag();
            for (BlockPos point : points) list.add(NbtUtils.writeBlockPos(point));
            tag.put("path", list);
        } else if (level.getBlockEntity(pos) instanceof AreaProvider area) {
            Type.AREA.set(stack);
            tag.putInt("xMin", area.min().getX());
            tag.putInt("yMin", area.min().getY());
            tag.putInt("zMin", area.min().getZ());
            tag.putInt("xMax", area.max().getX());
            tag.putInt("yMax", area.max().getY());
            tag.putInt("zMax", area.max().getZ());
        } else {
            Type.SPOT.set(stack);
            tag.putByte("side", (byte) hit.getDirection().get3DDataValue());
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
        }
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        for (String key : STORAGE) tag.remove(key);
        tag.remove(TYPE);
        if (tag.isEmpty()) stack.setTag(null);
        else Type.CLEAN.set(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("name") && !tag.getString("name").isEmpty()) tooltip.add(Component.literal(tag.getString("name")));
        switch (Type.of(stack)) {
            case SPOT -> {
                if (tag != null && tag.contains("x") && tag.contains("y") && tag.contains("z") && tag.contains("side")) {
                    Direction side = Direction.from3DDataValue(tag.getByte("side"));
                    tooltip.add(Component.literal("{" + tag.getInt("x") + ", " + tag.getInt("y") + ", " + tag.getInt("z") + ", " + side + "}"));
                }
            }
            case AREA -> {
                if (tag != null && tag.contains("xMin") && tag.contains("yMin") && tag.contains("zMin")
                        && tag.contains("xMax") && tag.contains("yMax") && tag.contains("zMax")) {
                    int x = tag.getInt("xMin"), y = tag.getInt("yMin"), z = tag.getInt("zMin");
                    tooltip.add(Component.literal("{" + x + ", " + y + ", " + z + "} + {" + (tag.getInt("xMax") - x + 1)
                            + " x " + (tag.getInt("yMax") - y + 1) + " x " + (tag.getInt("zMax") - z + 1) + "}"));
                }
            }
            case PATH, PATH_REPEATING -> {
                if (tag != null && tag.get("path") instanceof ListTag path && !path.isEmpty() && path.get(0) instanceof CompoundTag first) {
                    BlockPos start = NbtUtils.readBlockPos(first);
                    tooltip.add(Component.literal("{" + start.getX() + ", " + start.getY() + ", " + start.getZ() + "}, (+" + (path.size() - 1) + " elements)"));
                }
            }
            default -> {}
        }
        if (Type.of(stack) != Type.CLEAN) tooltip.add(Component.translatable("item.buildcraft.map_location.clean_hint"));
    }

    /** The path a path or spot map records, for callers that used {@code IMapLocation.getPath}. */
    @Nullable
    public static List<BlockPos> path(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return switch (Type.of(stack)) {
            case PATH, PATH_REPEATING -> {
                if (!(tag.get("path") instanceof ListTag list)) yield null;
                List<BlockPos> points = new ArrayList<>();
                for (Tag element : list) {
                    if (element instanceof CompoundTag point) points.add(NbtUtils.readBlockPos(point));
                }
                yield points;
            }
            case SPOT -> List.of(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
            default -> null;
        };
    }
}
