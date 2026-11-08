package net.krona.climora.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code climora:latitude_belts}: a climate parameter of the "Climate belts" world type.
 * Its value is the belt at this latitude ({@link Latitude}) plus a share of the original vanilla noise, so
 * the belts keep ragged, natural borders and their own patches of other biomes.
 * <p>
 * Vanilla has no density function that knows the z coordinate, so the belts cannot be made by a data pack
 * alone. Used only by {@code data/climora/worldgen/noise_settings/climate_belts.json}; the default world
 * type is untouched.
 *
 * @param input        the vanilla temperature or vegetation noise
 * @param parameter    which belt profile to use
 * @param noiseWeight  how much of the vanilla noise is kept
 * @param halfWidth    blocks from the equator to a pole
 */
public record LatitudeBelts(DensityFunction input, Parameter parameter, double noiseWeight, double halfWidth)
        implements DensityFunction {
    public static final MapCodec<LatitudeBelts> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(LatitudeBelts::input),
            Parameter.CODEC.fieldOf("parameter").forGetter(LatitudeBelts::parameter),
            Codec.doubleRange(0.0, 2.0).fieldOf("noise_weight").forGetter(LatitudeBelts::noiseWeight),
            Codec.doubleRange(500.0, 1.0E7).fieldOf("half_width").forGetter(LatitudeBelts::halfWidth)
    ).apply(instance, LatitudeBelts::new));
    public static final KeyDispatchDataCodec<LatitudeBelts> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public enum Parameter implements StringRepresentable {
        TEMPERATURE("temperature"),
        HUMIDITY("humidity");

        public static final Codec<Parameter> CODEC = StringRepresentable.fromEnum(Parameter::values);
        private final String name;

        Parameter(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public double compute(FunctionContext context) {
        float latitude = Latitude.of(context.blockZ(), halfWidth);
        double belt = parameter == Parameter.TEMPERATURE ? Latitude.temperature(latitude) : Latitude.humidity(latitude);
        return belt + noiseWeight * input.compute(context);
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
        provider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new LatitudeBelts(input.mapAll(visitor), parameter, noiseWeight, halfWidth));
    }

    @Override
    public double minValue() {
        double belt = parameter == Parameter.TEMPERATURE ? Latitude.minTemperature() : Latitude.minHumidity();
        return belt + noiseWeight * Math.min(input.minValue(), -input.maxValue());
    }

    @Override
    public double maxValue() {
        double belt = parameter == Parameter.TEMPERATURE ? Latitude.maxTemperature() : Latitude.maxHumidity();
        return belt + noiseWeight * Math.max(input.maxValue(), -input.minValue());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
