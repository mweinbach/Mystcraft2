package mekanism.common.integration.computer;

import java.util.function.Consumer;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ComputerCapabilityHelper {

    public static <TILE extends BlockEntity & IComputerTile> void addComputerCapabilities(TILE tile, Consumer<ICapabilityResolver> capabilityAdder) {
    }
}