package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.codegen.CheckResolveOrder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

@CheckResolveOrder
public final class BlocksConfigResolver {

    private BlocksConfigResolver() {}

    /** One world's placements. No subject: a pair is world state, so every placer in a world pairs the same way. */
    public record BlocksContext(@Nullable MechanicsWorld world, Services services) implements SubjectContext {
        @Override public @Nullable Entity subject() { return null; }
    }

    public record ResolvedBlocksConfig(BlocksConfig.ChestPlacement chestPlacement) {}

    /** Plain values; {@code null} config = modern. */
    public static ResolvedBlocksConfig resolve(@Nullable BlocksConfig config, BlocksContext ctx) {
        BlocksConfig cfg = config != null ? config.withOverlay(ctx) : null;
        return new ResolvedBlocksConfig(
                FieldValue.resolve(cfg != null ? cfg.chestPlacement : null, ctx, BlocksConfig.ChestPlacement.MODERN));
    }
}
