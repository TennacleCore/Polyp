package io.github.term4.polyp.mechanics.damage.silent;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * Suppresses outgoing health packets while {@link SilentDamage} applies a silent health change to a legacy client:
 * the player's {@code UpdateHealth}/{@code EntityAttributes} packets are cancelled so health rides entity metadata
 * instead (no 1.8 hurt-cam tilt).
 */
public final class HurtSuppression {

    private static final Tag<Boolean> SUPPRESS = Tag.Transient("polyp:suppress-health-packets");

    private HurtSuppression() {}

    /** Installs the tag cleanup under the damage system's node (so it lives and dies with the install). The drop
     *  itself is in {@code OptimizedPlayer.sendPacket}: a listener on {@code PlayerPacketOutEvent} costs the whole
     *  server an extract and an allocation per outgoing packet, and both packets here are self-only sends. */
    public static void install(EventNode<@NotNull Event> parent) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:hurt-suppression", EventFilter.PLAYER);
        node.addListener(PlayerDisconnectEvent.class, e -> e.getPlayer().removeTag(SUPPRESS));
        parent.addChild(node);
    }

    /** Whether {@code packet} is one this player's silent hit must swallow. */
    public static boolean swallows(@NotNull Player player, @NotNull SendablePacket packet) {
        if (!Boolean.TRUE.equals(player.getTag(SUPPRESS))) return false;
        return packet instanceof UpdateHealthPacket
                || (packet instanceof EntityAttributesPacket attr && attr.entityId() == player.getEntityId());
    }

    /** Toggle suppression of health packets for a player (set before setHealth, clear after). */
    public static void setSuppressHealthPackets(Player player, boolean suppress) {
        if (suppress) {
            player.setTag(SUPPRESS, true);
        } else {
            player.removeTag(SUPPRESS);
        }
    }
}
