package xyz.baz9k.UHCGame;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class UHCGame extends JavaPlugin {
    private TeamManager teamManager;
    private ConfigManager cfgManager;
    private GameManager gameManager;
    private HUDManager hudManager;
    private BossbarManager bbManager;

    @Override
    public void onEnable() {
        cfgManager = new ConfigManager();
        teamManager = new TeamManager(this);
        gameManager = new GameManager(this);
        hudManager = new HUDManager(this);
        bbManager = new BossbarManager(this);

        getServer().getPluginManager().registerEvents(gameManager, this);
        getServer().getPluginManager().registerEvents(hudManager, this);
        getServer().getPluginManager().registerEvents(cfgManager, this);

        Commands commands = new Commands(this);
        commands.registerAll();

        gameManager.loadManagerRefs();
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
    public ConfigManager getConfigManager() {
        return cfgManager;
    }
    public GameManager getGameManager() {
        return gameManager;
    }
    public HUDManager getHUDManager() {
        return hudManager;
    }
    public BossbarManager getBossbarManager() {
        return bbManager;
    }

    public MultiverseCore getMVCore() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Multiverse-Core");

        if (plugin instanceof MultiverseCore) return (MultiverseCore) plugin;
        throw new RuntimeException("Plugin Multiverse-Core is missing.");
    }

    public MVWorldManager getMVWorldManager() {
        return getMVCore().getMVWorldManager();
    }
}
