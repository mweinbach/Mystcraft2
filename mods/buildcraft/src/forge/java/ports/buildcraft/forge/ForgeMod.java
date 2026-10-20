package ports.buildcraft.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.BuildCraft;
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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(modBus));
    }
}
