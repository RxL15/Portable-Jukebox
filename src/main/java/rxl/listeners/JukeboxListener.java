package rxl.listeners;

import rxl.Jukebox;
import rxl.utils.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class JukeboxListener implements Listener {
    
    private final Jukebox plugin;
    private static final int PLAYING_DISC_SLOT = 13;
    private static final List<Integer> QUEUE_SLOTS = List.of(0, 1, 9, 10, 18, 19);
    
    public JukeboxListener(Jukebox plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        if (!ItemUtil.isJukebox(item)) {
            return;
        }
        
        if (!player.hasPermission("jukebox.use")) {
            return;
        }
        
        event.setCancelled(true);
        String jukeboxId = ItemUtil.getJukeboxId(item);
        if (jukeboxId == null) {
            jukeboxId = plugin.getJukeboxManager().createJukeboxId();
            player.getInventory().setItemInMainHand(ItemUtil.createJukebox(jukeboxId));
        }
        
        if (player.isSneaking()) {
            plugin.getGUIManager().openJukeboxGUI(player, jukeboxId);
        } else {
            plugin.getJukeboxManager().togglePlaying(jukeboxId, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String jukeboxId = plugin.getGUIManager().getOpenGUIJukeboxId(player);
        if (jukeboxId == null) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        Component jukeboxTitle = Component.text("Portable Jukebox", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
        if (!event.getView().title().equals(jukeboxTitle)) {
            return;
        }
        
        int clickedSlot = event.getSlot();

        if (event.getClickedInventory() == topInventory) {
            if (QUEUE_SLOTS.contains(clickedSlot)) {
                ItemStack cursorItem = event.getCursor();
                if (!cursorItem.getType().isAir() && !ItemUtil.isMusicDisc(cursorItem)) {
                    event.setCancelled(true);
                }
                
                Integer playingSlot = plugin.getJukeboxManager().getCurrentTrackSlot(jukeboxId);
                if (playingSlot != null && playingSlot == clickedSlot) {
                    if (event.getAction() == InventoryAction.PICKUP_ALL ||
                        event.getAction() == InventoryAction.PICKUP_HALF ||
                        event.getAction() == InventoryAction.PICKUP_SOME ||
                        event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        plugin.getJukeboxManager().stopPlaying(jukeboxId, player);
                    }
                }

            } else if (clickedSlot == PLAYING_DISC_SLOT) {
                plugin.getJukeboxManager().togglePlaying(jukeboxId, player);
                event.setCancelled(true);
            } else {
                event.setCancelled(true);
            }
        } else if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack clickedItem = event.getCurrentItem();
                if (ItemUtil.isMusicDisc(clickedItem)) {
                    for (int slot : QUEUE_SLOTS) {
                        if (topInventory.getItem(slot) == null) {
                            topInventory.setItem(slot, clickedItem.clone());
                            event.setCurrentItem(null);
                            break;
                        }
                    }
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String jukeboxId = plugin.getGUIManager().getOpenGUIJukeboxId(player);
        if (jukeboxId == null) {
            return;
        }

        plugin.getGUIManager().handleGUIClose(player);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        if (!ItemUtil.isJukebox(droppedItem)) {
            return;
        }
        
        String jukeboxId = ItemUtil.getJukeboxId(droppedItem);
        if (jukeboxId != null && plugin.getJukeboxManager().isPlaying(jukeboxId)) {
            plugin.getJukeboxManager().stopPlayingById(jukeboxId);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClickTransfer(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        String jukeboxId = plugin.getGUIManager().getOpenGUIJukeboxId(player);
        if (jukeboxId != null) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        if (isJukeboxTransferEvent(event, clickedItem, cursorItem)) {
            handleJukeboxTransfer(event, clickedItem, cursorItem);
        }
    }
    
    private boolean isJukeboxTransferEvent(InventoryClickEvent event, ItemStack clickedItem, ItemStack cursorItem) {
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && ItemUtil.isJukebox(clickedItem)) {
            return true;
        }
        
        if ((event.getAction() == InventoryAction.PLACE_ALL || 
             event.getAction() == InventoryAction.PLACE_ONE || 
             event.getAction() == InventoryAction.PLACE_SOME) && ItemUtil.isJukebox(cursorItem)) {
            return true;
        }

        return event.getAction() == InventoryAction.HOTBAR_SWAP && ItemUtil.isJukebox(clickedItem);
    }
    
    private void handleJukeboxTransfer(InventoryClickEvent event, ItemStack clickedItem, ItemStack cursorItem) {
        if (isTransferWithinPlayerInventory(event)) {
            return;
        }
        
        if (ItemUtil.isJukebox(clickedItem)) {
            String jukeboxId = ItemUtil.getJukeboxId(clickedItem);
            if (jukeboxId != null && plugin.getJukeboxManager().isPlaying(jukeboxId)) {
                plugin.getJukeboxManager().stopPlayingById(jukeboxId);
            }
        }
        
        if (ItemUtil.isJukebox(cursorItem)) {
            String jukeboxId = ItemUtil.getJukeboxId(cursorItem);
            if (jukeboxId != null && plugin.getJukeboxManager().isPlaying(jukeboxId)) {
                plugin.getJukeboxManager().stopPlayingById(jukeboxId);
            }
        }
    }
    
    private boolean isTransferWithinPlayerInventory(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory bottomInventory = event.getView().getBottomInventory();
        
        if (topInventory.getType() == InventoryType.CRAFTING && bottomInventory.getType() == InventoryType.PLAYER) {
            return true;
        }
        
        if (topInventory.getType() == InventoryType.PLAYER && bottomInventory.getType() == InventoryType.PLAYER) {
            return true;
        }
        
        if (clickedInventory != null && clickedInventory.getType() == InventoryType.PLAYER) {
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                return true;
            }
            
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                return topInventory.getType() == InventoryType.CRAFTING;
            }
        }
        return false;
    }
} 