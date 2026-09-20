package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.PacketShapes;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;

/**
 * A 1.7 client sizes its whole tab list from Join Game's {@code maxPlayers} - that many slots, 20 to a column -
 * and Minestom sends 0, so the list draws as a bare rule however many players it holds. 1.8+ ignores the field,
 * and the protocol is not known yet at join, so this goes to everyone.
 */
public final class LegacyTabGridFix {

    /** A vanilla server's default {@code max-players}, which is what a 1.7 tab grid was drawn against. */
    public static final int SLOTS = 20;

    private LegacyTabGridFix() {}

    public static SendablePacket rewrite(SendablePacket packet, boolean apply) {
        if (!apply) return packet;
        ServerPacket server = PacketShapes.unwrapStateless(packet);
        if (!(server instanceof JoinGamePacket join) || join.maxPlayers() > 0) return packet;
        return new JoinGamePacket(join.entityId(), join.isHardcore(), join.worlds(), SLOTS,
                join.viewDistance(), join.simulationDistance(), join.reducedDebugInfo(), join.enableRespawnScreen(),
                join.doLimitedCrafting(), join.playerSpawnInfo(), join.onlineMode(), join.enforcesSecureChat());
    }
}
