package ports.buildcraft.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only commands for screenshots: {@code /bcshowcase build} lays out {@link Showcase} at the caller's feet,
 * {@code /bcshowcase engines} builds the engine close-up bench there and {@code /bcshowcase use <pos>} right-clicks a block
 * with an empty hand to open its screen.
 */
public final class FabricShowcase implements ModInitializer {
    @Override
    public void onInitialize() {
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
                        .then(Commands.literal("use").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                            player.getLevel().getBlockState(pos).use(player.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                            return 1;
                        })))));
    }
}
