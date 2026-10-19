package com.technovision.advancedgenetics.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removes a gene from a specified player.
 *
 * @author TechnoVision
 */
public class RemoveGeneCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gene")
                .then(Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("gene", GeneArgumentType.gene())
                .requires(source -> source.hasPermission(2))
                .executes(RemoveGeneCommand::run)
        ))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Genes gene = context.getArgument("gene", Genes.class);
        ComponentRegistry.getGenetics(player).removeGene(gene);
        player.sendSystemMessage(Component.translatable("message.advancedgenetics.command.gene_remove", "§7"+gene.getName()+"§f", "§7"+player.getName().getString()+"§f"));
        return 1;
    }
}
