package net.mystcraft.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.AgeSavedData;
import net.mystcraft.minecraft.age.AgeServerLevel;

/** Namespaced modern adapters for classic dimension-local administration; vanilla commands stay intact. */
public final class MystcraftCommands {
    private static final SimpleCommandExceptionType NOT_AGE = new SimpleCommandExceptionType(Component.literal("Select an existing Mystcraft Age."));
    private static final SimpleCommandExceptionType INVENTORY_FULL = new SimpleCommandExceptionType(Component.literal("Make room for the descriptive book first."));
    private MystcraftCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ClassicLinkCommands.register(dispatcher);
        var root = Commands.literal("mystcraft").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("ages").executes(context -> {
            var ages = AgeManager.data(context.getSource().getServer()).entries();
            for (var age : ages) context.getSource().sendSuccess(Component.literal(age.dimensionId() + " — " + age.title() + " (seed " + age.seed() + ")"), false);
            return ages.size();
        }));
        root.then(book("book"));
        var time = Commands.literal("time");
        time.then(Commands.literal("query").executes(context -> query(context.getSource(), context.getSource().getLevel()))
                .then(Commands.argument("dimension", DimensionArgument.dimension()).executes(context -> query(context.getSource(), DimensionArgument.getDimension(context, "dimension")))));
        for (boolean add : new boolean[]{false, true}) {
            time.then(Commands.literal(add ? "add" : "set").then(Commands.argument("ticks", LongArgumentType.longArg(0))
                    .executes(context -> time(context.getSource(), context.getSource().getLevel(), LongArgumentType.getLong(context, "ticks"), add))
                    .then(Commands.argument("dimension", DimensionArgument.dimension()).executes(context ->
                            time(context.getSource(), DimensionArgument.getDimension(context, "dimension"), LongArgumentType.getLong(context, "ticks"), add)))));
        }
        root.then(time);
        var instability = Commands.literal("instability").executes(context -> {
            var source = context.getSource(); var age = current(source);
            var data = age.state().getCompound("Instability");
            source.sendSuccess(Component.literal(age.dimensionId() + ": score " + data.getInt("Score")
                    + ", profiled chunks " + data.getInt("ProfiledChunks") + ", symbol cost " + age.state().getInt("SymbolInstability")), false);
            return data.getInt("Score");
        });
        for (boolean enabled : new boolean[]{true, false}) instability.then(Commands.literal(enabled ? "enable" : "disable").executes(context -> {
            var source = context.getSource(); var age = current(source); var state = age.state();
            var data = state.getCompound("Instability"); data.putBoolean("Enabled", enabled); state.put("Instability", data);
            // An explicit operator enable can opt a pre-grammar Age into the new classic runtime.
            if (enabled) state.putInt("ClassicRuntimeVersion", 1);
            AgeManager.data(source.getServer()).setDirty();
            source.sendSuccess(Component.literal("Automatic classic instability " + (enabled ? "enabled" : "disabled") + " for " + age.dimensionId()), true);
            return 1;
        }));
        root.then(instability);
        dispatcher.register(root);
        dispatcher.register(book("myst-agebook").requires(source -> source.hasPermission(2)));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> book(String name) {
        return Commands.literal(name).executes(context -> giveBook(context.getSource(), current(context.getSource())))
                .then(Commands.argument("number", LongArgumentType.longArg(1)).executes(context -> {
                    long number = LongArgumentType.getLong(context, "number");
                    for (var age : AgeManager.data(context.getSource().getServer()).entries()) if (age.number() == number)
                        return giveBook(context.getSource(), age);
                    throw NOT_AGE.create();
                }));
    }
    private static AgeSavedData.Entry current(CommandSourceStack source) throws CommandSyntaxException {
        for (var age : AgeManager.data(source.getServer()).entries()) if (age.dimensionId().equals(source.getLevel().dimension().location())) return age;
        throw NOT_AGE.create();
    }
    private static int giveBook(CommandSourceStack source, AgeSavedData.Entry age) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (player.getInventory().getFreeSlot() < 0) throw INVENTORY_FULL.create();
        var book = AgeBookData.create(age.title(), age.pages());
        AgeBookData.assign(book, age.id(), age.number(), age.seed());
        if (age.state().contains("Authors", 9)) book.getOrCreateTag().put("Authors", age.state().getList("Authors", 8).copy());
        if (!player.getInventory().add(book)) throw INVENTORY_FULL.create();
        player.getInventory().setChanged();
        source.sendSuccess(Component.literal("Created descriptive book for " + age.dimensionId()), true);
        return 1;
    }
    private static int query(CommandSourceStack source, ServerLevel level) {
        source.sendSuccess(Component.literal(level.dimension().location() + " time: " + level.getDayTime()), false);
        return (int) (level.getDayTime() & Integer.MAX_VALUE);
    }
    private static int time(CommandSourceStack source, ServerLevel level, long ticks, boolean add) throws CommandSyntaxException {
        long value;
        try { value = add ? Math.addExact(level.getDayTime(), ticks) : ticks; }
        catch (ArithmeticException overflow) { throw new SimpleCommandExceptionType(Component.literal("Time exceeds signed 64-bit range.")).create(); }
        level.setDayTime(value);
        source.sendSuccess(Component.literal("Set " + level.dimension().location() + " time to " + value), true);
        return 1;
    }
}
