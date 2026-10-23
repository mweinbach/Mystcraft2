/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import ports.metalrender.config.MetalRenderConfig;
import ports.metalrender.terrain.SectionStore;

/** Client-side {@code /metalrender} (alias {@code /mr}) status and settings commands. */
final class MetalRenderCommands {

    private MetalRenderCommands() {
    }

    static void register(final CommandDispatcher<FabricClientCommandSource> dispatcher,
                         final CommandBuildContext buildContext) {
        final LiteralArgumentBuilder<FabricClientCommandSource> tier = literal("tier");
        for (final MetalRenderConfig.Tier value : MetalRenderConfig.Tier.values()) {
            tier.then(literal(value.id()).executes(c -> change(c.getSource(), cfg -> cfg.tier = value, true)));
        }
        final LiteralArgumentBuilder<FabricClientCommandSource> upscaler = literal("upscaler");
        for (final MetalRenderConfig.Upscaler value : MetalRenderConfig.Upscaler.values()) {
            upscaler.then(literal(value.id()).executes(c -> change(c.getSource(), cfg -> cfg.upscaler = value, false)));
        }
        final LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(literal("metalrender")
                .executes(c -> status(c.getSource()))
                .then(literal("status").executes(c -> status(c.getSource())))
                .then(literal("caps").executes(c -> capabilities(c.getSource())))
                .then(literal("enable").executes(c -> change(c.getSource(), cfg -> cfg.enabled = true, false)))
                .then(literal("disable").executes(c -> change(c.getSource(), cfg -> cfg.enabled = false, false)))
                .then(literal("reload").executes(c -> change(c.getSource(), cfg -> { }, true)))
                .then(tier)
                .then(upscaler)
                .then(literal("scale").then(argument("fraction",
                        FloatArgumentType.floatArg(MetalRenderConfig.MIN_SCALE, MetalRenderConfig.MAX_SCALE))
                        .executes(c -> change(c.getSource(),
                                cfg -> cfg.resolutionScale = FloatArgumentType.getFloat(c, "fraction"), false))))
                .then(literal("meshshaders").then(argument("on", BoolArgumentType.bool())
                        .executes(c -> change(c.getSource(),
                                cfg -> cfg.meshShaders = BoolArgumentType.getBool(c, "on"), false)))));
        dispatcher.register(literal("mr").executes(c -> status(c.getSource())).redirect(root));
    }

    private static int change(final FabricClientCommandSource source, final Consumer<MetalRenderConfig> edit,
                              final boolean recreateContext) {
        edit.accept(MetalRenderClient.config());
        MetalRenderClient.config().sanitized();
        MetalRenderClient.applyConfig(recreateContext);
        return status(source);
    }

    private static int status(final FabricClientCommandSource source) {
        final MetalRenderConfig config = MetalRenderClient.config();
        source.sendFeedback(Component.literal("MetalRender: " + MetalRenderClient.status()));
        source.sendFeedback(Component.literal(String.format(
                "  requested tier %s, upscaler %s, scale %.0f%%, mesh shaders %s",
                config.tier.id(), config.upscaler.id(), config.resolutionScale * 100.0f,
                config.meshShaders ? "on" : "off")));
        final SectionStore store = MetalRenderClient.store();
        if (store != null) {
            source.sendFeedback(Component.literal(String.format("  %d sections resident, %d/%d vertices, %d meshlets",
                    store.residentSections(), store.usedVertices(), store.vertexCapacity(), store.usedMeshlets())));
        }
        source.sendFeedback(Component.literal("  " + MetalRenderClient.capabilities().summary()));
        return 1;
    }

    private static int capabilities(final FabricClientCommandSource source) {
        for (final Map.Entry<String, String> entry : MetalRenderClient.capabilities().asMap().entrySet()) {
            source.sendFeedback(Component.literal("  " + entry.getKey() + " = " + entry.getValue()));
        }
        return 1;
    }
}
