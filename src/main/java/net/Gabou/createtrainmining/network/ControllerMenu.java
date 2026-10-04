package net.Gabou.createtrainmining.network;

import net.Gabou.createtrainmining.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public final class ControllerMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Player player;

    public ControllerMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModBlocks.CONTROLLER_MENU.get(), id);
        this.pos = pos;
        player = inventory.player;
    }

    public BlockPos position() {
        return pos;
    }

    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(player.level(), pos),
                player,
                ModBlocks.CONTROLLER.get());
    }

    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.serverLevel().getGameTime() % 10 == 0)
            ControllerNetworking.sendStatus(serverPlayer, this, "");
    }
}
