package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.mechanics.mobs.Difficulty;
import io.github.term4.polyp.mechanics.mobs.DifficultyScaling;
import io.github.term4.polyp.mechanics.mobs.MeleeReach;
import io.github.term4.polyp.mechanics.mobs.MobKindConfig;
import io.github.term4.polyp.mechanics.mobs.MobSound;
import io.github.term4.polyp.mechanics.mobs.MobsConfig;
import io.github.term4.polyp.mechanics.mobs.MobsConfigResolver.MobContext;
import io.github.term4.polyp.mechanics.mobs.PartDamage;
import io.github.term4.polyp.mechanics.mobs.Sides;
import io.github.term4.polyp.mechanics.mobs.Targets;
import io.github.term4.polyp.mechanics.mobs.kinds.IronGolemEntity;
import io.github.term4.polyp.mechanics.mobs.path.Pathing;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** 1.8's silverfish, iron golem and ender dragon. */
public final class Mobs {

    private Mobs() {}

    // 1.8 EntityDragon.destroyBlocksInAABB
    private static final Set<Block> DRAGON_PROOF = Set.of(Block.BARRIER, Block.OBSIDIAN, Block.END_STONE, Block.BEDROCK,
            Block.COMMAND_BLOCK, Block.CHAIN_COMMAND_BLOCK, Block.REPEATING_COMMAND_BLOCK);

    private static final Predicate<Block> DRAGON_EATS = block -> !DRAGON_PROOF.contains(block);

    // 1.8 canAttackClass: a player-built golem never turns on players
    private static final Function<MobContext, Predicate<LivingEntity>> GOLEM_ATTACKABLE = ctx -> t -> !(t instanceof Player)
            || !(ctx.mob() instanceof IronGolemEntity golem && golem.playerCreated());

    public static MobsConfig config() {
        return MobsConfig.builder()
                .sides(Sides.SCOREBOARD)
                .difficulty(Difficulty.EASY) // server.properties' default
                .difficultyScaling(DifficultyScaling.LEGACY)
                .goalTickRate(3)
                .defaults(MobKindConfig.builder()
                        .followRange(16.0)
                        .stepHeight(0.6)
                        .pathing(Pathing.LEGACY)
                        .knockbackResistance(0.0)
                        .attackInterval(20)
                        .reach(MeleeReach.LEGACY)
                        .attackNeedsSight(false)
                        .attackLift(0.0)
                        .targetChance(10)
                        .targetSight(true)
                        .targetNearbyOnly(false)
                        .seesInvisible(false)
                        .hostile(false)
                        .fallDamage(true)
                        .avoidsWater(false)
                        .hidesInBlocks(false)
                        .callsFromBlocks(false)
                        .build())
                .kind(EntityType.SILVERFISH, silverfish())
                .kind(EntityType.IRON_GOLEM, ironGolem())
                .kind(EntityType.ENDER_DRAGON, enderDragon())
                .build();
    }

    public static MobKindConfig silverfish() {
        return MobKindConfig.builder()
                .width(0.4).height(0.3).eyeHeight(0.1)
                .maxHealth(8.0).speed(0.25).attackDamage(1.0f)
                .hostile(true)
                .targetSelector(Targets.PLAYERS)
                .ambientSound(MobSound.of(SoundEvent.ENTITY_SILVERFISH_AMBIENT, 1.0f))
                .hurtSound(MobSound.of(SoundEvent.ENTITY_SILVERFISH_HURT, 1.0f))
                .deathSound(MobSound.of(SoundEvent.ENTITY_SILVERFISH_DEATH, 1.0f))
                .hidesInBlocks(true)
                .callsFromBlocks(true)
                .build();
    }

    public static MobKindConfig ironGolem() {
        return MobKindConfig.builder()
                .width(1.4).height(2.9).eyeHeight(2.9 * 0.85)
                .maxHealth(100.0).speed(0.25)
                .attackDamage(ctx -> 7.0f + ctx.random().nextInt(15))
                .attackLift(0.4000000059604645)
                .attackSound(MobSound.of(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, 1.0f))
                .targetSight(false)
                .targetNearbyOnly(true)
                .targetSelector(Targets.VISIBLE_MONSTERS)
                .attackable(GOLEM_ATTACKABLE)
                .fallDamage(false)
                .avoidsWater(true)
                .hurtSound(MobSound.of(SoundEvent.ENTITY_IRON_GOLEM_HURT, 1.0f))
                .deathSound(MobSound.of(SoundEvent.ENTITY_IRON_GOLEM_DEATH, 1.0f))
                .stepSound(MobSound.of(SoundEvent.ENTITY_IRON_GOLEM_STEP, 1.0f))
                .build();
    }

    public static MobKindConfig enderDragon() {
        return MobKindConfig.builder()
                .width(16.0).height(8.0).eyeHeight(8.0 * 0.85)
                .maxHealth(200.0)
                .hostile(true)
                .targetSelector(Targets.PLAYERS)
                .breaks(DRAGON_EATS)
                .partDamage(PartDamage.LEGACY)
                .enchantBonus(true)
                .wingPush(4.0)
                .wingLift(0.20000000298023224)
                .biteDamage(10.0f)
                .roamRadius(60.0)
                .roamMinY(70.0)
                .roamHeight(50.0)
                .ambientSound(MobSound.of(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, 5.0f))
                .hurtSound(MobSound.of(SoundEvent.ENTITY_ENDER_DRAGON_HURT, 5.0f))
                // EntityLivingBase's own, at the dragon's volume: no override in 1.8
                .deathSound(MobSound.of(SoundEvent.ENTITY_GENERIC_DEATH, 5.0f))
                .build();
    }
}
