package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.api.event.damage.DamageAppliedEvent;
import io.github.term4.polyp.api.event.damage.FatalDamageEvent;
import io.github.term4.polyp.mechanics.damage.types.generic.GenericDamage;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a cancelled killing blow leaves behind: the feed says nothing landed, and a new life starts outside the window. */
class CancelledKillLedgerTest extends HeadlessServerTest {

    @Test
    void aCancelledKillDealtNothing() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(540.5, 65, 540.5), "LedgerVictim");
        AtomicReference<DamageAppliedEvent> applied = new AtomicReference<>();
        EventListener<FatalDamageEvent> keepUp = EventListener.of(FatalDamageEvent.class, FatalDamageEvent::cancel);
        EventListener<DamageAppliedEvent> feed = EventListener.of(DamageAppliedEvent.class, applied::set);
        MinecraftServer.getGlobalEventHandler().addListener(keepUp);
        MinecraftServer.getGlobalEventHandler().addListener(feed);
        try {
            victim.player.setHealth(4f);
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE).withAmount(9f));
            assertNotNull(applied.get());
            assertEquals(0f, applied.get().dealt(), "the blow was refused, so it dealt nothing");
            assertEquals(4f, victim.player.getHealth(), "and took nothing");
            assertTrue(DamageSystem.isInvulnerableToDamage(victim.player), "it still spent the window, as vanilla's does");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(keepUp);
            MinecraftServer.getGlobalEventHandler().removeListener(feed);
            victim.player.remove();
        }
    }

    @Test
    void resetClearsTheWindow() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(542.5, 65, 540.5), "LedgerReset");
        try {
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE).withAmount(1f));
            assertTrue(DamageSystem.isInvulnerableToDamage(victim.player));
            DamageSystem.resetMechanicsState(victim.player);
            assertEquals(0, DamageSystem.remainingDamageInvul(victim.player), "a new life is not inside the old window");
            assertEquals(0f, DamageSystem.lastDamage(victim.player), "nor carries its highwater");
        } finally {
            victim.player.remove();
        }
    }
}
