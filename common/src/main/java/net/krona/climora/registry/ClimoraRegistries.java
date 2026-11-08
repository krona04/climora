package net.krona.climora.registry;

import com.mojang.serialization.MapCodec;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.climora.Climora;
import net.krona.climora.block.GlazeBlock;
import net.krona.climora.block.PuddleBlock;
import net.krona.climora.block.WeatherVaneBlock;
import net.krona.climora.block.WeatherVaneBlockEntity;
import net.krona.climora.worldgen.LatitudeBelts;
import net.krona.climora.worldgen.PolarBiomeSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ClimoraRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Climora.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Climora.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Climora.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Climora.MOD_ID, Registries.SOUND_EVENT);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Climora.MOD_ID, Registries.DENSITY_FUNCTION_TYPE);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Climora.MOD_ID, Registries.BIOME_SOURCE);

    public static final RegistrySupplier<WeatherVaneBlock> WEATHER_VANE = BLOCKS.register("weather_vane",
            () -> new WeatherVaneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** Ice crust from freezing rain. Not an item: only the weather makes it. */
    public static final RegistrySupplier<GlazeBlock> GLAZE = BLOCKS.register("glaze",
            () -> new GlazeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.2F)
                    .friction(GlazeBlock.FRICTION)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((state, level, pos, type) -> false)));

    /** Rain water in a hollow. Not an item: only the weather makes it. */
    public static final RegistrySupplier<PuddleBlock> PUDDLE = BLOCKS.register("puddle",
            () -> new PuddleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.EMPTY)
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistrySupplier<Item> WEATHER_VANE_ITEM = ITEMS.register("weather_vane",
            () -> new BlockItem(WEATHER_VANE.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistrySupplier<BlockEntityType<WeatherVaneBlockEntity>> WEATHER_VANE_ENTITY =
            BLOCK_ENTITY_TYPES.register("weather_vane",
                    () -> BlockEntityType.Builder.of(WeatherVaneBlockEntity::new, WEATHER_VANE.get()).build(null));

    public static final RegistrySupplier<SoundEvent> HAIL_SOUND = SOUND_EVENTS.register("weather.hail",
            () -> SoundEvent.createVariableRangeEvent(id("weather.hail")));
    /**
     * Rain and hail for Sound Physics Remastered to echo and muffle behind walls: the same sounds as vanilla rain
     * and {@link #HAIL_SOUND}, under names that mod processes (see {@code SoundPhysicsCompat}).
     */
    public static final RegistrySupplier<SoundEvent> SPATIAL_RAIN_SOUND = SOUND_EVENTS.register("weather.rain.spatial",
            () -> SoundEvent.createVariableRangeEvent(id("weather.rain.spatial")));
    public static final RegistrySupplier<SoundEvent> SPATIAL_HAIL_SOUND = SOUND_EVENTS.register("weather.hail.spatial",
            () -> SoundEvent.createVariableRangeEvent(id("weather.hail.spatial")));

    /** Climate belts by latitude, for the "Climate belts" world type. */
    public static final RegistrySupplier<MapCodec<LatitudeBelts>> LATITUDE_BELTS =
            DENSITY_FUNCTION_TYPES.register("latitude_belts", () -> LatitudeBelts.MAP_CODEC);
    /** Frozen biomes past the poles of the "Climate belts" world type. */
    public static final RegistrySupplier<MapCodec<PolarBiomeSource>> POLAR_BIOME_SOURCE =
            BIOME_SOURCES.register("polar", () -> PolarBiomeSource.CODEC);

    /** Hailstones hitting someone in the open, defined in {@code data/climora/damage_type/hail.json}. */
    public static final ResourceKey<DamageType> HAIL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("hail"));

    private ClimoraRegistries() {
    }

    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITY_TYPES.register();
        SOUND_EVENTS.register();
        DENSITY_FUNCTION_TYPES.register();
        BIOME_SOURCES.register();
        // append() adds the item at the end of the tab. modify() with an output callback crashes on
        // NeoForge when the tab is built, because it inserts relative to an empty stack.
        CreativeTabRegistry.append(CreativeModeTabs.REDSTONE_BLOCKS, WEATHER_VANE_ITEM);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Climora.MOD_ID, path);
    }
}
