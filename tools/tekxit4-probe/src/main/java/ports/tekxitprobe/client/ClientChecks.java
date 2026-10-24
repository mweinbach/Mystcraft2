package ports.tekxitprobe.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import ports.tekxitprobe.ServerChecks;

/** Client resource checks for the port namespaces: models, textures, translations and key bindings. */
final class ClientChecks {
    private static final int EXAMPLES = 8;
    /**
     * Upstream Mekanism 10.3.9 leaves the robit item's {@code #robit} texture unmapped on purpose: its model loader
     * picks the skin at render time and drops the missing-texture error.
     */
    private static final java.util.Set<String> UNMAPPED_BY_DESIGN = java.util.Set.of("mekanism:robit");

    private ClientChecks() {}

    static JsonObject run(Minecraft mc, List<String> failures, List<String> warnings) {
        JsonObject out = new JsonObject();
        BakedModel missing = mc.getModelManager().getMissingModel();
        ResourceLocation missingSprite = MissingTextureAtlasSprite.getLocation();
        RandomSource random = RandomSource.create(42L);
        Map<String, List<String>> problems = new TreeMap<>();

        for (Item item : Registry.ITEM) {
            ResourceLocation id = Registry.ITEM.getKey(item);
            if (!ServerChecks.ours(id)) continue;
            BakedModel model = mc.getItemRenderer().getItemModelShaper().getItemModel(item);
            if (model == null || model == missing) add(problems, "itemModelMissing", id);
            // builtin/entity items are drawn by their item renderer; their static model has no quads or particle
            else if (model.isCustomRenderer()) add(problems, "itemBuiltinRenderer", id);
            else if (!UNMAPPED_BY_DESIGN.contains(id.toString())) {
                Missing kind = missingSprite(model, null, random, missingSprite);
                if (kind == Missing.QUAD) add(problems, "itemTextureMissing", id);
                else if (kind == Missing.PARTICLE) add(problems, "itemParticleMissing", id);
            }
            if (!I18n.exists(item.getDescriptionId())) add(problems, "itemNameMissing", id);
        }
        for (Block block : Registry.BLOCK) {
            ResourceLocation id = Registry.BLOCK.getKey(block);
            if (!ServerChecks.ours(id)) continue;
            int missingStates = 0;
            int missingTextures = 0;
            int missingParticles = 0;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BakedModel model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
                if (model == null || model == missing) {
                    // Entity-rendered blocks may legitimately have no static model.
                    if (state.getRenderShape() == RenderShape.MODEL) missingStates++;
                } else if (state.getRenderShape() == RenderShape.MODEL) {
                    Missing kind = missingSprite(model, state, random, missingSprite);
                    if (kind == Missing.QUAD) missingTextures++;
                    else if (kind == Missing.PARTICLE) missingParticles++;
                }
            }
            if (missingStates > 0) add(problems, "blockModelMissing", id + " (" + missingStates + " states)");
            if (missingTextures > 0) add(problems, "blockTextureMissing", id + " (" + missingTextures + " states)");
            if (missingParticles > 0) add(problems, "blockParticleMissing", id + " (" + missingParticles + " states)");
            if (block.asItem() == net.minecraft.world.item.Items.AIR && !I18n.exists(block.getDescriptionId())) {
                add(problems, "blockNameMissing", id);
            }
        }
        for (EntityType<?> type : Registry.ENTITY_TYPE) {
            ResourceLocation id = Registry.ENTITY_TYPE.getKey(type);
            if (ServerChecks.ours(id) && !I18n.exists(type.getDescriptionId())) add(problems, "entityNameMissing", id);
        }

        JsonObject summary = new JsonObject();
        problems.forEach((kind, list) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("count", list.size());
            entry.add("examples", ServerChecks.strings(list.subList(0, Math.min(EXAMPLES, list.size()))));
            entry.add("all", ServerChecks.strings(list));
            summary.add(kind, entry);
            if (kind.equals("itemBuiltinRenderer")) return; // informational
            String line = kind + ": " + list.size() + ", e.g. " + list.subList(0, Math.min(EXAMPLES, list.size()));
            if (kind.endsWith("ModelMissing") || kind.endsWith("TextureMissing")) failures.add(line);
            else warnings.add(line);
        });
        out.add("resources", summary);
        out.add("keyConflicts", ServerChecks.strings(keyConflicts(mc, warnings)));
        out.add("metalrender", metalRender());
        return out;
    }

    /** QUAD: a drawn face uses the missing texture. PARTICLE: only the break/use particle does (warning). */
    private enum Missing { NONE, PARTICLE, QUAD }

    private static Missing missingSprite(BakedModel model, BlockState state, RandomSource random, ResourceLocation missing) {
        for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            List<BakedQuad> quads;
            try {
                quads = model.getQuads(state, side, random);
            } catch (RuntimeException e) {
                return Missing.NONE; // custom models that need render context
            }
            for (BakedQuad quad : quads) {
                if (missing.equals(quad.getSprite().getName())) return Missing.QUAD;
            }
        }
        TextureAtlasSprite particle = model.getParticleIcon();
        return particle != null && missing.equals(particle.getName()) ? Missing.PARTICLE : Missing.NONE;
    }

    private static void add(Map<String, List<String>> problems, String kind, Object id) {
        problems.computeIfAbsent(kind, k -> new ArrayList<>()).add(String.valueOf(id));
    }

    private static boolean ourKey(KeyMapping key) {
        String text = (key.getName() + " " + key.getCategory()).toLowerCase(Locale.ROOT);
        for (String ns : ServerChecks.NAMESPACES) if (text.contains(ns)) return true;
        return text.contains("icbm") || text.contains("extremereactors") || text.contains("metalrender");
    }

    private static List<String> keyConflicts(Minecraft mc, List<String> warnings) {
        List<String> conflicts = new ArrayList<>();
        KeyMapping[] keys = mc.options.keyMappings;
        for (KeyMapping key : keys) {
            if (!ourKey(key) || key.isUnbound()) continue;
            for (KeyMapping other : keys) {
                if (other != key && !other.isUnbound() && key.same(other)) {
                    conflicts.add(key.getName() + " [" + key.saveString() + "] also bound to " + other.getName()
                            + " (" + other.getCategory() + ")");
                }
            }
        }
        if (!conflicts.isEmpty()) warnings.add("key binding conflicts: " + conflicts);
        return conflicts;
    }

    private static JsonObject metalRender() {
        JsonObject out = new JsonObject();
        try {
            Class<?> client = Class.forName("ports.metalrender.client.MetalRenderClient");
            out.addProperty("active", (Boolean) client.getMethod("isActive").invoke(null));
            out.addProperty("status", String.valueOf(client.getMethod("status").invoke(null)));
        } catch (ReflectiveOperationException | LinkageError e) {
            out.addProperty("present", false);
        }
        return out;
    }
}
