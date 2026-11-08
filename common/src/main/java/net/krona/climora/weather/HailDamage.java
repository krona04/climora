package net.krona.climora.weather;

import net.krona.climora.climate.ClimateSimulation;
import net.krona.climora.config.ClimoraConfig;
import net.krona.climora.registry.ClimoraRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Hailstones hurt players and mobs caught in the open. Off by default ({@code hailDamage}): it changes
 * survival a lot. Armour protects as from any hit; a roof protects completely.
 */
public final class HailDamage {
    private static final int INTERVAL_TICKS = 20;
    /** Light hail only rattles; this much is needed to hurt. */
    private static final float MIN_HAIL = 0.25F;
    /** Chance per second to be hit hard enough to hurt, at full hail. */
    private static final float HIT_CHANCE_AT_FULL_HAIL = 0.5F;
    private static final float DAMAGE = 1.0F;
    /** Only mobs this close to a player are checked: hail elsewhere hurts no one who would notice. */
    private static final double RANGE = 48.0;

    private HailDamage() {
    }

    public static void tick(ClimateSimulation simulation) {
        ClimoraConfig.Server config = ClimoraConfig.server();
        ServerLevel level = simulation.level();
        if (!config.hailDamage || !config.hail || !config.localWeather || level.getGameTime() % INTERVAL_TICKS != 0) {
            return;
        }
        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ClimoraRegistries.HAIL_DAMAGE));
        Set<LivingEntity> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ServerPlayer player : level.players()) {
            if (simulation.hail(player.getX(), player.getZ()) < MIN_HAIL
                    && simulation.hail(player.getX() + RANGE, player.getZ()) < MIN_HAIL
                    && simulation.hail(player.getX() - RANGE, player.getZ()) < MIN_HAIL
                    && simulation.hail(player.getX(), player.getZ() + RANGE) < MIN_HAIL
                    && simulation.hail(player.getX(), player.getZ() - RANGE) < MIN_HAIL) {
                continue;
            }
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                    LivingEntity::isAlive)) {
                if (!checked.add(entity)) {
                    continue;
                }
                float hail = simulation.hail(entity.getX(), entity.getZ());
                if (hail < MIN_HAIL || level.random.nextFloat() >= hail * HIT_CHANCE_AT_FULL_HAIL) {
                    continue;
                }
                if (level.canSeeSky(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()))) {
                    entity.hurt(source, DAMAGE);
                }
            }
        }
    }
}
