package io.github.term4.polyp.platform;

import io.github.term4.polyp.Polyp;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONE lib scoreboard team ({@code polyp_lib}). Client-side team membership is exclusive - a later add to another team
 * silently moves the entity off the previous one - so every team-borne fix shares this roster; separate lib teams would
 * erase each other's members on the client. Members = the union of per-feature enrollments. Cosmetically neutral: no
 * color/prefix, friendly fire off ({@link Reason#ARROW_VISIBILITY} requires it; harmless otherwise - server-side damage
 * ignores teams).
 *
 * <p>{@link Reason#NO_PUSH}: entity pushback is CLIENT-predicted, and the only vanilla-wire off switch is
 * {@code collisionRule NEVER}. Players only - the client computes every push against its LOCAL player
 * ({@code EntitySelector.pushableBy}), so the player's own NEVER membership blocks pushes both ways against anything;
 * mobs never need the team. The rule is NEVER while anyone is enrolled for it, ALWAYS otherwise (an arrow-vis-only
 * server keeps vanilla pushes).
 *
 * <p>An app that runs its own scoreboard teams (nametag colors, spectator groups) must keep players off this roster
 * (disable the knobs) and set {@code collisionRule NEVER} / friendly-fire flags on ITS teams instead. Scoreboard teams
 * cannot express per-pair behavior at all, only partitions - see the compat docs.
 */
public final class SharedTeam {

    /** Why a player is on the roster; membership is the union of reasons. */
    public enum Reason {
        /** 1.8 arrow-visibility fix: shooter and target must share a friendly-fire-off team. */
        ARROW_VISIBILITY,
        /** {@code CompatConfig.disableEntityPush}: collision rule NEVER while anyone is enrolled for this. */
        NO_PUSH,
        /** A game's tab keeps a spectator on the roster: both eras' tab comparators weigh the TEAM name before the
         *  profile name, so a teamless spectator sorts AHEAD of players who share this team. On it, the team key
         *  ties and their sort name decides. Set by the game, untouched by the compat re-evaluation on spawn. */
        TAB_ORDER
    }

    private static final String TEAM_NAME = "polyp_lib"; // <=16 chars (the 1.8 wire limit)

    private static Team team;
    private static final Map<String, EnumSet<Reason>> roster = new HashMap<>();
    // username -> the profile name their 1.8 tab entry carries (a game's sort name), while it differs
    private static final Map<String, String> aliases = new HashMap<>();

    private SharedTeam() {}

    /** Installs the disconnect cleanup. Called by {@code Polyp.init()}. */
    public static void install(Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:shared-team", EventFilter.PLAYER);
        node.addListener(PlayerDisconnectEvent.class, e -> onDisconnect(e.getPlayer()));
        polyp.install(node);
    }

    /** Enrolls or withdraws {@code player} for {@code reason}; each feature calls this on its own config re-evaluation. */
    public static synchronized void set(@NotNull Player player, @NotNull Reason reason, boolean on) {
        String name = player.getUsername();
        EnumSet<Reason> reasons = roster.get(name);
        if (on) {
            if (reasons == null) roster.put(name, reasons = EnumSet.noneOf(Reason.class));
            if (!reasons.add(reason)) return;
        } else {
            if (reasons == null || !reasons.remove(reason)) return;
            if (reasons.isEmpty()) roster.remove(name);
        }
        sync(name);
        if (team != null) refresh(player);
    }

    // a joiner enrols at spawn, before broadcasts reach them: the play-init CREATE they already hold carries the
    // previous rule and roster, so they get the current ones directly
    private static void refresh(Player player) {
        player.sendPacket(new TeamsPacket(TEAM_NAME, new TeamsPacket.UpdateTeamAction(new TeamsPacket.Settings(
                team.getTeamDisplayName(), team.getPrefix(), team.getSuffix(), team.getNameTagVisibility(),
                team.getCollisionRule(), team.getTeamColor(), team.getFriendlyFlags()))));
        if (!team.getMembers().isEmpty()) {
            player.sendPacket(new TeamsPacket(TEAM_NAME, new TeamsPacket.AddEntitiesToTeamAction(List.copyOf(team.getMembers()))));
        }
    }

    /**
     * The profile name {@code player}'s 1.8 tab entry carries when a game gives them a sort name; {@code null}
     * clears it. A 1.8 client resolves a tab entry's team ({@code NetworkPlayerInfo.getPlayerTeam}) AND a player
     * entity's team ({@code EntityPlayer.getTeam}, the gate on {@code canAttackPlayer}) BY THAT NAME. A renamed
     * member must therefore sit on the roster under the alias too, or that client sees them teamless: the tab
     * sorts them ahead of their teammates, and the arrow-visibility fix silently stops holding for them.
     */
    public static synchronized void alias(@NotNull Player player, @Nullable String alias) {
        String name = player.getUsername();
        String old = alias == null ? aliases.remove(name) : aliases.put(name, alias);
        if (old != null && !old.equals(alias) && team != null && team.getMembers().contains(old)) team.removeMember(old);
        sync(name);
        if (team != null) refresh(player);
    }

    private static synchronized void onDisconnect(Player player) {
        String name = player.getUsername();
        if (roster.remove(name) != null) sync(name); // with the alias still mapped, so it leaves the team too
        aliases.remove(name);
    }

    /** Reconciles the Minestom team with the roster: membership for {@code name}, collision rule for everyone. */
    private static void sync(String name) {
        boolean member = roster.containsKey(name);
        if (team == null) {
            if (!member) return;
            // default team flags = friendly fire off; registration broadcasts to everyone online, and Minestom
            // sends all registered teams (with members) to each later joiner in the play init
            team = MinecraftServer.getTeamManager().createBuilder(TEAM_NAME).collisionRule(wantedRule()).build();
        }
        CollisionRule rule = wantedRule();
        if (team.getCollisionRule() != rule) team.updateCollisionRule(rule);
        String alias = aliases.get(name);
        for (String wire : alias == null ? List.of(name) : List.of(name, alias)) {
            if (member && !team.getMembers().contains(wire)) team.addMember(wire);
            else if (!member && team.getMembers().contains(wire)) team.removeMember(wire);
        }
    }

    private static CollisionRule wantedRule() {
        for (EnumSet<Reason> reasons : roster.values()) {
            if (reasons.contains(Reason.NO_PUSH)) return CollisionRule.NEVER;
        }
        return CollisionRule.ALWAYS;
    }
}
