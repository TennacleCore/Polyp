package io.github.term4.polyp.mechanics.mobs.kinds;

import io.github.term4.polyp.mechanics.damage.DamageSystem.DamageOutcome;
import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.MobsSystem;
import io.github.term4.polyp.mechanics.mobs.ai.Goal;
import io.github.term4.polyp.mechanics.mobs.ai.HurtByTargetGoal;
import io.github.term4.polyp.mechanics.mobs.ai.MeleeAttackGoal;
import io.github.term4.polyp.mechanics.mobs.ai.NearestAttackableTargetGoal;
import io.github.term4.polyp.mechanics.mobs.ai.SwimGoal;
import io.github.term4.polyp.mechanics.mobs.ai.WanderGoal;
import io.github.term4.polyp.mechanics.mobs.path.Blocks;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Random;

/** 1.8 EntitySilverfish. */
public final class SilverfishEntity extends MobEntity {

    private static final Map<Block, Block> INFESTABLE = Map.of(
            Block.STONE, Block.INFESTED_STONE,
            Block.COBBLESTONE, Block.INFESTED_COBBLESTONE,
            Block.STONE_BRICKS, Block.INFESTED_STONE_BRICKS,
            Block.MOSSY_STONE_BRICKS, Block.INFESTED_MOSSY_STONE_BRICKS,
            Block.CRACKED_STONE_BRICKS, Block.INFESTED_CRACKED_STONE_BRICKS,
            Block.CHISELED_STONE_BRICKS, Block.INFESTED_CHISELED_STONE_BRICKS);

    private static final Map<Block, Block> INFESTED = Map.of(
            Block.INFESTED_STONE, Block.STONE,
            Block.INFESTED_COBBLESTONE, Block.COBBLESTONE,
            Block.INFESTED_STONE_BRICKS, Block.STONE_BRICKS,
            Block.INFESTED_MOSSY_STONE_BRICKS, Block.MOSSY_STONE_BRICKS,
            Block.INFESTED_CRACKED_STONE_BRICKS, Block.CRACKED_STONE_BRICKS,
            Block.INFESTED_CHISELED_STONE_BRICKS, Block.CHISELED_STONE_BRICKS);

    private final SummonGoal summon = new SummonGoal();

    public SilverfishEntity(MobsSystem mobs) {
        super(mobs, EntityType.SILVERFISH);
    }

    public static SilverfishEntity spawn(MobsSystem mobs, MechanicsWorld world, Pos position) {
        return mobs.spawn(new SilverfishEntity(mobs), world, position);
    }

    @Override
    protected void goals() {
        goalSelector().add(1, new SwimGoal(this));
        goalSelector().add(3, summon);
        goalSelector().add(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector().add(5, new HideInStoneGoal());
        targetGoals().add(1, new HurtByTargetGoal(this, true));
        targetGoals().add(2, new NearestAttackableTargetGoal(this, targetSight(), targetNearbyOnly()));
    }

    @Override
    public float pathWeight(BlockVec cell) {
        return Blocks.at(world(), cell.blockX(), cell.blockY() - 1, cell.blockZ()).compare(Block.STONE) ? 10.0f : super.pathWeight(cell);
    }

    @Override
    protected void hurtBy(@Nullable Entity source, DamageOutcome outcome) {
        if (source != null) summon.call();
        super.hurtBy(source, outcome);
    }

    /** 1.8 AIHideInStone: wander at chance 10, or one time in ten crawl into a stone block beside. */
    private final class HideInStoneGoal extends WanderGoal {
        private Direction facing;
        private boolean hiding;

        HideInStoneGoal() {
            super(SilverfishEntity.this, 1.0, 10);
            mutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (attackTarget() != null || !navigation().noPath()) return false;
            Random random = random();
            if (knob(kind().hidesInBlocks, false) && random.nextInt(10) == 0) {
                facing = Direction.values()[random.nextInt(Direction.values().length)];
                if (INFESTABLE.containsKey(Blocks.at(world(), beside()))) {
                    hiding = true;
                    return true;
                }
            }
            hiding = false;
            return super.shouldExecute();
        }

        @Override
        public boolean continueExecuting() {
            return !hiding && super.continueExecuting();
        }

        @Override
        public void startExecuting() {
            if (!hiding) {
                super.startExecuting();
                return;
            }
            BlockVec at = beside();
            Block block = Blocks.at(world(), at);
            Block infested = INFESTABLE.get(block);
            if (infested != null) {
                world().setBlock(at, infested);
                triggerStatus((byte) 20);
                remove();
            }
        }

        private BlockVec beside() {
            Pos p = getPosition();
            return new BlockVec(p.blockX() + facing.normalX(), (int) Math.floor(p.y() + 0.5) + facing.normalY(), p.blockZ() + facing.normalZ());
        }
    }

    /** 1.8 AISummonSilverfish: a hit wakes the infested blocks around, one silverfish each until a coin says stop. */
    private final class SummonGoal extends Goal {
        private int countdown;

        void call() {
            if (countdown == 0) countdown = 20;
        }

        @Override
        public boolean shouldExecute() {
            return countdown > 0;
        }

        @Override
        public void updateTask() {
            if (--countdown > 0 || !knob(kind().callsFromBlocks, false)) return;
            Pos p = getPosition();
            Random random = random();
            for (int i = 0; i <= 5 && i >= -5; i = i <= 0 ? 1 - i : 0 - i) {
                for (int j = 0; j <= 10 && j >= -10; j = j <= 0 ? 1 - j : 0 - j) {
                    for (int k = 0; k <= 10 && k >= -10; k = k <= 0 ? 1 - k : 0 - k) {
                        BlockVec at = new BlockVec(p.blockX() + j, p.blockY() + i, p.blockZ() + k);
                        Block block = Blocks.at(world(), at);
                        if (!INFESTED.containsKey(block)) continue;
                        world().setBlock(at, Block.AIR);
                        SilverfishEntity.spawn(mobs(), world(), new Pos(at.x() + 0.5, at.y(), at.z() + 0.5));
                        if (random.nextBoolean()) return;
                    }
                }
            }
        }
    }

    public boolean hostileTo(LivingEntity other) {
        return canAttack(other);
    }
}
