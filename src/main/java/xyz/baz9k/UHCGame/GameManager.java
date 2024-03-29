package xyz.baz9k.UHCGame;

import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import xyz.baz9k.UHCGame.exception.UHCCheckFailException;
import xyz.baz9k.UHCGame.exception.UHCException;
import xyz.baz9k.UHCGame.util.*;
import xyz.baz9k.UHCGame.util.drops.BlockDropTransformer;
import xyz.baz9k.UHCGame.util.drops.LeafDropProducer;
import xyz.baz9k.UHCGame.util.tag.BooleanTagType;

import java.time.*;
import java.util.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

import com.destroystokyo.paper.MaterialTags;

import static xyz.baz9k.UHCGame.util.Utils.*;
import static xyz.baz9k.UHCGame.util.ComponentUtils.*;

public class GameManager implements Listener {
    private final UHCGamePlugin plugin;

    private TeamManager teamManager;
    private HUDManager hudManager;
    private BossbarManager bbManager;
    private WorldManager worldManager;
    private Recipes recipes;
    private BukkitTask tick;
    
    private final HashMap<UUID, Component> prevDisplayNames = new HashMap<>();
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    
    private GameStage stage = GameStage.NOT_IN_GAME;
    private Kit kit = Kit.none();

    private Optional<Instant> startTime = Optional.empty();
    private Optional<Instant> lastStageInstant = Optional.empty();

    private static final TreeSet<TimedEvent> timedEvents = new TreeSet<>();
    public record TimedEvent(Instant when, Runnable action) implements Comparable<TimedEvent> {

        @Override
        // according to TreeSet java docs, this violates the general contract of Set...
        // but then says it's not a problem so...
        public int compareTo(TimedEvent o) {
            return this.when.compareTo(o.when);
        }

        public boolean cancel() {
            return timedEvents.remove(this);
        }

    }
    private boolean win = false;

    public GameManager(UHCGamePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * After all managers are initialized, this function is run to give gameManager references to all other managers.
     */
    public void loadManagerRefs() {
        teamManager = plugin.getTeamManager();
        hudManager = plugin.getHUDManager();
        bbManager = plugin.getBossbarManager();
        worldManager = plugin.getWorldManager();
        recipes = plugin.getRecipes();
    }

    private enum GameInitFailure {
        TEAM_UNASSIGNED      (new Key("err.team.must_assigned")),
        WORLDS_NOT_REGENED   (new Key("err.world.must_regened"), new Key("err.world.must_regened_short")),
        GAME_NOT_STARTED     (new Key("err.not_started")       ),
        GAME_ALREADY_STARTED (new Key("err.already_started")   );

        private final Key errKey;
        private final Key panelErrKey;
        GameInitFailure(Key errKey) {
            this(errKey, errKey);
        }
        GameInitFailure(Key errKey, Key panelErrKey) {
            this.errKey = errKey;
            this.panelErrKey = panelErrKey;
        }

        public UHCCheckFailException exception() {
            return new UHCCheckFailException(errKey);
        }
        public String panelErr() {
            return renderString(panelErrKey.trans());
        }
    }

    private List<GameInitFailure> checkStart() {
        worldManager = plugin.getWorldManager();
        teamManager = plugin.getTeamManager();
        
        var fails = new ArrayList<GameInitFailure>();

        if (hasUHCStarted()) fails.add(GameInitFailure.GAME_ALREADY_STARTED);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.getPlayerState(p) == PlayerState.COMBATANT_UNASSIGNED) {
                fails.add(GameInitFailure.TEAM_UNASSIGNED);
                break;
            }
        }

        // if (!worldManager.worldsRegened()) {
        //     fails.add(GameInitFailure.WORLDS_NOT_REGENED);
        // }

