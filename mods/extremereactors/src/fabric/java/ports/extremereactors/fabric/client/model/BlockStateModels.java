/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads a block's block state file the way vanilla's model bakery does, for the blocks whose models Fabric loads
 * itself (see {@link ModelReplacements#blocks()}).
 * <p>
 * Vanilla reads the file from every resource pack, lowest priority first. In each one a state gets the multipart
 * model if there is one, then the last variant whose key matches it; a later pack that has a model for a state
 * replaces the earlier one.
 */
final class BlockStateModels {

    /**
     * @return the unbaked model of each state of the block, by its block state model id. States no pack has a model
     * for are left out.
     */
    static Map<ModelResourceLocation, UnbakedModel> load(final ResourceManager resources, final ResourceLocation blockId,
                                                       final Block block) {

        final ResourceLocation file = new ResourceLocation(blockId.getNamespace(),
                "blockstates/" + blockId.getPath() + ".json");
        final Map<ModelResourceLocation, UnbakedModel> models = new HashMap<>();
        final BlockModelDefinition.Context context = new BlockModelDefinition.Context();

        context.setDefinition(block.getStateDefinition());

        for (final Resource resource : resources.getResourceStack(file)) {

            final BlockModelDefinition definition;

            try (Reader reader = resource.openAsReader()) {
                definition = BlockModelDefinition.fromStream(context, reader);
            } catch (final Exception e) {
                LOGGER.warn("Unable to load block state definition {} in pack {}", file, resource.sourcePackId(), e);
                continue;
            }

            for (final BlockState state : block.getStateDefinition().getPossibleStates()) {

                final Map<String, String> values = valueNames(state);
                UnbakedModel model = definition.isMultiPart() ? definition.getMultiPart() : null;

                for (final Map.Entry<String, MultiVariant> variant : definition.getVariants().entrySet()) {
                    if (matches(variant.getKey(), values)) {
                        model = variant.getValue();
                    }
                }

                if (null != model) {
                    models.put(BlockModelShaper.stateToModelLocation(blockId, state), model);
                }
            }
        }

        return models;
    }

    /**
     * True if a block state file variant key ({@code "axis=x,lit=true"}, or {@code ""} for every state) selects a
     * state. As in vanilla, a key naming a property the block does not have, or a property without a value,
     * selects no state.
     *
     * @param values the state's property values, by property name, as written in the block state file
     */
    static boolean matches(final String key, final Map<String, String> values) {

        for (final String condition : key.split(",", -1)) {

            final int separator = condition.indexOf('=');

            if (separator < 0) {

                if (condition.isEmpty()) {
                    continue;
                }

                return false;
            }

            final String value = values.get(condition.substring(0, separator));

            if (null == value || !value.equals(condition.substring(separator + 1))) {
                return false;
            }
        }

        return true;
    }

    private static Map<String, String> valueNames(final BlockState state) {

        final Map<String, String> names = new HashMap<>();

        for (final Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            names.put(entry.getKey().getName(), valueName(entry.getKey(), entry.getValue()));
        }

        return names;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String valueName(final Property<T> property, final Comparable<?> value) {
        return property.getName((T) value);
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private BlockStateModels() {
    }
}
