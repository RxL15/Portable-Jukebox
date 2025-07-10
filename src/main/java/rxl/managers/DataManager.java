package rxl.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import rxl.Jukebox;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class DataManager {
    
    private final File dataFile;
    private final Gson gson;
    
    public DataManager(Jukebox plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "jukebox-data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        if (!plugin.getDataFolder().exists()) {
            boolean created = plugin.getDataFolder().mkdirs();
            if (!created) {
                Jukebox.getPluginLogger().warning("Failed to create plugin data directory");
            }
        }
    }
    
    public void saveJukeboxData(Map<String, ItemStack[]> jukeboxContents) {
        try {
            Map<String, SerializableItemStack[]> serializableData = new HashMap<>();
            for (Map.Entry<String, ItemStack[]> entry : jukeboxContents.entrySet()) {
                if (entry.getValue() != null) {
                    SerializableItemStack[] itemStacks = new SerializableItemStack[entry.getValue().length];
                    for (int i = 0; i < entry.getValue().length; i++) {
                        if (entry.getValue()[i] != null) {
                            itemStacks[i] = new SerializableItemStack(entry.getValue()[i]);
                        }
                    }
                    serializableData.put(entry.getKey(), itemStacks);
                }
            }
            
            String json = gson.toJson(serializableData);
            try (FileWriter writer = new FileWriter(dataFile)) {
                writer.write(json);
            }
        } catch (IOException e) {
            Jukebox.getPluginLogger().log(Level.SEVERE, "Failed to save Jukebox data", e);
        }
    }
    
    public Map<String, ItemStack[]> loadJukeboxData() {
        Map<String, ItemStack[]> jukeboxContents = new HashMap<>();
        if (!dataFile.exists()) {
            Jukebox.getPluginLogger().info("No existing Jukebox data file found. Starting with empty data.");
            return jukeboxContents;
        }
        
        try {
            String json = Files.readString(dataFile.toPath());
            Type type = new TypeToken<Map<String, SerializableItemStack[]>>(){}.getType();
            Map<String, SerializableItemStack[]> serializableData = gson.fromJson(json, type);
            if (serializableData != null) {
                for (Map.Entry<String, SerializableItemStack[]> entry : serializableData.entrySet()) {
                    ItemStack[] itemStacks = new ItemStack[entry.getValue().length];
                    for (int i = 0; i < entry.getValue().length; i++) {
                        if (entry.getValue()[i] != null) {
                            itemStacks[i] = entry.getValue()[i].toItemStack();
                        }
                    }
                    jukeboxContents.put(entry.getKey(), itemStacks);
                }
            }
            Jukebox.getPluginLogger().info("Jukebox data loaded successfully! Loaded " + jukeboxContents.size() + " Jukebox inventories.");
        } catch (Exception e) {
            Jukebox.getPluginLogger().log(Level.SEVERE, "Failed to load Jukebox data", e);
        }
        return jukeboxContents;
    }

    private static class SerializableItemStack {
        private final String material;
        private final int amount;
        private final int customModelData;
        
        public SerializableItemStack(ItemStack itemStack) {
            this.material = itemStack.getType().name();
            this.amount = itemStack.getAmount();
            ItemMeta meta = itemStack.getItemMeta();
            this.customModelData = meta != null && meta.hasCustomModelData() ? 
                meta.getCustomModelData() : 0;
        }
        
        public ItemStack toItemStack() {
            try {
                Material mat = Material.valueOf(material);
                ItemStack item = new ItemStack(mat, amount);
                if (customModelData > 0) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(customModelData);
                        item.setItemMeta(meta);
                    }
                }
                
                return item;
            } catch (IllegalArgumentException e) {
                Jukebox.getPluginLogger().warning("Unknown material in saved data: " + material);
                return null;
            }
        }
    }
} 