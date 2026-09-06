package io.github.term4.polyp.mechanics.attack;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.api.event.attack.AttackEvent;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackReachTest extends HeadlessServerTest {

    private static final AtomicInteger PASSED = new AtomicInteger();

    @BeforeAll
    static void installAttack() {
        AttackSystem.install(polyp);
        MinecraftServer.getGlobalEventHandler().addListener(AttackEvent.class, e -> PASSED.incrementAndGet());
    }

    /** Attacker at z 8.5 looking down +Z; the victim's box starts 0.3 short of {@code victimZ}. */
    private static int swing(AttackConfig config, double victimZ, String tag) {
        Instance inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.ATTACK, config).build());
        FakePlayer attacker = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5, 0f, 0f), "Reach" + tag);
        Player victim = FakePlayer.connect(inst, new Pos(8.5, 64, victimZ), "Far" + tag).player;
        int before = PASSED.get();
        polyp.module(AttackSystem.class).apply(new AttackSnapshot(attacker.player, victim, null));
        return PASSED.get() - before;
    }

    @Test
    void paddingAdmitsThreeNotEight() {
        assertEquals(1, swing(Vanilla18.attack(), 11.5, "Near"), "2.7 from the eye to the box: inside 3 + 3");
        assertEquals(0, swing(Vanilla18.attack(), 16.5, "Far"), "7.7 from the eye to the box: past 3 + 3");
    }

    @Test
    void aNegativePaddingDisablesTheGate() {
        AttackConfig off = Vanilla18.attack().toBuilder().reachPadding(-1.0).build();
        assertEquals(1, swing(off, 16.5, "Off"));
    }
}
