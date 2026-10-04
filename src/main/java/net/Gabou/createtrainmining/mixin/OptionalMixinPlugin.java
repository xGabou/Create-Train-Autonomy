package net.Gabou.createtrainmining.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Register the addon hook only when its bytecode is present; no optional linkage in the core. */
public final class OptionalMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String packageName) {}

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    public void acceptTargets(Set<String> mine, Set<String> others) {}

    public List<String> getMixins() {
        try {
            MixinService.getService()
                    .getBytecodeProvider()
                    .getClassNode(
                            "com.vodmordia.railwaysuntold_additions.contraption.CruiseControlManager");
            return List.of("RailwaysCruiseMixin");
        } catch (Exception e) {
            return List.of();
        }
    }

    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