        return Collections.unmodifiableList(fails);
    }

    private List<GameInitFailure> checkEnd() {
        var fails = new ArrayList<GameInitFailure>();

        if (!hasUHCStarted()) fails.add(GameInitFailure.GAME_NOT_STARTED);

        return Collections.unmodifiableList(fails);
    }

    public List<String> checkStartPanel() {
        return checkStart().stream()
            .map(GameInitFailure::panelErr)
            .toList();
    }
    public List<String> checkEndPanel() {
        return checkEnd().stream()
            .map(GameInitFailure::panelErr)
            .toList();
    }

    private void runEventWithChecks(String eventKey, Runnable event, Supplier<List<GameInitFailure>> checks, boolean skipChecks) throws UHCCheckFailException {
        Key EVENT_TRY       = new Key("debug.%s.try", eventKey),
            EVENT_FORCE_TRY = new Key("debug.%s.force", eventKey),
            EVENT_COMPLETE  = new Key("debug.%s.complete", eventKey),
            EVENT_FAIL      = new Key("debug.%s.fail", eventKey);
        
        var prevStage = stage;

        Debug.printDebug(EVENT_TRY.trans());
        if (!skipChecks) {
            var fails = checks.get();
            if (fails.size() != 0) {
                throw fails.get(0).exception();
            }
        } else {
            Debug.printDebug(EVENT_FORCE_TRY.trans());
        }

        try {
            event.run();
            Debug.printDebug(EVENT_COMPLETE.trans());
        } catch (Exception e) {
            setStage(prevStage);
            Debug.printDebug(EVENT_FAIL.trans());
            Debug.printError(e);
        }
    }
    /**
     * Starts UHC.
     * <p>
     * Accessible through /uhc start or /uhc start force.
     * <p>
     * /uhc start: Checks that teams are assigned, worlds have regenerated, and game has not started
     * <p>
     * /uhc start force: Skips checks
     * @param skipChecks If true, all checks are ignored.
     */
    public void startUHC(boolean skipChecks) throws UHCCheckFailException {
        runEventWithChecks("start", this::_startUHC, this::checkStart, skipChecks);
    }

    /**
     * Ends UHC.
     * <p>
     * Accessible through /uhc end or /uhc end force.
     * <p>
     * /uhc end: Checks that the game has started
     * <p>
     * /uhc end force: Forcibly starts game
     * @param skipChecks If true, started game checks are ignored.
     */
    public void endUHC(boolean skipChecks) throws UHCCheckFailException {
        runEventWithChecks("end", this::_endUHC, this::checkEnd, skipChecks);
    }

    private void _startUHC() {
        worldManager.initWorlds();

        // do spreadplayers
        Debug.printDebug(new Key("debug.spreadplayers.start").trans());

        //    | # Groups | Min   | Max  |
        //    |----------|-------|------|
        //    |        1 | 692.8 | 1200 |
        //    |        2 | 489.9 | 1200 |
        //    |        3 | 400.0 | 1200 |
        //    |        4 | 346.4 | 1200 |
        //    |        5 | 309.8 | 1200 |
        //    |        6 | 282.8 | 1200 |
        //    |        7 | 261.9 | 1200 |
        //    |        8 | 244.9 | 1200 |
        //    |        9 | 230.9 | 1200 |
        //    |       10 | 219.1 | 1200 |
        //    |       11 | 208.9 | 1200 |
        //    |       12 | 200.0 | 1200 |
        //    |       13 | 192.2 | 1200 |
        //    |       14 | 185.2 | 1200 |
        //    |       15 | 178.9 | 1200 |

        // btw if you're reading this,
        // i sampled the average # of points generated at ratios of min/max
        // x: min / max
        // y: number of points generated
        // y = .65 * (1/x)^2

        // the min value calculation here is based on that 
        // but the constant has been adjusted to give margin of error
        // (in case SP produces less points than average)

        int sp = plugin.configValues().spreadPlayersMethod();
        // 0 = by teams
        // 1 = individually
        double max = GameStage.WB_STILL.wbDiameter();
        Location defaultLoc = worldManager.gameSpawn();
        Location center = worldManager.getCenter();
        var spreadPlayers = plugin.spreadPlayers();
        switch (sp) {
            case 0 -> {
                double min = max / Math.sqrt(3 * teamManager.getNumSpreadGroups());
                spreadPlayers.random(SpreadPlayersManager.BY_TEAMS(defaultLoc), center, max, min);
            }
            case 1 -> {
                double min = max / Math.sqrt(3 * teamManager.getCombatants().online().size());
                spreadPlayers.random(SpreadPlayersManager.BY_PLAYERS(defaultLoc), center, max, min);
                
            }
        }
        
        Debug.printDebug(new Key("debug.spreadplayers.end").trans());
        // unload world
        plugin.getMVWorldManager().unloadWorld("lobby", true);

        setStage(GameStage.nth(0));
        startTime = lastStageInstant = Optional.of(Instant.now());
        
        win = false;
        kills.clear();
        timedEvents.clear();
        // register event for when grace period ends
        plugin.configValues().gracePeriod().ifPresent(d -> {
            Instant end = startTime.get().plus(d);
            registerEvent(end, () -> {
                // grace period does its check via inGracePeriod, so nothing else needs to be done
                GameStage.sendMessageAsBoxless(Bukkit.getServer(), new Key("chat.grace.end").trans());
            });
        });

        // register event for when final heal hits
        plugin.configValues().finalHealPeriod().ifPresent(d -> {
            Instant end = startTime.get().plus(d);
            registerEvent(end, () -> {
                GameStage.sendMessageAsBoxless(Bukkit.getServer(), new Key("chat.final_heal").trans());
                for (Player p : teamManager.getAliveCombatants().online()) {
                    p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
                }
            });
        });
        

        for (Player p : Bukkit.getOnlinePlayers()) {
            prepareToGame(p, true);
        }

        // start ticking
        startTick();
    }

    private void _endUHC() {
        setStage(GameStage.NOT_IN_GAME);
        for (Player p : Bukkit.getOnlinePlayers()) {
            prepareToLobby(p, true);
        }

        // global stuff, affects all players incl offline ones
        teamManager.resetAllPlayers();
        bbManager.disable(Bukkit.getServer());
        hudManager.cleanup();
        kills.clear();
        endTick();

    }

    public boolean hasUHCStarted() {
        return stage != GameStage.NOT_IN_GAME;
    }

    public void requireStarted() throws UHCException {
        if (!hasUHCStarted()) {
            throw new UHCException(new Key("err.not_started"));
        }
    }

    public void requireNotStarted() throws UHCException {
        if (hasUHCStarted()) {
            throw new UHCException(new Key("err.already_started"));
        }
    }

    public <X extends Throwable> void requireStarted(Class<X> exc) throws X {
        if (!hasUHCStarted()) {
            throw new Key("err.not_started").transErr(exc);
        }
    }

    public <X extends Throwable> void requireNotStarted(Class<X> exc) throws X {
        if (hasUHCStarted()) {
            throw new Key("err.already_started").transErr(exc);
        }
    }

    private void startTick() {
        tick = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!hasUHCStarted()) return;
    
            bbManager.tick();
            
            if (isStageComplete()) {
                incrementStage();
            }
            
            int dnCycle = plugin.configValues().dnCycle();
            // 0: 05:00 per cycle
            // 1: 10:00 per cycle
            // 2: 20:00 per cycle
            // 3: Always Day
            // 4: Always Night
            int timeIncr = switch (dnCycle) {
                case 0 -> 4;
                case 1 -> 2;
                default -> 0;
            };
            if (timeIncr != 0) {
                World w = worldManager.getGameWorld(0);
                w.setTime(w.getTime() + timeIncr);
            }

            // run thru all the events that have been registered and whose time have passed
            if (!timedEvents.isEmpty()) {
                TimedEvent e = timedEvents.first();
                while (e.when().isBefore(Instant.now())) {
                    timedEvents.pollFirst();
                    e.action.run();
                    e = timedEvents.first();
                }
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                hudManager.updateElapsedTimeHUD(p);
                hudManager.updateWBHUD(p);
            }
        }, 0L, 1L);
    }

    private void endTick() {
        if (tick != null) tick.cancel();
    }

    /**
     * Resets a player's statuses (health, food, sat, xp, etc.)
     * @param p the player
     */
    public void resetStatuses(@NotNull Player p) {
        // fully heal, adequately saturate, remove XP
        p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        p.setFoodLevel(20);
        p.setSaturation(5.0f);
        p.setLevel(0);
        p.setExp(0);
        p.getInventory().clear();
        
        // clear all potion effects
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }

        // clear all advancements
        Iterable<Advancement> advancements = Bukkit::advancementIterator;
        for (Advancement a : advancements) {
            var progress = p.getAdvancementProgress(a);
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    /**
     * @return the {@link Duration} since the game has started.
     */
    public Optional<Duration> getElapsedTime() {
        return startTime.map(start ->
            Duration.between(start, Instant.now())
        );
    }

    /**
     * @return the current stage of the game.
     */
    public GameStage getStage() {
        return stage;
    }

    public void incrementStage() {
        setStage(stage.next());
    }

    public void setStage(GameStage stage) {
        if (stage == null) return;
        this.stage = stage;
        updateStage();
    }

    private void updateStage() {
        if (!hasUHCStarted()) return;
        lastStageInstant = Optional.of(Instant.now());
        bbManager.updateBossbarStage();

        stage.sendMessage();
        stage.applyWBSize(worldManager.getGameWorlds());

        if (stage == GameStage.WB_STOP) {
            worldManager.forEachWorld((w, wm) -> {
                wm.purgeWorld(w);
                w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            });
        }

        // deathmatch
        if (isDeathmatch()) {
            World w = worldManager.getMainWorld();

            int radius = (int) GameStage.DEATHMATCH.wbRadius();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    w.getBlockAt(x, w.getMaxHeight() - 1, z).setType(Material.AIR);
                    w.getBlockAt(x, w.getMaxHeight() - 2, z).setType(Material.BARRIER);
                }
            }

            for (int x = -radius; x <= radius; x++) {
                w.getBlockAt(x, w.getMaxHeight() - 1, -radius - 1).setType(Material.BARRIER);
                w.getBlockAt(x, w.getMaxHeight() - 1, radius + 1).setType(Material.BARRIER);
            }

            for (int z = -radius; z <= radius; z++) {
                w.getBlockAt(-radius - 1, w.getMaxHeight() - 1, z).setType(Material.BARRIER);
                w.getBlockAt(radius + 1, w.getMaxHeight() - 1, z).setType(Material.BARRIER);
            }

            for (Player p : teamManager.getCombatants().online()) {
                p.addPotionEffects(Arrays.asList(
                    PotType.RESISTANCE.createEffect(10 * 20, 10),
                    PotType.SLOWNESS.createEffect(10 * 20, 10),
                    PotType.JUMP_BOOST.createEffect(10 * 20, 128),
                    PotType.BLINDNESS.createEffect(10 * 20, 10)
                ));
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(worldManager.getHighCenter());
            }

            double rad = GameStage.DEATHMATCH.wbRadius() - 1;
            plugin.spreadPlayers().rootsOfUnity(SpreadPlayersManager.BY_TEAMS(worldManager.getHighCenter()), worldManager.getCenter(), rad);

        }

    }

    /**
     * @return the {@link Duration} that the current stage lasts.
     */
    public @NotNull Duration getStageDuration() {
        requireStarted(IllegalStateException.class);
        return stage.duration();
    }

    /**
     * @return the {@link Duration} until the current stage ends.
     */
    public Optional<Duration> getRemainingStageDuration() {
        requireStarted(IllegalStateException.class);
        Duration stageDur = getStageDuration();
        if (isDeathmatch()) return Optional.of(stageDur); // if deathmatch, just return ∞
        return lastStageInstant.map(instant -> 
            Duration.between(Instant.now(), instant.plus(stageDur))
        );
    }

    /**
     * @return if deathmatch (last stage) has started.
     */
    public boolean isDeathmatch() {
        return stage == GameStage.DEATHMATCH;
    }

    /**
     * @return if the stage has completed and needs to be incremented.
     */
    public boolean isStageComplete() {
        if (win) return false;
        if (isDeathmatch()) return false;
        return lastStageInstant.map(instant -> {
                var end = instant.plus(stage.duration());
                return !end.isAfter(Instant.now());
            })
            .orElse(false);
    }

    /**
     * Returns the number of kills that this combatant has dealt.
     * @param p
     * @return the number of kills in an OptionalInt. 
     * <p>
     * If the player is not registered in the kills map (i.e. they're a spec), return empty OptionalInt.
     */
    public OptionalInt getKills(@NotNull Player p) {
        if (!teamManager.isSpectator(p)) {
            return OptionalInt.of(kills.computeIfAbsent(p.getUniqueId(), uuid -> 0));
        }
        return OptionalInt.empty();
    }
    
    /**
     * @return the kit players spawn with
     */
    public Kit kit() {
        return this.kit;
    }

    /**
     * Set the kit for players to spawn with
     * @param kit the kit
     */
    public void kit(Kit kit) {
        this.kit = kit;
    }

    public TimedEvent registerEvent(Instant when, Runnable action) {
        TimedEvent e = new TimedEvent(when, action);
        boolean succ = timedEvents.add(e);

        if (succ) return e;
        throw new IllegalArgumentException("uh oh you bozo don't copy your arguments");
    }

    public boolean inGracePeriod() {
        Optional<Duration> grace = plugin.configValues().gracePeriod();
        Optional<Duration> elapsedTime = getElapsedTime();

        if (elapsedTime.isPresent() && grace.isPresent()) {
            return elapsedTime.get().compareTo(grace.get()) <= 0;
        }
        return false;
    }

    public void respawnPlayer(Player p, Location loc) throws UHCException {
        TeamManager tm = plugin.getTeamManager();
        if (tm.isSpectator(p)) {
            throw new UHCException(new Key("cmd.respawn.fail.spectator"), p.getName());
        }

        p.teleport(loc);
        tm.setCombatantAliveStatus(p, true);
        p.setGameMode(GameMode.SURVIVAL);
        
        // clear all potion effects
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
    }

    private Component includeGameTimestamp(Component c) {
        if (c == null) return null;
        String timeStr = getLongTimeString(getElapsedTime(), "?");
        return Component.text(String.format("[%s]", timeStr))
               .append(Component.space())
               .append(c);
    }

    private void trySendWinMessage() {
        // check if qualify for win message
        if (win) return; // if win message already triggered, don't trigger it again

        var aliveGroups = teamManager.getAliveGroups();
        if (aliveGroups.length != 1) return; // only 1 group

        win = true;
        var winner = aliveGroups[0];
        Component winName = winner.getName();
        var winBukkitClr = TeamDisplay.getBukkitColor(PlayerState.COMBATANT_ALIVE, winner.team());

        Component winMsg = new Key("win").trans(winName)
            .style(noDeco(NamedTextColor.WHITE));
        winMsg = includeGameTimestamp(winMsg);

        // this msg should be displayed after player death
        delayedMessage(winMsg, 1);
        
        // fireworks
        var fwe = FireworkEffect.builder()
            .withColor(winBukkitClr, org.bukkit.Color.WHITE)
            .with(FireworkEffect.Type.BURST)
            .withTrail()
            .build();
        
        boolean isWildcard = winner.team() == 0;
        List<Player> winners;
        if (isWildcard) {
            Player p = Bukkit.getPlayer(winner.uuid());
            if (p != null) winners = List.of(p);
            else winners = List.of();
        } else {
            winners = List.copyOf(teamManager.getCombatantsOnTeam(winner.team()).online());
        }

        for (Player p : winners) {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(fwe);
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        }

        // wither bonus round
        boolean wbr = plugin.configValues().witherBonus();
        
        if (!wbr) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int nWinners = winners.size();
            Location spawnLoc;

            if (nWinners == 0) {
                spawnLoc = worldManager.getCenter();
            } else {
                int i = new Random().nextInt(winners.size());
                spawnLoc = winners.get(i).getLocation();
            }

            spawnLoc = spawnLoc.add(0, 10, 0);
            spawnLoc.getWorld().spawn(spawnLoc, Wither.class);
        }, 20 * 10);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        teamManager.addPlayer(p, hasUHCStarted());

        // set this perm for crossdim travel
        p.addAttachment(plugin, "mv.bypass.gamemode.*", true);
        p.recalculatePermissions();

        if (hasUHCStarted()) {
            prepareToGame(p, false);
        } else {
            prepareToLobby(p, false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!hasUHCStarted()) return;
        Player dead = e.getEntity();
        
        e.deathMessage(includeGameTimestamp(e.deathMessage()));

        // drop bonus items
        plugin.configValues().playerDrops(dead)
            .ifPresent(s -> {
                dead.getWorld().dropItem(dead.getLocation(), s);
            });

        dead.setGameMode(GameMode.SPECTATOR);
        if (teamManager.getPlayerState(dead) == PlayerState.COMBATANT_ALIVE) {
            teamManager.setCombatantAliveStatus(dead, false);

            // check team death
            int t = teamManager.getTeam(dead);
            if (teamManager.isTeamEliminated(t)) {
                Component teamElimMsg = new Key("eliminated").trans(TeamDisplay.getName(PlayerState.COMBATANT_ALIVE, t))
                    .style(noDeco(NamedTextColor.WHITE));
                teamElimMsg = includeGameTimestamp(teamElimMsg);
                // this msg should be displayed after player death
                delayedMessage(teamElimMsg, 1);
            }

            // check win condition
            trySendWinMessage();
        }
        
        // set bed spawn
        Location newSpawn = dead.getLocation();
        if (newSpawn.getY() < newSpawn.getWorld().getMinHeight()) {
            newSpawn = worldManager.gameSpawn();
        }
        dead.setBedSpawnLocation(newSpawn, true);

        Player killer = dead.getKiller();
        if (killer != null) {
            OptionalInt k = getKills(killer);
            if (k.isPresent()) {
                this.kills.put(killer.getUniqueId(), k.orElseThrow() + 1);
                hudManager.updateKillsHUD(killer);
            }
        }
    }

    @EventHandler
    public void onPlayerFight(EntityDamageByEntityEvent e) {
        if (!hasUHCStarted()) return;
        if (e.getEntity() instanceof Player target) {
            if (e.getDamager() instanceof Player damager) {
                // grace period
                if (inGracePeriod()) {
                    e.setCancelled(true);
                    return;
                }

                // cancel friendly fire
                if (!plugin.configValues().allowFriendlyFire()) {
                    if (teamManager.onSameTeam(target, damager)) {
                        e.setCancelled(true);
                    }
                }

            }
        }
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent e) {
        if (!hasUHCStarted()) return;

        Player p = e.getPlayer();
        if (!teamManager.getPlayerState(p).isSpectating()) {
            e.message(includeGameTimestamp(e.message()));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        if (!hasUHCStarted()) return;

        Player p = e.getPlayer();
        if (teamManager.getPlayerState(p).isSpectating()) {
            prepareToSpectate(p);
        }
    }

    private static final BlockDropTransformer AUTO_SMELT_TRANSFORMER = new BlockDropTransformer()
        .add(Tag.IRON_ORES,   Material.RAW_IRON,   Material.IRON_INGOT)
        .add(Tag.GOLD_ORES,   Material.RAW_GOLD,   Material.GOLD_INGOT)
        .add(Tag.COPPER_ORES, Material.RAW_COPPER, Material.COPPER_INGOT);

    private static final BlockDropTransformer GRAVEL_TRANSFORMER = new BlockDropTransformer()
        .add(Material.GRAVEL, Material.GRAVEL, Material.FLINT);

    private static final Map<Material, Material> GORDON_RAMSEYS_RECIPE_BOOK = Map.ofEntries(
        Map.entry(Material.BEEF,      Material.COOKED_BEEF),
        Map.entry(Material.CHICKEN,   Material.COOKED_CHICKEN),
        // Map.entry(Material.COD,    Material.COOKED_COD), // i see a universe where someone wants to tame a cat
        Map.entry(Material.MUTTON,    Material.COOKED_MUTTON),
        Map.entry(Material.PORKCHOP,  Material.COOKED_PORKCHOP),
        Map.entry(Material.RABBIT,    Material.COOKED_RABBIT)
        // Map.entry(Material.SALMON, Material.COOKED_SALMON)
    );

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        if (!hasUHCStarted()) return;
        var cfg = plugin.configValues();

        Material blockMaterial = e.getBlockState().getType();
        List<Item> items = e.getItems();

        if (cfg.autoSmelt()) {
            for (Item it : items) {
                AUTO_SMELT_TRANSFORMER.transform(blockMaterial, it);
            }
        }
        if (cfg.alwaysFlint()) {
            for (Item it : items) {
                GRAVEL_TRANSFORMER.transform(blockMaterial, it);
            }
        }

        new LeafDropProducer(e, cfg).addDrops();
    }

    @EventHandler
    public void onMobDrop(EntityDropItemEvent e) {
        if (!hasUHCStarted()) return;
        var cfg = plugin.configValues();

        if (cfg.autoCook()) {
            ItemStack stack = e.getItemDrop().getItemStack();
            Material m = stack.getType();
    
            if (GORDON_RAMSEYS_RECIPE_BOOK.containsKey(m)) {
                stack.setType(GORDON_RAMSEYS_RECIPE_BOOK.get(m));
            }
        }
    }

    @EventHandler
    public void onLeafDecay(LeavesDecayEvent e) {
        if (!hasUHCStarted()) return;

        var cfg = plugin.configValues();
        new LeafDropProducer(e, cfg).addDrops();
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!hasUHCStarted()) return;
        var cfg = plugin.configValues();
        var hastyBoys = cfg.hastyBoys();
        var luckyBoys = cfg.luckyBoys();

        Recipe r = e.getRecipe();
        ItemStack result = r.getResult();
        if (MaterialTags.ENCHANTABLE.isTagged(result)) {
            if (hastyBoys.isPresent()) {
                result.addUnsafeEnchantments(Map.ofEntries(
                    Map.entry(Ench.EFFICIENCY, hastyBoys.getAsInt()),
                    Map.entry(Ench.UNBREAKING, 3)
                ));
            }
    
            if (luckyBoys.isPresent()) {
                result.addUnsafeEnchantment(Ench.FORTUNE, luckyBoys.getAsInt());
            }
        }

        if (recipes.isRecipeEnabled(r)) {
            e.setCurrentItem(result);
        } else {
            e.setCurrentItem(new ItemStack(Material.AIR));
            e.getWhoClicked().sendMessage(Component.text("This recipe is disabled!"));
        }
    }

    @EventHandler
    public void onConsumeFood(PlayerItemConsumeEvent e) {
        if (!hasUHCStarted()) return;

        Player p = e.getPlayer();
        ItemStack food = e.getItem();
        ItemMeta m = food.getItemMeta();
        var container = m.getPersistentDataContainer();
        // this is a golden head
        if (container.getOrDefault(new NamespacedKey(plugin, "golden_head"), new BooleanTagType(), false)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.removePotionEffect(PotType.REGENERATION);
                new PotionEffect(PotType.REGENERATION, 25 * 8, 1).apply(p);
            }, 1);
        }
    }

    private void prepareToGame(Player p, boolean onGameStart) {
        bbManager.enable(p);
        hudManager.initPlayerHUD(p);
        
        recipes.discoverFor(p);

        PlayerState s = teamManager.getPlayerState(p);
        int t = teamManager.getTeam(p);
        setDisplayName(p, TeamDisplay.prefixed(s, t, p.getName()));
        
        if (onGameStart || !worldManager.inGame(p)) {
            // if the player joins midgame and are in the lobby, then idk where to put them! put in spawn
            if (!onGameStart && !worldManager.inGame(p)) {
                p.teleport(worldManager.gameSpawn());
            }

            // handle display name
            // prevDisplayNames.put(p.getUniqueId(), p.displayName());
            
            if (teamManager.isSpectator(p)) {
                p.setGameMode(GameMode.SPECTATOR);
                prepareToSpectate(p);
                resetStatuses(p);
            } else {
                p.setGameMode(GameMode.SURVIVAL);

                var cfgValues = plugin.configValues();
                // set maximum health and movement speed according to config options
                int maxHealth = cfgValues.maxHealth();
                double mvSpeed = cfgValues.movementSpeed();
                var bossMode = cfgValues.bossMode();

                if (bossMode.enabled()) {
                    int bossMaxHealth = bossMode.bossHealth();
                    if (t == 1) {
                        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(bossMaxHealth);
                    } else {
                        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
                    }
                } else {
                    p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
                }

                p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1 * mvSpeed); // 0.1 is default value
                
                resetStatuses(p);
                kit.apply(p);
                if (cfgValues.sardines()) plugin.getSardinesManager().giveSardineIfNeedy(p);
            }


            // 60s grace period
            PotType.RESISTANCE.createEffect(60 * 20 /* ticks */, /* lvl */ 5).apply(p);
        }
    }

    private void prepareToLobby(Player p, boolean onGameEnd) {
        bbManager.disable(p);
        hudManager.prepareToLobby(p);
        if (onGameEnd || worldManager.inGame(p)) {
            worldManager.escapePlayer(p);
            resetStatuses(p);
            p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);
            p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
            
            // update display names
            UUID uuid = p.getUniqueId();
            setDisplayName(p, prevDisplayNames.get(uuid));
        }
    }

    private void prepareToSpectate(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new PotionEffect(PotType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, true, false)
                .apply(p);
        }, 1);
    }

}
