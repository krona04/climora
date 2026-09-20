package net.krona.climora.climate;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.krona.climora.Climora;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns one {@link ClimateSimulation} per simulated dimension for the lifetime of the server.
 */
public final class ClimateManager {
    private static final Map<ResourceKey<Level>, ClimateSimulation> SIMULATIONS = new HashMap<>();

    private ClimateManager() {
    }

    public static void init() {
        LifecycleEvent.SERVER_LEVEL_LOAD.register(ClimateManager::onLevelLoad);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(level -> SIMULATIONS.remove(level.dimension()));
        LifecycleEvent.SERVER_STOPPED.register(server -> SIMULATIONS.clear());
        TickEvent.SERVER_LEVEL_POST.register(ClimateManager::onLevelTick);
    }

    @Nullable
    public static ClimateSimulation get(ServerLevel level) {
        return SIMULATIONS.get(level.dimension());
    }

    /** Only dimensions with an open sky have weather: the Overworld and similar modded dimensions. */
    public static boolean hasClimate(ServerLevel level) {
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasCeiling();
    }

    private static void onLevelLoad(ServerLevel level) {
        if (!hasClimate(level)) {
            return;
        }
        ClimateGrid grid = level.getDataStorage().computeIfAbsent(ClimateGrid.factory(), ClimateGrid.DATA_NAME);
        migrateLegacyData(level, grid);
        if (grid.isReadOnly()) {
            Climora.LOGGER.error("Climate simulation for {} is disabled: saved data is from a newer version.",
                    level.dimension().location());
            return;
        }
        SIMULATIONS.put(level.dimension(), new ClimateSimulation(level, grid));
        Climora.LOGGER.info("Climate simulation started for {} ({} saved cells).",
                level.dimension().location(), grid.size());
    }

    /** Worlds from before the rename keep their climate under the old file name; move it over once. */
    private static void migrateLegacyData(ServerLevel level, ClimateGrid grid) {
        if (grid.size() > 0 || grid.isReadOnly()) {
            return;
        }
        // Reading a missing file just gives an empty grid, and an empty grid is never written back.
        ClimateGrid legacy = level.getDataStorage().computeIfAbsent(ClimateGrid.factory(), ClimateGrid.LEGACY_DATA_NAME);
        if (legacy.size() > 0) {
            grid.takeOver(legacy);
            Climora.LOGGER.info("Moved {} climate cells of {} from the old Tempestra save file.",
                    legacy.size(), level.dimension().location());
        }
    }

    private static void onLevelTick(ServerLevel level) {
        ClimateSimulation simulation = SIMULATIONS.get(level.dimension());
        if (simulation != null) {
            simulation.tick();
        }
    }
}
