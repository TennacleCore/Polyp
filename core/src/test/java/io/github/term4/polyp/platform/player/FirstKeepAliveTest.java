package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The first keep-alive is due ~2s after join, never on the join tick (a 1.7 client dies on that one). */
class FirstKeepAliveTest extends HeadlessServerTest {

    @Test
    void firstKeepAliveIsDueTwoSecondsIn() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 64, 950.5), "KaFirst");
        try {
            long sinceLast = System.nanoTime() - p.player.getLastKeepAlive();
            long dueIn = TimeUnit.MILLISECONDS.toNanos(ServerFlag.KEEP_ALIVE_DELAY) - sinceLast;
            long ms = TimeUnit.NANOSECONDS.toMillis(dueIn);
            assertTrue(ms > 500 && ms <= 2_000, "due in " + ms + "ms, wanted ~2s minus the connect time");
            assertTrue(p.player.didAnswerKeepAlive(), "the first one must still be allowed to go out");
        } finally {
            p.player.remove();
        }
    }
}
