package io.github.term4.polyp;

import io.github.term4.polyp.mechanics.hunger.HungerConfig;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owned player contributions: newest in force, lower ones shadowed but intact, and a game clears its own. */
class ProfileContributionsTest extends HeadlessServerTest {

    private static final Key LOBBY = Key.key("test:lobby");
    private static final Key GAME = Key.key("test:game");

    private static MechanicsProfile hunger(boolean enabled) {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.HUNGER, HungerConfig.builder().enabled(enabled).build()).build();
    }

    @Test
    void newestWinsAndClearingRestoresWhatItShadowed() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "Stacked");
        try {
            var profiles = polyp.profiles();
            profiles.setPlayer(p.player, LOBBY, hunger(true));
            assertEquals(Boolean.TRUE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled());

            profiles.setPlayer(p.player, GAME, hunger(false));
            assertEquals(Boolean.FALSE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled(),
                    "the game's contribution is the player scope while it stands");
            assertEquals(2, profiles.contributions(p.player).size(), "the lobby's is shadowed, not lost");

            // a mid-game re-set must not leapfrog a contribution added after it
            profiles.setPlayer(p.player, LOBBY, hunger(true));
            assertEquals(Boolean.FALSE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled(),
                    "re-setting an older owner keeps its position");

            profiles.clearPlayer(p.player, GAME);
            assertEquals(Boolean.TRUE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled(),
                    "what the game shadowed applies again");
        } finally {
            p.player.remove();
        }
    }

    /** The hypothetical that used to break: a lobby's per-player setup must not follow you into a game world. */
    @Test
    void aWorldBoundContributionIsInertElsewhere() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(3.5, 65, 0.5), "Bound");
        try {
            var profiles = polyp.profiles();
            var lobby = io.github.term4.polyp.world.MechanicsWorld.of(instance);
            // one world is just a predicate - a nested shard tree would be w -> w.isUnder(shard)
            profiles.setPlayer(p.player, LOBBY, hunger(true), w -> w == lobby);
            assertEquals(Boolean.TRUE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled(),
                    "it applies in the world it was bound to");

            var elsewhere = flatInstance(null);
            p.player.setInstance(elsewhere, new Pos(0.5, 65, 0.5)).join();
            assertNull(profiles.player(p.player), "and is inert in another world - the game's profile wins there");

            p.player.setInstance(instance, new Pos(3.5, 65, 0.5)).join();
            assertEquals(Boolean.TRUE, profiles.resolve(p.player, MechanicsKeys.HUNGER).enabled(),
                    "coming back revives it - nothing was cleared");
        } finally {
            p.player.remove();
        }
    }

    /** A subtree binding: {@code isUnder} is the dynamic test a powerup that only works in one shard uses. */
    @Test
    void isUnderCoversAWorldAndItsDescendants() {
        var here = io.github.term4.polyp.world.MechanicsWorld.of(instance);
        var other = io.github.term4.polyp.world.MechanicsWorld.of(flatInstance(null));
        assertTrue(here.isUnder(here), "a world is under itself");
        assertFalse(here.isUnder(other), "and not under an unrelated one");
    }

    @Test
    void clearOwnerSweepsEveryPlayerTheGameTouched() {
        FakePlayer a = FakePlayer.connect(instance, new Pos(1.5, 65, 0.5), "SweepA");
        FakePlayer b = FakePlayer.connect(instance, new Pos(2.5, 65, 0.5), "SweepB");
        try {
            var profiles = polyp.profiles();
            profiles.setPlayer(a.player, GAME, hunger(false));
            profiles.setPlayer(b.player, GAME, hunger(false));

            profiles.clearOwner(GAME);

            assertTrue(profiles.contributions(a.player).isEmpty());
            assertTrue(profiles.contributions(b.player).isEmpty());
            assertNull(profiles.player(a.player), "no contribution left means no player scope at all");
        } finally {
            a.player.remove();
            b.player.remove();
        }
    }
}
