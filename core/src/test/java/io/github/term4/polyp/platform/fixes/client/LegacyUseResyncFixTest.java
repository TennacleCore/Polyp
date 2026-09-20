package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A draw that fires nothing leaves a 1.8 client believing it is still drawing; the player's own workaround is to
 * open and close their inventory, so the fix sends that refresh for them.
 */
class LegacyUseResyncFixTest extends HeadlessServerTest {

    @BeforeAll
    static void setUp() {
        FixesSystem.install(polyp, FixesConfig.builder().legacyUseResync(FixToggleConfig.on()).build());
    }

    @Test
    void emptyReleaseRebaselines() {
        assertTrue(refreshedAfterRelease(47), "a 1.8 client is re-baselined");
    }

    @Test
    void aModernClientIsLeftAlone() {
        assertFalse(refreshedAfterRelease(MinecraftServer.PROTOCOL_VERSION), "nothing to unstick, nothing sent");
    }

    private static boolean refreshedAfterRelease(int protocol) {
        FakePlayer drawer = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "Drawer" + protocol);
        try {
            Polyp.getInstance().clientInfo().setProtocol(drawer.player, protocol);
            drawer.sent.clear();
            EventDispatcher.call(new PlayerCancelItemUseEvent(drawer.player, PlayerHand.MAIN,
                    ItemStack.of(Material.BOW), 3)); // under the draw a shot needs: nothing fires
            MinecraftServer.getSchedulerManager().processTickEnd();
            return drawer.sent.stream().anyMatch(WindowItemsPacket.class::isInstance);
        } finally {
            drawer.player.remove();
        }
    }
}
