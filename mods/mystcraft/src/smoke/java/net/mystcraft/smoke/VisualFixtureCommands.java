package net.mystcraft.smoke;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.LightningRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.PageData;

/** Operator-only graphical fixtures, packaged solely in the opt-in smoke mod. */
public final class VisualFixtureCommands {
    private VisualFixtureCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("mystcraft_visual_fixture").requires(source -> source.hasPermission(4));
        for (String mode : List.of("clear", "rain", "snow", "storm", "cloudy", "rainbow", "lightning")) {
            root.then(Commands.literal(mode)
                    .executes(context -> give(context.getSource(), context.getSource().getPlayerOrException(), mode))
                    .then(Commands.argument("player", EntityArgument.player()).executes(context ->
                            give(context.getSource(), EntityArgument.getPlayer(context, "player"), mode))));
        }
        dispatcher.register(root);
        dispatcher.register(Commands.literal("mystcraft_lightning_fixture").requires(source -> source.hasPermission(4))
                .executes(context -> lightning(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(context ->
                        lightning(context.getSource(), EntityArgument.getPlayer(context, "player")))));
    }
    private static int give(CommandSourceStack source, ServerPlayer player, String mode) {
        List<CompoundTag> pages = new ArrayList<>();
        pages.add(PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get())));
        add(pages, "terrainflat", "biome/minecraft/plains", "bioconsingle");
        add(pages, switch (mode) { case "rain" -> "weatherrain"; case "snow" -> "weathersnow";
            case "storm" -> "weatherstorm"; case "cloudy" -> "weathercloudy"; default -> "weatheroff"; });
        add(pages, "lightingbright", "modcolorcyan", "colorcloud", "modcolormagenta", "colorgrass", "modcolorblue", "colorfoliage");
        if (mode.equals("lightning")) add(pages, "modcolorred", "modfull", "modgradient", "modcolorblue", "modfull", "modgradient", "envlightning");
        add(pages, "modeast", "rainbow", "nohorizon", "modfull", "modnorth", "modnoon", "sunnormal", "moondark", "starsdark",
                "featurelargedummy", "featuremediumdummy", "featuresmalldummy");
        ItemStack book = AgeBookData.create("Environment " + mode, pages);
        CompoundTag properties = book.getOrCreateTag().getCompound("Props"); properties.putString("Seed", "22345"); book.getOrCreateTag().put("Props", properties);
        if (!((Player) player).getInventory().add(book)) {
            source.sendFailure(Component.literal("Inventory full: make room before requesting the fixture book.")); return 0;
        }
        source.sendSuccess(Component.literal("MYSTCRAFT_VISUAL_FIXTURE " + mode + ": unallocated classic book delivered. Activate its panel; matching visual protocol3 client required. Rainbow is unconditional; cloud/grass/foliage are cyan/magenta/blue."), false);
        return 1;
    }
    private static int lightning(CommandSourceStack source, ServerPlayer player) {
        Vec3 forward = ((Player) player).getLookAngle();
        Vec3 position = ((Player) player).position().add(forward.scale(8));
        var bolt = LightningRegistry.spawn(player.getLevel(), position, 1, 0, 1);
        if (bolt == null) { source.sendFailure(Component.literal("Lightning entity spawn rejected.")); return 0; }
        ((LightningBolt) bolt).setVisualOnly(true);
        source.sendSuccess(Component.literal("MYSTCRAFT_LIGHTNING_FIXTURE: magenta synced native bolt spawned8blocks ahead; visual-only fixture disables damage/fire."), false);
        return 1;
    }
    private static void add(List<CompoundTag> pages, String... paths) {
        for (String path : paths) { CompoundTag page = new CompoundTag(); page.putString("symbol", "mystcraft:" + path); pages.add(page); }
    }
}
