package net.krona.climora.climate;

/**
 * Rolling performance numbers of a simulation over the last {@link #WINDOW_TICKS} ticks.
 */
public final class SimulationStats {
    public static final int WINDOW_TICKS = 200;

    private final long[] tickNanos = new long[WINDOW_TICKS];
    private final int[] cellUpdates = new int[WINDOW_TICKS];
    private int index;
    private int samples;

    private long cellsCreatedTotal;
    private long deferredUpdatesTotal;
    private long overBudgetTicksTotal;
    private long lightningStrikesTotal;

    // How much of the simulated time each kind of weather took, weighted by the ticks each update covered.
    private long weatherTicksTotal;
    private long rainTicksTotal;
    private long stormTicksTotal;
    private long hailTicksTotal;
    private long freezingRainTicksTotal;

    void recordTick(long nanos, int created, int updated, boolean overBudget) {
        tickNanos[index] = nanos;
        cellUpdates[index] = updated;
        index = (index + 1) % WINDOW_TICKS;
        samples = Math.min(samples + 1, WINDOW_TICKS);

        cellsCreatedTotal += created;
        if (overBudget) {
            overBudgetTicksTotal++;
        }
    }

    void recordDeferred(int count) {
        deferredUpdatesTotal += count;
    }

    public void recordLightningStrike() {
        lightningStrikesTotal++;
    }

    /** One cell update covering {@code ticks} of game time with the given weather. */
    void recordWeather(long ticks, boolean rain, boolean storm, boolean hail, boolean freezingRain) {
        if (ticks <= 0) {
            return;
        }
        weatherTicksTotal += ticks;
        if (rain) {
            rainTicksTotal += ticks;
        }
        if (storm) {
            stormTicksTotal += ticks;
        }
        if (hail) {
            hailTicksTotal += ticks;
        }
        if (freezingRain) {
            freezingRainTicksTotal += ticks;
        }
    }

    /** Share of simulated cell time with precipitation since the server started, 0..1, or NaN without data. */
    public double rainShare() {
        return share(rainTicksTotal);
    }

    public double stormShare() {
        return share(stormTicksTotal);
    }

    public double hailShare() {
        return share(hailTicksTotal);
    }

    public double freezingRainShare() {
        return share(freezingRainTicksTotal);
    }

    private double share(long ticks) {
        return weatherTicksTotal == 0 ? Double.NaN : ticks / (double) weatherTicksTotal;
    }

    public long lightningStrikesTotal() {
        return lightningStrikesTotal;
    }

    public double averageTickMillis() {
        if (samples == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += tickNanos[i];
        }
        return sum / (double) samples / 1_000_000.0;
    }

    public double maxTickMillis() {
        long max = 0;
        for (int i = 0; i < samples; i++) {
            max = Math.max(max, tickNanos[i]);
        }
        return max / 1_000_000.0;
    }

    public double updatesPerSecond() {
        if (samples == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += cellUpdates[i];
        }
        return sum / (samples / 20.0);
    }

    public long cellsCreatedTotal() {
        return cellsCreatedTotal;
    }

    public long deferredUpdatesTotal() {
        return deferredUpdatesTotal;
    }

    public long overBudgetTicksTotal() {
        return overBudgetTicksTotal;
    }
}
