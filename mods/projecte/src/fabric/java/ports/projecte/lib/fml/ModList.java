/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ports.projecte.lib.forgespi.language.ModFileScanData;
import ports.projecte.platform.Platform;

public final class ModList {

    private static final ModList INSTANCE = new ModList();

    private final Map<String, ModContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, ModContainer> ownContainers = new ConcurrentHashMap<>();
    private List<ModFileScanData> scanData;

    private ModList() {
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return Platform.isModLoaded(modId);
    }

    public Optional<? extends ModContainer> getModContainerById(String modId) {
        ModContainer own = ownContainers.get(modId);
        if (own != null) {
            return Optional.of(own);
        }
        return Optional.ofNullable(containers.computeIfAbsent(modId, id -> Platform.backend().modMetadata(id).map(ModContainer::new).orElse(null)));
    }

    /**
     * Registers the container of one of the port's own modules, whose classes are scanned for annotations.
     */
    public ModContainer addOwnContainer(String modId) {
        return ownContainers.computeIfAbsent(modId, id -> new ModContainer(Platform.backend().modMetadata(id)
              .orElseThrow(() -> new IllegalStateException("Mod " + id + " is not loaded"))));
    }

    public synchronized List<ModFileScanData> getAllScanData() {
        if (scanData == null) {
            List<ModFileScanData> data = new ArrayList<>();
            //In development all modules share their roots, so each root is scanned once
            Set<Path> seen = new HashSet<>();
            for (ModContainer container : ownContainers.values()) {
                List<Path> roots = container.getModInfo().getOwningFile().getFile().getRoots().stream().filter(seen::add).toList();
                if (!roots.isEmpty()) {
                    data.add(ModFileScanData.scan(container.getModInfo().getOwningFile(), roots));
                }
            }
            scanData = List.copyOf(data);
        }
        return scanData;
    }
}
