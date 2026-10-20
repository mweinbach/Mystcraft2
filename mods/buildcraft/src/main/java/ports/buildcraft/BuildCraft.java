package ports.buildcraft;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.builders.ArchitectTableMenu;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.ElectronicLibraryBlockEntity;
import ports.buildcraft.builders.snapshot.SnapshotPackets;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.BCTransport;

/** Shared entrypoint. Loader adapters install {@link Platform} first, then call {@link #init()}. */
public final class BuildCraft {
    public static final String MOD_ID = "buildcraft";
    private static CreativeModeTab mainTab;
    private static CreativeModeTab pipeTab;

    private BuildCraft() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static Item.Properties itemProperties() {
        return new Item.Properties().tab(mainTab());
    }

    public static Item.Properties pipeProperties() {
        return new Item.Properties().tab(pipeTab());
    }

    private static CreativeModeTab mainTab() {
        if (mainTab == null) mainTab = Platform.get().creativeTab("main", () -> new ItemStack(BCCore.WRENCH.get()));
        return mainTab;
    }

    private static CreativeModeTab pipeTab() {
        if (pipeTab == null) pipeTab = Platform.get().creativeTab("pipes", () -> new ItemStack(BCTransport.ITEM_PIPES.get(0).item()));
        return pipeTab;
    }

    /** Client-to-server messages by channel; loaders hand each payload over on the server thread. */
    public static Map<ResourceLocation, Platform.ServerReceiver> serverReceivers() {
        return Map.of(ArchitectTableMenu.NAME_CHANNEL, ArchitectTableMenu::receiveName,
                ElectronicLibraryBlockEntity.SELECT_CHANNEL, ElectronicLibraryBlockEntity::receiveSelect,
                ElectronicLibraryBlockEntity.UP_CHANNEL, ElectronicLibraryBlockEntity::receiveUp,
                SnapshotPackets.REQUEST_CHANNEL, SnapshotPackets::receiveRequest);
    }

    /** Loads every module so its registry entries are queued, in upstream module order. */
    public static void init() {
        mainTab();
        pipeTab();
        BCCore.init();
        BCEnergy.init();
        BCFactory.init();
        BCBuilders.init();
        BCTransport.init();
    }
}
