package rxl.managers;

import rxl.Jukebox;
import rxl.utils.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {
    
    private final Jukebox plugin;
    private final Map<Player, String> openGUIs = new ConcurrentHashMap<>();
    
    private static final int PLAYING_DISC_SLOT = 13;
    private static final List<Integer> QUEUE_SLOTS = List.of(0, 1, 9, 10, 18, 19);
    
    public GUIManager(Jukebox plugin) {
        this.plugin = plugin;
    }
    
    public void openJukeboxGUI(Player player, String jukeboxId) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Portable Jukebox", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        ItemStack[] jukeboxInventory = plugin.getJukeboxManager().getJukeboxInventory(jukeboxId);
        if (jukeboxInventory == null) {
            jukeboxInventory = new ItemStack[27];
            plugin.getJukeboxManager().setJukeboxInventory(jukeboxId, jukeboxInventory);
        }

        ItemStack glassPane = ItemUtil.createGrayGlassPane();
        for (int i = 0; i < 27; i++) {
            if (!QUEUE_SLOTS.contains(i) && i != PLAYING_DISC_SLOT) {
                gui.setItem(i, glassPane);
            }
        }
        
        for (int slot : QUEUE_SLOTS) {
            if (jukeboxInventory[slot] != null) {
                gui.setItem(slot, jukeboxInventory[slot]);
            }
        }
        
        ItemStack currentlyPlaying = plugin.getJukeboxManager().getPlayingDisc(jukeboxId);
        if (currentlyPlaying != null) {
            ItemStack displayDisc = currentlyPlaying.clone();
            ItemMeta meta = displayDisc.getItemMeta();
            if (meta != null) {
                if (plugin.getJukeboxManager().isPlaying(jukeboxId)) {
                    meta.lore(List.of(Component.text("▶ Now Playing", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
                    meta.addEnchant(Enchantment.LURE, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                displayDisc.setItemMeta(meta);
            }
            gui.setItem(PLAYING_DISC_SLOT, displayDisc);
        }
        player.openInventory(gui);
        openGUIs.put(player, jukeboxId);
    }
    
    public void handleGUIClose(Player player) {
        String jukeboxId = openGUIs.remove(player);
        if (jukeboxId != null) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            ItemStack[] newInventory = new ItemStack[27];
            Integer playingSlot = plugin.getJukeboxManager().getCurrentTrackSlot(jukeboxId);
            boolean playingDiscStillPresent = false;
            for (int slot : QUEUE_SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    newInventory[slot] = item.clone();
                    if (playingSlot != null && slot == playingSlot) {
                        playingDiscStillPresent = true;
                    }
                }
            }
            plugin.getJukeboxManager().setJukeboxInventory(jukeboxId, newInventory);
            if (plugin.getJukeboxManager().isPlaying(jukeboxId) && !playingDiscStillPresent) {
                plugin.getJukeboxManager().stopPlaying(jukeboxId, player);
            }
            plugin.getJukeboxManager().saveData();
        }
    }
    
    public String getOpenGUIJukeboxId(Player player) {
        return openGUIs.get(player);
    }
    
    public void closeAllGUIs() {
        for (Player player : openGUIs.keySet()) {
            player.closeInventory();
        }
        openGUIs.clear();
    }
} 