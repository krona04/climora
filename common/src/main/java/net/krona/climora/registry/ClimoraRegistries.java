package net.krona.climora.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.climora.Climora;
import net.krona.climora.block.WeatherVaneBlock;
import net.krona.climora.block.WeatherVaneBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ClimoraRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Climora.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Climora.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Climora.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<WeatherVaneBlock> WEATHER_VANE = BLOCKS.register("weather_vane",
            () -> new WeatherVaneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistrySupplier<Item> WEATHER_VANE_ITEM = ITEMS.register("weather_vane",
            () -> new BlockItem(WEATHER_VANE.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistrySupplier<BlockEntityType<WeatherVaneBlockEntity>> WEATHER_VANE_ENTITY =
            BLOCK_ENTITY_TYPES.register("weather_vane",
                    () -> BlockEntityType.Builder.of(WeatherVaneBlockEntity::new, WEATHER_VANE.get()).build(null));

    private ClimoraRegistries() {
    }

    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITY_TYPES.register();
        // append() adds the item at the end of the tab. modify() with an output callback crashes on
        // NeoForge when the tab is built, because it inserts relative to an empty stack.
        CreativeTabRegistry.append(CreativeModeTabs.REDSTONE_BLOCKS, WEATHER_VANE_ITEM);
    }
}
