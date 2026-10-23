package ports.jurassicraft.skull;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/**
 * Forge-only skull check: Forge wraps {@code Item.useOn} in block-snapshot capture and fires a
 * cancellable {@code BlockEvent.EntityPlaceEvent}, rolling the world and the stack back when it is
 * canceled. Fabric has no placement-cancel event, so this rollback contract exists only on Forge.
 * Skull placement itself is shared and covered for both loaders by {@link SkullGameTests}.
 */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkullForgeGameTests {
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);
    private SkullForgeGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }

    public static final class DenyPlacement {
        private final Player player;
        private DenyPlacement(Player player) { this.player = player; }
        @SubscribeEvent public void cancel(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() == player && event.getPlacedBlock().is(SkullRegistry.DISPLAY.get())) event.setCanceled(true);
        }
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void deniedPlacementRestoresItemAndBlock(GameTestHelper h) {
        h.setBlock(SUPPORT, Blocks.STONE); BlockPos support = h.absolutePos(SUPPORT);
        var player = h.makeMockPlayer(); player.setPos(support.getX() - 1, support.getY(), support.getZ() - 1);
        ItemStack skull = new ItemStack(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        skull.getOrCreateTag().putString("marker", "preserved");
        player.setItemInHand(InteractionHand.MAIN_HAND, skull);
        var click = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        Set<UUID> existing = h.getEntities(EntityType.ITEM, SUPPORT.above(), 2).stream().map(item -> item.getUUID()).collect(Collectors.toSet());
        DenyPlacement listener = new DenyPlacement(player);
        InteractionResult result;
        MinecraftForge.EVENT_BUS.register(listener);
        try { result = skull.useOn(click); } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        check(h, result == InteractionResult.FAIL, "cancelled placement must fail");
        check(h, h.getLevel().getBlockState(support.above()).isAir(), "cancelled placement must restore the block");
        check(h, skull.getCount() == 1 && "preserved".equals(skull.getTag().getString("marker")), "cancelled placement must restore the original item");
        check(h, h.getEntities(EntityType.ITEM, SUPPORT.above(), 2).stream().noneMatch(item -> !existing.contains(item.getUUID())), "rollback must not drop an extra skull");
        h.succeed();
    }
}
