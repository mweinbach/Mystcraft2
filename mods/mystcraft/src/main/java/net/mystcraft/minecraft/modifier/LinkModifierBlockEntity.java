package net.mystcraft.minecraft.modifier;

import net.mystcraft.integration.registry.ModifierRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.item.LinkingBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LinkModifierBlockEntity extends BlockEntity implements MenuProvider {
    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public void setChanged() { super.setChanged(); LinkModifierBlockEntity.this.setChanged(); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return isBook(stack); }
    };
    public LinkModifierBlockEntity(BlockPos pos, BlockState state) { super(ModifierRegistry.LINK_MODIFIER_ENTITY.get(), pos, state); }
    public static boolean isBook(ItemStack stack) { return stack.getItem() instanceof LinkingBookItem; }
    public static boolean authorized(Player player) { return player.isAlive() && !player.isSpectator() && (player.getAbilities().instabuild || player.hasPermissions(2)); }
    private boolean editable(Player player) { return level != null && !level.isClientSide && authorized(player) && isBook(inventory.getItem(0)); }
    public boolean setFlag(Player player, String flag, boolean enabled) {
        if (!editable(player) || !LinkModifierMenu.FLAGS.contains(flag)) return false;
        CompoundTag book = inventory.getItem(0).getOrCreateTag();
        CompoundTag flags = book.getCompound("Flags");
        flags.putBoolean(flag, enabled);
        book.put("Flags", flags);
        setChanged();
        return true;
    }
    public boolean rename(Player player, String title) {
        if (!editable(player) || title.length() > 21 || title.chars().anyMatch(Character::isISOControl)) return false;
        ItemStack book = inventory.getItem(0);
        if (title.isEmpty()) book.resetHoverName(); else book.setHoverName(Component.literal(title));
        book.getOrCreateTag().putString("DisplayName", title);
        setChanged();
        return true;
    }
    /** Re-seeding a live generated dimension would make chunk generation inconsistent. */
    public boolean setSeed(Player player, String text) {
        ItemStack book = inventory.getItem(0);
        if (!editable(player) || !AgeBookData.isUnallocated(book) || text.length() > 21) return false;
        if (!text.isEmpty()) {
            try { Long.parseLong(text); } catch (NumberFormatException invalid) { return false; }
        }
        CompoundTag tag = book.getOrCreateTag();
        CompoundTag props = tag.getCompound("Props");
        if (text.isEmpty()) props.remove("Seed"); else props.putString("Seed", text);
        tag.put("Props", props);
        setChanged();
        return true;
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level, worldPosition, inventory);
        inventory.clearContent();
        setChanged();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.mystcraft.link_modifier"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return authorized(player) ? new LinkModifierMenu(id, playerInventory, this) : null;
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("Book", inventory.getItem(0).save(new CompoundTag())); }
    @Override public void load(CompoundTag tag) { super.load(tag); inventory.setItem(0, ItemStack.of(tag.getCompound("Book"))); }
}
