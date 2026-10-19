package com.technovision.advancedgenetics.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.technovision.advancedgenetics.api.genetics.Genes;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GeneArgumentType implements ArgumentType<Genes> {

    private static final SimpleCommandExceptionType INVALID_GENE = new SimpleCommandExceptionType(Component.literal("Invalid gene name"));

    public static GeneArgumentType gene() {
        return new GeneArgumentType();
    }

    @Override
    public Genes parse(StringReader reader) throws CommandSyntaxException {
        int argBeginning = reader.getCursor();
        if (!reader.canRead()) {
            reader.skip();
        }
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        String geneString = reader.getString().substring(argBeginning, reader.getCursor());
        Genes gene = Genes.byName(geneString.toUpperCase());
        if (gene == null) {
            throw INVALID_GENE.createWithContext(reader);
        }
        return gene;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(Genes.values()).map(gene -> gene.toString().toLowerCase()), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("water_breathing", "resistance", "wither_hit");
    }
}
