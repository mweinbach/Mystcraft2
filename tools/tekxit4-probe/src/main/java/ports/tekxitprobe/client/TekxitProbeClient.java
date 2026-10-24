package ports.tekxitprobe.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ports.tekxitprobe.ServerChecks;
import ports.tekxitprobe.TekxitProbe;
import ports.tekxitprobe.mixin.CreateWorldScreenAccessor;
import ports.tekxitprobe.mixin.CreativeModeInventoryScreenAccessor;

/**
 * Drives one unattended client session in a disposable instance: title screen, a fresh singleplayer
 * world through the real Create World screen, in-world checks, a block showcase, one creative tab per
 * port, EMI recipe pages, optional GameTest classes, then Save and Quit and a clean shutdown.
 * Active only when {@code tekxit-probe/autorun} exists in the game directory or
 * {@code -Dtekxitprobe.client=true} is set.
 */
public final class TekxitProbeClient implements ClientModInitializer {
    private enum Stage { WAIT_TITLE, OPEN_CREATE, CREATING, IN_WORLD, SHOWCASE, CREATIVE, EMI, TESTS, QUIT, DONE }

    private static final int TITLE_TIMEOUT_TICKS = 20 * 60 * 20;
    private static final int WORLD_TIMEOUT_TICKS = 20 * 60 * 15;
    private static final int SETTLE_TICKS = 20 * 20;
    private static final int EMI_TIMEOUT_TICKS = 20 * 60 * 10;
    private static final int TEST_TICKS = 20 * 45;

