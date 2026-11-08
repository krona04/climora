package net.krona.climora.mixin.vanilla;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.krona.climora.registry.ClimoraRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Glaze ice is slippery. Vanilla takes friction from the block half a block under the feet, which for a
 * pixel-thin crust is the ground under it, so the crust itself would never count. Here the crust is what
 * the entity walks on. This hook is the same on both loaders; NeoForge rewrites the friction code itself.
 * Items slide on glaze too.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @ModifyReturnValue(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("RETURN"))
    private BlockPos climora$standOnGlaze(BlockPos below) {
        BlockPos crust = below.above();
        return ((Entity) (Object) this).level().getBlockState(crust).is(ClimoraRegistries.GLAZE.get()) ? crust : below;
    }
}
