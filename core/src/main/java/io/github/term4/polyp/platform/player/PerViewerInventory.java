package io.github.term4.polyp.platform.player;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import java.util.List;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.SendablePacket;
import org.jetbrains.annotations.NotNull;

/**
 * Delivers incremental slot/cursor refreshes bare: the {@link net.minestom.server.Viewable#sendPacketToViewers} default
 * wraps them in a shared {@code CachedPacket} the per-client item rewrite ({@link OptimizedPlayer#sendPacket}) can't
 * unwrap - so a stamp/reskin reverted after the first throw/drop until a full {@code WindowItems} resend. A player
 * inventory has one viewer; the shared cache buys nothing.
 */
public final class PerViewerInventory extends PlayerInventory {

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
        update();
        return true;
    }
}
