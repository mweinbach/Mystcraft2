package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.writing.WritingDeskBlock;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;

/** Actual item-use placement and loot transactions, not synthetic multiblock claims. */
public final class WritingDeskStructureSmoke {
    private WritingDeskStructureSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        BlockPos head = new BlockPos(100040, 200, 100040), foot = head.west();
        AABB area = new AABB(head).inflate(5);
        Map<BlockPos, BlockState> oldStates = new HashMap<>();
        Map<BlockPos, CompoundTag> oldEntities = new HashMap<>();
        Set<UUID> previousDrops = new HashSet<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) previousDrops.add(((Entity) drop).getUUID());
        for (BlockPos pos : List.of(head, foot, head.above(), foot.above(), head.below(), foot.below())) {
            oldStates.put(pos, level.getBlockState(pos));
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) oldEntities.put(pos, entity.saveWithFullMetadata());
        }
        try {
            for (BlockPos pos : List.of(head, foot, head.above(), foot.above())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 2);
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.fromString("4f733db6-9b67-4e2f-bb0b-85ccca49be01"), "DeskStructure"), null);
            ((Entity) player).setPos(head.getX() + .5, head.getY(), head.getZ() + 3.5);
            ((Entity) player).setYRot(0);
            ItemStack blueprint = new ItemStack(WritingDeskRegistry.WRITING_DESK_ITEM.get());
            ((Player) player).setItemInHand(InteractionHand.MAIN_HAND, blueprint);
            level.setBlock(foot, Blocks.STONE.defaultBlockState(), 2);
            UseOnContext baseContext = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(head.below()).add(0, .5, 0), Direction.UP, head.below(), false));
            require(((Item) blueprint.getItem()).useOn(baseContext) == InteractionResult.FAIL, "Blocked secondary desk cell accepted placement");
            require(blueprint.getCount() == 1 && level.getBlockState(head).isAir(), "Failed two-cell placement consumed item or left partial desk");
            level.setBlock(foot, Blocks.AIR.defaultBlockState(), 2);
            require(((Item) blueprint.getItem()).useOn(baseContext).consumesAction(), "Two-cell desk item did not place");
            require(blueprint.isEmpty(), "Survival desk placement did not consume one blueprint");
            BlockState headState = level.getBlockState(head), footState = level.getBlockState(foot);
            require(headState.is(WritingDeskRegistry.WRITING_DESK.get()) && headState.getValue(WritingDeskBlock.FACING) == Direction.WEST
                    && !headState.getValue(WritingDeskBlock.FOOT) && footState.getValue(WritingDeskBlock.FOOT), "Placed pair has wrong original sideways orientation");
            require(level.getBlockEntity(head) instanceof WritingDeskBlockEntity && level.getBlockEntity(foot) == null, "Desk pair has missing or duplicate inventory");
            require(WritingDeskBlock.headPosition(foot, footState).equals(head), "Secondary interaction did not resolve to owning desk");
            passed.add("writing desk structure: real item placement requires two free cells, consumes exactly one item, creates one inventory and resolves secondary half to its owner");

            ItemStack extension = new ItemStack(WritingDeskRegistry.WRITING_DESK_EXTENSION.get());
            ((Player) player).setItemInHand(InteractionHand.MAIN_HAND, extension);
            UseOnContext extensionContext = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(foot).add(0, .5, 0), Direction.UP, foot, false));
            require(((Item) extension.getItem()).useOn(extensionContext).consumesAction() && extension.isEmpty(), "Extension did not install from secondary desk half");
            BlockState topHead = level.getBlockState(head.above()), topFoot = level.getBlockState(foot.above());
            require(topHead.getValue(WritingDeskBlock.TOP) && !topHead.getValue(WritingDeskBlock.FOOT)
                    && topFoot.getValue(WritingDeskBlock.TOP) && topFoot.getValue(WritingDeskBlock.FOOT), "Extension upper pair not created correctly");
            require(level.getBlockEntity(head.above()) == null && level.getBlockEntity(foot.above()) == null, "Backboard duplicated inventory");
            require(WritingDeskBlock.headPosition(foot.above(), topFoot).equals(head), "Backboard did not resolve owner");
            AABB bounds = topHead.getShape(level, head.above()).bounds();
            require(bounds.maxY == .75 && bounds.minZ == .5 && bounds.maxZ == 1, "Original upper half-depth, three-quarter-height outline not retained");
            passed.add("writing desk structure: one backboard item adds both upper cells, shares original inventory and matches classic 0.75-height half-depth outline");

            WritingDeskBlockEntity desk = (WritingDeskBlockEntity) level.getBlockEntity(head);
            ItemStack pages = new ItemStack(AgeRegistry.PAGE.get(), 2); pages.getOrCreateTag().putString("SmokeDeskDrop", "retained");
            desk.inventory.setItem(5, pages);
            require(level.destroyBlock(foot, true, player), "Could not destroy secondary base half");
            for (BlockPos pos : List.of(head, foot, head.above(), foot.above()))
                require(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null, "Desk breakup left orphan part or inventory");
            int desks = 0, extensions = 0, pageCount = 0;
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (previousDrops.contains(((Entity) drop).getUUID())) continue;
                ItemStack stack = drop.getItem();
                if (stack.is(WritingDeskRegistry.WRITING_DESK_ITEM.get())) desks += stack.getCount();
                if (stack.is(WritingDeskRegistry.WRITING_DESK_EXTENSION.get())) extensions += stack.getCount();
                if (stack.is(AgeRegistry.PAGE.get())) {
                    require(stack.hasTag() && stack.getTag().getString("SmokeDeskDrop").equals("retained"), "Inventory break drop lost page payload");
                    pageCount += stack.getCount();
                }
            }
            require(desks == 1 && extensions == 1 && pageCount == 2, "Desk breakup dropped wrong counts: desk=" + desks + " extension=" + extensions + " pages=" + pageCount);
            passed.add("writing desk structure: breaking secondary base removes all four parts and drops exactly one desk, one backboard and original inventory once");
        } finally {
            for (BlockPos pos : oldStates.keySet())
                if (level.getBlockEntity(pos) instanceof WritingDeskBlockEntity desk) desk.inventory.clearContent();
            for (Map.Entry<BlockPos, BlockState> entry : oldStates.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
            for (Map.Entry<BlockPos, CompoundTag> entry : oldEntities.entrySet()) {
                BlockEntity restored = level.getBlockEntity(entry.getKey());
                if (restored != null) restored.load(entry.getValue());
            }
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area))
                if (!previousDrops.contains(((Entity) drop).getUUID())) ((Entity) drop).discard();
        }
        return passed;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
