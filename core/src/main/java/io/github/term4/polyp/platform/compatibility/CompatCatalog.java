package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.tracking.ClientRange;
import io.github.term4.polyp.tracking.ClientVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every compat knob and the clients it applies to. Most are ANY on purpose: they emulate an era's MECHANICS on the
 * server, so a modern client on a 1.8 preset takes them like everyone else - the note says which era a knob speaks
 * for. The narrowed ones are the knobs whose effect is a client's own rendering or prediction, and each of those was
 * already gated in {@link CompatState}; the range only states the gate in one place.
 */
public final class CompatCatalog {

    /** One knob: its config name, the clients it can act on, and the era it speaks for. */
    public record Knob(@NotNull String name, @NotNull ClientRange applies, @NotNull String note) {}

    private static final Map<String, Knob> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, ClientRange applies, String note) {
        BY_NAME.put(name, new Knob(name, applies, note));
    }

    static {
        // client-shaped: what the CLIENT draws or predicts, so off-era clients cannot take them
        add("disabledPoses", ClientRange.MODERN, "poses are a 1.9+ concept; 1.8 has none to disable");
        add("suppressSwim", ClientRange.MODERN, "the swim pose a 1.8 client never enters");
        add("swimBlindnessTicks", ClientRange.MODERN, "the BLINDNESS lever's refresh, with suppressSwim");
        add("restrictSwimSpeed", ClientRange.MODERN, "dampens the 1.9+ swim sprint; 1.8 never had it");
        add("swimFactor", ClientRange.MODERN, "with restrictSwimSpeed");
        add("swimVerticalFactor", ClientRange.MODERN, "with restrictSwimSpeed");
        add("attackHitboxMargin", ClientRange.MODERN, "the melee box margin; the server pads by it from 1.9 up, but only a 1.21.11 client can be STAMPED with it - see CompatState.stampsAttackRange");
        add("fistRayHits", ClientRange.MODERN, "the empty-hand half of the stamped attack box");
        add("suppressThrowSwing", ClientRange.MODERN, "the throwable reskin that hides a 1.9+ swing");
        add("swordBlockingPose", ClientRange.from(ClientVersion.BLOCKS_ATTACKS_PROTOCOL), "1.8 blocks natively; this restyles the use pose by stamping blocks_attacks, which only exists from 1.21.5");
        add("disableElytraFlight", ClientRange.MODERN, "the glider strip; a 1.8 client cannot glide");
        add("blockPlaceReach", ClientRange.MODERN, "only a modern survival client can sneak-bridge past 1.8 reach");
        add("legacySelfPlace", ClientRange.LEGACY, "stairs into a legacy placer's own body, as on Paper; nothing else lands there");

        // server-shaped: an era's mechanics, applied to every client on the scope
        add("legacyHitbox", ClientRange.ANY, "1.8 server hitbox/eye, no crouch shrink - the server's model, for every client");
        add("restrictMovement", ClientRange.ANY, "the movement enforcer as a whole");
        add("disableOffhand", ClientRange.ANY, "no offhand, 1.8 mechanics for everyone");
        add("restrictSprintSneak", ClientRange.ANY, "1.8 sprint rules");
        add("restrictSprintUse", ClientRange.ANY, "1.8 sprint rules");
        add("resetSprintOnSpawn", ClientRange.ANY, "1.8 clears sprint on respawn");
        add("attackReach", ClientRange.ANY, "the server's reach, not the client's view of it");
        add("oldPlacement", ClientRange.ANY, "1.8 placement rules, server-side");
        add("removeAttackCooldown", ClientRange.ANY, "1.8 combat for everyone - a modern client on this preset loses the cooldown too");
        add("removeUseCooldowns", ClientRange.ANY, "1.8 use timing for everyone");
        add("legacyFluids", ClientRange.ANY, "1.8 fluid movement, server-side");
        add("hookPredictionEscort", ClientRange.ANY, "the hook's own prediction escort");
        add("oldFlight", ClientRange.ANY, "1.8 flight, server-side");
        add("leftClickItemUsage", ClientRange.ANY, "1.8 left-click use, server-side");
        add("disableAutoSneak", ClientRange.ANY, "no 1.9+ auto-sneak, server-side");
        add("oldPhysics", ClientRange.ANY, "1.8 physics, server-side");
        add("oldMomentum", ClientRange.ANY, "1.8 momentum, server-side");
        add("disableBedBounce", ClientRange.ANY, "no 1.9+ bed bounce");
        add("disableHoneyPhysics", ClientRange.ANY, "no honey slowdown");
        add("disableBubbleColumn", ClientRange.ANY, "no bubble columns");
        add("disableEntityPush", ClientRange.ANY, "no entity push");

        // the Animatium handshake decides these, not the protocol
        add("nativeShortVelocity", ClientRange.ANY, "gated by the Animatium handshake, not the client's version");
        add("animatiumFeatures", ClientRange.ANY, "gated by the Animatium handshake, not the client's version");
        add("animatiumDebug", ClientRange.ANY, "gated by the Animatium handshake, not the client's version");
    }

    private CompatCatalog() {}

    public static @NotNull List<Knob> knobs() {
        return List.copyOf(BY_NAME.values());
    }

    public static @Nullable Knob of(@NotNull String name) {
        return BY_NAME.get(name);
    }

    /** Where {@code name} may act; an unlisted knob is {@link ClientRange#ANY}, so a new one is never silently gated off. */
    public static @NotNull ClientRange applies(@NotNull String name) {
        Knob knob = BY_NAME.get(name);
        return knob != null ? knob.applies() : ClientRange.ANY;
    }

    /** One line per knob: {@code name  range  note}. */
    public static @NotNull List<String> describe() {
        return BY_NAME.values().stream().map(k -> k.name() + "  [" + k.applies() + "]  " + k.note()).toList();
    }
}