    private final JsonObject report = new JsonObject();
    private final JsonArray screens = new JsonArray();
    private final JsonArray shots = new JsonArray();
    private final List<String> failures = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
    private Stage stage = Stage.WAIT_TITLE;
    private int ticks;
    private Boolean savedPauseOnLostFocus;
    private int stageTicks;
    private String lastScreen = "";
    private final List<CreativeModeTab> tabs = new ArrayList<>();
    private final List<ItemStack> emiStacks = new ArrayList<>();
    private int step;
    private int joinedTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("tekxitprobe.client") || Files.exists(TekxitProbe.outDir().resolve("autorun"));
    }

    @Override
    public void onInitializeClient() {
        if (enabled()) {
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] client autorun armed");
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        }
    }

    private void tick(Minecraft mc) {
        if (stage == Stage.DONE) return;
        // Unattended runs must not stall on the pause menu that vanilla reopens every tick while the window is in the
        // background. Restored before quitting so the instance keeps the player's setting.
        if (savedPauseOnLostFocus == null) {
            savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
        } else if (stage == Stage.QUIT) {
            mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        }
        ticks++;
        stageTicks++;
        String screen = mc.screen == null ? "none" : mc.screen.getClass().getName();
        if (ticks % 600 == 0) TekxitProbe.LOGGER.info("[TEKXIT_PROBE] stage {} screen {}", stage, screen);
        if (!screen.equals(lastScreen)) {
            lastScreen = screen;
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] screen {}", screen);
            JsonObject entry = new JsonObject();
            entry.addProperty("ms", System.currentTimeMillis() - jvmStart);
            entry.addProperty("screen", screen);
            entry.addProperty("overlay", mc.getOverlay() != null);
            screens.add(entry);
        }
        try {
            switch (stage) {
                case WAIT_TITLE -> waitTitle(mc);
                case OPEN_CREATE -> openCreate(mc);
                case CREATING -> creating(mc);
                case IN_WORLD -> inWorld(mc);
                case SHOWCASE -> showcase(mc);
                case CREATIVE -> creative(mc);
                case EMI -> emi(mc);
                case TESTS -> tests(mc);
                case QUIT -> quit(mc);
                default -> { }
            }
        } catch (RuntimeException e) {
            TekxitProbe.LOGGER.error("[TEKXIT_PROBE] client stage {} threw", stage, e);
            failures.add("stage " + stage + " threw " + e);
            next(Stage.QUIT);
        }
    }

    private void next(Stage to) {
        TekxitProbe.LOGGER.info("[TEKXIT_PROBE] stage {} -> {} after {} ticks", stage, to, stageTicks);
        stage = to;
        stageTicks = 0;
        step = 0;
        writeReport();
    }

    private void waitTitle(Minecraft mc) {
        if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            if (stageTicks % 20 == 0) mc.getToasts().clear();
            if (!report.has("titleScreenMs")) {
                report.addProperty("titleScreenMs", System.currentTimeMillis() - jvmStart);
                stageTicks = 0;
            }
            if (stageTicks == 60) {
                shot(mc, "01_title");
                next(Stage.OPEN_CREATE);
            }
        } else if (mc.level == null && mc.getOverlay() == null && mc.screen != null && !(mc.screen instanceof TitleScreen)
                && stageTicks > 0 && stageTicks % 100 == 0 && !report.has("titleScreenMs")) {
            // A notice shown before the title screen (Xaero's update list in Tekxit 4) waits for a click.
            warnings.add("dismissed pre-title screen " + mc.screen.getClass().getName());
            mc.setScreen(new TitleScreen());
        } else if (stageTicks > TITLE_TIMEOUT_TICKS) {
            failures.add("title screen not reached; last screen " + lastScreen);
            next(Stage.QUIT);
        }
    }

    private void openCreate(Minecraft mc) {
        CreateWorldScreen.openFresh(mc, mc.screen);
        next(Stage.CREATING);
    }

    private void creating(Minecraft mc) {
        if (step == 0 && mc.screen instanceof CreateWorldScreen create) {
            CreateWorldScreenAccessor accessor = (CreateWorldScreenAccessor) create;
            if (accessor.tekxitprobe$nameEdit() == null) return; // init() not run yet
            accessor.tekxitprobe$nameEdit().setValue("Tekxit Probe");
            accessor.tekxitprobe$setCommands(true);
            accessor.tekxitprobe$setCommandsChanged(true);
            shot(mc, "02_create_world");
            accessor.tekxitprobe$onCreate();
            report.addProperty("createWorldMs", System.currentTimeMillis() - jvmStart);
            step = 1;
            return;
        }
        boolean joined = mc.player != null && mc.level != null && mc.getSingleplayerServer() != null;
        if (joined && mc.screen == null) {
            report.addProperty("inWorldMs", System.currentTimeMillis() - jvmStart);
            next(Stage.IN_WORLD);
        } else if (joined && mc.getOverlay() == null && ++joinedTicks % 200 == 0) {
            // A screen the pack opens on first join (a guide or notice) waits for a click.
            warnings.add("closed post-join screen " + mc.screen.getClass().getName());
            mc.setScreen(null);
        } else if (stageTicks > WORLD_TIMEOUT_TICKS) {
            failures.add("world did not load; last screen " + lastScreen);
            next(Stage.QUIT);
        }
    }

    private void inWorld(Minecraft mc) {
        if (mc.screen != null && stageTicks < SETTLE_TICKS * 3) return; // e.g. a pack welcome screen
        if (stageTicks == 1) {
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
            });
        }
        if (stageTicks < SETTLE_TICKS) return;
        mc.getToasts().clear();
        shot(mc, "03_in_world");
        report.add("client", ClientChecks.run(mc, failures, warnings));
        next(Stage.SHOWCASE);
    }

    private void showcase(Minecraft mc) {
        if (step == 0) {
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> report.add("showcase", buildShowcase(server)));
            step = 1;
        } else if (stageTicks == SETTLE_TICKS) {
            mc.getToasts().clear();
            shot(mc, "04_showcase");
            for (CreativeModeTab tab : CreativeModeTab.TABS) {
                ItemStack icon = tab.getIconItem();
                ResourceLocation id = icon.isEmpty() ? null : Registry.ITEM.getKey(icon.getItem());
                if (ServerChecks.ours(id) && tabs.stream().noneMatch(t -> sameNamespace(t, id))) tabs.add(tab);
            }
            JsonArray names = new JsonArray();
            tabs.forEach(t -> names.add(t.getDisplayName().getString()));
            report.add("creativeTabs", names);
            next(Stage.CREATIVE);
        }
    }

    private static boolean sameNamespace(CreativeModeTab tab, ResourceLocation id) {
        return Registry.ITEM.getKey(tab.getIconItem().getItem()).getNamespace().equals(id.getNamespace());
    }

    private void creative(Minecraft mc) {
        if (step >= tabs.size()) {
            pickEmiStacks(mc);
            next(Stage.EMI);
            return;
        }
        if (!(mc.screen instanceof CreativeModeInventoryScreen)) {
            if (mc.gameMode != null && mc.gameMode.hasInfiniteItems()) {
                mc.setScreen(new CreativeModeInventoryScreen(mc.player));
                stageTicks = 0;
            }
            return;
        }
        CreativeModeTab tab = tabs.get(step);
        if (stageTicks == 5) {
            ((CreativeModeInventoryScreenAccessor) mc.screen).tekxitprobe$selectTab(tab);
        } else if (stageTicks == 20) {
            shot(mc, "05_creative_" + Registry.ITEM.getKey(tab.getIconItem().getItem()).getNamespace());
            step++;
            stageTicks = 0;
        }
    }

    private void pickEmiStacks(Minecraft mc) {
        Map<String, ItemStack> byNamespace = new LinkedHashMap<>();
        for (String ns : ServerChecks.NAMESPACES) byNamespace.put(ns, ItemStack.EMPTY);
        List<Recipe<?>> recipes = new ArrayList<>(mc.level.getRecipeManager().getRecipes());
        recipes.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (Recipe<?> recipe : recipes) {
            if (recipe.getType() != RecipeType.CRAFTING) continue;
            ItemStack out = recipe.getResultItem();
            ResourceLocation id = out.isEmpty() ? null : Registry.ITEM.getKey(out.getItem());
            if (ServerChecks.ours(id) && byNamespace.get(id.getNamespace()).isEmpty()) byNamespace.put(id.getNamespace(), out.copy());
        }
        byNamespace.values().stream().filter(s -> !s.isEmpty()).forEach(emiStacks::add);
    }

    private void emi(Minecraft mc) {
        if (!FabricLoader.getInstance().isModLoaded("emi")) {
            next(Stage.TESTS);
            return;
        }
        try {
            if (step == 0) {
                boolean loaded = (Boolean) Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded").invoke(null);
                if (!loaded) {
                    if (stageTicks > EMI_TIMEOUT_TICKS) {
                        failures.add("EMI did not finish reloading within 10 minutes");
                        next(Stage.TESTS);
                    }
                    return;
                }
                report.addProperty("emiLoadedMs", System.currentTimeMillis() - jvmStart);
                step = 1;
                stageTicks = 0;
                return;
            }
            int index = step - 1;
            if (index >= emiStacks.size()) {
                mc.setScreen(null);
                next(Stage.TESTS);
                return;
            }
            ItemStack stack = emiStacks.get(index);
            if (stageTicks == 1) {
                if (!(mc.screen instanceof CreativeModeInventoryScreen)) mc.setScreen(new CreativeModeInventoryScreen(mc.player));
            } else if (stageTicks == 5) {
                Class<?> emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack");
                Object ingredient = emiStack.getMethod("of", ItemStack.class).invoke(null, stack);
                Method display = Class.forName("dev.emi.emi.api.EmiApi")
                        .getMethod("displayRecipes", Class.forName("dev.emi.emi.api.stack.EmiIngredient"));
                display.invoke(null, ingredient);
            } else if (stageTicks == 25) {
                String id = Registry.ITEM.getKey(stack.getItem()).toString();
                boolean opened = mc.screen != null && mc.screen.getClass().getName().startsWith("dev.emi.");
                if (!opened) failures.add("EMI shows no recipe page for " + id + " (screen " + lastScreen + ")");
                shot(mc, "06_emi_" + id.replace(':', '_'));
                step++;
                stageTicks = 0;
            }
        } catch (ReflectiveOperationException e) {
            failures.add("EMI API call failed: " + e);
            next(Stage.TESTS);
        }
    }

    private void tests(Minecraft mc) {
        if (stageTicks == 1) {
            mc.setScreen(null);
            List<String> classes = new ArrayList<>();
            if (FabricLoader.getInstance().isModLoaded("fabric-gametest-api-v1")) {
                classes.add("TekxitProbeGameTests");
                if (FabricLoader.getInstance().isModLoaded("icbm_port_tekxittest")) classes.add("TekxitCompatibilityGameTests");
            }
            JsonArray ran = new JsonArray();
            classes.forEach(ran::add);
            report.add("gameTestClasses", ran);
            if (classes.isEmpty()) {
                next(Stage.QUIT);
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                int offset = 0;
                for (String test : classes) {
                    // Run each class away from the showcase so their structures do not overlap.
                    var source = player.createCommandSourceStack().withPermission(4)
                            .withPosition(player.position().add(24 + offset, 0, 24));
                    server.getCommands().performPrefixedCommand(source, "test runall " + test);
                    offset += 32;
                }
            });
        } else if (stageTicks > TEST_TICKS) {
            shot(mc, "07_gametests");
            next(Stage.QUIT);
        }
    }

    private void quit(Minecraft mc) {
        if (step == 0) {
            mc.setScreen(null);
            report.add("integratedServer", TekxitProbe.lastServerReport());
            if (mc.level != null) {
                boolean local = mc.isLocalServer();
                mc.level.disconnect();
                if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                else mc.clearLevel();
            }
            mc.setScreen(new TitleScreen());
            report.addProperty("savedAndQuitMs", System.currentTimeMillis() - jvmStart);
            step = 1;
            stageTicks = 0;
        } else if (stageTicks > 40) {
            report.addProperty("result", failures.isEmpty() ? "PASSED" : "FAILED");
            stage = Stage.DONE;
            writeReport();
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] client result {} with {} failure(s)", failures.isEmpty() ? "PASSED" : "FAILED", failures.size());
            failures.forEach(f -> TekxitProbe.LOGGER.error("[TEKXIT_PROBE] FAIL {}", f));
            warnings.forEach(w -> TekxitProbe.LOGGER.warn("[TEKXIT_PROBE] WARN {}", w));
            if (!Boolean.getBoolean("tekxitprobe.keepOpen")) mc.stop();
        }
    }

    /** One column per port namespace, up to five distinct blocks each, on a floating deck in front of the player. */
    private JsonObject buildShowcase(MinecraftServer server) {
        JsonObject out = new JsonObject();
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        BlockPos spawn = player.blockPosition();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        BlockPos o = new BlockPos(spawn.getX(), Math.min(Math.max(ground + 12, 100), level.getMaxBuildHeight() - 20), spawn.getZ());
        int width = ServerChecks.NAMESPACES.size();
        for (int x = -width; x <= width; x++) {
            for (int z = -14; z <= 3; z++) {
                level.setBlock(o.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 12; y++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        JsonObject placed = new JsonObject();
        List<String> errors = new ArrayList<>();
        int column = 0;
        for (String ns : ServerChecks.NAMESPACES) {
            List<Block> candidates = new ArrayList<>();
            for (Block block : Registry.BLOCK) {
                ResourceLocation id = Registry.BLOCK.getKey(block);
                if (!id.getNamespace().equals(ns) || block.asItem() == Items.AIR || block instanceof LiquidBlock) continue;
                if (block.defaultBlockState().getRenderShape() == RenderShape.INVISIBLE) continue;
                candidates.add(block);
            }
            JsonArray names = new JsonArray();
            int x = -width + 1 + column * 2;
            for (int row = 0; row < 5 && !candidates.isEmpty(); row++) {
                Block block = candidates.get(row * candidates.size() / 5 % candidates.size());
                BlockPos pos = o.offset(x, 1 + row * 2, -10);
                try {
                    BlockState state = block.defaultBlockState();
                    level.setBlock(pos, state, 3);
                    names.add(Registry.BLOCK.getKey(block).toString());
                } catch (RuntimeException e) {
                    errors.add(Registry.BLOCK.getKey(block) + ": " + e);
                    TekxitProbe.LOGGER.error("[TEKXIT_PROBE] placing {} threw", Registry.BLOCK.getKey(block), e);
                }
            }
            placed.add(ns, names);
            column++;
        }
        player.teleportTo(level, o.getX() + 0.5, o.getY() + 1, o.getZ() + 2.5, 180.0f, -8.0f);
        out.add("placed", placed);
        out.add("errors", ServerChecks.strings(errors));
        if (!errors.isEmpty()) failures.add("placing port blocks threw: " + errors);
        return out;
    }

    private void shot(Minecraft mc, String name) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(TekxitProbe.outDir());
            image.writeToFile(TekxitProbe.outDir().resolve(name + ".png"));
            shots.add(name + ".png");
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] screenshot {}", name);
        } catch (IOException | RuntimeException e) {
            warnings.add("screenshot " + name + " failed: " + e);
        }
    }

    private void writeReport() {
        report.addProperty("stage", stage.name());
        report.add("screens", screens);
        report.add("screenshots", shots);
        report.add("failures", ServerChecks.strings(failures));
        report.add("warnings", ServerChecks.strings(warnings));
        TekxitProbe.write("client-report.json", TekxitProbe.GSON.toJson(report));
    }
}
