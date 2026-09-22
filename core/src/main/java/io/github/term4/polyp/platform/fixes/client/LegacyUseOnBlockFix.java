package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.listener.UseItemListener;
import net.minestom.server.tag.Tag;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.MinecraftServer;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.Polyp;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;

/**
 * Vanilla 1.8's {@code C08} carries BOTH semantics: interact the block, else USE the item
 * ({@code PlayerConnection}: interact, then tryUseItem). A modern client sends the fallback
 * {@code USE_ITEM} itself and ViaRewind only synthesizes one for AIR clicks - so a 1.8 player starting a
 * bow draw (or eat, or sword block) while aiming at an in-range block never starts the use server-side,
 * and the eventual release either drops or reads a STALE draw (the tap-tap full-power arrow). Restore the
 * fallback through the real use path, so blocking/consumable/projectile systems all see a normal use.
 */
public final class LegacyUseOnBlockFix {

    private LegacyUseOnBlockFix() {}

    private static final Tag<Boolean> PENDING = Tag.Transient("polyp:use-on-block-pending");

    public static void install(EventNode<? super Event> node) {
        node.addListener(PlayerUseItemOnBlockEvent.class, e -> {
            Player p = e.getPlayer();
            Polyp polyp = Polyp.getInstance();
            if (!polyp.clientInfo().isLegacy(p)) return;
            if (p.getItemUseHand() != null) {
                // a drawing 1.8 client never re-sends C08: a repeat press means ITS draw restarted and the
                // release was lost - restart, never span (vanilla's reference no-op would bank the lost gap)
                p.refreshActiveHand(false, p.getItemUseHand() == net.minestom.server.entity.PlayerHand.OFF, false);
                p.clearItemUse();
            }
            // the 1.8 client follows a refused block click with its own use_item (a rod, a bow at a plain block);
            // synthesize only when none arrives this tick, or the rod casts and retrieves in one click
            p.setTag(PENDING, true);
            PlayerHand hand = e.getHand();
            MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
                if (!p.isOnline() || !Boolean.TRUE.equals(p.getTag(PENDING))) return;
                p.removeTag(PENDING);
                // straight to the listener, not the queue: a queued use lands a tick later, which a bow draw feels.
                // The queue's own hook is what marks the slot unverified, so call it here instead
                if (p instanceof OptimizedPlayer op) {
                    op.inventorySync().onPredictedUse(hand == PlayerHand.OFF
                            ? net.minestom.server.utils.inventory.PlayerInventoryUtils.OFFHAND_SLOT : op.getHeldSlot());
                }
                UseItemListener.useItemListener(new ClientUseItemPacket(hand, 0,
                        p.getPosition().yaw(), p.getPosition().pitch()), p);
            });
        });
        node.addListener(PlayerPacketEvent.class, e -> {
            if (e.getPacket() instanceof ClientUseItemPacket) e.getPlayer().removeTag(PENDING);
        });
    }
}
