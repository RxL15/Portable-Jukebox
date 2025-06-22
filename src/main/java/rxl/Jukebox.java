package rxl;

import rxl.commands.JukeboxCommand;
import rxl.listeners.JukeboxListener;
import rxl.managers.JukeboxManager;
import rxl.managers.DataManager;
import rxl.managers.GUIManager;
import rxl.managers.RecipeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public final class Jukebox extends JavaPlugin {

    private static Jukebox instance;
    private static final Logger logger = Logger.getLogger("Jukebox");
    
    private JukeboxManager jukeboxManager;
    private GUIManager guiManager;
    private DataManager dataManager;
    private RecipeManager recipeManager;
    
    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        initializeManagers();
        initializeListeners();
        initializeCommands();
        initializeRecipes();
        
        logger.info("Jukebox plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (jukeboxManager != null) {
            jukeboxManager.cleanup();
            jukeboxManager.saveData();
        }
        
        if (guiManager != null) {
            guiManager.closeAllGUIs();
        }

        getServer().getScheduler().cancelTasks(this);
        logger.info("Jukebox plugin disabled successfully!");
    }
    
    private void initializeManagers() {
        dataManager = new DataManager(this);
        jukeboxManager = new JukeboxManager(this, dataManager);
        guiManager = new GUIManager(this);
        recipeManager = new RecipeManager(this);
    }
    
    private void initializeListeners() {
        getServer().getPluginManager().registerEvents(new JukeboxListener(this), this);
    }
    
    private void initializeCommands() {
        JukeboxCommand commandExecutor = new JukeboxCommand(this);
        Objects.requireNonNull(getCommand("jukebox")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("jukebox")).setTabCompleter(commandExecutor);
    }
    
    private void initializeRecipes() {
        recipeManager.registerRecipes();
    }
    
    public static Jukebox getInstance() {
        return instance;
    }
    
    public static Logger getPluginLogger() {
        return logger;
    }
    
    public JukeboxManager getJukeboxManager() {
        return jukeboxManager;
    }
    
    public GUIManager getGUIManager() {
        return guiManager;
    }
    
    @SuppressWarnings("unused")
    public DataManager getDataManager() {
        return dataManager;
    }
    
    @SuppressWarnings("unused")
    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
} 