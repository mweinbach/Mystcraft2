package ports.jurassicraft.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParkSignGameTests {
    private ParkSignGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
    private static void begin(Player player, BlockPos support, Direction face, InteractionHand hand) {
        player.getItemInHand(hand).useOn(new UseOnContext(player, hand,
                new BlockHitResult(Vec3.atCenterOf(support), face, support, false)));
    }
    @GameTest(template = "mural_test", templateNamespace = PortMod.MOD_ID)
    public static void paddockSelectionIsValidatedAndPreserved(GameTestHelper h) {
        BlockPos support = h.absolutePos(new BlockPos(5, 4, 3));
        BlockPos anchor = support.north();
        h.getLevel().getEntitiesOfClass(ParkSignEntity.class, new AABB(anchor).inflate(2), e -> e.getPos().equals(anchor)).forEach(ParkSignEntity::discard);
        h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        Player player = h.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(support.getX(), support.getY(), support.getZ() - 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(DecorRegistry.PADDOCK_SIGN.get(), 2));
        int choice = ParkSigns.PADDOCKS.indexOf("triceratops");
        check(h, !ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, choice), "unrequested selection must fail");
        begin(player, support, Direction.NORTH, InteractionHand.OFF_HAND);
        check(h, !ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, 255), "invalid artwork must fail");
        check(h, !ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, choice), "failed selections cannot be replayed");
        begin(player, support, Direction.NORTH, InteractionHand.OFF_HAND);
        check(h, !ParkSignNetwork.select(player, anchor.east(), Direction.NORTH.get3DDataValue(), 1, choice), "selection cannot change the clicked wall");
        begin(player, support, Direction.NORTH, InteractionHand.OFF_HAND);
        player.setPos(support.getX() + 100, support.getY(), support.getZ());
        check(h, !ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, choice), "selection must remain within reach");
        player.setPos(support.getX(), support.getY(), support.getZ() - 2);
        begin(player, support, Direction.NORTH, InteractionHand.OFF_HAND);
        check(h, ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, choice), "valid selection should place the chosen paddock sign");
        check(h, player.getOffhandItem().getCount() == 1 && player.getMainHandItem().is(Items.STICK), "only the chosen hand should be consumed");
        check(h, !ParkSignNetwork.select(player, anchor, Direction.NORTH.get3DDataValue(), 1, choice), "successful selection cannot be replayed");
        var signs = h.getLevel().getEntitiesOfClass(ParkSignEntity.class, new AABB(anchor).inflate(2), e -> e.getPos().equals(anchor));
        check(h, signs.size() == 1, "exactly one sign should be placed");
        ParkSignEntity sign = signs.get(0);
        check(h, sign.artwork().equals("triceratops") && sign.survives(), "chosen artwork and wall support must be retained");
        check(h, !sign.interact(player, InteractionHand.MAIN_HAND).consumesAction() && sign.artwork().equals("triceratops"),
                "placed paddock signs do not cycle on interaction");
        CompoundTag tag = sign.saveWithoutId(new CompoundTag());
        ParkSignEntity restored = new ParkSignEntity(DecorRegistry.PADDOCK_SIGN_ENTITY.get(), h.getLevel());
        restored.load(tag);
        check(h, restored.artwork().equals(sign.artwork()) && restored.getPos().equals(anchor)
                && restored.getDirection() == Direction.NORTH && restored.getBoundingBox().equals(sign.getBoundingBox()),
                "paddock artwork, anchor, direction and bounds must persist");
        check(h, sign.getPickResult().getTag().getString("Artwork").equals("triceratops"), "picked sign should preserve artwork");
        h.succeed();
    }
    @GameTest(template = "mural_test", templateNamespace = PortMod.MOD_ID)
    public static void attractionSignSupportPersistenceAndCycle(GameTestHelper h) {
        BlockPos anchor = h.absolutePos(new BlockPos(5, 5, 2));
        Direction direction = Direction.NORTH;
        h.getLevel().getEntitiesOfClass(ParkSignEntity.class, new AABB(anchor).inflate(8), e -> e.getPos().equals(anchor)).forEach(ParkSignEntity::discard);
        ParkSignEntity sign = new ParkSignEntity(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), h.getLevel(), anchor, direction);
        sign.setArtwork("safari_plants");
        check(h, !sign.survives(), "attraction sign requires a solid support wall");
        Direction across = direction.getCounterClockWise();
        BlockPos first = anchor.relative(direction.getOpposite()).relative(across, -3);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 4; y++)
            h.getLevel().setBlock(first.relative(across, x).below(y), Blocks.STONE.defaultBlockState(), 3);
        check(h, sign.survives() && sign.getBoundingBox().getYsize() == 4 && sign.getBoundingBox().getXsize() == 8,
                "original sign supports eight wide by four high");
        check(h, h.getLevel().addFreshEntity(sign), "attraction sign should spawn");
        ParkSignEntity overlap = new ParkSignEntity(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), h.getLevel(), anchor, direction);
        check(h, !overlap.survives(), "overlapping attraction signs must be rejected");
        var bounds = sign.getBoundingBox();
        sign.lerpTo(sign.getX(), sign.getY(), sign.getZ(), sign.getYRot(), sign.getXRot(), 3, false);
        check(h, sign.getPos().equals(anchor) && sign.getBoundingBox().equals(bounds), "interpolation must not move the hanging anchor");
        ParkSignEntity restored = new ParkSignEntity(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), h.getLevel());
        restored.load(sign.saveWithoutId(new CompoundTag()));
        check(h, restored.artwork().equals("safari_plants") && restored.getPos().equals(anchor)
                && restored.getDirection() == direction && restored.getBoundingBox().equals(bounds), "attraction artwork and geometry must persist");
        h.getLevel().setBlock(first, Blocks.AIR.defaultBlockState(), 3);
        check(h, !sign.survives(), "removing any required support must detach the attraction sign");
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer grid = new CraftingContainer(menu, 2, 2);
        AttractionSignCycleRecipe recipe = new AttractionSignCycleRecipe(new ResourceLocation(PortMod.MOD_ID, "test_cycle"));
        ItemStack item = new ItemStack(DecorRegistry.ATTRACTION_SIGN.get());
        item.getOrCreateTag().putString("CustomTest", "retained");
        for (int i = 0; i < ParkSigns.ATTRACTIONS.size(); i++) {
            grid.setItem(0, item);
            check(h, recipe.matches(grid, h.getLevel()), "one attraction sign must match the cycle recipe");
            item = recipe.assemble(grid);
            check(h, item.getTag().getString("Artwork").equals(ParkSigns.ATTRACTIONS.get((i + 1) % ParkSigns.ATTRACTIONS.size()))
                    && item.getTag().getString("CustomTest").equals("retained"), "cycle must preserve custom data and advance with wraparound");
        }
        grid.setItem(1, new ItemStack(Items.DIRT));
        check(h, !recipe.matches(grid, h.getLevel()), "extra ingredients must not match the cycle recipe");
        h.succeed();
    }
}
