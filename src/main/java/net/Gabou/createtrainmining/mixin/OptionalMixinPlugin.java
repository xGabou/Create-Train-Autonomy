package net.Gabou.createtrainmining.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Register addon hooks only when their bytecode is present; no optional linkage in the core. */
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
        var mixins = new ArrayList<String>();
        if (isPresent("com.vodmordia.railwaysuntold_additions.contraption.CruiseControlManager"))
            mixins.add("RailwaysCruiseMixin");
        if (isPresent("de.mrjulsen.ctt.CreateThreadedTrains"))
            mixins.add("ThreadedTrainsMixin");
        return mixins;
    }

    private static boolean isPresent(String className) {
        try {
            MixinService.getService()
                    .getBytecodeProvider()
                    .getClassNode(className);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
