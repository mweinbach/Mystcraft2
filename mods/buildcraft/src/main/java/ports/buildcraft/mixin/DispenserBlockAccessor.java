package ports.buildcraft.mixin;

import java.util.Map;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Upstream's stripes dispenser handler read {@code BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY}, which 1.19 keeps private. */
@Mixin(DispenserBlock.class)
public interface DispenserBlockAccessor {
    @Accessor("DISPENSER_REGISTRY")
    static Map<Item, DispenseItemBehavior> buildcraft$getRegistry() {
        throw new AssertionError();
    }
}
