package net.krona.climora.climate;

/**
 * Climate of one biome.
 *
 * @param meanTemperature   average air temperature at {@link #referenceElevation}, °C
 * @param diurnalAmplitude  half of the day/night temperature swing, °C
 * @param humidity          how humid the air over this biome tends to be, 0..1
 * @param referenceElevation height at which {@code meanTemperature} applies. Mountain biomes are cold partly
 *                          because they are high, so their temperature is given at a mountain height and the
 *                          lapse rate is applied from there, not from sea level
 */
public record ClimateProfile(float meanTemperature, float diurnalAmplitude, float humidity, int referenceElevation) {
    /** Sea level of the Overworld; the reference height of ordinary biomes. */
    public static final int SEA_LEVEL = 63;

    public ClimateProfile(float meanTemperature, float diurnalAmplitude, float humidity) {
        this(meanTemperature, diurnalAmplitude, humidity, SEA_LEVEL);
    }

    /** Mean temperature at a given height, °C. */
    public float meanTemperatureAt(int elevation) {
        return meanTemperature + ClimateModel.altitudeOffset(referenceElevation, elevation);
    }
}
