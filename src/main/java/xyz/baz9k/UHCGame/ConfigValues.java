package xyz.baz9k.UHCGame;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

import org.bukkit.configuration.file.FileConfiguration;

import xyz.baz9k.UHCGame.util.Path;

public class ConfigValues {
    private final UHCGamePlugin plugin;
    private final FileConfiguration cfg;

    public ConfigValues(UHCGamePlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public Object get(String path) {
        return cfg.get(path);
    }

    /**
     * @param wbName name of world border to get diameter of
     * @return the diameter of the specified world border
     */
    public OptionalDouble wbDiameter(String wbName) {
        String path = Path.join("wb_size", wbName);
        if (cfg.isDouble(path)) {
            return OptionalDouble.of(cfg.getDouble(path));
        }
        return OptionalDouble.empty();
    }

    /**
     * @param wbName name of world border to get diameter of
     * @param def default value if there's no valid config present
     * @return the diameter of the specified world border
     */
    public double wbDiameter(String wbName, double def) {
        return cfg.getDouble(Path.join("wb_size", wbName), def);
    }

    /**
     * @param cfgStage name of stage (as it is labeled in config)
     * @return the duration of the stage
     */
    public Optional<Duration> stageDuration(String cfgStage) {
        String path = Path.join("intervals", cfgStage);
        if (cfg.isInt(path)) {
            var dur = Duration.ofSeconds(cfg.getInt(path));
            return Optional.of(dur);
        }
        return Optional.empty();
    }

    /// GLOBAL ///

    /**
     * @return if wither bonus round is enabled
     */
    public boolean witherBonus() {
        return cfg.getBoolean("global.wither_bonus");
    }
    
    /**
     * @return if players spawn in the nether
     */
    public boolean netherSpawn() {
        return cfg.getBoolean("global.nether_spawn");
    }

    /**
     * @return
     * <p> 0: 05:00 per cycle
     * <p> 1: 10:00 per cycle
     * <p> 2: 20:00 per cycle
     * <p> 3: Always Day
     * <p> 4: Always Night
     */
    public int dnCycle() {
        return cfg.getInt("global.dn_cycle");
    }

    /**
     * @return
     * <p> 0: Spread players by teams
     * <p> 1: Spread players individually
     */
    public int spreadPlayersMethod() {
        return cfg.getInt("global.spreadplayers");
    }

    /// TEAMS ///

    /**
     * @return
     * <p>0: Display all teams
     * <p>1: Display only your team
     * <p>2: Do not display teams
     */
    public int hideTeams() {
        return cfg.getInt("teams.hide_teams");
    }

    /**
     * @return whether friendly fire is enabled or not
     */
    public boolean allowFriendlyFire() {
        return cfg.getBoolean("team.friendly_fire");
    }

    public record BossMode(boolean enabled, int nPlayers, int bossHealth) {
        public BossMode {
            if (!enabled) {
                nPlayers = 0;
                bossHealth = 0;
            }
        }
        public static BossMode disabled() { return new BossMode(false, 0, 0); };
    };
    
    /**
     * @return a record of all information relating to boss mode.
     * <p> If boss mode is enabled
     * <p> If enabled, the number of players in the boss team (0 if disabled)
     * <p> If enabled, the health of players in boss team (0 if disabled)
     */
    public BossMode bossMode() {
        int bossN = cfg.getInt("team.boss_team");
        
        int normalHealth = maxHealth();
        int normalN = plugin.getTeamManager().getCombatantsOnTeam(2).size();
        
        int bossHealth = bossN > 0 ? normalHealth * normalN / bossN : 0;
        return new BossMode(bossN > 0, bossN, bossHealth);
    }

    /**
     * @return if sardines is enabled
     */
    public boolean sardines() {
        return cfg.getBoolean("team.sardines");
    }

    /// PLAYER ///

    /**
     * @return amount of health to assign (based on player.max_health)
     */
    public int maxHealth() {
        return new int[]{10, 20, 40, 60}[cfg.getInt("player.max_health")];
    }

    /**
     * @return speed of players (based on player.mv_speed)
     */
    public double movementSpeed() {
        return new double[]{0.5,1,2,3}[cfg.getInt("player.mv_speed")];
    }

    /**
     * @return length of time before grace period ends, or empty if grace period is not enabled
     */
    public Optional<Duration> gracePeriod() {
        int grace = cfg.getInt("player.grace_period");

        if (grace < 0) return Optional.empty();
        return Optional.of(Duration.ofSeconds(grace));
    }

    /**
     * @return length of time before final heal occurs, or empty if final heal is not enabled
     */
    public Optional<Duration> finalHealPeriod() {
        int fh = cfg.getInt("player.final_heal");

        if (fh < 0) return Optional.empty();
        return Optional.of(Duration.ofSeconds(fh));
    }

    /**
     * @return if natural regen is enabled
     */
    public boolean naturalRegen() {
        return cfg.getBoolean("player.natural_regen");
    }
}
