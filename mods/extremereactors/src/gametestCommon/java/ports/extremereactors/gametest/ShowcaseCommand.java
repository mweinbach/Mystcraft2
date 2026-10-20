/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The development-only {@code /ershowcase} command: {@code build} lays out the {@link Showcase} machines at the
 * caller's feet, {@code plant} builds a large reactor and turbine there, {@code status} reports whether they assembled and {@code open <part>} opens a part's screen.
 */
public final class ShowcaseCommand {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ershowcase")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("build").executes(context -> {

                    Showcase.build(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                    return 1;
                }))
                .then(Commands.literal("plant").executes(context -> {

                    Showcase.buildPlant(context.getSource().getLevel(), new BlockPos(context.getSource().getPosition()));
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {

                    Showcase.status(context.getSource().getLevel())
                            .forEach(line -> context.getSource().sendSuccess(line, false));
                    return 1;
                }))
                .then(Commands.literal("open")
                        .then(Commands.argument("part", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Showcase.screens(), builder))
                                .executes(context -> {

                                    final String part = StringArgumentType.getString(context, "part");

                                    if (Showcase.open(context.getSource().getPlayerOrException(), part)) {
                                        return 1;
                                    }

                                    context.getSource().sendFailure(Component.literal("Unknown part " + part));
                                    return 0;
                                }))));
    }

    private ShowcaseCommand() {
    }
}
