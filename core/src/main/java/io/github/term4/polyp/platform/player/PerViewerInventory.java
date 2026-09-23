package io.github.term4.polyp.platform.player;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import java.util.List;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.SendablePacket;
import org.jetbrains.annotations.NotNull;

/**
 * The player's own inventory. Slot and cursor changes go to the owner's
 * {@link io.github.term4.polyp.platform.inventory.InventorySync}, which sends only what the client does not already
 * show. Anything still sent from here reaches {@link OptimizedPlayer#sendPacket} bare: the
 * {@link net.minestom.server.Viewable#sendPacketToViewers} default wraps it in a shared {@code CachedPacket} the
 * per-client item rewrite can't unwrap.
 */
public final class PerViewerInventory extends PlayerInventory {

    @Override
    protected void UNSAFE_itemInsert(int slot, ItemStack item, ItemStack previous, boolean sendPacket) {
        super.UNSAFE_itemInsert(slot, item, previous, false);
    }

    @Override
    public void sendSlotRefresh(int slot, ItemStack item) {
        for (Player viewer : getViewers()) {
            if (viewer instanceof OptimizedPlayer op) op.inventorySync().refresh(this, slot, item);
        }
    }

    @Override
    public void update(Player player) {
        if (player instanceof OptimizedPlayer op) op.inventorySync().resync(this);
        else super.update(player);
    }

    @Override
    public void setCursorItem(ItemStack cursorItem, boolean sendPacket) {
        super.setCursorItem(cursorItem, false);
    }

    @Override
    public void sendPacketToViewers(@NotNull SendablePacket packet) {
        for (Player viewer : getViewers()) viewer.sendPacket(packet);
    }

    @Override
    public boolean middleClick(Player player, int slot) {
        if (CreativeClicks.clone(player, this, slot)) return true;
        update();
        return false;
    }

    @Override
    public boolean dragging(Player player, List<Integer> slots, int button) {
        if (button != 10) return super.dragging(player, slots, button);
        ItemStack cursor = CreativeClicks.cloneDrag(player, this, slots, getCursorItem());
        if (cursor == null) {
            update();
            return false;
        }
        setCursorItem(cursor);
        return true;
    }
}
