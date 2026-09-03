package io.github.term4.polyp.world;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The entity a spectator's camera rides, stamped by whoever drives the ride; {@link WorldPolicy#canSee} reads it. */
public final class SpectatorCamera {

    public static final Tag<Entity> RIDING = Tag.Transient("polyp:spectator-camera");

    private SpectatorCamera() {}

    public static @Nullable Entity riding(@NotNull Player player) {
        return player.getTag(RIDING);
    }

    public static boolean rides(@NotNull Player player) {
        return player.getTag(RIDING) != null;
    }
}
