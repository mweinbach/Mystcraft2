package com.technovision.advancedgenetics.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removes all genes from a specified player.
 *
 * @author TechnoVision
 */
public class ClearGeneCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gene")
                .then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player())
                .requires(source -> source.hasPermission(2))
                .executes(ClearGeneCommand::run)
        )));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ComponentRegistry.getGenetics(player).removeAllGenes();
        player.sendSystemMessage(Component.translatable("message.advancedgenetics.command.gene_clear", "§7"+player.getName().getString()+"§f"));
        return 1;
    }
}
