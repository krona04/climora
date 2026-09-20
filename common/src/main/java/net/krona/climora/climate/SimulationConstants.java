package net.krona.climora.climate;

/**
 * Fixed values of the climate simulation. Tunable options live in {@code ClimoraConfig}.
 */
public final class SimulationConstants {
    /** Cell edge length in chunks. Changing it invalidates saved grids. */
    public static final int CELL_SIZE_CHUNKS = 8;
    public static final int CELL_SIZE_BLOCKS = CELL_SIZE_CHUNKS * 16;

    public static final int ACTIVE_REFRESH_INTERVAL_TICKS = 20;

    /** Every active cell is updated at least once per this many ticks. */
    public static final int FULL_PASS_TICKS = 40;

    /** Time for a cell to close ~63% of the gap to its target temperature (2 in-game hours). */
    public static final float THERMAL_TIME_CONSTANT_TICKS = 2400.0F;
    /** Share of the target temperature taken from the mean of neighbouring cells. */
    public static final float NEIGHBOUR_COUPLING = 0.3F;

    private SimulationConstants() {
    }
}
