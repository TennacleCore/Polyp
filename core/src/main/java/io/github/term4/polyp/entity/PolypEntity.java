package io.github.term4.polyp.entity;

import io.github.term4.polyp.util.tick.TickScaler;
import io.github.term4.polyp.world.ExternallyTickable;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Chunk;
import org.jetbrains.annotations.NotNull;

/**
 * An entity Polyp drives: it ticks only on the clock that owns it ({@link ExternallyTickable}) and is gone below
 * the world. {@link Base} carries both. {@link DroppedItemEntity} keeps Minestom's {@code ItemEntity} parent, which
 * pickups, merging and the item tracker are built on, and a mob its {@code LivingEntity}, so those two apply them
 * themselves.
 */
public interface PolypEntity extends ExternallyTickable {

    /** 64 under the world's floor: 1.8's {@code posY < -64}, 26.2's {@code minY - 64}. False once removed. */
    static boolean outOfWorld(@NotNull Entity entity) {
        return entity.getInstance() != null && MechanicsWorld.of(entity).isInVoid(entity.getPosition());
    }

    /** Base for the library's hand-driven entities: the guards, the void, and the step-against-own-world movement tick. */
    abstract class Base extends Entity implements PolypEntity {

        protected static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;

        protected PhysicsResult lastPhysics;

        protected Base(@NotNull EntityType type) {
            super(type);
        }

        // bail on a foreign clock, or state advances on both and drifts (movementTick + update ride super.tick)
        @Override
        public void tick(long time) {
            if (!MechanicsWorld.ownsCurrentTick(this)) return;
            if (PolypEntity.outOfWorld(this)) {
                remove();
                return;
            }
            super.tick(time);
        }

        // @ApiStatus.Internal override: super is exactly this field write + dispatcher().updateElement (verified
        // 26.2, re-verify on bumps) - an externally ticked entity in the global dispatcher double-ticks
        @Override
        protected void refreshCurrentChunk(@NotNull Chunk chunk) {
            if (MechanicsWorld.externallyTicked(this)) {
                currentChunk = chunk;
                return;
            }
            super.refreshCurrentChunk(chunk);
        }

        /** One {@link MechanicsWorld#step} with the standard commit (velocity, onGround, silent position refresh). */
        protected final void stepAgainstWorld() {
            this.lastPhysics = MechanicsWorld.step(this, velocity.div(TPS), lastPhysics, result -> {
                this.velocity = result.newVelocity().mul(TPS);
                this.onGround = result.isOnGround();
                refreshPosition(result.newPosition(), true, false); // hand-sent wire: never Minestom's scheduled sync
            });
        }

        /** {@code motion} as the Minestom move vector: vanilla applies gravity BEFORE the move, Minestom after. */
        protected final Vec gravityLeadVector(Vec motion) {
            return motion.sub(0, TickScaler.aerodynamics(this, getAerodynamics()).gravity(), 0).mul(TPS);
        }
    }
}
