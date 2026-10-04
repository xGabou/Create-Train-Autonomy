package net.Gabou.createtrainmining.core;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Read-only cargo access using Create mounted storage. Does not require logistics addons. */
public final class TrainInventoryView {
    private final ManagedTrain train;

    public TrainInventoryView(ManagedTrain train) {
        this.train = train;
    }

    private List<IItemHandler> handlers() {
        var result = new ArrayList<IItemHandler>();
        train.unwrap()
                .carriages
                .forEach(
                        c -> {
                            if (c.storage != null) {
                                var items = c.storage.getAllItems();
                                if (items != null) result.add(items);
                            }
                        });
        return result;
    }

    public int getSlots() {
        return handlers().stream().mapToInt(IItemHandler::getSlots).sum();
    }

    public ItemStack getStack(int slot) {
        if (slot < 0) throw new IndexOutOfBoundsException(slot);
        for (var handler : handlers()) {
            if (slot < handler.getSlots()) return handler.getStackInSlot(slot).copy();
            slot -= handler.getSlots();
        }
        throw new IndexOutOfBoundsException(slot);
    }

    public long getUsedItemCount() {
        long count = 0;
        for (var h : handlers())
            for (int i = 0; i < h.getSlots(); i++) count += h.getStackInSlot(i).getCount();
        return count;
    }

    public long getEstimatedCapacity() {
        long count = 0;
        for (var h : handlers())
            for (int i = 0; i < h.getSlots(); i++)
                count += slotCapacity(h.getSlotLimit(i), h.getStackInSlot(i));
        return count;
    }

    /**
     * Mean occupied fraction per slot: a full stack of 1 or 16 consumes a full slot. Empty capacity
     * is estimated.
     */
    public double getUsageRatio() {
        double used = 0;
        int slots = 0;
        for (var h : handlers())
            for (int i = 0; i < h.getSlots(); i++) {
                int capacity = slotCapacity(h.getSlotLimit(i), h.getStackInSlot(i));
                if (capacity <= 0) continue;
                slots++;
                used += Math.min(1, (double) h.getStackInSlot(i).getCount() / capacity);
            }
        return slots == 0 ? 0 : used / slots;
    }

    static int slotCapacity(int limit, ItemStack stack) {
        return Math.max(0, Math.min(limit, stack.isEmpty() ? 64 : stack.getMaxStackSize()));
    }

    public List<ItemStack> findItems(Predicate<ItemStack> predicate) {
        var matches = new ArrayList<ItemStack>();
        for (var h : handlers())
            for (int i = 0; i < h.getSlots(); i++) {
                var stack = h.getStackInSlot(i).copy();
                if (!stack.isEmpty() && predicate.test(stack)) matches.add(stack);
            }
        return List.copyOf(matches);
    }

    public boolean isEmpty() {
        return getUsedItemCount() == 0;
    }

    int fingerprint() {
        int hash = 1;
        for (var handler : handlers())
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                hash = 31 * hash + ItemStack.hashItemAndComponents(stack);
                hash = 31 * hash + stack.getCount();
            }
        return hash;
    }

    List<Integer> versions() {
        return train.unwrap().carriages.stream()
                .map(c -> c.storage == null ? -1 : c.storage.getVersion())
                .toList();
    }
}
