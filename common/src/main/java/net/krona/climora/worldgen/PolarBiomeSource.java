package net.krona.climora.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Biome source {@code climora:polar}: another biome source, but where the climate is colder than
 * {@code freeze_below} every biome that would not freeze is swapped for its frozen counterpart.
 * <p>
 * Vanilla keeps a few temperature-blind biomes even in its coldest band (windswept hills, stony shores,
 * mushroom islands), so without this the land past the poles of the "Climate belts" world would still have
 * green patches. The border follows the climate temperature, noise and all, so it is as ragged as any
 * other biome border instead of a straight line across the map.
 */
public final class PolarBiomeSource extends BiomeSource {
    public static final MapCodec<PolarBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(source -> source.biomeSource),
            Codec.floatRange(-2.0F, 2.0F).fieldOf("freeze_below").forGetter(source -> source.freezeBelow),
            Codec.unboundedMap(ResourceKey.codec(Registries.BIOME), Biome.CODEC).fieldOf("replacements")
                    .forGetter(source -> source.replacements)
    ).apply(instance, PolarBiomeSource::new));

    private final BiomeSource biomeSource;
    private final float freezeBelow;
    private final long quantizedFreezeBelow;
    /** Biome to its frozen counterpart; biomes not listed (caves, already frozen ones) stay. */
    private final Map<ResourceKey<Biome>, Holder<Biome>> replacements;

    public PolarBiomeSource(BiomeSource biomeSource, float freezeBelow, Map<ResourceKey<Biome>, Holder<Biome>> replacements) {
        this.biomeSource = biomeSource;
        this.freezeBelow = freezeBelow;
        this.quantizedFreezeBelow = Climate.quantizeCoord(freezeBelow);
        this.replacements = Map.copyOf(replacements);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(biomeSource.possibleBiomes().stream(), replacements.values().stream()).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Climate.TargetPoint target = sampler.sample(x, y, z);
        // The multi-noise source reads the same point, so it is sampled once for both.
        Holder<Biome> biome = biomeSource instanceof MultiNoiseBiomeSource multiNoise
                ? multiNoise.getNoiseBiome(target)
                : biomeSource.getNoiseBiome(x, y, z, sampler);
        return freezes(target.temperature()) ? frozen(biome) : biome;
    }

    boolean freezes(long temperature) {
        return temperature < quantizedFreezeBelow;
    }

    Holder<Biome> frozen(Holder<Biome> biome) {
        Holder<Biome> replacement = biome.unwrapKey().map(replacements::get).orElse(null);
        return replacement != null ? replacement : biome;
    }

    @Override
    public void addDebugInfo(List<String> lines, BlockPos pos, Climate.Sampler sampler) {
        biomeSource.addDebugInfo(lines, pos, sampler);
    }
}
