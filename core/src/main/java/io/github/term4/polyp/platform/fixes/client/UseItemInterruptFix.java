package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import org.jetbrains.annotations.NotNull;
import net.minestom.server.event.entity.EntityAttackEvent;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

/**
 * Vanilla (1.8 and 26 alike) stops an item use the moment the used stack changes under it - a swapped
 * hotbar slot, a resync rewriting the hand. Minestom keeps the use armed, and the client never sends the
 * release it no longer owes - so a bow draw survives invisibly and folds its ticks into the NEXT release
 * (the too-powerful arrow after a dud). Stop it silently, exactly like vanilla: no release, no shot.
 * A 1.8 client compares the held stack by REFERENCE, so any slot packet that rewrites the hand ends its use;
 * and neither client attacks while using, so an attack proves the use is over. A recount is the exception: the
 * server's stack is the same one, so its use runs on while the client's has ended.
 */
public final class UseItemInterruptFix {

    private record UseStart(byte heldSlot, Material material) {}

    private static final Tag<UseStart> USE_START = Tag.Transient("polyp:use-start");

    private UseItemInterruptFix() {}

    public static void install(EventNode<? super Event> node) {
        node.addListener(PlayerBeginItemUseEvent.class, e -> e.getPlayer().setTag(USE_START,
                new UseStart(e.getPlayer().getHeldSlot(), e.getItemStack().material())));
        node.addListener(PlayerTickEvent.class, e -> {
            Player p = e.getPlayer();
            PlayerHand hand = p.getItemUseHand();
            if (hand == null) return;
            UseStart start = p.getTag(USE_START);
            if (start == null) return;
            boolean slotMoved = hand == PlayerHand.MAIN && p.getHeldSlot() != start.heldSlot();
            if (!slotMoved && p.getItemInHand(hand).material() == start.material()) return;
            interrupt(p, hand);
        });

        node.addListener(EntityAttackEvent.class, e -> {
            if (e.getEntity() instanceof Player p && p.getItemUseHand() != null && !cut(p)) interrupt(p, p.getItemUseHand());
        });
    }

    /** A slot rewrite the client reads as "the item you are using changed": called from the send path, because a
     *  listener on {@code PlayerPacketOutEvent} taxes every outgoing packet server-wide. */
    public static void onOutgoing(@NotNull Player p, @NotNull SendablePacket packet) {
        if (p.getItemUseHand() != PlayerHand.MAIN || !Polyp.getInstance().clientInfo().isLegacy(p)) return;
        if (p instanceof OptimizedPlayer op && op.inventorySync().recounting()) return;
        if (rewritesHand(packet, p.getHeldSlot())) interrupt(p, PlayerHand.MAIN);
    }

    private static boolean cut(Player p) {
        return p instanceof OptimizedPlayer op && op.inventorySync().useCut();
    }

    private static void interrupt(Player p, PlayerHand hand) {
        p.refreshActiveHand(false, hand == PlayerHand.OFF, false);
        p.clearItemUse();
        p.removeTag(USE_START);
    }

    // 1.8 slot 36+held is the hotbar; a container's contents carry the player half too
    private static boolean rewritesHand(SendablePacket packet, byte held) {
        return switch (packet) {
            case SetPlayerInventorySlotPacket p -> p.slot() == held;
            case SetSlotPacket p -> p.windowId() == 0 && p.slot() == PlayerInventoryUtils.convertMinestomSlotToWindowSlot(held);
            case WindowItemsPacket ignored -> true;
            default -> false;
        };
    }
}
