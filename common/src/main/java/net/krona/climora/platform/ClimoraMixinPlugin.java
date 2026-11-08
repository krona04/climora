package net.krona.climora.platform;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips the mixins into other mods ({@code mixin.compat}) when that mod is not installed. They are
 * {@code @Pseudo} and would be skipped anyway, but only after Mixin tried to load the missing class and logged a
 * warning that looks like an error to anyone reading the log. Asked before mods are loaded, so it looks for the
 * class file rather than the mod.
 */
public final class ClimoraMixinPlugin implements IMixinConfigPlugin {
    private static final String COMPAT_PACKAGE = ".mixin.compat.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(COMPAT_PACKAGE)) {
            return true;
        }
        return ClimoraMixinPlugin.class.getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
