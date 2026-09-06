package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A viewer who arrives after an effect landed is told about it: a client times a swing off the swinger's own. */
class EffectResyncFixTest extends HeadlessServerTest {

    @BeforeAll
    static void installFix() {
        FixesSystem.install(Polyp.getInstance(), FixesConfig.builder().effectResync(FixToggleConfig.on()).build());
    }

    private static boolean toldAbout(FakePlayer viewer, LivingEntity subject, PotionEffect effect) {
        return viewer.sent(EntityEffectPacket.class).stream()
                .anyMatch(p -> p.entityId() == subject.getEntityId() && p.potion().effect() == effect);
    }

    @Test
    void aLateViewerIsToldAboutTheEffects() {
        LivingEntity hasted = zombie(new Pos(700.5, 65, 700.5));
        hasted.addEffect(new Potion(PotionEffect.HASTE, 1, Potion.INFINITE_DURATION, 0)); // nobody watching yet
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(700.5, 65, 703.5), "LateViewer");
        try {
            for (int i = 0; i < 20 && !toldAbout(viewer, hasted, PotionEffect.HASTE); i++) {
                MinecraftServer.getSchedulerManager().processTick();
                MinecraftServer.getSchedulerManager().processTickEnd();
            }
            assertTrue(toldAbout(viewer, hasted, PotionEffect.HASTE), "the effect follows the spawn it was owed");

            viewer.sent.clear();
            hasted.removeEffect(PotionEffect.HASTE);
            for (int i = 0; i < 5; i++) MinecraftServer.getSchedulerManager().processTick();
            assertFalse(toldAbout(viewer, hasted, PotionEffect.HASTE), "and a bare entity resends nothing");
        } finally {
            hasted.remove();
            viewer.player.remove();
        }
    }
}
