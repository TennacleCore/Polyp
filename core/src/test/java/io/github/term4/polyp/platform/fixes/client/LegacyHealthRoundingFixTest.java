package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whole points for the 1.8 heart bar, a hair under the point when a hit stayed inside the same heart. */
class LegacyHealthRoundingFixTest {

    @Test
    void roundsToWholePoints() {
        assertEquals(14f, LegacyHealthRoundingFix.round(Float.NaN, 14.01f), "14.01 would draw as 15 half-hearts");
        assertEquals(14f, LegacyHealthRoundingFix.round(Float.NaN, 14.4f));
        assertEquals(1f, LegacyHealthRoundingFix.round(Float.NaN, 0.3f), "alive never rounds to nothing");
        assertEquals(0f, LegacyHealthRoundingFix.round(Float.NaN, 0f));
        assertEquals(0f, LegacyHealthRoundingFix.round(20f, -1f));
    }

    @Test
    void aHitInsideTheSameHeartStillFlinches() {
        float sent = LegacyHealthRoundingFix.round(14.4f, 13.6f);
        assertTrue(sent < 14f && sent > 13.99f, "a hair under 14, not 14: " + sent);
        assertEquals(14f, LegacyHealthRoundingFix.round(13.6f, 14.4f), "a heal to the same heart is just the point");
    }

    static class Wire extends HeadlessServerTest {
        @Test
        void onlyTheViewersOwnHealthAndOnlyWhenApplied() {
            FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 64, 900.5), "HpRound");
            try {
                UpdateHealthPacket raw = new UpdateHealthPacket(14.01f, 20, 5f);
                assertSame(raw, LegacyHealthRoundingFix.rewrite(p.player, false, raw), "modern or toggled off: untouched");
                SendablePacket out = LegacyHealthRoundingFix.rewrite(p.player, true, raw);
                assertEquals(14f, assertInstanceOf(UpdateHealthPacket.class, out).health());
                SendablePacket again = LegacyHealthRoundingFix.rewrite(p.player, true, new UpdateHealthPacket(13.6f, 20, 5f));
                float sent = assertInstanceOf(UpdateHealthPacket.class, again).health();
                assertTrue(sent < 14f && sent > 13.99f, "the same heart after a hit: " + sent);
            } finally {
                p.player.remove();
            }
        }
    }
}
