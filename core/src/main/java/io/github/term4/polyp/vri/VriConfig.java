package io.github.term4.polyp.vri;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.entity.DroppedItemEntity;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Toggles for the VRI (Vanilla Re-Implemented) behaviors, per scope via {@code MechanicsKeys.VRI}; the set grows as chests / deaths etc. land. */
@GenerateBuilder
public final class VriConfig {

    /** What a VRI knob resolves against: the player breaking, dropping, picking up or igniting. */
    public record VriContext(@Nullable Entity subject) implements SubjectContext {}

    /** Default off. */
    public final @Nullable FieldValue<VriContext, Boolean> blockBreakProgress;
    /** Unset = off; compose custom loot with {@link BlockDrops#chain}. */
    public final @Nullable FieldValue<VriContext, BlockDrops.DropRule> blockDrops;
    /** Unset resolves {@code MechanicsKeys.ITEM_PHYSICS} from the profile (LEGACY fallback). */
    public final @Nullable FieldValue<VriContext, DroppedItemEntity.Model> itemPhysics;
    /** Default off. */
    public final @Nullable FieldValue<VriContext, Boolean> itemPickup;
    /** Q / drag-out. Default off. */
    public final @Nullable FieldValue<VriContext, Boolean> itemDrop;
    /** Fire parity on breaks: direct-break fizz + orphaned-fire removal. Default off. */
    public final @Nullable FieldValue<VriContext, Boolean> fireBreaks;
    /** Hand ignition (flint and steel / fire charge); redstone, fire spread, dispensers and flaming projectiles are not. Default off. */
    public final @Nullable FieldValue<VriContext, Boolean> tntIgnite;

    private VriConfig(Builder b) {
        blockBreakProgress = b.blockBreakProgress;
        blockDrops = b.blockDrops;
        itemPhysics = b.itemPhysics;
        itemPickup = b.itemPickup;
        itemDrop = b.itemDrop;
        fireBreaks = b.fireBreaks;
        tntIgnite = b.tntIgnite;
    }

    /** A boolean toggle for {@code actor}; unset is off. */
    public static boolean on(@Nullable FieldValue<VriContext, Boolean> knob, @Nullable Entity actor) {
        return Boolean.TRUE.equals(FieldValue.resolve(knob, new VriContext(actor)));
    }

    public static Builder builder() { return new Builder(); }

    /** Everything on: {@link BlockDrops#VANILLA} drops, item physics from the profile. */
    public static VriConfig all() {
        return builder().blockBreakProgress(true)
                .blockDrops(BlockDrops.VANILLA).itemPickup(true).itemDrop(true).fireBreaks(true).tntIgnite(true).build();
    }

    public Builder toBuilder() { return new Builder(this); }

    /** Merges this config over {@code base}. */
    public VriConfig fromBase(VriConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b.build();
    }

    public static final class Builder extends VriConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        Builder() {}
        Builder(VriConfig c) { super(c); }

        public VriConfig build() { return new VriConfig(this); }
    }
}
