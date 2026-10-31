package net.Gabou.createtrainmining.core;

import net.Gabou.createtrainmining.Createtrainmining;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import java.util.UUID;

/** Persistent controller tickets and transient train footprints, with startup validation. */
public final class ControllerChunkLoader {
    private static final TicketController TICKETS = new TicketController(
            ResourceLocation.fromNamespaceAndPath(Createtrainmining.MODID, "controllers"),
            ControllerChunkLoader::validateTickets);

    private ControllerChunkLoader() {}

    public static void register(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static void setLoaded(ServerLevel level, BlockPos owner, boolean loaded) {
        var chunk = new ChunkPos(owner);
        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, loaded, true);
    }

    public static boolean isValidOwner(ServerLevel level, BlockPos owner) {
        return level.getBlockEntity(owner) instanceof TrainAutomationControllerBlockEntity entity
                && entity.shouldKeepLoaded();
    }

    public static void setTrainLoaded(ServerLevel level, UUID owner, ChunkPos chunk, boolean loaded) {
        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, loaded, true);
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        helper.getBlockTickets().forEach((owner, tickets) -> {
            if (!isValidOwner(level, owner)) {
                helper.removeAllTickets(owner);
                return;
            }
            long ownChunk = new ChunkPos(owner).toLong();
            // Only the controller's own ticking ticket is valid; discard obsolete extras.
            for (long chunk : tickets.nonTicking()) helper.removeTicket(owner, chunk, false);
            for (long chunk : tickets.ticking())
                if (chunk != ownChunk) helper.removeTicket(owner, chunk, true);
        });
        // Moving footprints are rebuilt from live train positions when their controller resumes.
        helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);
    }
}
