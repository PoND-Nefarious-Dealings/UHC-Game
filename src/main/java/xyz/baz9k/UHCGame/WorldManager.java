package xyz.baz9k.UHCGame;

import static xyz.baz9k.UHCGame.util.Utils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.jetbrains.annotations.NotNull;
import xyz.baz9k.UHCGame.util.Debug;
import xyz.baz9k.UHCGame.util.Point2D;

public class WorldManager implements Listener {
    private final UHCGamePlugin plugin;
    private boolean worldsRegened = false;
    private final List<String> worldNames = new ArrayList<>();
    private final Point2D center = new Point2D(0.5, 0.5);

    public WorldManager(UHCGamePlugin plugin) {
        this.plugin = plugin;

        // create MV worlds if missing
        registerWorld("game", Environment.NORMAL);
        registerWorld("game_nether", Environment.NETHER);

        plugin.getMVWorldManager().setFirstSpawnWorld(getLobbyWorld().getName());
    }

    public boolean worldsRegened() { return worldsRegened; }

    private void registerWorld(String world, Environment env) {
        boolean succ = true;
        if (Bukkit.getWorld(world) == null) {
            var wm = plugin.getMVWorldManager();
            succ = wm.addWorld(world, env, String.valueOf(new Random().nextLong()), WorldType.NORMAL, true, null);
        }

        if (succ) worldNames.add(world);
    }
    
    /**
     * @return an Array of {@link World} which the UHC uses.
     */
    public World[] getGameWorlds() {
        return worldNames.stream()
            .map(Bukkit::getWorld)
            .toArray(World[]::new);
    }

    /**
     * Get a specific game world.
     * @param i Index of world in registration
     * @return the world
     */
    public World getGameWorld(int i) {
        return Bukkit.getWorld(worldNames.get(i));
    }

    /**
     * Get the main game world (where players spawn).
     * @return the world
     */
    public World getMainWorld() {
        boolean isNether = plugin.configValues().netherSpawn();
        int worldIndex = isNether ? 1 : 0;
        return Bukkit.getWorld(worldNames.get(worldIndex));
    }

    public World getLobbyWorld() {
        World lobby = Bukkit.getWorld("lobby");
        return lobby != null ? lobby : Bukkit.getWorld("world");
    }

    public Location gameSpawn() {
        return getMainWorld().getSpawnLocation();
    }
    public Location lobbySpawn() {
        return getLobbyWorld().getSpawnLocation();
    }
    

