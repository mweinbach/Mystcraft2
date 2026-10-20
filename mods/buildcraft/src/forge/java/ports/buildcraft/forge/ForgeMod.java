package ports.buildcraft.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.SchematicSingleItem;
import ports.buildcraft.core.BCCoreEvents;
import ports.buildcraft.platform.Platform;

/** Forge entrypoint: installs the platform, loads the shared modules and attaches capabilities. */
@Mod(BuildCraft.MOD_ID)
public final class ForgeMod {
    public ForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        Platform.install(new ForgePlatform(modBus));
        BuildCraft.init();
        forgeBus.addGenericListener(BlockEntity.class, ForgePorts::attach);
        forgeBus.addGenericListener(ItemStack.class, ForgeShardHandler::attach);
        ForgeNetwork.init();
        forgeBus.addListener((TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) BCCoreEvents.onLevelTick(level);
        });
        forgeBus.addListener((EntityJoinLevelEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
                BCCoreEvents.onPlayerJoinLevel(player, level);
            }
        });
        forgeBus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = SchematicSingleItem.useFirst(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(modBus));
    }
}
