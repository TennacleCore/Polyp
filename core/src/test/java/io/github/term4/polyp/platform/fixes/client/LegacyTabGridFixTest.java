package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.entity.GameMode;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Join Game's max players is the 1.7 tab grid; Minestom sends 0, which draws as a bare rule. */
class LegacyTabGridFixTest {

    private static JoinGamePacket join(int maxPlayers) {
        return new JoinGamePacket(7, false, List.of("minecraft:overworld"), maxPlayers, 8, 8, false, true, false,
                new PlayerSpawnInfo(0, "minecraft:overworld", 0L, GameMode.SURVIVAL, null, false, false, null, 0, 63),
                false, false);
    }

    @Test
    void zeroBecomesAGrid() {
        var out = LegacyTabGridFix.rewrite(join(0), true);
        assertEquals(LegacyTabGridFix.SLOTS, assertInstanceOf(JoinGamePacket.class, out).maxPlayers());
    }

    @Test
    void aRealCountAndAnUnappliedFixPass() {
        JoinGamePacket real = join(64);
        assertSame(real, LegacyTabGridFix.rewrite(real, true), "an app that sets its own is left alone");
        JoinGamePacket zero = join(0);
        assertSame(zero, LegacyTabGridFix.rewrite(zero, false));
    }
}
