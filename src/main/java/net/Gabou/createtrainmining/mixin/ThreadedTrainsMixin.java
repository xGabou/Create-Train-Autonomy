package net.Gabou.createtrainmining.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** Keep the addon's tick and future lifecycle, but never send world controls to its worker. */
@Pseudo
@Mixin(targets = "de.mrjulsen.ctt.CreateThreadedTrains", remap = false)
public abstract class ThreadedTrainsMixin {
    @Unique private static boolean automation$reportedThreadedFallback;

    @Redirect(
            method = "preTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/mrjulsen/ctt/WorkerThread;submitTask(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"),
            require = 1)
    private static Future<?> automation$tickOnServerThread(
            @Coerce Object worker, Runnable task, MinecraftServer server) {
        if (!server.isSameThread())
            throw new IllegalStateException("Railway compatibility ticks must run on the server thread");
        if (!automation$reportedThreadedFallback) {
            automation$reportedThreadedFallback = true;
            LogUtils.getLogger().warn(
                    "Create Train Automation: Create Threaded Trains compatibility is active. "
                            + "Railway ticks run on the server thread; parallel railway ticking is disabled.");
        }
        // Do not enqueue onto the server and wait: postTick waits for this future, which could
        // deadlock. Run the original railway task now, exactly once, before returning its future.
        // This also avoids racing controller block/entity ticks against any unmanaged trains.
        task.run();
        return CompletableFuture.completedFuture(null);
    }
}
