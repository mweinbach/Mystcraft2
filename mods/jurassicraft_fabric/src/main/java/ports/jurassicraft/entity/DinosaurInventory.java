package ports.jurassicraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.content.Species;

/** The original species-sized owner inventory, displayed using vanilla chest menus. */
final class DinosaurInventory extends SimpleContainer {
    private final DinosaurEntity dinosaur;

    DinosaurInventory(DinosaurEntity dinosaur) {
        super(capacity(dinosaur.species()));
        this.dinosaur = dinosaur;
    }

    static int capacity(Species species) {
        return switch (species) {
            case COELACANTH, MICRORAPTOR, MUSSAURUS -> 9;
            case VELOCIRAPTOR, DILOPHOSAURUS, GALLIMIMUS -> 27;
            case PARASAUROLOPHUS, TRICERATOPS -> 36;
            case BRACHIOSAURUS, TYRANNOSAURUS -> 54;
        };
    }

    @Override public boolean stillValid(Player player) {
        return dinosaur.isAlive() && dinosaur.growth() > .75f && player.isAlive()
                && player.level == dinosaur.level && dinosaur.getOwnerUUID() != null
                && dinosaur.getOwnerUUID().equals(player.getUUID()) && dinosaur.distanceToSqr(player) <= 64;
    }

    ChestMenu menu(int id, Inventory playerInventory) {
        int rows = getContainerSize() / 9;
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalStateException("Unexpected dinosaur inventory size");
        };
        return new ChestMenu(type, id, playerInventory, this, rows);
    }

    void save(CompoundTag tag) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = stack.save(new CompoundTag());
                entry.putByte("Slot", (byte) slot);
                items.add(entry);
            }
        }
        tag.put("Items", items);
    }

    void load(CompoundTag tag) {
        clearContent();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < getContainerSize()) setItem(slot, ItemStack.of(entry));
        }
    }

    void dropContents() {
        if (dinosaur.level.isClientSide) return;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = removeItemNoUpdate(slot);
            if (!stack.isEmpty()) dinosaur.spawnAtLocation(stack);
        }
        setChanged();
    }
}
