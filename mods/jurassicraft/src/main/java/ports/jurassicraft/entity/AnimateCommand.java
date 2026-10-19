package ports.jurassicraft.entity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;

/** Original /animate and /anim commands, restricted to poses present for each species. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID)
public final class AnimateCommand {
    private static final SimpleCommandExceptionType NO_TARGET = new SimpleCommandExceptionType(Component.literal("No dinosaur to animate"));
    private static final DynamicCommandExceptionType UNSUPPORTED = new DynamicCommandExceptionType(
            value -> Component.literal("No selected dinosaur has animation " + value));
    private AnimateCommand() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) { register(event.getDispatcher()); }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = dispatcher.register(Commands.literal("animate")
                .then(Commands.argument("animation", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(DinosaurAnimationData.names(), builder))
                        .executes(context -> execute(context, false, false))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> execute(context, true, false))
                                .then(Commands.argument("variant", IntegerArgumentType.integer(0, 255))
                                        .executes(context -> execute(context, true, true))))));
        dispatcher.register(Commands.literal("anim").redirect(command));
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean hasTargets, boolean hasVariant)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "animation").toUpperCase(Locale.ROOT);
        int variant = hasVariant ? IntegerArgumentType.getInteger(context, "variant") : 0;
        Collection<? extends Entity> targets;
        if (hasTargets) {
            // Upstream's selector proxy permits @ selectors even for non-operator users.
            targets = EntityArgument.getEntities(context.copyFor(source.withPermission(2)), "targets");
        } else {
            var nearby = new ArrayList<DinosaurEntity>();
            for (Entity entity : source.getLevel().getAllEntities())
                if (entity instanceof DinosaurEntity dinosaur && dinosaur.isAlive()) nearby.add(dinosaur);
            targets = nearby.stream().min(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(source.getPosition())))
                    .<Collection<? extends Entity>>map(java.util.List::of).orElseGet(java.util.List::of);
        }
        int dinosaurs = 0, played = 0;
        for (Entity target : targets) {
            if (target instanceof DinosaurEntity dinosaur && dinosaur.isAlive()) {
                dinosaurs++;
                if (dinosaur.playPose(name, variant, true)) played++;
            }
        }
        if (dinosaurs == 0) throw NO_TARGET.create();
        if (played == 0) throw UNSUPPORTED.create(name + (variant == 0 ? "" : " variant " + variant));
        source.sendSuccess(Component.literal("Playing " + name + " on " + played + " dinosaur(s)"), false);
        return played;
    }
}