    /**
     * On start, this function is called to prepare the worlds for play.
     */
    public void initWorlds() {
        worldsRegened = false;

        var cfg = plugin.configValues();
        int dnCycle = cfg.dnCycle();
        // 0: 05:00 per cycle
        // 1: 10:00 per cycle
        // 2: 20:00 per cycle
        // 3: Always Day
        // 4: Always Night

        for (World w : getGameWorlds()) {
            // set time to 0 and delete rain
            w.setTime(dnCycle == 4 ? 18000 : 0);
            w.setClearWeatherDuration(Integer.MAX_VALUE); // there is NO rain. Ever again. [ :( ]
            w.setDifficulty(Difficulty.HARD);

            w.getWorldBorder().setCenter(center.x(), center.z());
            w.getWorldBorder().setWarningDistance(25);
            w.getWorldBorder().setDamageBuffer(0);
            w.getWorldBorder().setDamageAmount(1);
            
            setDefaultGamerules(w);
            w.setGameRule(GameRule.NATURAL_REGENERATION, cfg.naturalRegen());
            w.setGameRule(GameRule.DROWNING_DAMAGE,      cfg.drowningDamage());
            w.setGameRule(GameRule.FALL_DAMAGE,          cfg.fallDamage());
            w.setGameRule(GameRule.FIRE_DAMAGE,          cfg.fireDamage());
            w.setGameRule(GameRule.FREEZE_DAMAGE,        cfg.freezeDamage());
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,    dnCycle == 2);
            purgeWorld(w);

            // create beacon in worlds
            int min = w.getMinHeight(),
                max = w.getMaxHeight();
                
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    w.getBlockAt(x, min, z).setType(Material.BEDROCK);
                }
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    w.getBlockAt(x, min, z).setType(Material.NETHERITE_BLOCK);
                    w.getBlockAt(x, min + 1, z).setType(Material.BEDROCK);
                }
            }
            w.getBlockAt(0, min + 1, 0).setType(Material.BEACON);
            w.getBlockAt(0, min + 2, 0).setType(Material.BEDROCK);

            for (int y = min + 3; y < max; y++) {
                w.getBlockAt(0, y, 0).setType(Material.BARRIER);
            }
        }
    }

    /**
     * Reseed worlds then mark worlds as reseeded.
     * <p>
     * Accessible through /uhc reseed
     */
    private void reseedWorld(World w, long seed) {
        plugin.getMVWorldManager().regenWorld(w.getName(), true, false, String.valueOf(seed));
    }

    /**
     * Reseed worlds then mark worlds as reseeded.
     * <p>
     * Accessible through /uhc reseed
     */
    public void reseedWorlds() {
        boolean doVerify = !plugin.configValues().netherSpawn();

        Random r = new Random();
        long l;
        do {
            l = r.nextLong();
            if (doVerify) Debug.printDebug(String.format("Checking seed %s", l));
            reseedWorld(getMainWorld(), l);
        } while(doVerify && !isGoodWorld(getMainWorld()));
        
        Debug.printDebug(String.format("Using seed %s", l));
        reseedWorlds(l, true);
    }

    /**
     * Reseed worlds then mark worlds as reseeded. <p>
     * Accessible through /uhc reseed <seed>
     * @param seed Specified seed
     */
    public void reseedWorlds(long seed, boolean ignoreOverworld) {
        World[] worlds = getGameWorlds();

        int init = ignoreOverworld ? 1 : 0;
        for (int i = init; i < worlds.length; i++) {
            reseedWorld(worlds[i], seed);
        }
        worldsRegened = true;
    }

    private static final Set<Biome> rejectedBiomes = Set.of(
        Biome.OCEAN,
        Biome.COLD_OCEAN,
        Biome.DEEP_COLD_OCEAN,
        Biome.DEEP_FROZEN_OCEAN,
        Biome.DEEP_LUKEWARM_OCEAN,
        Biome.DEEP_OCEAN,
        Biome.FROZEN_OCEAN,
        Biome.LUKEWARM_OCEAN,
        Biome.WARM_OCEAN
    );

    public boolean isGoodWorld(@NotNull World w) {
        var loc = getHighestLoc(w, 1, 1);
        Debug.printDebug(String.format("Checking %s's biome", w.getSeed()));
        Biome b = w.getBiome(1, (int) loc.getY() - 1, 1);
        Debug.printDebug(String.format("Checked %s's biome, it's %s", w.getSeed(), b));
        
        return !rejectedBiomes.contains(b);
    }

    /**
     * Kills all monsters in a world
     * @param w World to kill all monsters in
     */
    public void purgeWorld(World w) {
        var wm = plugin.getMVWorldManager();
        var purger = wm.getTheWorldPurger();
        var mvWorld = wm.getMVWorld(w);

        purger.purgeWorld(mvWorld, List.of("MONSTERS"), false, false); // multiverse is stupid (purges all monsters, hopefully)
    }
    
    public Location getCenter() {
        return getCenterAtY(0);
    }
    
    public Location getCenterAtY(double y) {
        return center.loc(getMainWorld(), y);
    }

    public Location getHighCenter() {
        return getHighestLoc(getMainWorld(), center);
    }

    /**
     * Return player to lobby
     */
    public void escapePlayer(Player p) {
        plugin.getMVWorldManager().loadWorld("lobby");
        Location ls = lobbySpawn();
        p.setBedSpawnLocation(ls);
        p.teleport(ls);
        p.setGameMode(GameMode.ADVENTURE);
    }

    public boolean inGame(Player p) {
        return Arrays.asList(getGameWorlds()).contains(p.getWorld());
    }

    public void forEachWorld(Consumer<World> cons) {
        for (World w : getGameWorlds()) cons.accept(w);
    }

    public void forEachWorld(BiConsumer<World, WorldManager> cons) {
        for (World w : getGameWorlds()) cons.accept(w, this);
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        // https://github.com/PaperMC/Paper/issues/2270
        // You can speed up world creation by calling World#keepSpawnInMemory(false) 
        // inside a listener of WorldInitEvent. In this way, I've confirmed that 
        // Paper skipped generating the chunks around spawn area.
        e.getWorld().setKeepSpawnInMemory(false);
    }

    // GAMERULE STUFF
    public static void setDefaultGamerules(World w) {
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
        w.setGameRule(GameRule.COMMAND_BLOCK_OUTPUT, false); //
        w.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, false);
        w.setGameRule(GameRule.DISABLE_RAIDS, false);
        // w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true); // set elsewhere
        w.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        w.setGameRule(GameRule.DO_FIRE_TICK, true);
        w.setGameRule(GameRule.DO_INSOMNIA, false); //
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true); //
        w.setGameRule(GameRule.DO_LIMITED_CRAFTING, false);
        w.setGameRule(GameRule.DO_MOB_LOOT, true);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, true);
        w.setGameRule(GameRule.DO_TILE_DROPS, true);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, true);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        // w.setGameRule(GameRule.DROWNING_DAMAGE, true); // set elsewhere
        // w.setGameRule(GameRule.FALL_DAMAGE, true); // set elsewhere
        // w.setGameRule(GameRule.FIRE_DAMAGE, true); // set elsewhere
        w.setGameRule(GameRule.FORGIVE_DEAD_PLAYERS, true);
        // w.setGameRule(GameRule.FREEZE_DAMAGE, true); // set elsewhere
        w.setGameRule(GameRule.KEEP_INVENTORY, false);
        w.setGameRule(GameRule.LOG_ADMIN_COMMANDS, true);
        w.setGameRule(GameRule.MAX_COMMAND_CHAIN_LENGTH, 65536);
        w.setGameRule(GameRule.MAX_ENTITY_CRAMMING, 24);
        w.setGameRule(GameRule.MOB_GRIEFING, true);
        // w.setGameRule(GameRule.NATURAL_REGENERATION, false); // set elsewhere
        w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 100);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        w.setGameRule(GameRule.REDUCED_DEBUG_INFO, false);
        w.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, true);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0); //
        w.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, true);
        w.setGameRule(GameRule.UNIVERSAL_ANGER, false);
    }

}
