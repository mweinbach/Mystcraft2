package ports.jurassicraft.fabric;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;

/**
 * Item automation for JurassiCraft's machines, matching Forge's {@code ITEM_HANDLER} wiring in
 * {@code ForgeMod}. A face sees the vanilla sided-container rules, as Fabric's container fallback
 * does. Unsided access ({@code null} side) sees the input (up) face followed by the output (down)
 * face, each with its face rules, instead of the fallback's unrestricted view of every slot.
 */
final class FabricItemStorage {
    private FabricItemStorage() {}

    /** After the block entity types are registered. */
    static void register() {
        BlockEntityType<?>[] types = Registry.BLOCK_ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(PortMod.MOD_ID))
                .map(entry -> entry.getValue()).toArray(BlockEntityType<?>[]::new);
        ItemStorage.SIDED.registerForBlockEntities(
                (blockEntity, side) -> blockEntity instanceof WorldlyContainer container ? of(container, side) : null, types);
    }

    static Storage<ItemVariant> of(WorldlyContainer container, @Nullable Direction side) {
        if (side != null) return InventoryStorage.of(container, side);
        if (Arrays.equals(container.getSlotsForFace(Direction.UP), container.getSlotsForFace(Direction.DOWN)))
            return InventoryStorage.of(container, Direction.UP);
        return new Unsided(InventoryStorage.of(container, Direction.UP), InventoryStorage.of(container, Direction.DOWN));
    }

    /** The input face's slots, then the output face's, like Forge's {@code CombinedInvWrapper}. */
    static final class Unsided extends CombinedStorage<ItemVariant, InventoryStorage> {
        Unsided(InventoryStorage input, InventoryStorage output) { super(List.of(input, output)); }
    }
}
