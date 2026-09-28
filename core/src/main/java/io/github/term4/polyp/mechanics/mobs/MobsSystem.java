package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.ScopedSystem;
import io.github.term4.polyp.api.event.damage.DamageAppliedEvent;
import io.github.term4.polyp.api.event.damage.DamageEvent;
import io.github.term4.polyp.api.event.damage.PreDamageEvent;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.damage.types.fall.FallDamage;
import io.github.term4.polyp.mechanics.mobs.MobsConfigResolver.MobContext;
import io.github.term4.polyp.mechanics.mobs.kinds.EnderDragonEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import io.github.term4.polyp.world.WorldPolicy;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Spawns and serves the 1.8 mobs. Owns what the mob can't from inside a hit: the hurt bookkeeping, the fall
 * exemption, the dragon's part ids on the wire and its part rules.
 */
public final class MobsSystem extends ScopedSystem<MobsConfig> {

    public static final Key KEY = Key.key("polyp:mobs");

    private final EventNode<@NotNull Event> node = EventNode.all("polyp:mobs");

    public MobsSystem(Polyp polyp, MobsConfig config) {
        super(polyp, MechanicsKeys.MOBS, config);
        node.addListener(EntityDamageEvent.class, this::onMinestomDamage);
        node.addListener(PreDamageEvent.class, this::onPreDamage);
        node.addListener(DamageEvent.class, this::onDamage);
        node.addListener(DamageAppliedEvent.class, this::onDamageApplied);
        node.addListener(PlayerPacketEvent.class, this::onPacket);
    }

    public static MobsSystem install(Polyp polyp, MobsConfig config) {
        return polyp.installModule(new MobsSystem(polyp, config));
    }

    @Override
    public EventNode<@NotNull Event> node() { return node; }

    /** The config for {@code world}'s scope, the install's when the scope has none. */
    public MobsConfig configFor(MechanicsWorld world) {
        MobsConfig scoped = polyp.profiles().resolveWorld(world, MechanicsKeys.MOBS);
        return scoped != null ? scoped : config();
    }

    public Difficulty difficulty(MobContext ctx) {
        return FieldValue.resolve(configFor(ctx.world()).difficulty, ctx, Difficulty.EASY);
    }

    /** A mob's hit on a player, scaled by the victim world's difficulty. */
    public float scaleOnPlayer(MobEntity mob, Entity victim, float amount) {
        MobContext ctx = new MobContext(mob, MechanicsWorld.of(victim), services());
        MobsConfig cfg = configFor(ctx.world());
        Difficulty difficulty = FieldValue.resolve(cfg.difficulty, ctx, Difficulty.EASY);
        DifficultyScaling scaling = FieldValue.resolve(cfg.difficultyScaling, ctx, DifficultyScaling.LEGACY);
        return scaling.scale(difficulty, amount);
    }

    /** 1.8 EntityPlayer.getArmorVisibility: worn armor pieces out of four. */
    public float armorVisibility(Player player) {
        int worn = 0;
        for (EquipmentSlot slot : EquipmentSlot.armors()) {
            if (!player.getEquipment(slot).isAir()) ++worn;
        }
        return worn / 4.0f;
    }

    /** Puts {@code mob} into {@code world} at {@code position} with the world's knobs applied. */
    public <M extends MobEntity> M spawn(M mob, MechanicsWorld world, Pos position) {
        mob.configure(world);
        mob.yaw(position.yaw());
        mob.headYaw(position.yaw());
        mob.bodyYaw(position.yaw());
        world.spawn(mob, position);
        return mob;
    }

    // the mob's own hurt sound plays from its kind, not Minestom's generic one
    private void onMinestomDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof MobEntity) e.setSound(null);
    }

    private void onPreDamage(PreDamageEvent e) {
        if (e.target() instanceof MobEntity mob && !mob.takesFallDamage() && FallDamage.KEY.equals(e.type().key())) {
            e.setCancelled(true);
        }
    }

    private void onDamage(DamageEvent e) {
        if (e.target() instanceof EnderDragonEntity dragon) dragon.onHit(e);
    }

    private void onDamageApplied(DamageAppliedEvent e) {
        if (e.target() instanceof MobEntity mob) mob.hurt(e.source(), e.outcome());
    }

    // a dragon part is an id with no entity: route the swing to the dragon carrying the part
    private void onPacket(PlayerPacketEvent e) {
        int target;
        if (e.getPacket() instanceof ClientAttackPacket attack) target = attack.targetId();
        else if (e.getPacket() instanceof ClientInteractEntityPacket interact) target = interact.targetId();
        else return;
        Player player = e.getPlayer();
        if (player.getInstance() == null) return;
        EnderDragonEntity dragon = dragonOfPart(player, target);
        if (dragon == null) return;
        e.setCancelled(true);
        if (!(e.getPacket() instanceof ClientAttackPacket) || !WorldPolicy.canAffect(player, dragon)) return;
        if (dragon.isDead()) return;
        dragon.hitPart(target);
        EventDispatcher.call(new EntityAttackEvent(player, dragon));
    }

    private @Nullable EnderDragonEntity dragonOfPart(Player player, int id) {
        for (Entity e : MechanicsWorld.of(player).entities()) {
            if (e instanceof EnderDragonEntity dragon && dragon.partIndex(id) >= 0) return dragon;
        }
        return null;
    }
}
