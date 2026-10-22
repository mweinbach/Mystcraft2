/*
 * Mekanism 1.19.2 port - development-only Fabric client for the ComputerCraft screenshot.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.client;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ClientComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.network.container.ComputerContainerData;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.machine.TileEntityElectricPump;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Opens one creative world, boots a computer whose startup program wraps the electric pump behind it, and writes the
 * frame once that program has printed the pump's energy. F3, chat and tutorial toasts stay off. Registered only on
 * the ComputerCraft showcase client. It is not in the production JAR.
 */
public final class FabricComputerCraftShowcase implements ClientModInitializer {

    private static final Path SHOT = Path.of("/cursor/stores/bc-50246035-7515-4ce7-9afe-6c26b6fee95c/media/mekanism/i4-computercraft.png");
    private static final BlockPos COMPUTER = new BlockPos(0, 81, 1);
    private static final BlockPos PUMP = new BlockPos(0, 81, 2);
    private static final String SCRIPT = """
          local wrapped
          for _ = 1, 40 do
            wrapped = peripheral.wrap("back")
            if wrapped ~= nil then
              break
            end
            os.sleep(0.25)
          end
          if wrapped == nil then
            print("wrap missed")
          else
            print("wrap " .. peripheral.getType("back"))
            print("energy " .. tostring(wrapped.getEnergy()))
          end
          """;

    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicBoolean clientHasText = new AtomicBoolean();
    private final AtomicInteger instanceId = new AtomicInteger(-1);
    private final AtomicReference<String> terminal = new AtomicReference<>("");
    private final AtomicReference<String> clientTerminal = new AtomicReference<>("");
    private final AtomicReference<String> failure = new AtomicReference<>();
    private boolean started;
    private boolean placed;
    private int waiting;
    private int shown;
    private int resend;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("mekanism.computercraft.screenshot")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepare(minecraft);
                RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access);
                LevelSettings level = new LevelSettings("i4", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("i4", level, access, settings);
            }
            return;
        }
        if (minecraft.getSingleplayerServer() == null) {
            return;
        }
        String failed = failure.get();
        if (failed != null) {
            throw new IllegalStateException(failed);
        }
        if (!placed) {
            // The progress screen is still up while the world is loading. Place once the game is in.
            if (minecraft.screen != null) {
                return;
            }
            placed = true;
            minecraft.getSingleplayerServer().execute(() -> layOut(minecraft.getSingleplayerServer().overworld()));
            return;
        }
        minecraft.getSingleplayerServer().execute(() -> poll(minecraft.getSingleplayerServer().overworld()));
        // openMenu alone never sends the terminal. The client screen stays a black empty terminal until
        // ComputerTerminalClientMessage arrives, which a normal right-click sends before opening the menu.
        if (!clientHasText.get()) {
            rememberClientTerminal();
        }
        if (!opened.get() || minecraft.screen == null || !clientHasText.get()) {
            waiting++;
            if (waiting > 1200) {
                throw new IllegalStateException("Computer never showed the pump energy. Server terminal: " + terminal.get()
                      + " Client terminal: " + clientTerminal.get());
            }
            return;
        }
        shown++;
        // The screenshot reads the framebuffer from the previous render. Wait until that render included the text.
        if (shown < 40) {
            return;
        }
        try {
            Files.createDirectories(SHOT.getParent());
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget()).writeToFile(SHOT.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + SHOT, e);
        }
        minecraft.stop();
    }

    private void poll(ServerLevel level) {
        if (!(level.getBlockEntity(COMPUTER) instanceof TileComputerBase tile)) {
            return;
        }
        ServerComputer computer = tile.getServerComputer();
        if (computer == null || !computer.isOn()) {
            return;
        }
        String text = read(computer.getTerminal());
        terminal.set(text);
        if (text.contains("wrap missed")) {
            failure.compareAndSet(null, "peripheral.wrap missed the pump. Terminal:\n" + text);
            return;
        }
        if (!text.contains("energy")) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        if (!opened.get()) {
            player.teleportTo(level, 0.5D, 81.0D, 0.2D, 0.0F, 0.0F);
            // Same order as TileComputerBase.onActivate: the full terminal, then the screen.
            computer.sendTerminalState(player);
            new ComputerContainerData(computer).open(player, tile);
            instanceId.set(computer.getInstanceID());
            opened.set(true);
            return;
        }
        if (!clientHasText.get() && ++resend % 20 == 0) {
            computer.sendTerminalState(player);
        }
    }

    private void rememberClientTerminal() {
        int id = instanceId.get();
        if (id < 0) {
            return;
        }
        ClientComputer computer = ComputerCraft.clientComputerRegistry.get(id);
        if (computer == null) {
            return;
        }
        Terminal shownTerminal = computer.getTerminal();
        if (shownTerminal == null) {
            return;
        }
        String text = read(shownTerminal);
        clientTerminal.set(text);
        if (text.contains("energy")) {
            clientHasText.set(true);
        }
    }

    private static String read(Terminal terminal) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < terminal.getHeight(); line++) {
            text.append(terminal.getLine(line)).append('\n');
        }
        return text.toString();
    }

    private static void prepare(Minecraft minecraft) {
        minecraft.options.hideGui = false;
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.gamma().set(1.0D);
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.bobView().set(false);
        minecraft.options.save();
    }

    private static void layOut(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 5; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 81; y <= 86; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState pumpState = MekanismBlocks.ELECTRIC_PUMP.getBlock().defaultBlockState();
        level.setBlockAndUpdate(PUMP, pumpState);
        MekanismBlocks.ELECTRIC_PUMP.getBlock().setPlacedBy(level, PUMP, pumpState, null, ItemStack.EMPTY);
        TileEntityElectricPump pump = (TileEntityElectricPump) level.getBlockEntity(PUMP);
        ((IStrictEnergyHandler) pump).insertEnergy(FloatingLong.createConst(40_000), Action.EXECUTE);

        Block computerBlock = Registry.BLOCK.get(new ResourceLocation("computercraft", "computer_advanced"));
        if (computerBlock == null || computerBlock == Blocks.AIR) {
            throw new IllegalStateException("CC: Restitched did not register computercraft:computer_advanced");
        }
        BlockState computerState = facing(computerBlock.defaultBlockState(), Direction.NORTH);
        level.setBlockAndUpdate(COMPUTER, computerState);
        if (!(level.getBlockEntity(COMPUTER) instanceof TileComputerBase tile)) {
            throw new IllegalStateException("Advanced computer block entity is " + level.getBlockEntity(COMPUTER));
        }
        // Placement does not create the server computer. The id has to exist before startup.lua is written,
        // because the computer mounts computer/<id> when it turns on.
        ServerComputer computer = tile.createServerComputer();
        int id = computer.getID();
        if (id < 0) {
            id = computer.assignNewID();
            computer.setID(id);
            tile.setComputerID(id);
        }
        try {
            var mount = ComputerCraftAPI.createSaveDirMount(level, "computer/" + id, computer.getComputerSpaceLimit());
            ByteBuffer script = StandardCharsets.UTF_8.encode(SCRIPT);
            try (WritableByteChannel channel = mount.openForWrite("startup.lua")) {
                while (script.hasRemaining()) {
                    channel.write(script);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write startup.lua", e);
        }
        computer.turnOn();
        level.setDayTime(6000);
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.teleportTo(level, 0.5D, 81.0D, 0.2D, 0.0F, 10.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static BlockState facing(BlockState state, Direction direction) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty facing && property.getName().equals("facing") && facing.getPossibleValues().contains(direction)) {
                return state.setValue(facing, direction);
            }
        }
        return state;
    }
}
