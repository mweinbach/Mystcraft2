package ports.buildcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.energy.OilWellFeature;

/**
 * Development-only commands for screenshots: {@code /bcshowcase build} lays out {@link Showcase} at the caller's feet,
 * {@code /bcshowcase engines} builds the engine close-up bench there, {@code /bcshowcase builders} the builder bench,
 * {@code /bcshowcase library} the electronic library and replacer bench, {@code /bcshowcase silicon} the laser and table bench,
 * {@code /bcshowcase plugs} the pipe plug, power adapter and structure pipe bench, {@code /bcshowcase gates} the gate bench,
 * {@code /bcshowcase wires} the pipe wire bench, {@code /bcshowcase lenses} the lens and filter bench,
 * {@code /bcshowcase refinery} a running refinery,
 * {@code /bcshowcase oilwell} reports the nearest oil well, {@code /bcshowcase use <pos>} right-clicks a block
 * with an empty hand to open its screen and {@code /bcshowcase useitem} right-clicks with the held item where the caller looks.
 */
public final class FabricShowcase implements ModInitializer {
    private static final int FILL_DELAY_TICKS = 5;
    private static final int WELL_SEARCH_RADIUS = 256;
    private static final double USE_REACH = 5;

    private record Delayed(int dueTick, Runnable task) {}

    private final List<Delayed> delayed = new ArrayList<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> delayed.removeIf(task -> {
            if (server.getTickCount() < task.dueTick()) return false;
            task.task().run();
            return true;
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("bcshowcase").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("build").executes(context -> {
                            Showcase.build(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("engines").executes(context -> {
                            Showcase.buildEngineBench(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("builders").executes(context -> {
                            Showcase.buildBuilders(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("library").executes(context -> {
                            Showcase.buildLibrary(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("silicon").executes(context -> {
                            Showcase.buildSilicon(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("plugs").executes(context -> {
                            Showcase.buildPlugs(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("gates").executes(context -> {
                            Showcase.buildGates(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("wires").executes(context -> {
                            Showcase.buildWires(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("lenses").executes(context -> {
                            Showcase.buildLenses(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("refinery").executes(context -> {
                            Runnable fill = Showcase.buildRefinery(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                            delayed.add(new Delayed(context.getSource().getServer().getTickCount() + FILL_DELAY_TICKS, fill));
                            return 1;
                        }))
                        .then(Commands.literal("oilwell")
                                .executes(context -> reportWell(context.getSource(), null))
                                .then(Commands.literal("large").executes(context -> reportWell(context.getSource(), OilWellFeature.WellType.LARGE))))
                        .then(Commands.literal("use").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                            player.getLevel().getBlockState(pos).use(player.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                            return 1;
                        })))
                        .then(Commands.literal("useitem").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack stack = player.getMainHandItem();
                            InteractionResult result = InteractionResult.PASS;
                            if (player.pick(USE_REACH, 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                                result = player.gameMode.useItemOn(player, player.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
                                context.getSource().sendSuccess(Component.literal("Used " + stack + " on " + hit.getBlockPos().toShortString()
                                        + " " + hit.getDirection() + ": " + result), false);
                            }
                            if (!result.consumesAction()) {
                                result = player.gameMode.useItem(player, player.getLevel(), stack, InteractionHand.MAIN_HAND);
                                context.getSource().sendSuccess(Component.literal("Used " + stack + ": " + result), false);
                            }
                            return 1;
                        }))));
    }

    private static int reportWell(CommandSourceStack source, @Nullable OilWellFeature.WellType type) {
        ServerLevel level = source.getLevel();
        ChunkPos chunk = new ChunkPos(new BlockPos(source.getPosition()));
        OilWellFeature.Well well = Showcase.nearestWell(level, chunk.x, chunk.z, WELL_SEARCH_RADIUS, type);
        String message = well == null ? "No oil well within " + WELL_SEARCH_RADIUS + " chunks"
                : "Nearest oil well: " + well.type() + " at " + well.centre().toShortString();
        source.sendSuccess(Component.literal(message), false);
        return well == null ? 0 : 1;
    }
}
