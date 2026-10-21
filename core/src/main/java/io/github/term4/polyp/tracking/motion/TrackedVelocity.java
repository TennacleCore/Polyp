package io.github.term4.polyp.tracking.motion;

import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;

/**
 * A non-player whose server-tracked velocity is not its motion: a body standing in for a player, whose melee
 * knockback the server would never fold back in.
 */
public interface TrackedVelocity {

    /** Blocks a tick. */
    @NotNull Vec trackedVelocity();
}
