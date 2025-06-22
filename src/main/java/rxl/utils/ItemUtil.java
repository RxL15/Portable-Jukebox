package rxl.utils;

import rxl.Jukebox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemUtil {
    
    private static final NamespacedKey JUKEBOX_KEY = new NamespacedKey(Jukebox.getInstance(), "jukebox");
    private static final NamespacedKey JUKEBOX_ID_KEY = new NamespacedKey(Jukebox.getInstance(), "jukebox_id");
    
    public static ItemStack createJukebox() {
        ItemStack jukebox = new ItemStack(Material.JUKEBOX);
        ItemMeta meta = jukebox.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Portable Jukebox", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.setCustomModelData(1001);
            meta.getPersistentDataContainer().set(JUKEBOX_KEY, PersistentDataType.BOOLEAN, true);
            jukebox.setItemMeta(meta);
        }
        return jukebox;
    }
    
    public static ItemStack createJukebox(String uuid) {
        ItemStack jukebox = createJukebox();
        ItemMeta meta = jukebox.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(JUKEBOX_ID_KEY, PersistentDataType.STRING, uuid);
            jukebox.setItemMeta(meta);
        }
        return jukebox;
    }
    
    public static boolean isValidItem(ItemStack item) {
        return item != null && item.hasItemMeta();
    }
    
    public static boolean isJukebox(ItemStack item) {
        if (!isValidItem(item)) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(JUKEBOX_KEY, PersistentDataType.BOOLEAN);
    }
    
    public static String getJukeboxId(ItemStack item) {
        if (!isJukebox(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(JUKEBOX_ID_KEY, PersistentDataType.STRING);
    }
    
    public static ItemStack createGrayGlassPane() {
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glassPane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            glassPane.setItemMeta(meta);
        }
        return glassPane;
    }
    
    public static boolean isMusicDisc(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        return material.name().startsWith("MUSIC_DISC_");
    }
} 