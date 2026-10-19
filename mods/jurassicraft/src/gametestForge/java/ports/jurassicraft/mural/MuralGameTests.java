package ports.jurassicraft.mural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MuralGameTests {
    private MuralGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }

    @GameTest(template = "mural_test", templateNamespace = PortMod.MOD_ID)
    public static void placementPersistenceAndSupport(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        AABB fixture = new AABB(origin, origin.offset(12, 8, 5));
        h.getLevel().getEntitiesOfClass(MuralEntity.class, fixture, mural -> fixture.contains(mural.position()))
                .forEach(MuralEntity::discard);
        BlockPos support = new BlockPos(5, 5, 3);
        BlockPos anchor = h.absolutePos(support.north());
        check(h, MuralEntity.create(h.getLevel(), anchor, Direction.NORTH).isEmpty(), "murals require a large solid wall");
        for (int x = 0; x < 12; x++) for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
        var player = h.makeMockPlayer();
        BlockPos clicked = h.absolutePos(support);
        player.setPos(clicked.getX() - 2, clicked.getY(), clicked.getZ() - 2);
        ItemStack stack = new ItemStack(MuralRegistry.MURAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var click = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false));
        check(h, stack.useOn(click).consumesAction(), "mural item must place through normal item use");
        var found = h.getEntities(MuralRegistry.ENTITY.get(), support.north(), 10).stream().filter(entity -> entity.getPos().equals(anchor)).toList();
        check(h, found.size() == 1, "one mural must be spawned");
        MuralEntity mural = found.get(0);
        check(h, mural.getPos().equals(anchor) && mural.getDirection() == Direction.NORTH && mural.survives(), "mural must attach to its wall");
        check(h, MuralEntity.create(h.getLevel(), anchor, Direction.NORTH).isEmpty(), "overlapping murals must be rejected");
        var bounds = mural.getBoundingBox();
        mural.lerpTo(mural.getX(), mural.getY(), mural.getZ(), mural.getYRot(), mural.getXRot(), 3, false);
        check(h, mural.getPos().equals(anchor) && mural.getBoundingBox().equals(bounds),
                "client position packets must not reinterpret the rendered position as the wall anchor");
        CompoundTag saved = mural.saveWithoutId(new CompoundTag());
        MuralEntity restored = new MuralEntity(MuralRegistry.ENTITY.get(), h.getLevel());
        restored.load(saved);
        check(h, restored.artwork() == mural.artwork() && restored.getDirection() == mural.getDirection()
                && restored.getPos().equals(mural.getPos()) && restored.getBoundingBox().equals(mural.getBoundingBox()),
                "artwork, direction, anchor and bounds must survive saving");
        var existing = h.getEntities(EntityType.ITEM, support.north(), 10).stream().map(item -> item.getUUID()).collect(java.util.stream.Collectors.toSet());
        for (int x = 0; x < 12; x++) for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, 3), Blocks.AIR);
        check(h, !mural.survives(), "mural must no longer survive after its support is removed");
        for (int i = 0; i <= 100; i++) mural.tick();
        check(h, mural.isRemoved(), "unsupported mural must detach");
        check(h, h.getEntities(EntityType.ITEM, support.north(), 10).stream().anyMatch(e -> !existing.contains(e.getUUID()) && e.getItem().is(MuralRegistry.MURAL.get())), "unsupported mural must drop its item");
        h.succeed();
    }
}
