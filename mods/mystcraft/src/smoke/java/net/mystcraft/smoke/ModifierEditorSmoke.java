package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.mystcraft.integration.network.MenuActions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.ModifierRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.modifier.LinkModifierBlockEntity;
import net.mystcraft.minecraft.modifier.LinkModifierMenu;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Admin editor authorization, bounded input and original target preservation checks. */
public final class ModifierEditorSmoke {
    private ModifierEditorSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        NonNullList<ItemStack> creativePanels = NonNullList.create();
        MystcraftItems.LINKING_PANEL.get().fillItemCategory(MystcraftItems.TAB, creativePanels);
        require(creativePanels.size() == 1 && creativePanels.get(0).is(MystcraftItems.LINKING_PANEL.get()), "Panel PageItem conversion polluted creative inventory with symbol panels");
        passed.add("panel item: PageItem tooltips retain a single fixed-model linking panel creative entry");
        BlockPos pos = new BlockPos(100050, 80, 100050);
        BlockState oldState = level.getBlockState(pos);
        BlockEntity old = level.getBlockEntity(pos);
        CompoundTag oldTag = old == null ? null : old.saveWithFullMetadata();
        try {
            BlockState state = ModifierRegistry.LINK_MODIFIER.get().defaultBlockState();
            level.setBlock(pos, state, 3);
            LinkModifierBlockEntity modifier = (LinkModifierBlockEntity) level.getBlockEntity(pos);
            require(modifier != null, "Link modifier block entity missing");
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.fromString("9f4eb9a0-51d1-4717-93c5-fa9cf7d7f930"), "ModifierSmoke"), null);
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
            LinkDestination destination = new LinkDestination(level.dimension().location(), 321.25, 90, -21.5, 35, -5);
            destination.write(book);
            modifier.inventory.setItem(0, book);
            AbstractContainerMenu menu = new LinkModifierMenu(1, player.getInventory(), modifier);
            CompoundTag unchanged = book.getTag().copy();
            require(!LinkModifierBlockEntity.authorized(player), "Fresh survival fixture unexpectedly authorized");
            require(!menu.stillValid(player) && !menu.clickMenuButton(player, LinkModifierMenu.FLAG_START), "Survival player can toggle admin editor flags");
            require(menu.quickMoveStack(player, 0).isEmpty() && unchanged.equals(book.getTag()), "Survival editor access moved or edited book");
            player.getAbilities().instabuild = true;
            require(menu.stillValid(player), "Creative fixture cannot use editor");
            AbstractContainerMenu previousMenu = player.containerMenu;
            player.containerMenu = menu;
            try {
                require(dispatch(player, menu.containerId, LinkModifierMenu.TITLE_CLEAR), "Encoded modifier title clear refused");
                require(dispatch(player, menu.containerId, LinkModifierMenu.TITLE_CHAR | '\u03a9'), "Encoded modifier Unicode title action refused");
                require(dispatch(player, menu.containerId, LinkModifierMenu.TITLE_APPLY) && book.getHoverName().getString().equals("\u03a9"), "Encoded Unicode title lost high bits");
                CompoundTag encodedTitle = book.getTag().copy();
                require(!dispatch(player, menu.containerId + 1, LinkModifierMenu.FLAG_START) && encodedTitle.equals(book.getTag()), "Wrong-container modifier action mutated book");
                dispatch(player, menu.containerId, LinkModifierMenu.TITLE_CLEAR);
                dispatch(player, menu.containerId, LinkModifierMenu.TITLE_APPLY);
            } finally { player.containerMenu = previousMenu; }
            for (int i = 0; i < LinkModifierMenu.FLAGS.size(); i++) require(menu.clickMenuButton(player, LinkModifierMenu.FLAG_START + i), "Flag toggle refused");
            LinkModifiers flags = LinkModifiers.from(book);
            require(flags.intraLinking() && flags.intraLinkingOnly() && flags.disarm() && flags.relative() && flags.maintainMomentum() && flags.generatePlatform(), "Editor did not write exact source flags");
            require(LinkDestination.read(book).orElseThrow().equals(destination), "Flag editing changed destination coordinates or orientation");
            menu.clickMenuButton(player, LinkModifierMenu.TITLE_CLEAR);
            for (char ch : "Admin Title".toCharArray()) menu.clickMenuButton(player, LinkModifierMenu.TITLE_CHAR | ch);
            require(!book.hasCustomHoverName(), "Buffered title edited before apply");
            require(menu.clickMenuButton(player, LinkModifierMenu.TITLE_APPLY) && book.getHoverName().getString().equals("Admin Title"), "Bounded title update failed");
            passed.add("link modifier: survival access and transfers rejected; creative six-flag and title edits preserve exact destination");

            CompoundTag panel = new CompoundTag(); PageData.makeLinkPanel(panel);
            ItemStack unallocated = AgeBookData.create("Seed Test", List.of(panel));
            modifier.inventory.setItem(0, unallocated);
            previousMenu = player.containerMenu;
            player.containerMenu = menu;
            try {
                require(dispatch(player, menu.containerId, LinkModifierMenu.SEED_CLEAR), "Encoded seed clear refused");
                for (char ch : "-42".toCharArray()) require(dispatch(player, menu.containerId, LinkModifierMenu.SEED_CHAR | ch), "Encoded seed character truncated");
                require(dispatch(player, menu.containerId, LinkModifierMenu.SEED_APPLY) && unallocated.getTag().getCompound("Props").getString("Seed").equals("-42"), "Encoded signed seed command failed");
            } finally { player.containerMenu = previousMenu; }
            passed.add("modifier transport: Unicode title and signed seed commands survive full-width byte-buffer roundtrip, dispatch only to the active validated menu, and reject wrong menu IDs");
            text(menu, player, Long.toString(Long.MIN_VALUE));
            require(menu.clickMenuButton(player, LinkModifierMenu.SEED_APPLY), "Signed Long.MIN_VALUE seed rejected");
            require(unallocated.getTag().getCompound("Props").getString("Seed").equals(Long.toString(Long.MIN_VALUE)), "Source string seed stored incorrectly");
            text(menu, player, "9223372036854775808");
            require(!menu.clickMenuButton(player, LinkModifierMenu.SEED_APPLY), "Overflow seed accepted");
            require(unallocated.getTag().getCompound("Props").getString("Seed").equals(Long.toString(Long.MIN_VALUE)), "Invalid seed destroyed previous setting");
            menu.clickMenuButton(player, LinkModifierMenu.SEED_CLEAR);
            require(menu.clickMenuButton(player, LinkModifierMenu.SEED_APPLY) && !unallocated.getTag().getCompound("Props").contains("Seed"), "Empty seed failed to restore random allocation");
            AgeBookData.assign(unallocated, UUID.fromString("f1bd7312-d7ee-483f-a5b8-e996019e23cc"), 99, 777);
            CompoundTag allocated = unallocated.getTag().copy();
            text(menu, player, "123");
            require(!menu.clickMenuButton(player, LinkModifierMenu.SEED_APPLY) && allocated.equals(unallocated.getTag()), "Generated Age seed can be changed through editor");
            passed.add("link modifier: signed seed strings bounded and validated, overflow leaves settings intact, clear restores random seed, allocated Ages cannot be reseeded");

            CompoundTag saved = ((BlockEntity) modifier).saveWithFullMetadata();
            LinkModifierBlockEntity restored = new LinkModifierBlockEntity(pos, state);
            ((BlockEntity) restored).load(saved);
            require(ItemStack.matches(modifier.inventory.getItem(0), restored.inventory.getItem(0)), "Stored admin book lost NBT on restart");
            player.getAbilities().instabuild = false;
            require(!menu.clickMenuButton(player, LinkModifierMenu.FLAG_START), "Revoked creative access retained editing permissions");
            player.getAbilities().instabuild = true;
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            require(!menu.stillValid(player) && !menu.clickMenuButton(player, LinkModifierMenu.FLAG_START), "Remote player can edit admin book");
            passed.add("link modifier: book inventory persists; permission revocation and distance immediately stop edits");
        } finally {
            if (level.getBlockEntity(pos) instanceof LinkModifierBlockEntity modifier) modifier.inventory.clearContent();
            level.setBlock(pos, oldState, 3);
            if (oldTag != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(oldTag);
        }
        return passed;
    }
    private static void text(AbstractContainerMenu menu, ServerPlayer player, String text) {
        menu.clickMenuButton(player, LinkModifierMenu.SEED_CLEAR);
        for (char ch : text.toCharArray()) require(menu.clickMenuButton(player, LinkModifierMenu.SEED_CHAR | ch), "Seed character refused");
    }
    private static boolean dispatch(ServerPlayer player, int menuId, int command) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MenuActions.Action original = new MenuActions.Action(menuId, command);
            original.encode(buffer);
            MenuActions.Action decoded = MenuActions.Action.decode(buffer);
            require(original.equals(decoded) && buffer.readableBytes() == 0, "Menu command truncated or left unread data during VarInt roundtrip");
            return MenuActions.handle(player, decoded);
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
