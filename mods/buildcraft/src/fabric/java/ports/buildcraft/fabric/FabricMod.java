package ports.buildcraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;

/** Fabric entrypoint: installs the platform, registers the shared modules and exposes storages. */
public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        BuildCraft.init();
        platform.flush();
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(BuildCraft.MOD_ID))
                .map(entry -> entry.getValue())
                .toArray(BlockEntityType<?>[]::new);
        FabricPorts.register(types);
    }
}
