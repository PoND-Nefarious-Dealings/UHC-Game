package xyz.baz9k.UHCGame;

import java.util.Locale;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import xyz.baz9k.UHCGame.util.Debug;

public class UHCGame extends JavaPlugin {
    private TeamManager teamManager;
    private GameManager gameManager;
    private HUDManager hudManager;
    private BossbarManager bbManager;
    private LangManager langManager;
    private ConfigManager configManager;
    private WorldManager worldManager;
    private SpreadPlayersManager spreadPlayersManager;
    private Recipes recipes;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        langManager = new LangManager(this);
        Debug.setLogger(getLogger());
        
        GameStage.setPlugin(this);
        teamManager = new TeamManager();
        gameManager = new GameManager(this);
        hudManager = new HUDManager(this);
        bbManager = new BossbarManager(this);
        configManager = new ConfigManager(this);
        worldManager = new WorldManager(this);
        spreadPlayersManager = new SpreadPlayersManager(this);
        recipes = new Recipes(this);

        Bukkit.getPluginManager().registerEvents(gameManager, this);
        Bukkit.getPluginManager().registerEvents(hudManager, this);
        Bukkit.getPluginManager().registerEvents(configManager, this);

        Commands commands = new Commands(this);
        commands.registerAll();
        recipes.registerAll();

        gameManager.loadManagerRefs();
    }

    @Override
    public void onDisable() {
        this.saveConfig();
    }
    
    public TeamManager getTeamManager() {
        return teamManager;
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
    public LangManager getLangManager() {
        return langManager;
    }
    public ConfigManager getConfigManager() {
        return configManager;
    }
    public WorldManager getWorldManager() {
        return worldManager;
    }
    public SpreadPlayersManager spreadPlayers() {
        return spreadPlayersManager;
    }

    public Recipes getRecipes() {
        return recipes;
    }

    public MultiverseCore getMVCore() {
        return getPlugin(MultiverseCore.class);
    }

    public MVWorldManager getMVWorldManager() {
        return getMVCore().getMVWorldManager();
    }

    public static Locale getLocale() {
        return LangManager.getLocale();
    }
}
