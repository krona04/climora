package net.krona.climora.climate;

/**
 * Large-scale weather systems: areas of high and low pressure a couple of kilometres wide that
 * drift with the prevailing wind and slowly change shape.
 * <p>
 * The field is a pure function of position, game time and world seed. It needs no saved state,
 * covers infinite worlds and is the same for inactive and active cells.
 */
public final class SynopticField {
    public static final double SEA_LEVEL_PRESSURE = 1013.0;
    /** Pressure anomaly of the strongest systems, hPa. */
    public static final double AMPLITUDE = 22.0;
    /** Size of a weather system, blocks. */
    public static final double SCALE_BLOCKS = 2048.0;
    /** Time for a system to change its shape noticeably, ticks (2 in-game days). */
    public static final double EVOLUTION_TICKS = 48000.0;
    /** Speed of the prevailing drift, blocks per tick (~1.6 blocks/s: a system passes in about a day). */
    public static final double DRIFT_SPEED = 0.08;
    /**
     * Sideways wobble of the drift, blocks, and its period in ticks (3 in-game days).
     * Its peak speed (~0.05 blocks/tick) stays below {@link #DRIFT_SPEED}, so systems never move backwards.
     */
    public static final double WOBBLE_BLOCKS = 600.0;
    public static final double WOBBLE_PERIOD_TICKS = 72000.0;

    private final GradientNoise noise;
    /** Unit vector of the prevailing drift, chosen per world. */
    private final double driftX;
    private final double driftZ;

    public SynopticField(long seed) {
        this.noise = new GradientNoise(seed ^ 0x7E3A5E57A11L);
        double angle = new java.util.Random(seed ^ 0x51D0L).nextDouble() * Math.PI * 2.0;
        this.driftX = Math.cos(angle);
        this.driftZ = Math.sin(angle);
    }

    /** How far the weather systems have moved since game time 0, blocks. X component. */
    public double driftOffsetX(long gameTime) {
        return driftX * DRIFT_SPEED * gameTime - driftZ * wobble(gameTime);
    }

    /** How far the weather systems have moved since game time 0, blocks. Z component. */
    public double driftOffsetZ(long gameTime) {
        return driftZ * DRIFT_SPEED * gameTime + driftX * wobble(gameTime);
    }

    private static double wobble(long gameTime) {
        return WOBBLE_BLOCKS * Math.sin(gameTime * 2.0 * Math.PI / WOBBLE_PERIOD_TICKS);
    }

    /** Sea-level pressure of the weather systems at a position, hPa. */
    public double pressure(double x, double z, long gameTime) {
        // Move the pattern with the drift.
        double px = x - driftOffsetX(gameTime);
        double pz = z - driftOffsetZ(gameTime);
        double time = gameTime / EVOLUTION_TICKS;

        double value = noise.sample(px / SCALE_BLOCKS, pz / SCALE_BLOCKS, time)
                + 0.5 * noise.sample(px / (SCALE_BLOCKS * 0.5), pz / (SCALE_BLOCKS * 0.5), time * 1.7 + 31.0);
        // Two octaves of gradient noise rarely leave [-1.1, 1.1].
        return SEA_LEVEL_PRESSURE + AMPLITUDE * clamp(value / 1.1, -1.0, 1.0);
    }

    /** Velocity at which the pattern moves, blocks per tick. X component. */
    public double driftVelocityX(long gameTime) {
        return driftX * DRIFT_SPEED - driftZ * wobbleVelocity(gameTime);
    }

    /** Velocity at which the pattern moves, blocks per tick. Z component. */
    public double driftVelocityZ(long gameTime) {
        return driftZ * DRIFT_SPEED + driftX * wobbleVelocity(gameTime);
    }

    private static double wobbleVelocity(long gameTime) {
        double omega = 2.0 * Math.PI / WOBBLE_PERIOD_TICKS;
        return WOBBLE_BLOCKS * omega * Math.cos(gameTime * omega);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
